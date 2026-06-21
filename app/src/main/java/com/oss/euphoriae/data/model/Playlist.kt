package com.oss.euphoriae.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // These are marked @Ignore because Room doesn't need to persist them
    // They are calculated dynamically from playlist_songs table
    @Ignore
    val songCount: Int = 0,
    @Ignore
    val covers: List<String> = emptyList()
) {
    // Secondary constructor for Room that only takes persisted fields
    constructor(id: Long, name: String, coverUri: String?, createdAt: Long) 
        : this(id, name, coverUri, createdAt, 0, emptyList())
}

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
