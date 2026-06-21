package com.lyrictica.visualizer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class VisualizerMood {
    BLUE,
    GREEN,
    PURPLE,
    RED
}

data class VisualizerPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val ambientGlow: Color,
    val topScrimStart: Color,
    val topScrimEnd: Color,
    val controlText: Color,
    val mutedText: Color,
    val sliderActiveTrack: Color,
    val sliderInactiveTrack: Color,
    val lyricsActive: Color,
    val lyricsInactive: Color,
    val pillBackground: Color,
    val pillBorder: Color,
    val bassWave: List<Color>,
    val midWave: List<Color>,
    val presenceWave: List<Color>,
    val trebleWave: List<Color>
) {
    fun colorsFor(band: VisualizerBand): List<Color> = when (band) {
        VisualizerBand.BASS -> bassWave
        VisualizerBand.MID -> midWave
        VisualizerBand.PRESENCE -> presenceWave
        VisualizerBand.TREBLE -> trebleWave
    }
}

object VisualizerPalettes {
    val blue: VisualizerPalette = VisualizerPalette(
        backgroundTop = Color(0xFF02060D),
        backgroundBottom = Color(0xFF050912),
        ambientGlow = Color(0xFF1F5BFF),
        topScrimStart = Color(0x6610182C),
        topScrimEnd = Color.Transparent,
        controlText = Color(0xFFDDE8FF),
        mutedText = Color(0xFF8FA3B8),
        sliderActiveTrack = Color(0x66B9D7FF),
        sliderInactiveTrack = Color(0x1AB9D7FF),
        lyricsActive = Color(0xFFC9D8EA),
        lyricsInactive = Color(0xFF5A6B7C),
        pillBackground = Color(0x33101725),
        pillBorder = Color(0x2A7CB7FF),
        bassWave = listOf(
            Color(0x331E3A8A),
            Color(0x663B82E6),
            Color(0x003B82E6)
        ),
        midWave = listOf(
            Color(0x443B82E6),
            Color(0x806FB4FF),
            Color(0x006FB4FF)
        ),
        presenceWave = listOf(
            Color(0x4478BFFF),
            Color(0x8899CCFF),
            Color(0x0099CCFF)
        ),
        trebleWave = listOf(
            Color(0x6693C5FD),
            Color(0xB3E0F2FE),
            Color(0x00E0F2FE)
        )
    )

    val green: VisualizerPalette = VisualizerPalette(
        backgroundTop = Color(0xFF020704),
        backgroundBottom = Color(0xFF050B07),
        ambientGlow = Color(0xFF1ECF7B),
        topScrimStart = Color(0x66101714),
        topScrimEnd = Color.Transparent,
        controlText = Color(0xFFDFF7E6),
        mutedText = Color(0xFF8EA99A),
        sliderActiveTrack = Color(0x669CF5C4),
        sliderInactiveTrack = Color(0x1A9CF5C4),
        lyricsActive = Color(0xFFCDEAD9),
        lyricsInactive = Color(0xFF5D7A67),
        pillBackground = Color(0x33101512),
        pillBorder = Color(0x2A73E3A0),
        bassWave = listOf(
            Color(0x332A6E45),
            Color(0x6640C56A),
            Color(0x0040C56A)
        ),
        midWave = listOf(
            Color(0x443D8B57),
            Color(0x8087EB9F),
            Color(0x0087EB9F)
        ),
        presenceWave = listOf(
            Color(0x4457A96B),
            Color(0x88B0F6C8),
            Color(0x00B0F6C8)
        ),
        trebleWave = listOf(
            Color(0x6686F3C0),
            Color(0xB3E1FFF0),
            Color(0x00E1FFF0)
        )
    )

