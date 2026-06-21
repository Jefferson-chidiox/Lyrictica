package com.lyrictica.lyrics

internal object LyricsSync {

    data class ActiveWordPosition(
        val lineIndex: Int,
        val wordIndex: Int
    )

    /** Returns the active line index for [positionMs] (last line whose time <= position). */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (lines[0].timeMs == null) return -1

        var lo = 0
        var hi = lines.size - 1
        var ans = -1

        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val t = lines[mid].timeMs ?: return -1
            if (t <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }

        return ans
    }

    fun currentWordIndex(line: LyricLine, positionMs: Long): Int {
        val voicedWords = line.words
        if (voicedWords.isEmpty()) return -1

        for (index in voicedWords.indices.reversed()) {
            val word = voicedWords[index]
            if (positionMs >= word.startTimeMs && positionMs <= word.endTimeMs) {
                return index
            }
            if (positionMs > word.endTimeMs) {
                return index
            }
        }

        return if (positionMs < voicedWords.first().startTimeMs) 0 else voicedWords.lastIndex
    }

    fun currentWordPosition(lines: List<LyricLine>, positionMs: Long): ActiveWordPosition? {
        val lineIndex = currentIndex(lines, positionMs)
        if (lineIndex !in lines.indices) return null
        val wordIndex = currentWordIndex(lines[lineIndex], positionMs)
        return wordIndex.takeIf { it >= 0 }?.let { ActiveWordPosition(lineIndex, it) }
    }
}
