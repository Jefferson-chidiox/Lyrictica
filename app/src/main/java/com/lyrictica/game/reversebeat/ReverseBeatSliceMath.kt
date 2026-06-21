package com.lyrictica.game.reversebeat

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal object ReverseBeatSliceMath {

    fun segmentHitsCircle(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        circleX: Double,
        circleY: Double,
        radius: Double
    ): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        if (dx == 0.0 && dy == 0.0) {
            return hypot(circleX - startX, circleY - startY) <= radius
        }

        val lengthSquared = (dx * dx) + (dy * dy)
        val t = (((circleX - startX) * dx) + ((circleY - startY) * dy)) / lengthSquared
        val clampedT = t.coerceIn(0.0, 1.0)
        val nearestX = startX + (dx * clampedT)
        val nearestY = startY + (dy * clampedT)
        return hypot(circleX - nearestX, circleY - nearestY) <= radius
    }

    fun quadraticBezier(
        start: Double,
        control: Double,
        end: Double,
        t: Double
    ): Double {
        val oneMinusT = 1.0 - t
        return (oneMinusT * oneMinusT * start) + (2.0 * oneMinusT * t * control) + (t * t * end)
    }

    fun sanitizeLyricToken(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        return trimmed.takeIf { token -> token.any { it.isLetterOrDigit() } }
    }

    fun emphasizeWord(durationMs: Long, text: String): Float {
        val durationBias = (durationMs / 420f).coerceIn(0f, 1f)
        val lengthBias = (text.length / 10f).coerceIn(0f, 1f)
        return (0.34f + (durationBias * 0.42f) + (lengthBias * 0.24f)).coerceIn(0.22f, 1f)
    }

    fun spacingBoost(previousTimeMs: Long?, currentTimeMs: Long): Float {
        if (previousTimeMs == null) return 0.18f
        val gap = currentTimeMs - previousTimeMs
        return when {
            gap <= 120L -> 0.92f
            gap <= 180L -> 0.78f
            gap <= 260L -> 0.60f
            gap <= 360L -> 0.42f
            else -> 0.20f
        }
    }

    fun clampFraction(value: Float, minValue: Float = 0.08f, maxValue: Float = 0.92f): Float {
        return max(minValue, min(maxValue, value))
    }

    fun wave(seed: Int, index: Int, frequency: Double, amplitude: Double): Double {
        val value = kotlin.math.sin(((seed * 0.173) + (index * frequency))) * amplitude
        return if (abs(value) < 1e-4) 0.0 else value
    }
}
