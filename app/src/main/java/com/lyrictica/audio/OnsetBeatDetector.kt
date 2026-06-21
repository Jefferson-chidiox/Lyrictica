package com.lyrictica.audio

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Real-time beat trigger from an onset/novelty function (e.g. spectral flux).
 *
 * Designed for Android Visualizer-style frame rates (~10–20Hz):
 * - adaptive threshold: mean + k*std over recent history
 * - refractory (min interval) to prevent double triggering
 * - time-based exponential decay for a smooth transient envelope
 */
class OnsetBeatDetector(
    private val historySize: Int = 30, // ~1.5s @ 20Hz
    private val armAfterFrames: Int = 10, // wait a few frames so threshold isn't near-zero
    private val thresholdK: Float = 1.6f,
    private val minIntervalMs: Long = 230L, // suppress 1/8th-note chatter; allow up to ~260 BPM
    private val minOnset: Float = 1e-3f,
    impulseHalfLifeMs: Float = 180f
) {
    private val history = FloatArray(historySize)
    private var historyFilled = 0
    private var historyIndex = 0

    private var lastBeatMs: Long = 0L
    private var hasLastBeat = false

    private var lastUpdateMs: Long = 0L
    private var hasLastUpdate = false

    private val decayTauMs: Float = (impulseHalfLifeMs / 0.69314718056f) // halfLife / ln(2)

    private var impulse = 0f

    data class Result(
        val impulse: Float,
        val isBeat: Boolean
    )

    /**
     * @return transient envelope in 0..1 plus a boolean edge trigger when a spike is detected.
     */
    fun process(onsetValue: Float, nowMs: Long): Result {
        // 1) Decay impulse (time-based, independent of frame rate)
        if (hasLastUpdate) {
            val dtMs = (nowMs - lastUpdateMs).coerceAtLeast(0L)
            val mult = exp(-dtMs.toFloat() / decayTauMs)
            impulse *= mult
        } else {
            hasLastUpdate = true
        }
        lastUpdateMs = nowMs

        // 2) Compute adaptive threshold from history
        val (mean, std) = meanStd()
        val threshold = mean + thresholdK * std

        val armed = historyFilled >= armAfterFrames.coerceAtMost(historySize)
        val intervalOk = !hasLastBeat || (nowMs - lastBeatMs) >= minIntervalMs
        val canTrigger = armed && intervalOk
        val isBeat = canTrigger && onsetValue >= minOnset && onsetValue > threshold

        if (isBeat) {
            impulse = 1f
            lastBeatMs = nowMs
            hasLastBeat = true
        }

        // 3) Update history AFTER decision (prevents the current spike from inflating its own threshold)
        history[historyIndex] = onsetValue
        historyIndex = (historyIndex + 1) % historySize
        if (historyFilled < historySize) historyFilled++

        // Clamp to sane bounds
        if (impulse < 0f) impulse = 0f
        if (impulse > 1f) impulse = 1f
        return Result(impulse = impulse, isBeat = isBeat)
    }

    fun reset() {
        history.fill(0f)
        historyFilled = 0
        historyIndex = 0
        lastBeatMs = 0L
        hasLastBeat = false
        impulse = 0f
        hasLastUpdate = false
        lastUpdateMs = 0L
    }

    private fun meanStd(): Pair<Float, Float> {
        val n = historyFilled
        if (n <= 1) return 0f to 0f

        var sum = 0f
        for (i in 0 until n) sum += history[i]
        val mean = sum / n

        var varSum = 0f
        for (i in 0 until n) {
            val d = history[i] - mean
            varSum += d * d
        }
        val variance = varSum / n
        val std = sqrt(variance)
        return mean to std
    }
}
