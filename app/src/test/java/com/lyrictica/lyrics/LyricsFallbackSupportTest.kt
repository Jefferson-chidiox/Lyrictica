package com.lyrictica.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFallbackSupportTest {

    @Test
    fun stripsInstrumentalAndNoiseFromSearchTitle() {
        assertEquals("Song", LyricsQueryNormalizer.searchTitle("Song (Instrumental)"))
        assertEquals("Song", LyricsQueryNormalizer.searchTitle("Song - instrumental"))
        assertEquals("UNFOLDING", LyricsQueryNormalizer.searchTitle("UNFOLDING [FQZZP6SCVR8]"))
    }

    @Test
    fun keepsTitleWhenStripWouldGoBlank() {
        assertEquals("Instrumental", LyricsQueryNormalizer.searchTitle("Instrumental"))
    }

    @Test
    fun parsesMusixmatchSubtitleBody() {
        val parsed = MusixmatchLyricsParsers.parseSubtitleBody(
            "[01:21.24] Shit them all festival, laugh at the beautiful\n[01:25.98] It's just a nod to the canon"
        )

        assertNotNull(parsed)
        assertTrue(parsed!!.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(81_240L, parsed.lines.first().timeMs)
        assertEquals("Shit them all festival, laugh at the beautiful", parsed.lines.first().text)
    }

    @Test
    fun ignoresLiteralNullLyrics() {
        val parsed = MusixmatchLyricsParsers.parseSubtitleBody(
            "[00:15.00]null\n[00:20.00]First line"
        )

        assertNotNull(parsed)
        assertTrue(parsed!!.isSynced)
        assertEquals(1, parsed.lines.size)
        assertEquals("First line", parsed.lines.first().text)
    }

    @Test
    fun parsesMusixmatchRichsyncBody() {
        val parsed = MusixmatchLyricsParsers.parseRichsyncBody(
            "[{\"ts\":27.55,\"x\":\"Listen to the wind blow\"},{\"ts\":31.5,\"x\":\"Watch the sun rise\"}]"
        )

        assertNotNull(parsed)
        assertTrue(parsed!!.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(27_550L, parsed.lines.first().timeMs)
        assertEquals("Listen to the wind blow", parsed.lines.first().text)
    }

    @Test
    fun parsesPlainLyricsBody() {
        val parsed = MusixmatchLyricsParsers.parseLyricsBody("First line...\nSecond line")

        assertNotNull(parsed)
        assertFalse(parsed!!.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals("First line...", parsed.lines.first().text)
    }
}
