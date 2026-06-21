package com.oss.euphoriae.data.model

data class LyricLine(
    val text: String,
    val timestamp: Long // in milliseconds
)

data class Lyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean = true
)
