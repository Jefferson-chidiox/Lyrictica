package com.lyrictica.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.oss.euphoriae.data.preferences.ThemeColorOption
import com.lyrictica.visualizer.VisualizerPalette

private const val SEED_PURPLE = 0xFF6750A4.toInt()
private const val SEED_BLUE = 0xFF0061A4.toInt()
private const val SEED_GREEN = 0xFF386A20.toInt()
private const val SEED_ORANGE = 0xFF8B5000.toInt()
private const val SEED_PINK = 0xFFBC004B.toInt()
private const val SEED_RED = 0xFFBA1A1A.toInt()

fun ThemeColorOption.seedColor(): Color = when (this) {
    ThemeColorOption.DYNAMIC -> Color(SEED_BLUE)
    ThemeColorOption.PURPLE -> Color(SEED_PURPLE)
    ThemeColorOption.BLUE -> Color(SEED_BLUE)
    ThemeColorOption.GREEN -> Color(SEED_GREEN)
    ThemeColorOption.ORANGE -> Color(SEED_ORANGE)
    ThemeColorOption.PINK -> Color(SEED_PINK)
    ThemeColorOption.RED -> Color(SEED_RED)
}

fun VisualizerPalette.harmonize(seed: Color): VisualizerPalette {
    fun mix(color: Color, amount: Float) = if (color.alpha == 0f) {
        color
    } else {
        lerp(color, seed, amount.coerceIn(0f, 1f))
    }

    return copy(
        backgroundTop = mix(backgroundTop, 0.10f),
        backgroundBottom = mix(backgroundBottom, 0.12f),
        ambientGlow = mix(ambientGlow, 0.28f),
        topScrimStart = mix(topScrimStart, 0.08f),
        topScrimEnd = mix(topScrimEnd, 0.04f),
        controlText = mix(controlText, 0.04f),
        mutedText = mix(mutedText, 0.06f),
        sliderActiveTrack = mix(sliderActiveTrack, 0.24f),
        sliderInactiveTrack = mix(sliderInactiveTrack, 0.20f),
        lyricsActive = mix(lyricsActive, 0.16f),
        lyricsInactive = mix(lyricsInactive, 0.10f),
        pillBackground = mix(pillBackground, 0.12f),
        pillBorder = mix(pillBorder, 0.22f),
        bassWave = bassWave.map { mix(it, 0.16f) },
        midWave = midWave.map { mix(it, 0.18f) },
        presenceWave = presenceWave.map { mix(it, 0.20f) },
        trebleWave = trebleWave.map { mix(it, 0.22f) }
    )
}

fun VisualizerPalette.toColorScheme(darkTheme: Boolean): ColorScheme {
    val background = backgroundBottom
    val surface = background
    val surfaceVariant = lerp(background, ambientGlow, if (darkTheme) 0.04f else 0.03f)
    val surfaceBright = lerp(surface, Color.White, if (darkTheme) 0.06f else 0.02f)
    val surfaceContainer = lerp(surface, ambientGlow, if (darkTheme) 0.03f else 0.02f)
    val surfaceContainerHigh = lerp(surface, ambientGlow, if (darkTheme) 0.05f else 0.03f)
    val surfaceContainerHighest = lerp(surface, ambientGlow, if (darkTheme) 0.07f else 0.04f)
    val surfaceContainerLow = surface
    val surfaceContainerLowest = background
    val surfaceDim = lerp(background, Color.Black, if (darkTheme) 0.12f else 0.0f)

    return if (darkTheme) {
        darkColorScheme(
            primary = sliderActiveTrack,
            onPrimary = background,
            primaryContainer = surfaceVariant,
            onPrimaryContainer = controlText,
            inversePrimary = ambientGlow,
            secondary = ambientGlow,
            onSecondary = background,
            secondaryContainer = surface,
            onSecondaryContainer = controlText,
            tertiary = lyricsActive,
            onTertiary = background,
            tertiaryContainer = surfaceVariant,
            onTertiaryContainer = controlText,
            background = background,
            onBackground = controlText,
            surface = surface,
            onSurface = controlText,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = mutedText,
            surfaceTint = ambientGlow,
            inverseSurface = background,
            inverseOnSurface = controlText,
            error = Color(0xFFFF6B6B),
            onError = Color(0xFF160B0D),
            errorContainer = Color(0x33FF6B6B),
            onErrorContainer = Color(0xFFFEE2E2),
            outline = pillBorder,
            outlineVariant = pillBorder.copy(alpha = 0.60f),
            scrim = Color.Black,
            surfaceBright = surfaceBright,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceDim = surfaceDim
        )
    } else {
        lightColorScheme(
            primary = sliderActiveTrack,
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, sliderActiveTrack, 0.18f),
            onPrimaryContainer = Color(0xFF10151C),
            inversePrimary = ambientGlow,
            secondary = ambientGlow,
            onSecondary = Color.White,
            secondaryContainer = lerp(Color.White, ambientGlow, 0.16f),
            onSecondaryContainer = Color(0xFF10151C),
            tertiary = lyricsActive,
            onTertiary = Color(0xFF10151C),
            tertiaryContainer = lerp(Color.White, lyricsActive, 0.18f),
            onTertiaryContainer = Color(0xFF10151C),
            background = Color(0xFFF6F7FA),
            onBackground = Color(0xFF10151C),
            surface = Color.White,
            onSurface = Color(0xFF10151C),
            surfaceVariant = lerp(Color.White, sliderActiveTrack, 0.10f),
            onSurfaceVariant = mutedText,
            surfaceTint = ambientGlow,
            inverseSurface = Color(0xFF10151C),
            inverseOnSurface = Color.White,
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = pillBorder.copy(alpha = 0.85f),
            outlineVariant = pillBorder.copy(alpha = 0.45f),
            scrim = Color.Black,
            surfaceBright = surfaceBright,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceDim = surfaceDim
        )
    }
}
