package com.lyrictica.audio

import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.Song

sealed class QueueSource(
    open val key: String,
    open val label: String,
    open val category: String
) {
    data object AllSongs : QueueSource("all_songs", "All Songs", "Library")
    data object RecentlyAdded : QueueSource("recently_added", "Recently Added", "Library")
    data object MostPlayedWeek : QueueSource("most_played_week", "Most Played • Week", "Library")
    data object MostPlayedMonth : QueueSource("most_played_month", "Most Played • Month", "Library")
    data object MostPlayedAllTime : QueueSource("most_played_all_time", "Most Played • All Time", "Library")
    data object Favorites : QueueSource("favorites", "Favorites", "Library")
    data object NotPlayed : QueueSource("not_played", "Not Played", "Library")

    data class Playlist(
        val playlistId: Long,
        val playlistName: String
    ) : QueueSource(
        key = "playlist:$playlistId",
        label = playlistName,
        category = "Playlist"
    )

    data class Album(
        val albumId: Long,
        val albumName: String,
        val artistName: String? = null
    ) : QueueSource(
        key = "album:$albumId",
        label = if (artistName.isNullOrBlank()) albumName else "$artistName • $albumName",
        category = "Album"
    )

    data class Custom(
        override val key: String,
        override val label: String,
        override val category: String = "Queue"
    ) : QueueSource(key, label, category)
}

data class PlaybackQueue(
    val source: QueueSource,
    val songs: List<Song>,
    val isCustomOrder: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    val key: String get() = source.key
    val title: String get() = source.label
    val subtitle: String get() = source.category

    fun withSongs(
        newSongs: List<Song>,
        customOrder: Boolean = isCustomOrder,
        lastUsedAt: Long = System.currentTimeMillis()
    ): PlaybackQueue {
        return copy(
            songs = newSongs,
            isCustomOrder = customOrder,
            lastUsedAt = lastUsedAt
        )
    }

    fun mergePreservingOrder(newSongs: List<Song>): PlaybackQueue {
        if (songs.isEmpty()) {
            return copy(songs = newSongs, lastUsedAt = System.currentTimeMillis())
        }

        val incomingById = newSongs.associateBy { it.id }
        val preserved = songs.mapNotNull { existing -> incomingById[existing.id] }
        val preservedIds = preserved.mapTo(mutableSetOf()) { it.id }
        val appended = newSongs.filterNot { preservedIds.contains(it.id) }

        return copy(
            songs = preserved + appended,
            lastUsedAt = System.currentTimeMillis()
        )
    }

    fun reorder(fromIndex: Int, toIndex: Int): PlaybackQueue {
        if (fromIndex == toIndex || fromIndex !in songs.indices || toIndex !in songs.indices) {
            return this
        }

        val updatedSongs = songs.move(fromIndex, toIndex)
        return copy(
            songs = updatedSongs,
            isCustomOrder = true,
            lastUsedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun allSongs(songs: List<Song>) = PlaybackQueue(QueueSource.AllSongs, songs)
        fun recentlyAdded(songs: List<Song>) = PlaybackQueue(QueueSource.RecentlyAdded, songs)
        fun mostPlayedWeek(songs: List<Song>) = PlaybackQueue(QueueSource.MostPlayedWeek, songs)
        fun mostPlayedMonth(songs: List<Song>) = PlaybackQueue(QueueSource.MostPlayedMonth, songs)
        fun mostPlayedAllTime(songs: List<Song>) = PlaybackQueue(QueueSource.MostPlayedAllTime, songs)
        fun favorites(songs: List<Song>) = PlaybackQueue(QueueSource.Favorites, songs)
        fun notPlayed(songs: List<Song>) = PlaybackQueue(QueueSource.NotPlayed, songs)
        fun playlist(playlist: Playlist, songs: List<Song>) = PlaybackQueue(
            source = QueueSource.Playlist(playlist.id, playlist.name),
            songs = songs
        )
        fun album(albumId: Long, albumName: String, artistName: String? = null, songs: List<Song>) = PlaybackQueue(
            source = QueueSource.Album(albumId, albumName, artistName),
            songs = songs
        )
        fun custom(key: String, label: String, songs: List<Song>) = PlaybackQueue(
            source = QueueSource.Custom(key, label),
            songs = songs
        )
    }
}

fun PlaybackQueue.asSavedQueueSnapshot(): PlaybackQueue {
    val savedAt = System.currentTimeMillis()
    if (source.key.startsWith("saved:")) {
        return this.copy(lastUsedAt = savedAt)
    }
    return PlaybackQueue(
        source = QueueSource.Custom(
            key = "saved:${source.key}",
            label = source.label,
            category = "Saved Queue"
        ),
        songs = songs,
        isCustomOrder = true,
        lastUsedAt = savedAt
    )
}

fun List<Song>.move(fromIndex: Int, toIndex: Int): List<Song> {
    if (isEmpty() || fromIndex == toIndex) return this
    if (fromIndex !in indices || toIndex !in indices) return this

    val mutable = toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}
