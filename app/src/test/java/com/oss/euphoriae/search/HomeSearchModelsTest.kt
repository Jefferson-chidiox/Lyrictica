package com.oss.euphoriae.search

import com.oss.euphoriae.data.model.OnlineSource
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSearchModelsTest {

    @Test
    fun `withOnlineTracksDisabled clears provider results and preview sources`() {
        val state = HomeSearchUiState(
            activeQuery = "alive",
            isLoading = true,
            results = HomeSearchResults(
                musixmatch = listOf(
                    MusixmatchSearchResult(
                        trackId = 7,
                        title = "Alive",
                        artist = "Deliriousninja",
                        album = "Search Album",
                        durationMs = 187_000,
                        hasSyncedLyrics = true,
                        localMatch = Song(id = 41, title = "Alive", artist = "Deliriousninja"),
                        availableSources = listOf(
                            SearchAvailability(
                                platform = SearchAvailabilityPlatform.AUDIUS,
                                song = Song(
                                    id = 77,
                                    title = "Alive",
                                    artist = "Deliriousninja",
                                    album = "Spinamp",
                                    data = "https://spinamp.example/alive"
                                )
                            )
                        )
                    )
                ),
                audius = listOf(
                    OnlineTrack(
                        id = "a1",
                        provider = OnlineSource.AUDIUS,
                        title = "Alive",
                        artist = "Deliriousninja",
                        streamUrl = "https://spinamp.example/alive"
                    )
                ),
                ncs = listOf(
                    OnlineTrack(
                        id = "n1",
                        provider = OnlineSource.NCS,
                        title = "Alive",
                        artist = "CADMIUM",
                        streamUrl = "https://ncs.example/alive"
                    )
                ),
                localSongs = listOf(Song(id = 1, title = "Alive", artist = "Deliriousninja"))
            )
        )

        val filtered = state.withOnlineTracksDisabled()

        assertTrue(filtered.results.audius.isEmpty())
        assertTrue(filtered.results.ncs.isEmpty())
        assertEquals(emptyList<SearchAvailability>(), filtered.results.musixmatch.first().availableSources)
        assertEquals(state.results.localSongs, filtered.results.localSongs)
        assertTrue(!filtered.isLoading)
    }
}
