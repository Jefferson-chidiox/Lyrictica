package com.lyrictica.game.reversebeat

import android.content.Context
import android.net.Uri
import com.lyrictica.audio.AudioAnalysisStore
import com.lyrictica.audio.SpectrumBandsPrecomputer
import com.lyrictica.lyrics.ParsedLyrics
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val REVERSE_BEAT_LYRIC_MIN_GAP_MS = 380L
internal const val REVERSE_BEAT_BEAT_MIN_GAP_MS = 400L

internal class ReverseBeatChartBuilder(context: Context) {

    private val analysisStore = AudioAnalysisStore(context.applicationContext)

    suspend fun build(
        songSource: String?,
        lyrics: ParsedLyrics?,
        songSeed: Int
    ): ReverseBeatChart? {
        val lyricChart = lyrics
            ?.takeIf { it.hasWordSync }
            ?.let { buildLyricChart(it, songSeed) }
            ?.takeIf { it.entries.isNotEmpty() }
        if (lyricChart != null) return lyricChart

        val uri = songSource?.toPlayableUri() ?: return null
        val bands = analysisStore.loadOrComputeBands(uri)
        return buildBeatChart(bands, songSeed)
    }
}

private data class PlayableLyricCue(
    val hitTimeMs: Long,
    val durationMs: Long,
    val label: String
)

private data class ReverseBeatArcPattern(
    val startXFraction: Float,
    val apexXFraction: Float,
    val endXFraction: Float,
    val apexYFraction: Float
)

internal fun buildLyricChart(
    lyrics: ParsedLyrics,
    songSeed: Int
): ReverseBeatChart {
    val baseEntries = buildLyricEntries(lyrics, songSeed)
    val bombEntries = injectBombs(baseEntries, ReverseBeatChartMode.LYRIC, songSeed)
    val entries = injectPowerUps(bombEntries, ReverseBeatChartMode.LYRIC, songSeed)
    return ReverseBeatChart(
        mode = ReverseBeatChartMode.LYRIC,
        entries = entries,
        durationMs = (entries.lastOrNull()?.expiryTimeMs ?: 0L) + 1L,
        summary = buildSummary(ReverseBeatChartMode.LYRIC, entries)
    )
}

internal fun buildLyricEntries(
    lyrics: ParsedLyrics,
    songSeed: Int
): List<ReverseBeatChartEntry> {
    val rawCues = lyrics.lines.flatMap { line ->
        line.words.mapNotNull { word ->
            val label = ReverseBeatSliceMath.sanitizeLyricToken(word.text) ?: return@mapNotNull null
            PlayableLyricCue(
                hitTimeMs = ((word.startTimeMs + word.endTimeMs) / 2L).coerceAtLeast(0L),
                durationMs = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L),
                label = label
            )
        }
    }

    val playableCues = condenseLyricCues(rawCues)
    val entries = mutableListOf<ReverseBeatChartEntry>()
    var previousTime: Long? = null

    playableCues.forEachIndexed { index, cue ->
        val emphasis = ReverseBeatSliceMath.emphasizeWord(cue.durationMs, cue.label)
        val densityBoost = ReverseBeatSliceMath.spacingBoost(previousTime, cue.hitTimeMs)
        entries += buildChartEntry(
            index = index,
            hitTimeMs = cue.hitTimeMs,
            emphasis = emphasis,
            densityBoost = densityBoost,
            label = cue.label,
            songSeed = songSeed,
            kind = ReverseBeatTargetKind.BALL
        )
        previousTime = cue.hitTimeMs
    }

    return reindexEntries(entries)
}

internal fun buildBeatChart(
    bands: SpectrumBandsPrecomputer.PrecomputedBands,
    songSeed: Int
): ReverseBeatChart? {
    val onsets = buildBeatOnsets(bands)
    val rawBeats = extractBeatChartCandidates(onsets, bands.fps)
    val beats = condenseBeatMoments(lockBeatMomentsToDominantPulse(rawBeats, bands.fps))
    if (beats.isEmpty()) return null

    var previousTime: Long? = null
    val baseEntries = beats.mapIndexed { index, beat ->
        val densityBoost = ReverseBeatSliceMath.spacingBoost(previousTime, beat.timeMs)
        previousTime = beat.timeMs
        buildChartEntry(
            index = index,
            hitTimeMs = beat.timeMs,
            emphasis = (0.30f + (beat.strength * 0.70f)).coerceIn(0.24f, 1f),
            densityBoost = densityBoost,
            label = null,
            songSeed = songSeed,
            kind = ReverseBeatTargetKind.BALL
        )
    }

    val bombEntries = injectBombs(baseEntries, ReverseBeatChartMode.BEAT, songSeed)
    val entries = injectPowerUps(bombEntries, ReverseBeatChartMode.BEAT, songSeed)
    return ReverseBeatChart(
        mode = ReverseBeatChartMode.BEAT,
        entries = entries,
        durationMs = max(bands.durationMs, (entries.lastOrNull()?.expiryTimeMs ?: 0L) + 1L),
        summary = buildSummary(ReverseBeatChartMode.BEAT, entries)
    )
}

