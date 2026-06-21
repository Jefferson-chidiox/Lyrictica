package com.oss.euphoriae.data.repository

import android.util.Log
import com.oss.euphoriae.data.local.MusicDao
import com.oss.euphoriae.data.model.AudiusPlaylist
import com.oss.euphoriae.data.model.AudiusTrack
import com.oss.euphoriae.data.model.CachedAudiusShelf
import com.oss.euphoriae.data.model.HomeFeedItem
import com.oss.euphoriae.data.model.OnlineSource
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.ShelfType
import com.oss.euphoriae.data.remote.AudiusService
import com.oss.euphoriae.data.remote.AudiusService.SpinampTrackNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Orchestrates fetching Audius online shelves and caching the results in Room.
 *
 * Strategy:
 * 1. On cold launch, emit cached shelves immediately if they're < [CACHE_MAX_AGE_MS] old
 * 2. Then refetch in background and update
 * 3. On failure, fall back to cached data or empty (graceful degradation)
 */
class AudiusRepository(
    private val audiusService: AudiusService,
    private val musicDao: MusicDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val defaultGenres = listOf("Electronic", "Hip-Hop/Rap", "Pop")

    fun getOnlineShelves(localGenres: List<String>): Flow<List<HomeFeedItem>> = flow {
        val cached = loadCachedShelves(localGenres)
        if (cached.isNotEmpty()) {
            emit(cached)
            return@flow // DO NOT refetch in background if we have valid cache to prevent UI jumping
        }

        val fresh = fetchAllShelves(localGenres, isRefresh = false)
        if (fresh.isNotEmpty()) {
            emit(fresh)
        } else if (cached.isEmpty()) {
            emit(emptyList())
        }
    }

    fun refreshShelves(localGenres: List<String>): Flow<List<HomeFeedItem>> = flow {
        emit(fetchAllShelves(localGenres, isRefresh = true))
    }

    private suspend fun fetchAllShelves(
        localGenres: List<String>,
        isRefresh: Boolean = false
    ): List<HomeFeedItem> = withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                val customGenres = localGenres.ifEmpty { defaultGenres }.take(3)
                val backupGenres = defaultGenres.filter { it !in customGenres }

                val requestLimit = if (isRefresh) 30 else 15
                val shelfSize = 15

                val trendingDeferred = async {
                    mapSpinampTracks(audiusService.getSpinampTrendingTracks(limit = requestLimit))
                }
                val newReleasesDeferred = async {
                    mapSpinampTracks(audiusService.getSpinampTracks(limit = requestLimit, offset = 0))
                }
                val customGenreDeferreds = customGenres.map { genre ->
                    genre to async {
                        mapSpinampTracks(
                            audiusService.getSpinampTracksByGenre(
                                genre = genre,
                                limit = requestLimit
                            )
                        )
                    }
                }
                val backupGenreDeferreds = backupGenres.map { genre ->
                    genre to async {
                        mapSpinampTracks(
                            audiusService.getSpinampTracksByGenre(
                                genre = genre,
                                limit = requestLimit
                            )
                        )
                    }
                }
                val undergroundDeferred = async {
                    mapSpinampTracks(
                        audiusService.getSpinampTracks(
                            limit = requestLimit * 2,
                            offset = requestLimit
                        )
                    )
                }

                fun List<OnlineTrack>.processTracks(): List<OnlineTrack> {
                    return if (isRefresh) this.shuffled().take(shelfSize) else this.take(shelfSize)
                }

                val newReleaseTracks = newReleasesDeferred.await()
                val trending = trendingDeferred.await().ifEmpty { newReleaseTracks }.processTracks()
                val newReleases = newReleaseTracks.processTracks()
                val customGenreResults = customGenreDeferreds.map { (genre, deferred) ->
                    genre to deferred.await().processTracks()
                }
                val backupGenreResults = backupGenreDeferreds.map { (genre, deferred) ->
                    genre to deferred.await().processTracks()
                }
                val underground = undergroundDeferred.await().processTracks()
                val playlists = emptyList<AudiusPlaylist>() // Spinamp does not expose a drop-in playlist shelf here yet

                val shelves = mutableListOf<HomeFeedItem>()

                if (newReleases.isNotEmpty()) {
                    cacheTracks("new_releases", newReleases)
                    shelves.add(
                        HomeFeedItem.OnlineTrackShelf(
                            stableId = "shelf_new_releases",
                            title = "Fresh Online Releases",
                            tracks = newReleases,
                            type = ShelfType.NEW_RELEASES,
                            source = OnlineSource.AUDIUS
                        )
                    )
                }

                if (trending.isNotEmpty()) {
                    cacheTracks("trending_week", trending)
                    shelves.add(
                        HomeFeedItem.OnlineTrackShelf(
                            stableId = "shelf_trending_week",
                            title = "Mixed Momentum",
                            tracks = trending,
                            type = ShelfType.TRENDING,
                            source = OnlineSource.AUDIUS
                        )
                    )
                }

                val activeGenreShelves = mutableListOf<Pair<String, List<OnlineTrack>>>()
                for ((genre, tracks) in customGenreResults) {
                    if (tracks.isNotEmpty()) {
                        activeGenreShelves.add(genre to tracks)
                    }
                }

                if (activeGenreShelves.size < 3) {
                    for ((fallbackGenre, fallbackTracks) in backupGenreResults) {
                        if (activeGenreShelves.size >= 3) break
                        if (fallbackTracks.isNotEmpty()) {
                            activeGenreShelves.add(fallbackGenre to fallbackTracks)
                        }
                    }
                }

                for ((genre, tracks) in activeGenreShelves) {
                    val safeGenre = genre.replace("/", "_").replace(" ", "_").lowercase()
                    cacheTracks("genre_$safeGenre", tracks)
                    shelves.add(
                        HomeFeedItem.OnlineTrackShelf(
                            stableId = "shelf_genre_$safeGenre",
                            title = "$genre Spotlight",
                            tracks = tracks,
                            type = ShelfType.GENRE,
                            source = OnlineSource.AUDIUS
                        )
                    )
                }

                if (underground.isNotEmpty()) {
                    cacheTracks("trending_underground", underground)
                    shelves.add(
                        HomeFeedItem.OnlineTrackShelf(
                            stableId = "shelf_trending_underground",
                            title = "Indie Signal",
                            tracks = underground,
                            type = ShelfType.UNDERGROUND,
                            source = OnlineSource.AUDIUS
                        )
                    )
                }

                if (playlists.isNotEmpty()) {
                    cachePlaylists("trending_playlists", playlists)
                    shelves.add(
                        HomeFeedItem.OnlinePlaylistShelf(
                            stableId = "shelf_trending_playlists",
                            title = "Community Mixes",
                            playlists = playlists,
                            source = OnlineSource.AUDIUS
                        )
                    )
                }

                shelves
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch online shelves", e)
            emptyList()
        }
    }

    private suspend fun loadCachedShelves(localGenres: List<String>): List<HomeFeedItem> = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
            val shelves = mutableListOf<HomeFeedItem>()

            loadCachedTrackShelf("new_releases", "Fresh Online Releases", ShelfType.NEW_RELEASES, cutoff)?.let {
                shelves.add(it)
            }

            loadCachedTrackShelf("trending_week", "Mixed Momentum", ShelfType.TRENDING, cutoff)?.let {
                shelves.add(it)
            }

            val genresToTry = (localGenres + defaultGenres).distinct().take(6)
            var genreCount = 0
            for (genre in genresToTry) {
                if (genreCount >= 3) break
                val safeGenre = genre.replace("/", "_").replace(" ", "_").lowercase()
                loadCachedTrackShelf("genre_$safeGenre", "$genre Spotlight", ShelfType.GENRE, cutoff)?.let {
                    shelves.add(it)
                    genreCount++
                }
            }

            loadCachedTrackShelf("trending_underground", "Indie Signal", ShelfType.UNDERGROUND, cutoff)?.let {
                shelves.add(it)
            }

            loadCachedPlaylistShelf("trending_playlists", "Community Mixes", cutoff)?.let {
                shelves.add(it)
            }

            shelves
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached shelves", e)
            emptyList()
        }
    }

    private suspend fun loadCachedTrackShelf(
        id: String,
        title: String,
        type: ShelfType,
        cutoff: Long
    ): HomeFeedItem.OnlineTrackShelf? {
        val cached = musicDao.getCachedShelf(id) ?: return null
        if (cached.fetchedAt < cutoff) return null
        return try {
            val tracks: List<OnlineTrack> = json.decodeFromString(cached.shelfJson)
            val playableTracks = tracks.filter { it.isPlayable }
            if (playableTracks.isEmpty()) null
            else HomeFeedItem.OnlineTrackShelf(
                stableId = "shelf_$id",
                title = title,
                tracks = playableTracks,
                type = type,
                source = OnlineSource.AUDIUS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached shelf $id", e)
            null
        }
    }

    private suspend fun loadCachedPlaylistShelf(
        id: String,
        title: String,
        cutoff: Long
    ): HomeFeedItem.OnlinePlaylistShelf? {
        val cached = musicDao.getCachedShelf(id) ?: return null
        if (cached.fetchedAt < cutoff) return null
        return try {
            val playlists: List<AudiusPlaylist> = json.decodeFromString(cached.shelfJson)
            if (playlists.isEmpty()) null
            else HomeFeedItem.OnlinePlaylistShelf(
                stableId = "shelf_$id",
                title = title,
                playlists = playlists,
                source = OnlineSource.AUDIUS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached playlist shelf $id", e)
            null
        }
    }

    private suspend fun cacheTracks(id: String, tracks: List<OnlineTrack>) {
        try {
            val jsonStr = json.encodeToString(tracks)
            musicDao.insertCachedShelf(CachedAudiusShelf(shelfId = id, shelfJson = jsonStr))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache shelf $id", e)
        }
    }

    private suspend fun cachePlaylists(id: String, playlists: List<AudiusPlaylist>) {
        try {
            val jsonStr = json.encodeToString(playlists)
            musicDao.insertCachedShelf(CachedAudiusShelf(shelfId = id, shelfJson = jsonStr))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache playlist shelf $id", e)
        }
    }

    suspend fun getTrendingTracks(limit: Int = 20): List<OnlineTrack> {
        return try {
            val fetchLimit = limit.coerceIn(1, 50)
            mapSpinampTracks(audiusService.getSpinampTrendingTracks(limit = fetchLimit))
                .take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Spinamp trending tracks", e)
            emptyList()
        }
    }

    suspend fun getNewReleaseTracks(limit: Int = 20): List<OnlineTrack> {
        return try {
            val fetchLimit = limit.coerceIn(1, 50)
            mapSpinampTracks(audiusService.getSpinampTracks(limit = fetchLimit, offset = 0))
                .take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Spinamp releases", e)
            emptyList()
        }
    }

    suspend fun getUndergroundTracks(limit: Int = 20): List<OnlineTrack> {
        return try {
            val requestLimit = (limit * 2).coerceIn(2, 60)
            val offset = limit.coerceIn(1, 30)
            mapSpinampTracks(
                audiusService.getSpinampTracks(
                    limit = requestLimit,
                    offset = offset
                )
            ).take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Spinamp deep cuts", e)
            emptyList()
        }
    }

    suspend fun getTracksByGenre(genre: String, limit: Int = 20): List<OnlineTrack> {
        if (genre.isBlank()) return emptyList()
        return try {
            val fetchLimit = (limit * 2).coerceIn(1, 30)
            val genreMatches = mapSpinampTracks(
                audiusService.getSpinampTracksByGenre(
                    genre = genre,
                    limit = fetchLimit
                )
            )
            if (genreMatches.isNotEmpty()) {
                genreMatches.take(limit)
            } else {
                mapSpinampTracks(audiusService.getSpinampTracks(limit = fetchLimit, offset = 0))
                    .shuffled()
                    .take(limit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Spinamp genre tracks for $genre", e)
            emptyList()
        }
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        return try {
            mapSpinampTracks(audiusService.searchSpinampTracks(query = query, limit = limit))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search Spinamp tracks for $query", e)
            emptyList()
        }
    }

    suspend fun getRelatedTracks(genre: String, limit: Int = 20): List<OnlineTrack> {
        return getTracksByGenre(genre = genre, limit = limit)
    }

    private fun mapPlayableTracks(tracks: List<AudiusTrack>): List<OnlineTrack> {
        return tracks.mapNotNull { it.toOnlineTrack() }
    }

    private fun mapSpinampTracks(tracks: List<SpinampTrackNode>): List<OnlineTrack> {
        return tracks.mapNotNull { it.toOnlineTrack() }
    }

    private fun SpinampTrackNode.toOnlineTrack(): OnlineTrack? {
        val streamUrl = lossyAudioUrl?.trim().orEmpty()
        if (streamUrl.isBlank()) return null

        val durationFloat = duration?.toFloatOrNull() ?: 0f
        val genreLabel = tagsByEntityId?.nodes.orEmpty()
            .firstOrNull { it.type.equals("genre", ignoreCase = true) && !it.value.isNullOrBlank() }
            ?.value
            ?.trim()
            .orEmpty()
            .ifBlank {
                tagsByEntityId?.nodes.orEmpty()
                    .firstOrNull { !it.value.isNullOrBlank() }
                    ?.value
                    ?.trim()
                    .orEmpty()
            }
            .ifBlank { "Web3" }

        return OnlineTrack(
            id = id,
            provider = OnlineSource.AUDIUS, // Temporary Spinamp-backed replacement for the former Audius slot.
            title = title,
            artist = artistByArtistId?.name ?: "Unknown Artist",
            streamUrl = streamUrl,
            artworkUrl = lossyArtworkUrl,
            genre = genreLabel,
            durationMs = (durationFloat * 1000).toLong()
        )
    }

    private fun AudiusTrack.toOnlineTrack(): OnlineTrack? {
        val streamUrl = stream?.url?.trim().orEmpty()
        if (streamUrl.isBlank()) return null
        return OnlineTrack(
            id = id,
            provider = OnlineSource.AUDIUS,
            title = title,
            artist = user?.name.orEmpty(),
            streamUrl = streamUrl,
            artworkUrl = artwork?.medium ?: artwork?.small,
            genre = genre,
            durationMs = duration.toLong() * 1000L
        )
    }

    suspend fun getPlaylistTracks(playlistId: String): List<OnlineTrack> {
        if (playlistId.isBlank()) return emptyList()
        return try {
            mapPlayableTracks(audiusService.getPlaylistTracks(playlistId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get playlist tracks for $playlistId", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "AudiusRepository"
        private const val CACHE_MAX_AGE_MS = 3 * 60 * 60 * 1000L // 3 hours
    }
}
