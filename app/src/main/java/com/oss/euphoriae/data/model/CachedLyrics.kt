package com.oss.euphoriae.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for caching lyrics fetched from Musixmatch
 * to avoid re-fetching the same lyrics repeatedly
 */
@Entity(tableName = "cached_lyrics")
data class CachedLyrics(
    @PrimaryKey
    val songId: Long,
    val trackName: String,
    val artistName: String,
    val syncedLyrics: String?, // LRC format
    val plainLyrics: String?,
    val fetchedAt: Long = System.currentTimeMillis()
)
