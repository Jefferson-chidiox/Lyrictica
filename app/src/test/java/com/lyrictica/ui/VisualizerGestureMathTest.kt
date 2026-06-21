package com.lyrictica.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerGestureMathTest {

    @Test
    fun fasterDragSeeksFurtherThanSlowDrag() {
        val slow = gestureSeekPosition(
            basePositionMs = 60_000,
            dragAmountPx = 12f,
            durationMs = 180_000,
            density = 1f
        )
        val fast = gestureSeekPosition(
            basePositionMs = 60_000,
            dragAmountPx = 60f,
            durationMs = 180_000,
            density = 1f
        )

        assertTrue(fast > slow)
    }

    @Test
    fun backwardDragClampsAtStart() {
        assertEquals(
            0L,
            gestureSeekPosition(
                basePositionMs = 2_000,
                dragAmountPx = -500f,
                durationMs = 180_000,
                density = 1f
            )
        )
    }

    @Test
    fun forwardDragClampsAtDuration() {
        assertEquals(
            180_000L,
            gestureSeekPosition(
                basePositionMs = 170_000,
                dragAmountPx = 1_000f,
                durationMs = 180_000,
                density = 1f
            )
        )
    }

    @Test
    fun metricsExposeDirectionAndSpeed() {
        val metrics = gestureSeekMetrics(
            basePositionMs = 60_000,
            dragAmountPx = -72f,
            durationMs = 180_000,
            density = 1f
        )

        assertEquals(SeekDirection.BACKWARD, metrics.direction)
        assertEquals(4f, metrics.speedMultiplier, 0f)
    }
}