private fun buildBeatOnsets(
    bands: SpectrumBandsPrecomputer.PrecomputedBands
): FloatArray {
    val n = minOf(bands.bass.size, bands.mid.size, bands.presence.size, bands.treble.size)
    if (n < 3) return FloatArray(0)

    val onsets = FloatArray(n)
    for (index in 1 until n) {
        val bassRise = max(0f, bands.bass[index] - bands.bass[index - 1])
        val midRise = max(0f, bands.mid[index] - bands.mid[index - 1])
        val presenceRise = max(0f, bands.presence[index] - bands.presence[index - 1])
        val trebleRise = max(0f, bands.treble[index] - bands.treble[index - 1])
        onsets[index] = (bassRise * 1.20f) + (midRise * 0.72f) + (presenceRise * 0.88f) + (trebleRise * 0.56f)
    }
    return onsets
}

private fun extractBeatChartCandidates(
    onsets: FloatArray,
    fps: Int
): List<ReverseBeatBeatMoment> {
    if (onsets.size < 3 || fps <= 0) return emptyList()

    val moments = mutableListOf<ReverseBeatBeatMoment>()
    for (index in 1 until onsets.lastIndex) {
        val onset = onsets[index]
        if (onset <= 0f) continue

        val threshold = rollingThreshold(onsets, index)
        val isLocalPeak = onset >= onsets[index - 1] && onset > onsets[index + 1]
        if (isLocalPeak && onset >= threshold) {
            moments += ReverseBeatBeatMoment(
                timeMs = ((index * 1000.0) / fps.toDouble()).toLong(),
                strength = onset
            )
        }
    }

    if (moments.size >= 8) return moments.sortedBy { it.timeMs }
    return supplementBeatChartCandidates(onsets, fps, existing = moments).sortedBy { it.timeMs }
}

internal fun extractBeatMoments(
    bands: SpectrumBandsPrecomputer.PrecomputedBands
): List<ReverseBeatBeatMoment> {
    val onsets = buildBeatOnsets(bands)
    if (onsets.size < 3 || bands.fps <= 0) return emptyList()

    val moments = mutableListOf<ReverseBeatBeatMoment>()
    var lastAcceptedMs = Long.MIN_VALUE
    for (index in 1 until onsets.lastIndex) {
        val onset = onsets[index]
        if (onset <= 0f) continue
        val timeMs = ((index * 1000.0) / bands.fps.toDouble()).toLong()
        if (timeMs - lastAcceptedMs < 240L) continue

        val threshold = rollingThreshold(onsets, index)
        val isLocalPeak = onset >= onsets[index - 1] && onset > onsets[index + 1]
        if (isLocalPeak && onset >= threshold) {
            moments += ReverseBeatBeatMoment(timeMs = timeMs, strength = onset)
            lastAcceptedMs = timeMs
        }
    }

    if (moments.size >= 12) return moments

    val supplemented = supplementBeatMoments(onsets, bands.fps, existing = moments)
    return supplemented.sortedBy { it.timeMs }
}

private fun condenseLyricCues(cues: List<PlayableLyricCue>): List<PlayableLyricCue> {
    if (cues.isEmpty()) return emptyList()

    val windows = mutableListOf<PlayableLyricCue>()
    val selected = mutableListOf<PlayableLyricCue>()

    for (cue in cues.sortedBy { it.hitTimeMs }) {
        if (windows.isEmpty()) {
            windows += cue
            continue
        }

        if (cue.hitTimeMs - windows.first().hitTimeMs < REVERSE_BEAT_LYRIC_MIN_GAP_MS) {
            windows += cue
        } else {
            selected += chooseBestLyricCue(windows)
            windows.clear()
            windows += cue
        }
    }

    if (windows.isNotEmpty()) {
        selected += chooseBestLyricCue(windows)
    }

    return enforceLyricGap(selected.sortedBy { it.hitTimeMs })
}

