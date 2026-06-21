package com.lyrictica.karaoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeMelodyReferenceTest {

    @Test
    fun `lines with sparse short melody coverage fall back to timing scoring`() {
        val reference = KaraokeMelodyReference(
            wordRefs = listOf(
                KaraokeMelodyWordReference(
                    lineIndex = 0,
                    wordIndex = 0,
                    text = "hey",
                    startTimeMs = 0L,
                    endTimeMs = 110L,
                    expectedPitchHz = 440f,
                    averageConfidence = 0.82f,
                    sampleCount = 4
                ),
                KaraokeMelodyWordReference(
                    lineIndex = 0,
                    wordIndex = 1,
                    text = "there",
                    startTimeMs = 110L,
                    endTimeMs = 360L,
                    expectedPitchHz = null,
                    averageConfidence = 0f,
                    sampleCount = 0
                ),
                KaraokeMelodyWordReference(
                    lineIndex = 0,
                    wordIndex = 2,
                    text = "now",
                    startTimeMs = 360L,
                    endTimeMs = 520L,
                    expectedPitchHz = null,
                    averageConfidence = 0f,
                    sampleCount = 0
                )
            )
        )

        assertFalse(reference.hasMelodyForLine(0))
        assertEquals(0L, reference.requiredInTuneMs(0))
    }

    @Test
    fun `sustained confident melody coverage keeps the line pitch scorable`() {
        val reference = KaraokeMelodyReference(
            wordRefs = listOf(
                KaraokeMelodyWordReference(
                    lineIndex = 0,
                    wordIndex = 0,
                    text = "you",
                    startTimeMs = 0L,
                    endTimeMs = 500L,
                    expectedPitchHz = 440f,
                    averageConfidence = 0.74f,
                    sampleCount = 18
                ),
                KaraokeMelodyWordReference(
                    lineIndex = 0,
                    wordIndex = 1,
                    text = "know",
                    startTimeMs = 500L,
                    endTimeMs = 650L,
                    expectedPitchHz = null,
                    averageConfidence = 0f,
                    sampleCount = 0
                )
            )
        )

        assertTrue(reference.hasMelodyForLine(0))
        val requiredInTuneMs = reference.requiredInTuneMs(0)
        assertTrue(requiredInTuneMs in 250L..300L)
        assertTrue(requiredInTuneMs <= 500L)
    }
}
