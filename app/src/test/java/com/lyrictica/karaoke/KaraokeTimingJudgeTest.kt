package com.lyrictica.karaoke

import com.lyrictica.lyrics.LyricLine
import com.lyrictica.lyrics.LyricWord
import com.lyrictica.lyrics.ParsedLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeTimingJudgeTest {

    @Test
    fun `buildLineWindows derives timing from word sync`() {
        val parsed = ParsedLyrics(
            lines = listOf(
                LyricLine(
                    timeMs = 1_000L,
                    text = "Hello world",
                    words = listOf(
                        LyricWord(text = "Hello", startTimeMs = 1_000L, endTimeMs = 1_350L),
                        LyricWord(text = " ", startTimeMs = 1_350L, endTimeMs = 1_420L),
                        LyricWord(text = "world", startTimeMs = 1_420L, endTimeMs = 1_900L)
                    )
                )
            ),
            isSynced = true
        )

        val windows = KaraokeTimingJudge.buildLineWindows(parsed)

        assertEquals(1, windows.size)
        assertEquals(1_000L, windows[0].startTimeMs)
        assertEquals(1_900L, windows[0].endTimeMs)
        assertTrue(windows[0].requiredVoicedMs >= 260L)
    }
}