private fun chooseBestLyricCue(window: List<PlayableLyricCue>): PlayableLyricCue {
    if (window.size == 1) return window.first()
    val center = (window.first().hitTimeMs + window.last().hitTimeMs) / 2.0
    return window.maxBy { cue ->
        val durationWeight = cue.durationMs / 140.0
        val labelWeight = cue.label.length / 3.5
        val proximityWeight = 1.6 - (abs(cue.hitTimeMs - center) / 180.0)
        durationWeight + labelWeight + proximityWeight
    }
}

private fun enforceLyricGap(cues: List<PlayableLyricCue>): List<PlayableLyricCue> {
    if (cues.size < 2) return cues

    val accepted = mutableListOf<PlayableLyricCue>()
    cues.forEach { cue ->
        val previous = accepted.lastOrNull()
        if (previous == null || cue.hitTimeMs - previous.hitTimeMs >= REVERSE_BEAT_LYRIC_MIN_GAP_MS) {
            accepted += cue
        } else {
            accepted[accepted.lastIndex] = chooseBestLyricCue(listOf(previous, cue))
        }
    }
    return accepted
}

internal fun condenseBeatMoments(beats: List<ReverseBeatBeatMoment>): List<ReverseBeatBeatMoment> {
    if (beats.isEmpty()) return emptyList()

    val window = mutableListOf<ReverseBeatBeatMoment>()
    val selected = mutableListOf<ReverseBeatBeatMoment>()

    for (beat in beats.sortedBy { it.timeMs }) {
        if (window.isEmpty()) {
            window += beat
            continue
        }

        if (beat.timeMs - window.first().timeMs < REVERSE_BEAT_BEAT_MIN_GAP_MS) {
            window += beat
        } else {
            selected += window.maxBy { it.strength }
            window.clear()
            window += beat
        }
    }

    if (window.isNotEmpty()) {
        selected += window.maxBy { it.strength }
    }

    return selected.sortedBy { it.timeMs }
}

private data class BeatGridCandidate(
    val candidateIndex: Int,
    val frameIndex: Int,
    val beat: ReverseBeatBeatMoment
)

private data class BeatGridMatch(
    val candidate: BeatGridCandidate,
    val distanceFrames: Int,
    val weightedScore: Float
)

private fun lockBeatMomentsToDominantPulse(
    beats: List<ReverseBeatBeatMoment>,
    fps: Int
): List<ReverseBeatBeatMoment> {
    if (beats.size < 4 || fps <= 0) return beats

    val intervalFrames = estimateDominantBeatIntervalFrames(beats, fps) ?: return beats
    val sortedCandidates = beats.sortedBy { it.timeMs }
        .mapIndexed { index, beat ->
            BeatGridCandidate(
                candidateIndex = index,
                frameIndex = timeMsToFrameIndex(beat.timeMs, fps),
                beat = beat
            )
        }
    val toleranceFrames = (intervalFrames / 4).coerceIn(1, max(2, fps / 10))
    val minimumLockedCount = max(4, beats.size / 3)

    val bestSequence = sortedCandidates.maxByOrNull { anchor ->
        scoreLockedBeatSequence(
            buildLockedBeatSequence(
                anchor = anchor,
                candidates = sortedCandidates,
                intervalFrames = intervalFrames,
                toleranceFrames = toleranceFrames
            )
        )
    }?.let { anchor ->
        buildLockedBeatSequence(
            anchor = anchor,
            candidates = sortedCandidates,
            intervalFrames = intervalFrames,
            toleranceFrames = toleranceFrames
        )
    } ?: return beats

    return if (bestSequence.size >= minimumLockedCount) {
        bestSequence.map { it.candidate.beat }.sortedBy { it.timeMs }
    } else {
        beats
    }
}

private fun buildLockedBeatSequence(
    anchor: BeatGridCandidate,
    candidates: List<BeatGridCandidate>,
    intervalFrames: Int,
    toleranceFrames: Int
): List<BeatGridMatch> {
    if (candidates.isEmpty()) return emptyList()

    val used = mutableSetOf<Int>()
    val matched = mutableListOf<BeatGridMatch>()
    val minFrame = candidates.first().frameIndex
    val maxFrame = candidates.last().frameIndex
    var expectedFrame = anchor.frameIndex

    while (expectedFrame - intervalFrames >= minFrame - toleranceFrames) {
        expectedFrame -= intervalFrames
    }

    while (expectedFrame <= maxFrame + toleranceFrames) {
        val bestMatch = pickBeatCandidateForGrid(
            expectedFrame = expectedFrame,
            candidates = candidates,
            toleranceFrames = toleranceFrames,
            used = used
        )
        if (bestMatch != null) {
            used += bestMatch.candidate.candidateIndex
            matched += bestMatch
        }
        expectedFrame += intervalFrames
    }

    return matched
}

