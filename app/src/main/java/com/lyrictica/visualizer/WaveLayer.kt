package com.lyrictica.visualizer

import androidx.compose.ui.graphics.Color

/** Static config for one band-driven wave layer. */
internal data class WaveLayer(
    val band: VisualizerBand,
    val baseHeightPercent: Float,
    val amplitude: Float,
    val frequency: Float,
    val activationThreshold: Float,
    val alpha: Float,
    val colors: List<Color>
)
