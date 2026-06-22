package com.lyrictica.karaoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLineSpeechMatcherTest {

    @Test
    fun `normalizeText lowercases strips punctuation and collapses spaces`() {
        val normalized = KaraokeLineSpeechMatcher.normalizeText("  Hello,   WORLD!!  ")
        assertEquals("hello world", normalized)
    }

    @Test
    fun `evaluate passes when enough words match in order`() {
        val result = KaraokeLineSpeechMatcher.evaluate(
            expectedLine = "we found love in a hopeless place",
            transcriptAlternatives = listOf("we found love in hopeless place")
        )

        assertTrue(result.isMatch)
        assertEquals(6, result.matchedWords)
    }

    @Test
    fun `evaluate fails when too few words match`() {
        val result = KaraokeLineSpeechMatcher.evaluate(
            expectedLine = "we found love in a hopeless place",
            transcriptAlternatives = listOf("we love place")
        )

        assertFalse(result.isMatch)
        assertEquals(3, result.matchedWords)
    }

    @Test
    fun `evaluate checks short lines strictly`() {
        assertTrue(
            KaraokeLineSpeechMatcher.evaluate(
                expectedLine = "hello you",
                transcriptAlternatives = listOf("hello you")
            ).isMatch
        )

        assertFalse(
            KaraokeLineSpeechMatcher.evaluate(
                expectedLine = "hello you",
                transcriptAlternatives = listOf("hello")
            ).isMatch
        )
    }
}