private fun pickBeatCandidateForGrid(
    expectedFrame: Int,
    candidates: List<BeatGridCandidate>,
    toleranceFrames: Int,
    used: Set<Int>
): BeatGridMatch? {
    return candidates
        .asSequence()
        .filterNot { it.candidateIndex in used }
        .mapNotNull { candidate ->
            val distanceFrames = abs(candidate.frameIndex - expectedFrame)
            if (distanceFrames > toleranceFrames) return@mapNotNull null
            val closeness = 1f - (distanceFrames.toFloat() / (toleranceFrames + 1).toFloat())
            val weightedScore = candidate.beat.strength * (0.15f + (closeness * closeness * 1.35f))
            BeatGridMatch(
                candidate = candidate,
                distanceFrames = distanceFrames,
                weightedScore = weightedScore
            )
        }
        .maxByOrNull { it.weightedScore }
}

private fun scoreLockedBeatSequence(matches: List<BeatGridMatch>): Float {
    if (matches.isEmpty()) return Float.NEGATIVE_INFINITY
    val baseScore = matches.sumOf { it.weightedScore.toDouble() }.toFloat()
    return baseScore + (matches.size * 0.25f)
}

private fun estimateDominantBeatIntervalFrames(
    beats: List<ReverseBeatBeatMoment>,
    fps: Int
): Int? {
    if (beats.size < 4 || fps <= 0) return null

    val minFrames = ((REVERSE_BEAT_BEAT_MIN_GAP_MS * fps) / 1000.0).roundToInt().coerceAtLeast(1)
    val maxFrames = ((900L * fps) / 1000.0).roundToInt().coerceAtLeast(minFrames + 1)
    val scores = FloatArray(maxFrames + 1)
    val frameStrengths = beats.sortedBy { it.timeMs }.map { beat ->
        timeMsToFrameIndex(beat.timeMs, fps) to beat.strength
    }

    frameStrengths.forEachIndexed { index, (frameIndex, strength) ->
        val upper = minOf(frameStrengths.lastIndex, index + 6)
        for (nextIndex in index + 1..upper) {
            val (nextFrameIndex, nextStrength) = frameStrengths[nextIndex]
            var foldedGap = (nextFrameIndex - frameIndex).toDouble()
            while (foldedGap > maxFrames) {
                foldedGap /= 2.0
            }
            while (foldedGap < minFrames) {
                foldedGap *= 2.0
            }
            val bucket = foldedGap.roundToInt()
            if (bucket !in minFrames..maxFrames) continue
            val closeness = (1.0 / (1.0 + abs(foldedGap - bucket))).toFloat()
            scores[bucket] += strength * nextStrength * closeness
        }
    }

    val bestBucket = (minFrames..maxFrames).maxByOrNull { scores[it] } ?: return null
    return bestBucket.takeIf { scores[it] > 0f }
}

private fun timeMsToFrameIndex(timeMs: Long, fps: Int): Int {
    return ((timeMs.toDouble() * fps.toDouble()) / 1000.0).roundToInt()
}

internal fun injectBombs(
    entries: List<ReverseBeatChartEntry>,
    mode: ReverseBeatChartMode,
    songSeed: Int
): List<ReverseBeatChartEntry> {
    if (entries.size < 5) return reindexEntries(entries)

    val minGapForBomb = if (mode == ReverseBeatChartMode.LYRIC) 1_450L else 1_300L
    val cadence = if (mode == ReverseBeatChartMode.LYRIC) 4 else 3
    val result = mutableListOf<ReverseBeatChartEntry>()
    var normalsSinceLastBomb = 0

    entries.forEachIndexed { index, current ->
        result += current
        if (current.kind.isStandardBall) {
            normalsSinceLastBomb += 1
        }

        val next = entries.getOrNull(index + 1) ?: return@forEachIndexed
        val gap = next.hitTimeMs - current.hitTimeMs
        if (!current.kind.isStandardBall || !next.kind.isStandardBall) return@forEachIndexed
        if (normalsSinceLastBomb < cadence) return@forEachIndexed
        if (gap < minGapForBomb) return@forEachIndexed

        val midpoint = current.hitTimeMs + (gap / 2L)
        val safeOffset = ((gap / 2L) - 520L).coerceAtLeast(0L)
        val offset = minOf(safeOffset, 140L + ((abs(ReverseBeatSliceMath.wave(songSeed + 41, index, 0.72, 1.0)) * 120.0).toLong()))
        val bombTime = midpoint + if ((songSeed + index) % 2 == 0) offset else -offset
        val densityBoost = ReverseBeatSliceMath.spacingBoost(current.hitTimeMs, bombTime)

        result += buildChartEntry(
            index = result.size,
            hitTimeMs = bombTime,
            emphasis = 0.96f,
            densityBoost = densityBoost,
            label = null,
            songSeed = songSeed + 97,
            kind = ReverseBeatTargetKind.BOMB
        )
        normalsSinceLastBomb = 0
    }

    return reindexEntries(result.sortedBy { it.hitTimeMs })
}

