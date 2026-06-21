package com.lyrictica.theme

import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.lyrictica.visualizer.VisualizerPalette

/**
 * Shared accent used by the media notification so it can follow the current visualizer mood.
 * This is intentionally a simple volatile bridge because the playback service and the visualizer
 * live in the same process, but they are driven by different controllers.
 */
object NotificationThemeBridge {
    @Volatile
    private var accentColorArgb: Int = Color(0xFF1F5BFF).toArgb()

    @Volatile
    private var hasExplicitAccent: Boolean = false

    fun updateFromPalette(palette: VisualizerPalette) {
        accentColorArgb = ColorUtils.blendARGB(
            palette.backgroundBottom.toArgb(),
            palette.ambientGlow.toArgb(),
            0.48f
        )
        hasExplicitAccent = true
    }

    fun updateFromColor(color: Color) {
        accentColorArgb = color.toArgb()
        hasExplicitAccent = true
    }

    fun clear() {
        hasExplicitAccent = false
    }

    fun explicitColorOrNull(): Int? = if (hasExplicitAccent) accentColorArgb else null

    fun currentColor(defaultArgb: Int = Color(0xFF1F5BFF).toArgb()): Int {
        return if (hasExplicitAccent) accentColorArgb else defaultArgb
    }
}
