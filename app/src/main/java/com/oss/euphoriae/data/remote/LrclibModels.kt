package com.oss.euphoriae.data.remote

data class MusixmatchResponse(
    val trackId: Long = 0L,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)