internal fun injectPowerUps(
    entries: List<ReverseBeatChartEntry>,
    mode: ReverseBeatChartMode,
    songSeed: Int
): List<ReverseBeatChartEntry> {
    if (entries.size < 6) return reindexEntries(entries)

    val standardCount = entries.count { it.kind.isStandardBall }
    if (standardCount < 6) return reindexEntries(entries)

    val minSpacingBetweenPowerUps = if (mode == ReverseBeatChartMode.LYRIC) 1_450L else 1_300L
    val targetPowerUpCount = (standardCount / if (mode == ReverseBeatChartMode.LYRIC) 5 else 6)
        .coerceAtLeast(2)
        .coerceAtMost(if (mode == ReverseBeatChartMode.LYRIC) 8 else 7)

    val candidates = buildPowerUpCandidates(
        entries = entries,
        mode = mode,
        songSeed = songSeed
    )
    if (candidates.isEmpty()) return reindexEntries(entries)

    val selected = selectPowerUpCandidates(
        candidates = candidates,
        targetCount = targetPowerUpCount,
        minSpacingMs = minSpacingBetweenPowerUps
    )
    if (selected.isEmpty()) return reindexEntries(entries)

    val result = entries.toMutableList()
    selected.forEachIndexed { index, candidate ->
        val densityBoost = ReverseBeatSliceMath.spacingBoost(candidate.previousBallHitTimeMs, candidate.hitTimeMs)
        result += buildChartEntry(
            index = candidate.anchorIndex + index,
            hitTimeMs = candidate.hitTimeMs,
            emphasis = if (candidate.kind == ReverseBeatTargetKind.POWER_LINE_CLEAR) 0.94f else 0.88f,
            densityBoost = densityBoost,
            label = null,
            songSeed = songSeed + 151 + index,
            kind = candidate.kind
        )
    }

    return reindexEntries(result.sortedBy { it.hitTimeMs })
}

private data class ReverseBeatPowerUpCandidate(
    val anchorIndex: Int,
    val previousBallHitTimeMs: Long,
    val hitTimeMs: Long,
    val kind: ReverseBeatTargetKind,
    val score: Float,
    val urgency: Int
)

private data class ReverseBeatPowerUpWindow(
    val standardCount: Int,
    val closeStandardCount: Int,
    val bombCount: Int,
    val tightGapCount: Int,
    val score: Float
)

