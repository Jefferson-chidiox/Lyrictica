package com.lyrictica.visualizer

import com.lyrictica.audio.AudioFeatures
import com.lyrictica.audio.BpmEstimator
import com.lyrictica.audio.OnsetBeatDetector
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal class VisualizerMoodEngine {

    private data class Frame(
        val positionMs: Long,
        val intensity: Float,
        val brightness: Float,
        val bass: Float,
        val mid: Float,
        val presence: Float,
        val treble: Float,
        val onset: Float,
        val beatImpulse: Float,
        val beatTriggered: Boolean
    )

    private data class WindowStats(
        val avgIntensity: Float,
        val avgBrightness: Float,
        val avgBass: Float,
        val avgMid: Float,
        val avgPresence: Float,
        val avgTreble: Float,
        val avgOnset: Float,
        val avgBeatImpulse: Float,
        val beatCount: Int,
        val stdIntensity: Float
    )

    private data class MoodScores(
        val blue: Float,
        val green: Float,
        val purple: Float,
        val red: Float
    ) {
        fun scoreFor(mood: VisualizerMood): Float = when (mood) {
            VisualizerMood.BLUE -> blue
            VisualizerMood.GREEN -> green
            VisualizerMood.PURPLE -> purple
            VisualizerMood.RED -> red
        }

        fun bestMood(preferred: VisualizerMood): VisualizerMood {
            val entries = listOf(
                VisualizerMood.BLUE to blue,
                VisualizerMood.GREEN to green,
                VisualizerMood.PURPLE to purple,
                VisualizerMood.RED to red
            )

            val best = entries.maxByOrNull { it.second } ?: (preferred to scoreFor(preferred))
            val preferredScore = scoreFor(preferred)

            // Small inertia: if the current mood is nearly as strong as the leader,
            // keep it to avoid twitchy palette hopping.
            return if (best.second - preferredScore <= 0.02f) preferred else best.first
        }
    }

    private val history = ArrayDeque<Frame>()
    private val beatDetector = OnsetBeatDetector(
        historySize = 90,
        armAfterFrames = 18,
        thresholdK = 1.35f,
        minIntervalMs = 220L
    )
    private val bpmEstimator = BpmEstimator()

    private var displayedPalette: VisualizerPalette = VisualizerPalettes.green
    private var targetMood: VisualizerMood = VisualizerMood.GREEN
    private var candidateMood: VisualizerMood = VisualizerMood.GREEN
    private var candidateSinceMs: Long = 0L
    private var lastPositionMs: Long = Long.MIN_VALUE
    private var lastFeatures: AudioFeatures? = null
    private var lastBpm: Int = 0

    val currentTheme: VisualizerPalette
        get() = displayedPalette

    internal val currentMood: VisualizerMood
        get() = targetMood

    fun reset() {
        history.clear()
        beatDetector.reset()
        bpmEstimator.reset()
        displayedPalette = VisualizerPalettes.green
        targetMood = VisualizerMood.GREEN
        candidateMood = VisualizerMood.GREEN
        candidateSinceMs = 0L
        lastPositionMs = Long.MIN_VALUE
        lastFeatures = null
        lastBpm = 0
    }

    fun update(
        features: AudioFeatures,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ): VisualizerPalette {
        if (!isPlaying) {
            return displayedPalette
        }

        if (shouldResetForSeek(positionMs)) {
            clearHistory()
        }

        val onset = computeOnset(features, lastFeatures)
        val beatResult = beatDetector.process(onsetValue = onset, nowMs = positionMs)
        if (beatResult.isBeat) {
            bpmEstimator.onBeat(positionMs)
        }
        lastBpm = bpmEstimator.bpm

        val intensity = weightedIntensity(features)
        val brightness = weightedBrightness(features)
        history.addLast(
            Frame(
                positionMs = positionMs,
                intensity = intensity,
                brightness = brightness,
                bass = features.bass,
                mid = features.mid,
                presence = features.presence,
                treble = features.treble,
                onset = onset,
                beatImpulse = beatResult.impulse,
                beatTriggered = beatResult.isBeat
            )
        )
        trimHistory(positionMs)

        val scores = computeScores(positionMs, durationMs)
        updateTargetMood(scores, positionMs)

        displayedPalette = VisualizerPalettes.lerp(
            from = displayedPalette,
            to = VisualizerPalettes.forMood(targetMood),
            progress = 0.05f
        )

        lastPositionMs = positionMs
        lastFeatures = features
        return displayedPalette
    }

    private fun clearHistory() {
        history.clear()
        beatDetector.reset()
        bpmEstimator.reset()
        lastPositionMs = Long.MIN_VALUE
        lastFeatures = null
        lastBpm = 0
        candidateMood = targetMood
        candidateSinceMs = 0L
    }

    private fun shouldResetForSeek(positionMs: Long): Boolean {
        val previous = lastPositionMs
        if (previous == Long.MIN_VALUE) return false
        return positionMs < previous || positionMs - previous > 2500L
    }

    private fun trimHistory(positionMs: Long) {
        val cutoff = positionMs - HISTORY_WINDOW_MS
        while (history.isNotEmpty() && history.first().positionMs < cutoff) {
            history.removeFirst()
        }
    }

    private fun computeScores(nowMs: Long, durationMs: Long): MoodScores {
        val current = statsForWindow(nowMs, CURRENT_WINDOW_MS)
        val previous = statsForWindow(nowMs - CURRENT_WINDOW_MS, CURRENT_WINDOW_MS)

        val energy = current.avgIntensity.coerceIn(0f, 1f)
        val brightness = current.avgBrightness.coerceIn(0f, 1f)
        val drive = (current.avgBass * 0.55f + current.avgMid * 0.45f).coerceIn(0f, 1f)
        val trend = ((current.avgIntensity - previous.avgIntensity) + ((current.avgBrightness - previous.avgBrightness) * 0.6f))
            .coerceIn(-1f, 1f)
        val trendPositive = max(0f, trend)

        val stability = (1f - current.stdIntensity * 2.0f).coerceIn(0f, 1f)
        val beatDensity = (current.beatCount.toFloat() / 4f).coerceIn(0f, 1f)
        val impulse = current.avgBeatImpulse.coerceIn(0f, 1f)
        val bpmScore = if (lastBpm > 0) {
            (1f - abs(lastBpm - 120f) / 120f).coerceIn(0f, 1f)
        } else {
            0f
        }

        val repeatScore = (stability * 0.42f + current.avgOnset * 0.18f + beatDensity * 0.18f + impulse * 0.12f + bpmScore * 0.10f)
            .coerceIn(0f, 1f)
        val repeatZone = smoothStep(0.38f, 0.66f, repeatScore)

        val introZone = 1f - smoothStep(0.06f, 0.16f, energy)
        val grooveZone = smoothStep(0.09f, 0.24f, energy) * (1f - smoothStep(0.30f, 0.44f, energy))
        val buildEnergyZone = smoothStep(0.18f, 0.38f, energy) * (1f - smoothStep(0.46f, 0.62f, energy))
        val peakEnergyZone = smoothStep(0.32f, 0.56f, energy)
        val brightZone = smoothStep(0.08f, 0.28f, brightness)
        val riseZone = smoothStep(0.03f, 0.10f, trendPositive)
        val surgeZone = smoothStep(0.03f, 0.10f, current.avgBeatImpulse + current.avgOnset * 0.5f)

        val greenBase = introZone * (0.86f + 0.14f * (1f - repeatScore))
        val blueBase = grooveZone * repeatZone * (0.72f + 0.28f * (1f - riseZone)) * (1f - 0.20f * brightZone)
        val purpleBase = buildEnergyZone * brightZone * (0.60f + 0.40f * riseZone) * (0.65f + 0.35f * repeatScore) * (0.70f + 0.30f * drive)
        val redBase = peakEnergyZone * brightZone * (0.65f + 0.35f * surgeZone) * (0.55f + 0.45f * repeatScore)

        return MoodScores(
            blue = blueBase,
            green = greenBase,
            purple = purpleBase,
            red = redBase
        )
    }

    private fun updateTargetMood(scores: MoodScores, nowMs: Long) {
        val preferred = targetMood
        val bestMood = scores.bestMood(preferred)
        if (bestMood == targetMood) {
            candidateMood = targetMood
            candidateSinceMs = nowMs
            return
        }

        val currentTargetScore = scores.scoreFor(targetMood)
        val bestScore = scores.scoreFor(bestMood)
        val strongEnough = bestScore >= currentTargetScore + 0.02f

        if (!strongEnough) {
            candidateMood = targetMood
            candidateSinceMs = nowMs
            return
        }

        if (candidateMood != bestMood) {
            candidateMood = bestMood
            candidateSinceMs = nowMs
        }

        if (nowMs - candidateSinceMs >= 250L) {
            targetMood = bestMood
        }
    }

    private fun statsForWindow(nowMs: Long, windowMs: Long): WindowStats {
        val lower = nowMs - windowMs
        val upper = nowMs

        var count = 0
        var sumIntensity = 0f
        var sumIntensitySq = 0f
        var sumBrightness = 0f
        var sumBass = 0f
        var sumMid = 0f
        var sumPresence = 0f
        var sumTreble = 0f
        var sumOnset = 0f
        var sumBeatImpulse = 0f
        var beatCount = 0

        for (frame in history) {
            if (frame.positionMs < lower || frame.positionMs > upper) continue
            count++
            sumIntensity += frame.intensity
            sumIntensitySq += frame.intensity * frame.intensity
            sumBrightness += frame.brightness
            sumBass += frame.bass
            sumMid += frame.mid
            sumPresence += frame.presence
            sumTreble += frame.treble
            sumOnset += frame.onset
            sumBeatImpulse += frame.beatImpulse
            if (frame.beatTriggered) beatCount++
        }

        if (count == 0) {
            return WindowStats(
                avgIntensity = 0f,
                avgBrightness = 0f,
                avgBass = 0f,
                avgMid = 0f,
                avgPresence = 0f,
                avgTreble = 0f,
                avgOnset = 0f,
                avgBeatImpulse = 0f,
                beatCount = 0,
                stdIntensity = 0f
            )
        }

        val invCount = 1f / count.toFloat()
        val avgIntensity = sumIntensity * invCount
        val variance = (sumIntensitySq * invCount) - (avgIntensity * avgIntensity)
        val stdIntensity = sqrt(max(0f, variance))

        return WindowStats(
            avgIntensity = avgIntensity,
            avgBrightness = sumBrightness * invCount,
            avgBass = sumBass * invCount,
            avgMid = sumMid * invCount,
            avgPresence = sumPresence * invCount,
            avgTreble = sumTreble * invCount,
            avgOnset = sumOnset * invCount,
            avgBeatImpulse = sumBeatImpulse * invCount,
            beatCount = beatCount,
            stdIntensity = stdIntensity
        )
    }

    private fun computeOnset(features: AudioFeatures, previous: AudioFeatures?): Float {
        if (previous == null) return 0f

        val deltaBass = (features.bass - previous.bass).coerceAtLeast(0f)
        val deltaMid = (features.mid - previous.mid).coerceAtLeast(0f)
        val deltaPresence = (features.presence - previous.presence).coerceAtLeast(0f)
        val deltaTreble = (features.treble - previous.treble).coerceAtLeast(0f)

        val flux = deltaBass * 1.25f + deltaMid * 1.0f + deltaPresence * 0.90f + deltaTreble * 0.80f
        return (flux * 3.25f).coerceIn(0f, 1f)
    }

    private fun weightedIntensity(features: AudioFeatures): Float {
        return (features.bass * 0.34f + features.mid * 0.28f + features.presence * 0.20f + features.treble * 0.18f)
            .coerceIn(0f, 1f)
    }

    private fun weightedBrightness(features: AudioFeatures): Float {
        return (features.presence * 0.55f + features.treble * 0.45f).coerceIn(0f, 1f)
    }

    private fun progressFor(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val width = (edge1 - edge0).let { if (it == 0f) 1f else it }
        val t = ((x - edge0) / width).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    companion object {
        private const val HISTORY_WINDOW_MS = 8000L
        private const val CURRENT_WINDOW_MS = 800L
    }
}
