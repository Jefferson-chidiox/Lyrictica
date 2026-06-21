package com.oss.euphoriae.search

import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.Song

data class HomeSearchUiState(
    val activeQuery: String = "",
    val isLoading: Boolean = false,
    val results: HomeSearchResults = HomeSearchResults(),
    val error: String? = null
) {
    val isActive: Boolean
        get() = activeQuery.isNotBlank()

    fun withOnlineTracksDisabled(): HomeSearchUiState = copy(
        isLoading = false,
        results = results.withOnlineTracksDisabled()
    )
}

data class HomeSearchResults(
    val musixmatch: List<MusixmatchSearchResult> = emptyList(),
    val audius: List<OnlineTrack> = emptyList(),
    val ncs: List<OnlineTrack> = emptyList(),
    val localSongs: List<Song> = emptyList()
) {
    val isEmpty: Boolean
        get() = musixmatch.isEmpty() && audius.isEmpty() && ncs.isEmpty() && localSongs.isEmpty()

    fun withOnlineTracksDisabled(): HomeSearchResults = copy(
        musixmatch = musixmatch.map { it.copy(availableSources = emptyList()) },
        audius = emptyList(),
        ncs = emptyList()
    )
}

enum class SearchAvailabilityPlatform(val displayName: String) {
    LOCAL("Local"),
    AUDIUS("Spinamp"),
    NCS("NCS")
}

data class SearchAvailability(
    val platform: SearchAvailabilityPlatform,
    val song: Song
)

data class MusixmatchSearchResult(
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val hasSyncedLyrics: Boolean,
    val localMatch: Song? = null,
    val availableSources: List<SearchAvailability> = emptyList(),
    val artworkUri: String? = null
)