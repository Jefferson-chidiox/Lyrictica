package com.oss.euphoriae.search

import com.lyrictica.lyrics.LyricsQueryNormalizer
import com.lyrictica.lyrics.MusixmatchClient
import com.oss.euphoriae.data.repository.AudiusRepository
import com.oss.euphoriae.data.repository.MusicRepository
import com.oss.euphoriae.data.repository.NcsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

private const val MUSIXMATCH_LIMIT = 8
private const val ONLINE_LIMIT = 8
private const val ONLINE_MATCH_POOL = 16
private const val LOCAL_LIMIT = 10

internal class HomeSearchRepository(
    private val musicRepository: MusicRepository,
    private val audiusRepository: AudiusRepository,
    private val ncsRepository: NcsRepository,
    private val musixmatchClient: MusixmatchClient = MusixmatchClient(userAgent = "Lyrictica/1.0 (https://github.com)")
) {

    suspend fun search(
        query: String,
        includeOnlineProviders: Boolean = true
    ): HomeSearchResults = coroutineScope {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return@coroutineScope HomeSearchResults()
        val musixmatchQuery = LyricsQueryNormalizer.searchTitle(trimmedQuery)

        val localSongsDeferred = async {
            runCatching { musicRepository.getAllSongs().first() }.getOrDefault(emptyList())
        }
        val audiusDeferred = async {
            if (includeOnlineProviders) {
                runCatching { audiusRepository.searchTracks(trimmedQuery, limit = ONLINE_MATCH_POOL) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
        val ncsDeferred = async {
            if (includeOnlineProviders) {
                runCatching { ncsRepository.searchTracks(trimmedQuery, limit = ONLINE_MATCH_POOL) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
        val musixmatchDeferred = async {
            runCatching { musixmatchClient.search(query = musixmatchQuery) }
                .getOrDefault(emptyList())
        }

        val localCatalog = localSongsDeferred.await()
        val rankedAudius = if (includeOnlineProviders) {
            HomeSearchOrganizer.rankOnlineTracks(
                query = trimmedQuery,
                tracks = audiusDeferred.await(),
                limit = ONLINE_MATCH_POOL
            )
        } else {
            emptyList()
        }
        val rankedNcs = if (includeOnlineProviders) {
            HomeSearchOrganizer.rankOnlineTracks(
                query = trimmedQuery,
                tracks = ncsDeferred.await(),
                limit = ONLINE_MATCH_POOL
            )
        } else {
            emptyList()
        }

        HomeSearchResults(
            musixmatch = HomeSearchOrganizer.buildMusixmatchResults(
                query = trimmedQuery,
                rawResults = musixmatchDeferred.await(),
                localSongs = localCatalog,
                audiusTracks = rankedAudius,
                ncsTracks = rankedNcs,
                limit = MUSIXMATCH_LIMIT
            ),
            audius = rankedAudius.take(ONLINE_LIMIT),
            ncs = rankedNcs.take(ONLINE_LIMIT),
            localSongs = HomeSearchOrganizer.rankLocalSongs(
                query = trimmedQuery,
                songs = localCatalog,
                limit = LOCAL_LIMIT
            )
        )
    }
}
