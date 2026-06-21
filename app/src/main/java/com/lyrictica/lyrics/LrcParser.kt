package com.lyrictica.lyrics

/** Minimal LRC parser for lines like: [mm:ss.xx] lyric text */
internal object LrcParser {

    // Matches [mm:ss.xx] or [mm:ss]
    private val timeTag = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,2}))?]" )

    fun parse(lrc: String): ParsedLyrics {
        val out = ArrayList<LyricLine>(256)

        lrc.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach

            // Skip metadata tags like [ar:], [ti:], etc.
            if (line.startsWith("[ar:") || line.startsWith("[ti:") || line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[offset:")) {
                return@forEach
            }

            val matches = timeTag.findAll(line).toList()
            if (matches.isEmpty()) {
                // If there are lines without timestamps, keep them as unsynced lines.
                LyricsQueryNormalizer.text(stripTags(line))?.let { text ->
                    out.add(LyricLine(timeMs = null, text = text))
                }
                return@forEach
            }

            val text = LyricsQueryNormalizer.text(stripTags(line)) ?: return@forEach
            for (m in matches) {
                val mm = m.groupValues[1].toIntOrNull() ?: continue
                val ss = m.groupValues[2].toIntOrNull() ?: continue
                val cs = m.groupValues[3].toIntOrNull() ?: 0 // centiseconds

                val timeMs = (mm * 60_000L) + (ss * 1000L) + (cs * 10L)
                out.add(LyricLine(timeMs = timeMs, text = text))
            }
        }

        val synced = out.any { it.timeMs != null }

        val sorted = if (synced) {
            out.filter { it.timeMs != null }.sortedBy { it.timeMs!! }
        } else {
            out
        }

        return ParsedLyrics(lines = sorted, isSynced = synced)
    }

    private fun stripTags(line: String): String {
        return line.replace(timeTag, "")
    }
}
