package com.lyrictica.audio

import kotlin.math.abs

/**
 * Lightweight BPM estimator from beat timestamps.
 *
 * Uses a median of recent inter-beat intervals to reject outliers.
 */
class BpmEstimator(
    private val historySize: Int = 8,
    private val minBpm: Int = 60,
    private val maxBpm: Int = 200
) {
    private val intervalsMs = ArrayDeque<Long>(historySize)
    private var lastBeatMs: Long = 0L
    private var hasLastBeat = false

    var bpm: Int = 0
        private set

    fun reset() {
        intervalsMs.clear()
        lastBeatMs = 0L
        hasLastBeat = false
        bpm = 0
    }

    /** Call this only on beat edges. */
    fun onBeat(nowMs: Long) {
        if (hasLastBeat) {
            val interval = nowMs - lastBeatMs
            // Ignore nonsense (timer hiccups / double triggers).
            if (interval in 200L..2000L) {
                intervalsMs.addLast(interval)
                if (intervalsMs.size > historySize) intervalsMs.removeFirst()
                bpm = estimateBpmFromIntervals()
            }
        }
        lastBeatMs = nowMs
        hasLastBeat = true
    }

    private fun estimateBpmFromIntervals(): Int {
        if (intervalsMs.size < 2) return bpm

        val sorted = intervalsMs.sorted()
        val median = sorted[sorted.size / 2]
        if (median <= 0) return bpm

        var candidate = (60000.0 / median).toInt()

        // Fold common half/double time into a sensible range.
        while (candidate < minBpm) candidate *= 2
        while (candidate > maxBpm) candidate /= 2

        // Stabilize tiny jitter
        if (bpm != 0 && abs(candidate - bpm) <= 1) return bpm

        return candidate
    }
}
