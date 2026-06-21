package com.lyrictica.audio

/** Normalized per-band energy snapshot for the visualizer. */
data class AudioFeatures(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val presence: Float = 0f,
    val treble: Float = 0f
)
