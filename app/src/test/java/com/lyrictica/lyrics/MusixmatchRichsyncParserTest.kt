package com.lyrictica.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusixmatchRichsyncParserTest {

    @Test
    fun `parseRichsyncBody preserves word timing`() {
        val richsync = """
            [
              {
                "ts": 12.0,
                "te": 14.4,
                "x": "Hello world",
                "l": [
                  {"c": "Hello", "o": 0.0},
                  {"c": " ", "o": 0.6},
                  {"c": "world", "o": 0.72}
                ]
              }
            ]
        """.trimIndent()

        val parsed = MusixmatchLyricsParsers.parseRichsyncBody(richsync)

        assertNotNull(parsed)
        assertTrue(parsed!!.isSynced)
        assertTrue(parsed.hasWordSync)
        assertEquals(1, parsed.lines.size)
        assertEquals("Hello world", parsed.lines[0].text)
        assertEquals(3, parsed.lines[0].words.size)
        assertEquals("Hello", parsed.lines[0].words[0].text)
        assertEquals(12_000L, parsed.lines[0].words[0].startTimeMs)
        assertEquals(12_600L, parsed.lines[0].words[0].endTimeMs)
        assertEquals("world", parsed.lines[0].words[2].text)
        assertEquals(12_720L, parsed.lines[0].words[2].startTimeMs)
        assertEquals(14_400L, parsed.lines[0].words[2].endTimeMs)
    }
}
