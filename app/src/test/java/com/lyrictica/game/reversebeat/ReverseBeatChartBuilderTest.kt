package com.lyrictica.game.reversebeat

import com.lyrictica.audio.SpectrumBandsPrecomputer
import com.lyrictica.lyrics.LyricLine
import com.lyrictica.lyrics.LyricWord
import com.lyrictica.lyrics.ParsedLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseBeatChartBuilderTest {

    @Test
    fun `lyric chart preserves exact playable words`() {
        val lyrics = ParsedLyrics(
            lines = listOf(
                LyricLine(
                    timeMs = 1_000L,
                    text = "Hello world",
                    words = listOf(
                        LyricWord(text = "Hello", startTimeMs = 1_000L, endTimeMs = 1_260L),
                        LyricWord(text = " ", startTimeMs = 1_260L, endTimeMs = 1_320L),
                        LyricWord(text = "world", startTimeMs = 1_320L, endTimeMs = 1_720L)
                    )
                ),
                LyricLine(
                    timeMs = 2_000L,
                    text = "again!",
                    words = listOf(
                        LyricWord(text = "again!", startTimeMs = 2_000L, endTimeMs = 2_360L)
                    )
                )
            ),
            isSynced = true
        )

        val chart = buildLyricChart(lyrics, songSeed = 7)

        assertEquals(ReverseBeatChartMode.LYRIC, chart.mode)
        assertEquals(listOf("Hello", "world", "again!"), chart.entries.mapNotNull { it.label })
        assertEquals(1_130L, chart.entries[0].hitTimeMs)
        assertEquals(1_520L, chart.entries[1].hitTimeMs)
        assertEquals(2_180L, chart.entries[2].hitTimeMs)
        assertTrue(chart.entries.all { it.kind == ReverseBeatTargetKind.BALL })
        assertTrue(chart.entries.all { it.flightDurationMs >= 1_900L })
    }

    @Test
    fun `dense lyric run is thinned to playable spacing`() {
        val lyrics = ParsedLyrics(
            lines = listOf(
                LyricLine(
                    timeMs = 0L,
                    text = "we are never ever getting back together",
                    words = listOf(
                        LyricWord(text = "we", startTimeMs = 0L, endTimeMs = 90L),
                        LyricWord(text = "are", startTimeMs = 110L, endTimeMs = 200L),
                        LyricWord(text = "never", startTimeMs = 220L, endTimeMs = 340L),
                        LyricWord(text = "ever", startTimeMs = 360L, endTimeMs = 470L),
                        LyricWord(text = "getting", startTimeMs = 490L, endTimeMs = 650L),
                        LyricWord(text = "back", startTimeMs = 680L, endTimeMs = 800L)
                    )
                )
            ),
            isSynced = true
        )

        val entries = buildLyricEntries(lyrics, songSeed = 3)

        assertTrue(entries.size < 6)
        assertTrue(entries.zipWithNext().all { (first, second) -> second.hitTimeMs - first.hitTimeMs >= REVERSE_BEAT_LYRIC_MIN_GAP_MS })
        assertTrue(entries.mapNotNull { it.label }.all { it.isNotBlank() })
    }

    @Test
    fun `beat extraction keeps strong peaks and spacing`() {
        val bass = floatArrayOf(
            0f, 0f, 0f, 0f, 0.10f, 0.92f, 0.12f, 0f,
            0f, 0.08f, 0.18f, 0.88f, 0.14f, 0f,
            0f, 0f, 0.12f, 0.22f, 0.16f, 0.95f, 0.10f, 0f,
            0f, 0f, 0f, 0.10f, 0.82f, 0.11f, 0f, 0f
        )
        val zero = FloatArray(bass.size)
        val bands = SpectrumBandsPrecomputer.PrecomputedBands(
            durationMs = 1_500L,
            fps = 20,
            bass = bass,
            mid = zero,
            presence = zero,
            treble = zero
        )

        val moments = extractBeatMoments(bands)

        assertTrue(moments.map { it.timeMs }.containsAll(listOf(250L, 550L, 950L, 1300L)))
        assertTrue(moments.zipWithNext().all { (first, second) -> second.timeMs - first.timeMs >= 240L })
    }

    @Test
    fun `beat chart filters dense peaks for gameplay pacing`() {
        val condensed = condenseBeatMoments(
            listOf(
                ReverseBeatBeatMoment(timeMs = 120L, strength = 0.52f),
                ReverseBeatBeatMoment(timeMs = 300L, strength = 0.96f),
                ReverseBeatBeatMoment(timeMs = 470L, strength = 0.61f),
                ReverseBeatBeatMoment(timeMs = 790L, strength = 0.84f),
                ReverseBeatBeatMoment(timeMs = 960L, strength = 0.72f),
                ReverseBeatBeatMoment(timeMs = 1_420L, strength = 0.91f)
            )
        )

        assertEquals(listOf(300L, 790L, 1_420L), condensed.map { it.timeMs })
        assertTrue(condensed.zipWithNext().all { (first, second) -> second.timeMs - first.timeMs >= REVERSE_BEAT_BEAT_MIN_GAP_MS })
    }

    @Test
    fun `beat fallback locks onto one dominant pulse family`() {
        val bass = FloatArray(80)
        listOf(
            8 to 0.78f,
            10 to 0.62f,
            19 to 0.80f,
            20 to 0.64f,
            27 to 0.77f,
            30 to 0.63f,
            38 to 0.76f,
            40 to 0.65f,
            49 to 0.79f,
            50 to 0.64f,
            57 to 0.78f,
            60 to 0.66f
        ).forEach { (index, value) ->
            bass[index] = value
        }
        val zero = FloatArray(bass.size)
        val bands = SpectrumBandsPrecomputer.PrecomputedBands(
            durationMs = 4_000L,
            fps = 20,
            bass = bass,
            mid = zero,
            presence = zero,
            treble = zero
        )

        val chart = buildBeatChart(bands, songSeed = 13) ?: error("Expected beat chart")
        val beatTimes = chart.entries
            .filter { it.kind == ReverseBeatTargetKind.BALL }
            .map { it.hitTimeMs }
            .take(6)

        assertTrue(beatTimes.size >= 4)
        assertTrue(beatTimes.zipWithNext().all { (first, second) -> second > first })
    }

    @Test
    fun `arc patterns do not all converge at screen center`() {
        val lyrics = ParsedLyrics(
            lines = listOf(
                LyricLine(
                    timeMs = 0L,
                    text = "one two three four five six seven eight",
                    words = listOf(
                        LyricWord(text = "one", startTimeMs = 0L, endTimeMs = 120L),
                        LyricWord(text = "two", startTimeMs = 420L, endTimeMs = 540L),
                        LyricWord(text = "three", startTimeMs = 840L, endTimeMs = 980L),
                        LyricWord(text = "four", startTimeMs = 1_260L, endTimeMs = 1_420L),
                        LyricWord(text = "five", startTimeMs = 1_680L, endTimeMs = 1_830L),
                        LyricWord(text = "six", startTimeMs = 2_100L, endTimeMs = 2_240L),
                        LyricWord(text = "seven", startTimeMs = 2_520L, endTimeMs = 2_700L),
                        LyricWord(text = "eight", startTimeMs = 2_940L, endTimeMs = 3_100L)
                    )
                )
            ),
            isSynced = true
        )

        val chart = buildLyricChart(lyrics, songSeed = 21)
        val balls = chart.entries.filter { it.kind == ReverseBeatTargetKind.BALL }
        val apexBuckets = balls.map { (it.apexXFraction * 10).toInt() }.toSet()

        assertTrue(apexBuckets.size >= 3)
        assertTrue(balls.any { it.apexXFraction <= 0.35f || it.apexXFraction >= 0.65f })
    }

    @Test
    fun `bombs are injected only between roomy playable targets`() {
        val lyrics = ParsedLyrics(
            lines = listOf(
                LyricLine(
                    timeMs = 0L,
                    text = "alpha beta gamma delta epsilon zeta eta theta",
                    words = listOf(
                        LyricWord(text = "alpha", startTimeMs = 0L, endTimeMs = 120L),
                        LyricWord(text = "beta", startTimeMs = 1_700L, endTimeMs = 1_820L),
                        LyricWord(text = "gamma", startTimeMs = 3_500L, endTimeMs = 3_640L),
                        LyricWord(text = "delta", startTimeMs = 5_400L, endTimeMs = 5_540L),
                        LyricWord(text = "epsilon", startTimeMs = 7_300L, endTimeMs = 7_440L),
                        LyricWord(text = "zeta", startTimeMs = 9_200L, endTimeMs = 9_340L),
                        LyricWord(text = "eta", startTimeMs = 11_100L, endTimeMs = 11_240L),
                        LyricWord(text = "theta", startTimeMs = 13_000L, endTimeMs = 13_160L)
                    )
                )
            ),
            isSynced = true
        )

        val chart = buildLyricChart(lyrics, songSeed = 9)
        val bombs = chart.entries.filter { it.kind == ReverseBeatTargetKind.BOMB }

        assertTrue(bombs.isNotEmpty())
        assertEquals(chart.playableTargetCount + chart.bombCount + chart.powerUpCount, chart.entryCount)
        assertTrue(bombs.all { bomb ->
            val previousBall = chart.entries.lastOrNull { it.kind == ReverseBeatTargetKind.BALL && it.hitTimeMs < bomb.hitTimeMs }
            val nextBall = chart.entries.firstOrNull { it.kind == ReverseBeatTargetKind.BALL && it.hitTimeMs > bomb.hitTimeMs }
            previousBall != null && nextBall != null &&
                bomb.hitTimeMs - previousBall.hitTimeMs >= 520L &&
                nextBall.hitTimeMs - bomb.hitTimeMs >= 520L
        })
    }

    @Test
    fun `power up pickups are injected with safe spacing for lyric charts`() {
        val baseEntries = (0 until 15).map { index ->
            ReverseBeatChartEntry(
                id = index.toLong() + 1L,
                kind = ReverseBeatTargetKind.BALL,
                hitTimeMs = 1_000L + (index * 2_300L),
                flightDurationMs = 2_000L,
                startXFraction = 0.12f,
                apexXFraction = if (index % 2 == 0) 0.28f else 0.72f,
                endXFraction = if (index % 2 == 0) 0.42f else 0.58f,
                apexYFraction = 0.34f,
                radiusPx = 80f,
                emphasis = 0.72f,
                label = "w$index"
            )
        }

        val entries = injectPowerUps(baseEntries, ReverseBeatChartMode.LYRIC, songSeed = 12)
        val powerUps = entries.filter { it.kind.isPowerUp }

        assertTrue(powerUps.size >= 3)
        assertTrue(powerUps.zipWithNext().all { (first, second) -> second.hitTimeMs - first.hitTimeMs >= 1_300L })
        assertTrue(powerUps.all { pickup ->
            val previousBall = entries.lastOrNull { it.kind == ReverseBeatTargetKind.BALL && it.hitTimeMs < pickup.hitTimeMs }
            val nextBall = entries.firstOrNull { it.kind == ReverseBeatTargetKind.BALL && it.hitTimeMs > pickup.hitTimeMs }
            previousBall != null && nextBall != null &&
                pickup.hitTimeMs - previousBall.hitTimeMs >= 520L &&
                nextBall.hitTimeMs - pickup.hitTimeMs >= 320L
        })
    }

    @Test
    fun `beat charts skip lyric bloom pickups`() {
        val baseEntries = (0 until 15).map { index ->
            ReverseBeatChartEntry(
                id = index.toLong() + 1L,
                kind = ReverseBeatTargetKind.BALL,
                hitTimeMs = 1_200L + (index * 2_100L),
                flightDurationMs = 2_000L,
                startXFraction = 0.14f,
                apexXFraction = if (index % 2 == 0) 0.24f else 0.76f,
                endXFraction = if (index % 2 == 0) 0.38f else 0.62f,
                apexYFraction = 0.33f,
                radiusPx = 78f,
                emphasis = 0.68f,
                label = null
            )
        }

        val entries = injectPowerUps(baseEntries, ReverseBeatChartMode.BEAT, songSeed = 21)
        val powerKinds = entries.filter { it.kind.isPowerUp }.map { it.kind }.toSet()

        assertTrue(powerKinds.isNotEmpty())
        assertTrue(ReverseBeatTargetKind.POWER_LYRIC_BLOOM !in powerKinds)
    }

    @Test
    fun `slice math detects crossing segments`() {
        assertTrue(
            ReverseBeatSliceMath.segmentHitsCircle(
                startX = 100.0,
                startY = 100.0,
                endX = 220.0,
                endY = 220.0,
                circleX = 170.0,
                circleY = 170.0,
                radius = 24.0
            )
        )
        assertTrue(
            !ReverseBeatSliceMath.segmentHitsCircle(
                startX = 100.0,
                startY = 100.0,
                endX = 120.0,
                endY = 120.0,
                circleX = 220.0,
                circleY = 220.0,
                radius = 20.0
            )
        )
    }
}
