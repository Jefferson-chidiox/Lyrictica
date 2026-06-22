package com.lyrictica.game.reversebeat

import com.lyrictica.audio.AudioFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseBeatGameRuntimeTest {

    @Test
    fun `double score pickup activates timed multiplier`() {
        val runtime = ReverseBeatGameRuntime(songSeed = 5, performanceProfile = ReverseBeatPerformanceProfile.STANDARD)
        runtime.loadChart(
            ReverseBeatChart(
                mode = ReverseBeatChartMode.LYRIC,
                entries = listOf(
                    testEntry(id = 1L, kind = ReverseBeatTargetKind.POWER_DOUBLE_SCORE, hitTimeMs = 1_000L, apexX = 0.30f),
                    testEntry(id = 2L, kind = ReverseBeatTargetKind.BALL, hitTimeMs = 1_600L, apexX = 0.72f, label = "hello")
                ),
                durationMs = 4_000L,
                summary = "test"
            )
        )
        runtime.startRun()

        hitFirstTargetOfKind(runtime, timeMs = 1_000L, kind = ReverseBeatTargetKind.POWER_DOUBLE_SCORE)

        val afterPickup = runtime.uiState.value
        assertTrue(afterPickup.doubleScoreActive)
        assertTrue(afterPickup.doubleScoreRemainingMs > 0L)

        hitFirstTargetOfKind(runtime, timeMs = 1_600L, kind = ReverseBeatTargetKind.BALL)

        val afterBall = runtime.uiState.value
        assertEquals(1, afterBall.catches)
        assertTrue(afterBall.score > 200)
    }

    @Test
    fun `line clear pickup clears current non bomb balls`() {
        val runtime = ReverseBeatGameRuntime(songSeed = 9, performanceProfile = ReverseBeatPerformanceProfile.STANDARD)
        runtime.loadChart(
            ReverseBeatChart(
                mode = ReverseBeatChartMode.LYRIC,
                entries = listOf(
                    testEntry(id = 1L, kind = ReverseBeatTargetKind.POWER_LINE_CLEAR, hitTimeMs = 1_000L, apexX = 0.24f),
                    testEntry(id = 2L, kind = ReverseBeatTargetKind.BALL, hitTimeMs = 1_080L, apexX = 0.64f, label = "one"),
                    testEntry(id = 3L, kind = ReverseBeatTargetKind.BALL, hitTimeMs = 1_180L, apexX = 0.78f, label = "two"),
                    testEntry(id = 4L, kind = ReverseBeatTargetKind.BOMB, hitTimeMs = 1_300L, apexX = 0.50f)
                ),
                durationMs = 4_000L,
                summary = "test"
            )
        )
        runtime.startRun()

        runtime.onPlaybackSample(positionMs = 1_000L, isPlaying = true, features = AudioFeatures(), ended = false)
        hitFirstTargetOfKind(runtime, timeMs = 1_000L, kind = ReverseBeatTargetKind.POWER_LINE_CLEAR)

        val state = runtime.uiState.value
        val visibleKinds = runtime.snapshot().targets.map { it.kind }.toSet()
        assertTrue(state.catches >= 2)
        assertTrue(ReverseBeatTargetKind.BALL !in visibleKinds)
        assertTrue(ReverseBeatTargetKind.BOMB in visibleKinds)
    }

    @Test
    fun `air dance summary appears when alternating streak breaks`() {
        val runtime = ReverseBeatGameRuntime(songSeed = 3, performanceProfile = ReverseBeatPerformanceProfile.STANDARD)
        runtime.loadChart(
            ReverseBeatChart(
                mode = ReverseBeatChartMode.LYRIC,
                entries = listOf(
                    testEntry(id = 1L, kind = ReverseBeatTargetKind.BALL, hitTimeMs = 1_000L, apexX = 0.24f, label = "one"),
                    testEntry(id = 2L, kind = ReverseBeatTargetKind.BALL, hitTimeMs = 1_450L, apexX = 0.76f, label = "two"),
                    testEntry(id = 3L, kind = ReverseBeatTargetKind.BALL, hitTimeMs = 1_900L, apexX = 0.26f, label = "three"),
                    testEntry(id = 4L, kind = ReverseBeatTargetKind.BALL, hitTimeMs = 2_350L, apexX = 0.28f, label = "four")
                ),
                durationMs = 5_000L,
                summary = "test"
            )
        )
        runtime.startRun()

        hitFirstTargetOfKind(runtime, timeMs = 1_000L, kind = ReverseBeatTargetKind.BALL)
        hitFirstTargetOfKind(runtime, timeMs = 1_450L, kind = ReverseBeatTargetKind.BALL)
        assertEquals(2, runtime.uiState.value.airDanceActiveCount)
        hitFirstTargetOfKind(runtime, timeMs = 1_900L, kind = ReverseBeatTargetKind.BALL)
        assertEquals(3, runtime.uiState.value.airDanceActiveCount)
        hitFirstTargetOfKind(runtime, timeMs = 2_350L, kind = ReverseBeatTargetKind.BALL)

        val finalState = runtime.uiState.value
        assertEquals(0, finalState.airDanceActiveCount)
        assertEquals(3, finalState.airDanceSummaryCount)
        assertTrue(finalState.airDanceSummaryAlpha > 0f)
    }

    @Test
    fun `bomb reveal and bomb hit cues increment`() {
        val runtime = ReverseBeatGameRuntime(songSeed = 11, performanceProfile = ReverseBeatPerformanceProfile.STANDARD)
        runtime.loadChart(
            ReverseBeatChart(
                mode = ReverseBeatChartMode.BEAT,
                entries = listOf(
                    testEntry(id = 1L, kind = ReverseBeatTargetKind.BOMB, hitTimeMs = 1_600L, apexX = 0.50f)
                ),
                durationMs = 4_000L,
                summary = "test"
            )
        )
        runtime.startRun()

        runtime.onPlaybackSample(positionMs = 600L, isPlaying = true, features = AudioFeatures(), ended = false)
        assertEquals(1, runtime.uiState.value.bombRevealCueCount)

        hitFirstTargetOfKind(runtime, timeMs = 1_600L, kind = ReverseBeatTargetKind.BOMB)
        assertEquals(1, runtime.uiState.value.bombHitCueCount)
    }

    private fun hitFirstTargetOfKind(
        runtime: ReverseBeatGameRuntime,
        timeMs: Long,
        kind: ReverseBeatTargetKind
    ) {
        runtime.onPlaybackSample(positionMs = timeMs, isPlaying = true, features = AudioFeatures(), ended = false)
        val target = runtime.snapshot().targets.first { it.kind == kind }
        runtime.beginSwipe(target.x - 120.0, target.y)
        runtime.extendSwipe(target.x + 120.0, target.y)
        runtime.endSwipe()
    }

    private fun testEntry(
        id: Long,
        kind: ReverseBeatTargetKind,
        hitTimeMs: Long,
        apexX: Float,
        label: String? = null
    ): ReverseBeatChartEntry {
        return ReverseBeatChartEntry(
            id = id,
            kind = kind,
            hitTimeMs = hitTimeMs,
            flightDurationMs = 2_000L,
            startXFraction = (apexX - 0.10f).coerceIn(0.08f, 0.90f),
            apexXFraction = apexX,
            endXFraction = (apexX + 0.10f).coerceIn(0.10f, 0.92f),
            apexYFraction = 0.34f,
            radiusPx = 78f,
            emphasis = 0.72f,
            label = label
        )
    }
}
