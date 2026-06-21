package com.oss.euphoriae.data.repository

import android.util.Log
import com.oss.euphoriae.data.model.HomeFeedItem
import com.oss.euphoriae.data.model.OnlineSource
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.ShelfType
import com.oss.euphoriae.data.remote.NcsApiTrack
import com.oss.euphoriae.data.remote.NcsService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class NcsRepository(
    private val ncsService: NcsService
) {

    fun getOnlineShelves(localGenres: List<String>): Flow<List<HomeFeedItem>> = flow {
        emit(emptyList())
        emit(fetchShelves(localGenres))
    }

    fun refreshShelves(localGenres: List<String>): Flow<List<HomeFeedItem>> = flow {
        emit(fetchShelves(localGenres))
    }

    suspend fun getFreshTracks(limit: Int = 20): List<OnlineTrack> {
        return fetchDistinctTracks(
            genre = NcsGenre.FRESH,
            desiredCount = limit.coerceIn(4, 30)
        )
    }

    suspend fun getTracksByGenre(genre: String, limit: Int = 20): List<OnlineTrack> {
        val resolvedGenre = resolveGenre(genre) ?: NcsGenre.ALL
        return fetchDistinctTracks(
            genre = resolvedGenre,
            desiredCount = limit.coerceIn(4, 30)
        )
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        return try {
            ncsService.searchTracks(searchQuery = query)
                .mapNotNull { it.toSearchTrack() }
                .filter { it.isPlayable }
                .distinctBy { it.streamUrl }
                .take(limit.coerceIn(4, 30))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search NCS tracks for $query", e)
            emptyList()
        }
    }

    suspend fun getRelatedTracks(genre: String, limit: Int = 20): List<OnlineTrack> {
        return getTracksByGenre(genre = genre, limit = limit)
    }

    suspend fun getPopularTracks(limit: Int = 20): List<OnlineTrack> {
        return fetchDistinctTracks(
            genre = NcsGenre.POPULAR,
            desiredCount = limit.coerceIn(4, 30)
        )
    }

    suspend fun getRandomTracks(limit: Int = 20): List<OnlineTrack> {
        return fetchDistinctTracks(
            genre = NcsGenre.RANDOM,
            desiredCount = limit.coerceIn(4, 30)
        )
    }

    private suspend fun fetchShelves(localGenres: List<String>): List<HomeFeedItem> = coroutineScope {
        try {
            val freshAsync = async { fetchDistinctTracks(NcsGenre.FRESH, 10) }
            val trendingAsync = async { fetchDistinctTracks(NcsGenre.POPULAR, 10) }
            val randomAsync = async { fetchDistinctTracks(NcsGenre.RANDOM, 10) }
            val genreAsyncs = resolveGenreCandidates(localGenres).map { genre ->
                async { fetchDistinctTracks(genre, 10) }
            }

            val allTracks = buildList {
                addAll(freshAsync.await())
                addAll(trendingAsync.await())
                addAll(randomAsync.await())
                genreAsyncs.awaitAll().forEach { addAll(it) }
            }.distinctBy { it.streamUrl }.take(30)

            if (allTracks.isNotEmpty()) {
                listOf(
                    HomeFeedItem.OnlineTrackShelf(
                        stableId = "shelf_ncs_discover",
                        title = "NCS Discover",
                        tracks = allTracks,
                        type = ShelfType.NEW_RELEASES,
                        source = OnlineSource.NCS
                    )
                )
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch NCS shelves", e)
            emptyList()
        }
    }

    private suspend fun fetchDistinctTracks(
        genre: NcsGenre,
        desiredCount: Int
    ): List<OnlineTrack> = coroutineScope {
        val tracksByUrl = LinkedHashMap<String, OnlineTrack>()
        var attempts = 0

        while (tracksByUrl.size < desiredCount && attempts < MAX_ATTEMPTS_PER_SHELF) {
            val batchSize = minOf(
                REQUEST_BATCH_SIZE,
                MAX_ATTEMPTS_PER_SHELF - attempts,
                (desiredCount - tracksByUrl.size).coerceAtLeast(1)
            )
            attempts += batchSize

            val batch = List(batchSize) {
                async { ncsService.searchTracks(genre.id) }
            }

            val newTracks = batch.awaitAll().flatten()
            if (newTracks.isEmpty()) {
                delay(2000L)
            }
            newTracks.forEach { apiTrack ->
                apiTrack.toOnlineTrack(genre)
                    ?.takeIf { it.isPlayable }
                    ?.let { track -> tracksByUrl.putIfAbsent(track.streamUrl, track) }
            }
        }

        tracksByUrl.values.take(desiredCount)
    }

    private fun resolveGenreCandidates(localGenres: List<String>): List<NcsGenre> {
        val resolved = linkedSetOf<NcsGenre>()
        localGenres.mapNotNullTo(resolved) { resolveGenre(it) }
        if (resolved.isEmpty()) {
            resolved.addAll(FALLBACK_GENRES)
        }
        return resolved
            .filter { it != NcsGenre.ALL }
            .take(MAX_GENRE_SHELVES)
    }

    private fun resolveGenre(rawGenre: String): NcsGenre? {
        val normalized = rawGenre
            .trim()
            .lowercase()
            .replace("&", "and")
            .replace("/", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")

        if (normalized.isBlank()) return null

        if (normalized == "all") return NcsGenre.ALL
        if (normalized == "fresh") return NcsGenre.FRESH
        if (normalized == "popular") return NcsGenre.POPULAR
        if (normalized == "random") return NcsGenre.RANDOM

        return exactGenreMatches[normalized]
            ?: when {
                "electro house" in normalized -> NcsGenre("48", "Electro House")
                "deep house" in normalized -> NcsGenre("66", "Deep House")
                "future bass" in normalized -> NcsGenre("17", "Future Bass")
                "drum and bass" in normalized || "dnb" in normalized -> NcsGenre("3", "Drum & Bass")
                "hip hop" in normalized || "rap" in normalized -> NcsGenre("32", "Alternative Hip-Hop")
                "lofi" in normalized -> NcsGenre("60", "Lofi Hip-Hop")
                "phonk" in normalized -> NcsGenre("16", "Phonk")
                "pop" in normalized -> NcsGenre("19", "Pop")
                "house" in normalized -> NcsGenre("10", "House")
                "dubstep" in normalized -> NcsGenre("5", "Dubstep")
                "edm" in normalized -> NcsGenre("6", "EDM")
                "electronic" in normalized -> NcsGenre("7", "Electronic")
                "trap" in normalized -> NcsGenre("14", "Trap")
                "garage" in normalized -> NcsGenre("51", "Garage")
                "techno" in normalized -> NcsGenre("80", "Techno")
                else -> NcsGenre(id = normalized.replace(" ", "-"), label = rawGenre.trim())
            }
    }

    private fun NcsApiTrack.toOnlineTrack(genre: NcsGenre): OnlineTrack? {
        val cleanedTitle = title.trim()
        val cleanedArtists = artists.trim()
        val cleanedAudioUrl = audioUrl.trim()
        if (cleanedTitle.isBlank() || cleanedAudioUrl.isBlank()) return null

        return OnlineTrack(
            id = "$cleanedTitle|$cleanedArtists|$cleanedAudioUrl".hashCode().toString(),
            provider = OnlineSource.NCS,
            title = cleanedTitle,
            artist = cleanedArtists,
            streamUrl = cleanedAudioUrl,
            artworkUrl = coverUrl,
            genre = genre.label
        )
    }

    private fun NcsApiTrack.toSearchTrack(): OnlineTrack? {
        val cleanedTitle = title.trim()
        val cleanedArtists = artists.trim()
        val cleanedAudioUrl = audioUrl.trim()
        if (cleanedTitle.isBlank() || cleanedAudioUrl.isBlank()) return null

        return OnlineTrack(
            id = "$cleanedTitle|$cleanedArtists|$cleanedAudioUrl".hashCode().toString(),
            provider = OnlineSource.NCS,
            title = cleanedTitle,
            artist = cleanedArtists,
            streamUrl = cleanedAudioUrl,
            artworkUrl = coverUrl,
            genre = ""
        )
    }

    private data class NcsGenre(
        val id: String,
        val label: String
    ) {
        companion object {
            val ALL = NcsGenre(id = "all", label = "All")
            val FRESH = NcsGenre(id = "fresh", label = "Fresh")
            val POPULAR = NcsGenre(id = "popular", label = "Popular")
            val RANDOM = NcsGenre(id = "random", label = "Random")
        }
    }

    companion object {
        private const val TAG = "NcsRepository"
        private const val MAX_GENRE_SHELVES = 3
        private const val MAX_ATTEMPTS_PER_SHELF = 3
        private const val REQUEST_BATCH_SIZE = 1

        private val FALLBACK_GENRES = listOf(
            NcsGenre("7", "Electronic"),
            NcsGenre("17", "Future Bass")
        )

        private val exactGenreMatches = mapOf(
            "electronic" to NcsGenre("7", "Electronic"),
            "hip hop rap" to NcsGenre("32", "Alternative Hip-Hop"),
            "hip hop" to NcsGenre("32", "Alternative Hip-Hop"),
            "rap" to NcsGenre("32", "Alternative Hip-Hop"),
            "pop" to NcsGenre("19", "Pop"),
            "house" to NcsGenre("10", "House"),
            "future house" to NcsGenre("8", "Future House"),
            "electro house" to NcsGenre("48", "Electro House"),
            "deep house" to NcsGenre("66", "Deep House"),
            "drum and bass" to NcsGenre("3", "Drum & Bass"),
            "drum bass" to NcsGenre("3", "Drum & Bass"),
            "dubstep" to NcsGenre("5", "Dubstep"),
            "edm" to NcsGenre("6", "EDM"),
            "future bass" to NcsGenre("17", "Future Bass"),
            "bass house" to NcsGenre("18", "Bass House"),
            "phonk" to NcsGenre("16", "Phonk"),
            "trap" to NcsGenre("14", "Trap"),
            "garage" to NcsGenre("51", "Garage"),
            "lofi hip hop" to NcsGenre("60", "Lofi Hip-Hop"),
            "melodic dubstep" to NcsGenre("12", "Melodic Dubstep"),
            "melodic house" to NcsGenre("54", "Melodic House"),
            "tech house" to NcsGenre("73", "Tech House"),
            "techno" to NcsGenre("80", "Techno")
        )
    }
}
