package com.lyrictica.karaoke

import com.lyrictica.lyrics.LyricLine
import com.lyrictica.lyrics.ParsedLyrics
import kotlin.math.max
import kotlin.math.min

internal data class KaraokeLineWindow(
    val lineIndex: Int,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val requiredVoicedMs: Long
)

internal object KaraokeTimingJudge {
    fun buildLineWindows(parsed: ParsedLyrics?): List<KaraokeLineWindow> {
        if (parsed == null || !parsed.isSynced) return emptyList()

        return parsed.lines.mapIndexedNotNull { index, line ->
            val startTimeMs = line.words.firstOrNull { it.text.isNotBlank() }?.startTimeMs ?: line.timeMs ?: return@mapIndexedNotNull null
            val endTimeMs = line.words.lastOrNull { it.text.isNotBlank() }?.endTimeMs
                ?: parsed.lines.getOrNull(index + 1)?.timeMs
                ?: return@mapIndexedNotNull null
            if (endTimeMs <= startTimeMs) return@mapIndexedNotNull null

            KaraokeLineWindow(
                lineIndex = index,
                text = line.text,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                requiredVoicedMs = requiredVoicedDuration(line, startTimeMs, endTimeMs)
            )
        }
    }

    fun windowForLine(windows: List<KaraokeLineWindow>, lineIndex: Int): KaraokeLineWindow? {
        return windows.firstOrNull { it.lineIndex == lineIndex }
    }

    fun rewindTargetLineIndex(lineIndex: Int, rewindLines: Int = 2): Int {
        return (lineIndex - rewindLines).coerceAtLeast(0)
    }

    private fun requiredVoicedDuration(line: LyricLine, startTimeMs: Long, endTimeMs: Long): Long {
        val voicedWordCount = line.words.count { it.text.isNotBlank() }.coerceAtLeast(1)
        val durationMs = (endTimeMs - startTimeMs).coerceAtLeast(250L)
        val wordDriven = voicedWordCount * 140L
        val durationDriven = (durationMs * 0.30f).toLong()
        return min(1_100L, max(260L, max(wordDriven, durationDriven)))
    }
}