    val purple: VisualizerPalette = VisualizerPalette(
        backgroundTop = Color(0xFF05040A),
        backgroundBottom = Color(0xFF0A0710),
        ambientGlow = Color(0xFF9B5CFF),
        topScrimStart = Color(0x66170A24),
        topScrimEnd = Color.Transparent,
        controlText = Color(0xFFEEE1FF),
        mutedText = Color(0xFFA38FB8),
        sliderActiveTrack = Color(0x66C49CFF),
        sliderInactiveTrack = Color(0x1AC49CFF),
        lyricsActive = Color(0xFFE0D2F6),
        lyricsInactive = Color(0xFF766189),
        pillBackground = Color(0x33120C1C),
        pillBorder = Color(0x2AB07CFF),
        bassWave = listOf(
            Color(0x333B1C67),
            Color(0x665D2DAE),
            Color(0x005D2DAE)
        ),
        midWave = listOf(
            Color(0x444F2E8F),
            Color(0x808E62E8),
            Color(0x008E62E8)
        ),
        presenceWave = listOf(
            Color(0x446D42BC),
            Color(0x88B89CFF),
            Color(0x00B89CFF)
        ),
        trebleWave = listOf(
            Color(0x66B58CFF),
            Color(0xB3F0E6FF),
            Color(0x00F0E6FF)
        )
    )

    val red: VisualizerPalette = VisualizerPalette(
        backgroundTop = Color(0xFF070203),
        backgroundBottom = Color(0xFF100406),
        ambientGlow = Color(0xFFFF5A72),
        topScrimStart = Color(0x661E0B10),
        topScrimEnd = Color.Transparent,
        controlText = Color(0xFFFFE3E5),
        mutedText = Color(0xFFB89A9B),
        sliderActiveTrack = Color(0x66FF8F97),
        sliderInactiveTrack = Color(0x1AFF8F97),
        lyricsActive = Color(0xFFF5D5D8),
        lyricsInactive = Color(0xFF8A6A6D),
        pillBackground = Color(0x331A1012),
        pillBorder = Color(0x2AFF818D),
        bassWave = listOf(
            Color(0x334B1318),
            Color(0x66D94C5A),
            Color(0x00D94C5A)
        ),
        midWave = listOf(
            Color(0x445D1A22),
            Color(0x80FF5A61),
            Color(0x00FF5A61)
        ),
        presenceWave = listOf(
            Color(0x447A2230),
            Color(0x88FF818B),
            Color(0x00FF818B)
        ),
        trebleWave = listOf(
            Color(0x66FFA0A8),
            Color(0xB3FFD7D9),
            Color(0x00FFD7D9)
        )
    )

    fun forMood(mood: VisualizerMood): VisualizerPalette = when (mood) {
        VisualizerMood.BLUE -> blue
        VisualizerMood.GREEN -> green
        VisualizerMood.PURPLE -> purple
        VisualizerMood.RED -> red
    }

    fun lerp(from: VisualizerPalette, to: VisualizerPalette, progress: Float): VisualizerPalette {
        val t = progress.coerceIn(0f, 1f)
        return VisualizerPalette(
            backgroundTop = lerp(from.backgroundTop, to.backgroundTop, t),
            backgroundBottom = lerp(from.backgroundBottom, to.backgroundBottom, t),
            ambientGlow = lerp(from.ambientGlow, to.ambientGlow, t),
            topScrimStart = lerp(from.topScrimStart, to.topScrimStart, t),
            topScrimEnd = lerp(from.topScrimEnd, to.topScrimEnd, t),
            controlText = lerp(from.controlText, to.controlText, t),
            mutedText = lerp(from.mutedText, to.mutedText, t),
            sliderActiveTrack = lerp(from.sliderActiveTrack, to.sliderActiveTrack, t),
            sliderInactiveTrack = lerp(from.sliderInactiveTrack, to.sliderInactiveTrack, t),
            lyricsActive = lerp(from.lyricsActive, to.lyricsActive, t),
            lyricsInactive = lerp(from.lyricsInactive, to.lyricsInactive, t),
            pillBackground = lerp(from.pillBackground, to.pillBackground, t),
            pillBorder = lerp(from.pillBorder, to.pillBorder, t),
            bassWave = lerpColors(from.bassWave, to.bassWave, t),
            midWave = lerpColors(from.midWave, to.midWave, t),
            presenceWave = lerpColors(from.presenceWave, to.presenceWave, t),
            trebleWave = lerpColors(from.trebleWave, to.trebleWave, t)
        )
    }

    private fun lerpColors(from: List<Color>, to: List<Color>, progress: Float): List<Color> {
        val t = progress.coerceIn(0f, 1f)
        val size = minOf(from.size, to.size)
        return List(size) { index -> lerp(from[index], to[index], t) }
    }
}
