package com.lyrictica.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class OnsetBeatDetectorTest {

    @Test
    fun `does not trigger before armed`() {
        val detector = OnsetBeatDetector(historySize = 30, armAfterFrames = 10)

        var t = 0L
        repeat(5) {
            t += 50
            val res = detector.process(onsetValue = 0.5f, nowMs = t)
            assertEquals(0f, res.impulse, 0.0001f)
            assert(!res.isBeat)
        }
    }

    @Test
    fun `triggers on clear spike after arming`() {
        val detector = OnsetBeatDetector(historySize = 30, armAfterFrames = 10, thresholdK = 1.6f)

        var t = 0L

        // Baseline: low variance so threshold stays near baseline.
        repeat(12) {
            t += 50
            detector.process(onsetValue = 0.01f, nowMs = t)
        }

        // Spike
        t += 50
        val res = detector.process(onsetValue = 0.5f, nowMs = t)
        assertEquals(1f, res.impulse, 0.0001f)
        assert(res.isBeat)
    }

    @Test
    fun `refractory period prevents double trigger`() {
        val detector = OnsetBeatDetector(historySize = 30, armAfterFrames = 10, minIntervalMs = 260)

        var t = 0L
        repeat(12) {
            t += 50
            detector.process(onsetValue = 0.01f, nowMs = t)
        }

        // First beat
        t += 50
        val beat1 = detector.process(onsetValue = 0.5f, nowMs = t)
        assertEquals(1f, beat1.impulse, 0.0001f)
        assert(beat1.isBeat)

        // Too soon (100ms later)
        t += 100
        val beat2 = detector.process(onsetValue = 0.5f, nowMs = t)

        // Must not re-attack to 1f.
        assert(!beat2.isBeat)
        assert(beat2.impulse < 1f)

        // After refractory window, it can re-trigger
        t += 300
        val beat3 = detector.process(onsetValue = 0.5f, nowMs = t)
        assertEquals(1f, beat3.impulse, 0.0001f)
        assert(beat3.isBeat)
    }
}
