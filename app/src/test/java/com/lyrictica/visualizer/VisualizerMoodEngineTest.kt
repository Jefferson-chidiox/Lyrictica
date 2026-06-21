package com.lyrictica.visualizer

import com.lyrictica.audio.AudioFeatures
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerMoodEngineTest {

    @Test
    fun `mood follows intro groove build peak and drop`() {
        val engine = VisualizerMoodEngine()
        val durationMs = 180_000L
        var positionMs = 0L

        fun feed(features: AudioFeatures, frames: Int) {
            repeat(frames) {
                engine.update(
                    features = features,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = true
                )
                positionMs += 50L
            }
        }

        // Intro / calm.
        feed(
            AudioFeatures(
                bass = 0.04f,
                mid = 0.03f,
                presence = 0.02f,
                treble = 0.02f
            ),
            frames = 22
        )
        assertEquals(VisualizerMood.GREEN, engine.currentMood)

        // Steady groove.
        feed(
            AudioFeatures(
                bass = 0.30f,
                mid = 0.28f,
                presence = 0.14f,
                treble = 0.10f
            ),
            frames = 40
        )
        assertEquals(VisualizerMood.BLUE, engine.currentMood)

        // Build / rise.
        feed(
            AudioFeatures(
                bass = 0.42f,
                mid = 0.48f,
                presence = 0.46f,
                treble = 0.34f
            ),
            frames = 30
        )
        assertEquals(VisualizerMood.PURPLE, engine.currentMood)

        // Peak / chorus.
        feed(
            AudioFeatures(
                bass = 0.74f,
                mid = 0.80f,
                presence = 0.82f,
                treble = 0.78f
            ),
            frames = 28
        )
        assertEquals(VisualizerMood.RED, engine.currentMood)

        // Back to a calmer verse.
        feed(
            AudioFeatures(
                bass = 0.08f,
                mid = 0.07f,
                presence = 0.05f,
                treble = 0.04f
            ),
            frames = 22
        )
        assertEquals(VisualizerMood.GREEN, engine.currentMood)
    }
}
