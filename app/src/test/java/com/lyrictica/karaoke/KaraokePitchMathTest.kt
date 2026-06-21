package com.lyrictica.karaoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokePitchMathTest {

    @Test
    fun `note label resolves from concert pitch`() {
        assertEquals("A4", KaraokePitchMath.noteLabel(440f))
    }

    @Test
    fun `comparePitch marks close notes in tune`() {
        val match = KaraokePitchMath.comparePitch(actualHz = 442f, expectedHz = 440f, toleranceCents = 15f)
        assertTrue(match.inTune)
    }

    @Test
    fun `comparePitch treats octave-shifted singing as in tune`() {
        val match = KaraokePitchMath.comparePitch(actualHz = 220f, expectedHz = 440f, toleranceCents = 15f)
        assertTrue(match.inTune)
        assertEquals("Perfect", match.rating)
    }

    @Test
    fun `comparePitch still rejects non-octave note mismatches`() {
        val match = KaraokePitchMath.comparePitch(actualHz = 233.08f, expectedHz = 220f, toleranceCents = 15f)
        assertTrue(match.centsError > 90f)
        assertTrue(!match.inTune)
    }
}