private fun buildPowerUpCandidates(
    entries: List<ReverseBeatChartEntry>,
    mode: ReverseBeatChartMode,
    songSeed: Int
): List<ReverseBeatPowerUpCandidate> {
    val minGapBeforePickup = if (mode == ReverseBeatChartMode.LYRIC) 1_350L else 1_220L

    return buildList {
        entries.forEachIndexed { index, anchor ->
            if (!anchor.kind.isStandardBall) return@forEachIndexed

            val previousBall = entries.subList(0, index).lastOrNull { it.kind.isStandardBall } ?: return@forEachIndexed
            val gapBefore = anchor.hitTimeMs - previousBall.hitTimeMs
            if (gapBefore < minGapBeforePickup) return@forEachIndexed

            val window = analyzePowerUpWindow(entries, anchorIndex = index, anchorTimeMs = anchor.hitTimeMs)
            if (window.standardCount < 2 && window.bombCount == 0) return@forEachIndexed

            val kind = when {
                window.bombCount > 0 || window.closeStandardCount >= 3 || window.tightGapCount >= 2 -> {
                    ReverseBeatTargetKind.POWER_LINE_CLEAR
                }
                mode == ReverseBeatChartMode.LYRIC && window.closeStandardCount >= 2 -> {
                    ReverseBeatTargetKind.POWER_LYRIC_BLOOM
                }
                else -> ReverseBeatTargetKind.POWER_DOUBLE_SCORE
            }

            val minLeadMs = when (kind) {
                ReverseBeatTargetKind.POWER_LINE_CLEAR -> 420L
                ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> 360L
                ReverseBeatTargetKind.POWER_DOUBLE_SCORE -> 520L
                else -> 420L
            }
            val idealLeadMs = when (kind) {
                ReverseBeatTargetKind.POWER_LINE_CLEAR -> 520L
                ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> 430L
                ReverseBeatTargetKind.POWER_DOUBLE_SCORE -> 620L
                else -> 520L
            }
            val latestSafeLeadMs = minOf(gapBefore - 520L, 860L)
            if (latestSafeLeadMs < minLeadMs) return@forEachIndexed

            val leadMs = idealLeadMs.coerceIn(minLeadMs, latestSafeLeadMs)
            val jitter = ((abs(ReverseBeatSliceMath.wave(songSeed + 73, index, 0.66, 1.0)) * 110.0).toLong()) - 55L
            val rawPickupTime = anchor.hitTimeMs - leadMs + if ((songSeed + index) % 2 == 0) jitter else -jitter
            val pickupTime = rawPickupTime.coerceIn(
                minimumValue = previousBall.hitTimeMs + 520L,
                maximumValue = anchor.hitTimeMs - 320L
            )
            val tooCloseToBomb = entries.any { candidate ->
                candidate.kind == ReverseBeatTargetKind.BOMB && abs(candidate.hitTimeMs - pickupTime) < 360L
            }
            if (tooCloseToBomb) return@forEachIndexed

            add(
                ReverseBeatPowerUpCandidate(
                    anchorIndex = index,
                    previousBallHitTimeMs = previousBall.hitTimeMs,
                    hitTimeMs = pickupTime,
                    kind = kind,
                    score = window.score + (gapBefore / 1000f * 0.22f),
                    urgency = (window.bombCount * 3) + window.tightGapCount + window.closeStandardCount
                )
            )
        }
    }
}

private fun analyzePowerUpWindow(
    entries: List<ReverseBeatChartEntry>,
    anchorIndex: Int,
    anchorTimeMs: Long
): ReverseBeatPowerUpWindow {
    val endTimeMs = anchorTimeMs + 3_000L
    val standards = entries
        .drop(anchorIndex)
        .filter { it.kind.isStandardBall && it.hitTimeMs <= endTimeMs }
    val closeStandardCount = standards.count { it.hitTimeMs <= anchorTimeMs + 1_200L }
    val bombCount = entries.count { entry ->
        entry.kind == ReverseBeatTargetKind.BOMB &&
            entry.hitTimeMs in (anchorTimeMs + 140L)..(anchorTimeMs + 1_500L)
    }
    val tightGapCount = standards.zipWithNext().count { (first, second) ->
        second.hitTimeMs - first.hitTimeMs <= 780L
    }
    val score =
        (standards.size * 2.1f) +
            (closeStandardCount * 1.45f) +
            (bombCount * 3.8f) +
            (tightGapCount * 1.2f)

    return ReverseBeatPowerUpWindow(
        standardCount = standards.size,
        closeStandardCount = closeStandardCount,
        bombCount = bombCount,
        tightGapCount = tightGapCount,
        score = score
    )
}

private fun selectPowerUpCandidates(
    candidates: List<ReverseBeatPowerUpCandidate>,
    targetCount: Int,
    minSpacingMs: Long
): List<ReverseBeatPowerUpCandidate> {
    val selected = mutableListOf<ReverseBeatPowerUpCandidate>()

    candidates
        .sortedWith(compareByDescending<ReverseBeatPowerUpCandidate> { it.urgency }.thenByDescending { it.score }.thenBy { it.hitTimeMs })
        .forEach { candidate ->
            if (selected.size >= targetCount) return@forEach
            val overlaps = selected.any { chosen ->
                abs(chosen.hitTimeMs - candidate.hitTimeMs) < minSpacingMs || abs(chosen.anchorIndex - candidate.anchorIndex) < 2
            }
            if (!overlaps) {
                selected += candidate
            }
        }

    if (selected.size < targetCount) {
        candidates.sortedBy { it.hitTimeMs }.forEach { candidate ->
            if (selected.size >= targetCount) return@forEach
            val overlaps = selected.any { chosen ->
                abs(chosen.hitTimeMs - candidate.hitTimeMs) < minSpacingMs
            }
            if (!overlaps) {
                selected += candidate
            }
        }
    }

    return selected.sortedBy { it.hitTimeMs }
}

