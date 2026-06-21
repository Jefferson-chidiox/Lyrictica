package com.oss.euphoriae.data.model

/**
 * Represents a single section in the unified home-screen feed.
 * The order of items in the list is the order they render in.
 */
sealed interface HomeFeedItem {
    val stableId: String

    /** Branding header above the mixed online shelves. */
    data class OnlineHeader(
        override val stableId: String = "online_header"
    ) : HomeFeedItem

    /** A horizontally-scrolling grid of streamable online tracks. */
    data class OnlineTrackShelf(
        override val stableId: String,
        val title: String,
        val tracks: List<OnlineTrack>,
        val type: ShelfType,
        val source: OnlineSource = OnlineSource.AUDIUS
    ) : HomeFeedItem

    /** A horizontally-scrolling row of online playlists. */
    data class OnlinePlaylistShelf(
        override val stableId: String,
        val title: String,
        val playlists: List<AudiusPlaylist>,
        val source: OnlineSource = OnlineSource.AUDIUS
    ) : HomeFeedItem
}

enum class ShelfType {
    TRENDING,
    GENRE,
    UNDERGROUND,
    NEW_RELEASES
}
