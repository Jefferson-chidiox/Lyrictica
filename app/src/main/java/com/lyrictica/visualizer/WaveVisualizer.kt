package com.lyrictica.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import com.lyrictica.audio.AudioFeatures
import kotlin.math.sin

@Composable
internal fun WaveVisualizer(
    features: AudioFeatures,
    theme: VisualizerPalette,
    modifier: Modifier = Modifier
) {
    val layers = listOf(
        WaveLayer(
            band = VisualizerBand.BASS,
            baseHeightPercent = 0.825f,
            amplitude = 96f,
            frequency = 0.95f,
            activationThreshold = 0.03f,
            alpha = 0.56f,
            colors = theme.colorsFor(VisualizerBand.BASS)
        ),
        WaveLayer(
            band = VisualizerBand.MID,
            baseHeightPercent = 0.845f,
            amplitude = 84f,
            frequency = 1.25f,
            activationThreshold = 0.028f,
            alpha = 0.50f,
            colors = theme.colorsFor(VisualizerBand.MID)
        ),
        WaveLayer(
            band = VisualizerBand.PRESENCE,
            baseHeightPercent = 0.865f,
            amplitude = 76f,
            frequency = 1.65f,
            activationThreshold = 0.024f,
            alpha = 0.48f,
            colors = theme.colorsFor(VisualizerBand.PRESENCE)
        ),
        WaveLayer(
            band = VisualizerBand.TREBLE,
            baseHeightPercent = 0.885f,
            amplitude = 64f,
            frequency = 2.05f,
            activationThreshold = 0.02f,
            alpha = 0.42f,
            colors = theme.colorsFor(VisualizerBand.TREBLE)
        )
    )

    fun energyFor(band: VisualizerBand): Float = when (band) {
        VisualizerBand.BASS -> features.bass
        VisualizerBand.MID -> features.mid
        VisualizerBand.PRESENCE -> features.presence
        VisualizerBand.TREBLE -> features.treble
    }

    fun activityFor(band: VisualizerBand, energy: Float, threshold: Float): Float {
        if (energy <= threshold) return 0f

        val normalized = ((energy - threshold) / (1f - threshold)).coerceIn(0f, 1f)
        val bandGain = when (band) {
            VisualizerBand.BASS -> 1.10f
            VisualizerBand.MID -> 1.00f
            VisualizerBand.PRESENCE -> 0.92f
            VisualizerBand.TREBLE -> 0.84f
        }

        // Keep the gate hard, but let active bands become visible quickly enough to read.
        return (normalized * 1.75f * bandGain).coerceIn(0f, 1f)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        if (width <= 0f || height <= 0f) return@Canvas

        // Render fixed, band-isolated layers.
        layers.forEachIndexed { index, layer ->
            val energy = energyFor(layer.band)
            val activity = activityFor(layer.band, energy, layer.activationThreshold)
            if (activity <= 0f) return@forEachIndexed

            val dynamicAmplitude = layer.amplitude * activity
            val baseHeight = height * layer.baseHeightPercent
            val phase = index * 1.75f

            val path = Path().apply {
                moveTo(0f, height)
                lineTo(0f, baseHeight)

                val step = 6
                for (x in 0..width.toInt() step step) {
                    val xFloat = x.toFloat()
                    val angle = (xFloat / width) * (2 * Math.PI.toFloat()) * layer.frequency + phase
                    val y = baseHeight + sin(angle) * dynamicAmplitude
                    lineTo(xFloat, y.coerceAtMost(height))
                }

                lineTo(width, height)
                close()
            }

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = layer.colors,
                    startY = baseHeight - dynamicAmplitude,
                    endY = height
                ),
                alpha = layer.alpha * (0.25f + activity * 0.75f)
            )
        }
    }
}