private fun supplementBeatChartCandidates(
    onsets: FloatArray,
    fps: Int,
    existing: List<ReverseBeatBeatMoment>
): List<ReverseBeatBeatMoment> {
    val accepted = existing.toMutableList()
    val occupiedFrames = accepted.mapTo(mutableSetOf()) { timeMsToFrameIndex(it.timeMs, fps) }
    val candidates = buildList {
        for (index in 1 until onsets.lastIndex) {
            val onset = onsets[index]
            if (onset <= 0f) continue
            if (onset < onsets[index - 1] || onset < onsets[index + 1]) continue
            add(
                ReverseBeatBeatMoment(
                    timeMs = ((index * 1000.0) / fps.toDouble()).toLong(),
                    strength = onset
                )
            )
        }
    }.sortedByDescending { it.strength }

    candidates.forEach { candidate ->
        val candidateFrame = timeMsToFrameIndex(candidate.timeMs, fps)
        val conflicts = occupiedFrames.any { abs(it - candidateFrame) <= 1 }
        if (!conflicts) {
            accepted += candidate
            occupiedFrames += candidateFrame
        }
        if (accepted.size >= 96) return@forEach
    }

    return accepted
}

private fun supplementBeatMoments(
    onsets: FloatArray,
    fps: Int,
    existing: List<ReverseBeatBeatMoment>
): List<ReverseBeatBeatMoment> {
    val accepted = existing.toMutableList()
    val candidates = buildList {
        for (index in 1 until onsets.lastIndex) {
            val onset = onsets[index]
            if (onset <= 0f) continue
            if (onset < onsets[index - 1] || onset < onsets[index + 1]) continue
            add(
                ReverseBeatBeatMoment(
                    timeMs = ((index * 1000.0) / fps.toDouble()).toLong(),
                    strength = onset
                )
            )
        }
    }.sortedByDescending { it.strength }

    candidates.forEach { candidate ->
        val conflicts = accepted.any { abs(it.timeMs - candidate.timeMs) < 260L }
        if (!conflicts) {
            accepted += candidate
        }
        if (accepted.size >= 64) return@forEach
    }

    return accepted
}

private fun rollingThreshold(onsets: FloatArray, index: Int): Float {
    val start = (index - 20).coerceAtLeast(0)
    val count = index - start
    if (count < 6) return 0.018f

    var sum = 0f
    for (i in start until index) {
        sum += onsets[i]
    }
    val mean = sum / count

    var variance = 0f
    for (i in start until index) {
        val delta = onsets[i] - mean
        variance += delta * delta
    }
    val std = sqrt(variance / count)
    return max(0.016f, mean + (std * 1.35f))
}

private fun buildChartEntry(
    index: Int,
    hitTimeMs: Long,
    emphasis: Float,
    densityBoost: Float,
    label: String?,
    songSeed: Int,
    kind: ReverseBeatTargetKind
): ReverseBeatChartEntry {
    val arc = buildArcPattern(index = index, densityBoost = densityBoost, songSeed = songSeed, kind = kind)
    val baseFlightDurationMs = when {
        densityBoost >= 0.90f -> 1_900L
        densityBoost >= 0.72f -> 2_050L
        densityBoost >= 0.54f -> 2_200L
        densityBoost >= 0.38f -> 2_350L
        else -> 2_500L
    }
    val flightDurationMs = when (kind) {
        ReverseBeatTargetKind.BOMB -> baseFlightDurationMs + 120L
        ReverseBeatTargetKind.POWER_DOUBLE_SCORE,
        ReverseBeatTargetKind.POWER_LINE_CLEAR,
        ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> baseFlightDurationMs + 220L
        ReverseBeatTargetKind.BALL -> baseFlightDurationMs
    }
    val radiusPx = (70f + (densityBoost * 18f) + (emphasis * 22f)).coerceIn(64f, 110f)

    return ReverseBeatChartEntry(
        id = index.toLong() + 1L,
        kind = kind,
        hitTimeMs = hitTimeMs,
        flightDurationMs = flightDurationMs,
        startXFraction = arc.startXFraction,
        apexXFraction = arc.apexXFraction,
        endXFraction = arc.endXFraction,
        apexYFraction = arc.apexYFraction,
        radiusPx = radiusPx,
        emphasis = emphasis,
        label = label,
    )
}

