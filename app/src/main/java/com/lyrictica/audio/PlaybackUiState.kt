package com.lyrictica.audio

data class PlaybackUiState(
    val trackName: String = "No track selected",
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
    val ended: Boolean = false
)