private fun buildArcPattern(
    index: Int,
    densityBoost: Float,
    songSeed: Int,
    kind: ReverseBeatTargetKind
): ReverseBeatArcPattern {
    val lanes = floatArrayOf(0.10f, 0.18f, 0.28f, 0.40f, 0.52f, 0.64f, 0.76f, 0.88f)
    val ballRoutes = listOf(
        intArrayOf(0, 2, 5),
        intArrayOf(7, 5, 2),
        intArrayOf(1, 0, 4),
        intArrayOf(6, 7, 3),
        intArrayOf(2, 5, 0),
        intArrayOf(5, 2, 7),
        intArrayOf(3, 1, 6),
        intArrayOf(4, 6, 1)
    )
    val bombRoutes = listOf(
        intArrayOf(0, 4, 7),
        intArrayOf(7, 3, 0),
        intArrayOf(2, 6, 1),
        intArrayOf(5, 1, 6)
    )
    val powerRoutes = listOf(
        intArrayOf(1, 4, 6),
        intArrayOf(6, 3, 1),
        intArrayOf(2, 6, 4),
        intArrayOf(5, 1, 3)
    )
    val routePool = when (kind) {
        ReverseBeatTargetKind.BOMB -> bombRoutes
        ReverseBeatTargetKind.POWER_DOUBLE_SCORE,
        ReverseBeatTargetKind.POWER_LINE_CLEAR,
        ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> powerRoutes
        ReverseBeatTargetKind.BALL -> ballRoutes
    }
    val route = routePool[Math.floorMod((songSeed * 3) + (index * 5), routePool.size)]
    val laneShift = ((songSeed + index) % 3) - 1
    val noise = ReverseBeatSliceMath.wave(songSeed + 17, index, frequency = 0.77, amplitude = 0.032).toFloat()
    val apexNoise = ReverseBeatSliceMath.wave(songSeed + 29, index, frequency = 1.07, amplitude = 0.045).toFloat()
    val startX = ReverseBeatSliceMath.clampFraction(lanes[shiftLane(route[0], laneShift)] + noise)
    val apexX = ReverseBeatSliceMath.clampFraction(lanes[shiftLane(route[1], -laneShift)] + apexNoise)
    val endX = ReverseBeatSliceMath.clampFraction(lanes[shiftLane(route[2], laneShift)] - (noise * 0.7f))
    val apexY = ReverseBeatSliceMath.clampFraction(
        value = 0.27f + (densityBoost * 0.11f) + abs(noise) * 0.10f + when (kind) {
            ReverseBeatTargetKind.BOMB -> 0.03f
            ReverseBeatTargetKind.POWER_DOUBLE_SCORE,
            ReverseBeatTargetKind.POWER_LINE_CLEAR,
            ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> 0.05f
            ReverseBeatTargetKind.BALL -> 0f
        },
        minValue = 0.22f,
        maxValue = 0.56f
    )

    return ReverseBeatArcPattern(
        startXFraction = startX,
        apexXFraction = apexX,
        endXFraction = endX,
        apexYFraction = apexY
    )
}

private fun shiftLane(baseIndex: Int, shift: Int): Int {
    return (baseIndex + shift).coerceIn(0, 7)
}

private fun reindexEntries(entries: List<ReverseBeatChartEntry>): List<ReverseBeatChartEntry> {
    return entries.sortedBy { it.hitTimeMs }.mapIndexed { index, entry ->
        entry.copy(id = index.toLong() + 1L)
    }
}

private fun buildSummary(mode: ReverseBeatChartMode, entries: List<ReverseBeatChartEntry>): String {
    val playableCount = entries.count { it.kind.isStandardBall }
    val bombCount = entries.count { it.kind == ReverseBeatTargetKind.BOMB }
    val powerUpCount = entries.count { it.kind.isPowerUp }
    val body = when (mode) {
        ReverseBeatChartMode.LYRIC -> "Word-sync chart • $playableCount lyric balls"
        ReverseBeatChartMode.BEAT -> "Beat chart • $playableCount audio markers"
    }
    val bombSuffix = if (bombCount > 0) " • $bombCount bombs" else ""
    val powerSuffix = if (powerUpCount > 0) " • $powerUpCount power-ups" else ""
    return body + bombSuffix + powerSuffix
}

private fun String.toPlayableUri(): Uri {
    return when {
        startsWith("content://", ignoreCase = true) ||
            startsWith("file://", ignoreCase = true) ||
            startsWith("http://", ignoreCase = true) ||
            startsWith("https://", ignoreCase = true) -> Uri.parse(this)
        else -> Uri.fromFile(File(this))
    }
}
