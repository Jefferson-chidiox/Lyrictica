package com.lyrictica.lyrics

import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object LyricsQueryNormalizer {
    private val instrumentalWord = Regex("(?i)\\binstrumental\\b")
    private val codeBracket = Regex("\\[[A-Za-z0-9._-]{4,}]")
    private val codeParen = Regex("\\([A-Za-z0-9._-]{4,}\\)")
    private val brackets = Regex("[()\\[\\]{}]")
    private val trailingSeparators = Regex("\\s*[-–—:]+\\s*$")
    private val whitespace = Regex("\\s+")

    fun searchTitle(title: String): String {
        val stripped = title
            .replace(instrumentalWord, " ")
            .replace(codeBracket, " ")
            .replace(codeParen, " ")
            .replace(brackets, " ")
            .replace(trailingSeparators, "")
            .replace(whitespace, " ")
            .trim()

        return stripped.ifBlank { title.trim() }
    }

    fun text(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
}

internal data class TimedLyricEntry(
    val text: String,
    val startTimeMs: Long
)

internal object MusixmatchLyricsParsers {
    private const val DISCLAIMER_MARKER = "******* This Lyrics is NOT for Commercial use *******"

    fun parseSubtitleBody(body: String?): ParsedLyrics? {
        val lyrics = sanitizedLyricsText(body) ?: return null
        return parseLyricsText(lyrics)
    }

    fun parseLyricsBody(body: String?): ParsedLyrics? {
        val lyrics = sanitizedLyricsText(body) ?: return null
        val lines = lyrics.lines()
            .mapNotNull { rawLine ->
                LyricsQueryNormalizer.text(rawLine)?.let { text -> LyricLine(timeMs = null, text = text) }
            }

        if (lines.isEmpty()) return null

        return ParsedLyrics(lines = lines, isSynced = false)
    }

    fun parseRichsyncBody(body: String?): ParsedLyrics? {
        val raw = sanitizedLyricsText(body) ?: return null

        return try {
            val arr = Json.parseToJsonElement(raw).jsonArray
            val lines = ArrayList<LyricLine>(arr.size)
            for (element in arr) {
                val obj = element.jsonObject
                val text = LyricsQueryNormalizer.text(obj["x"]?.jsonPrimitive?.content) ?: continue
                val lineStartMs = (obj["ts"]?.jsonPrimitive?.doubleOrNull ?: -1.0).times(1000.0).roundToLong()
                val lineEndMs = (obj["te"]?.jsonPrimitive?.doubleOrNull ?: -1.0).times(1000.0).roundToLong()
                val wordParts = obj["l"]?.jsonArray.orEmpty()
                val words = ArrayList<LyricWord>(wordParts.size)

                for (index in wordParts.indices) {
                    val wordObj = wordParts[index].jsonObject
                    val tokenText = wordObj["c"]?.jsonPrimitive?.content ?: continue
                    val tokenOffsetMs = (wordObj["o"]?.jsonPrimitive?.doubleOrNull ?: 0.0).times(1000.0).roundToLong()
                    val nextOffsetMs = wordParts.getOrNull(index + 1)
                        ?.jsonObject
                        ?.get("o")
                        ?.jsonPrimitive
                        ?.doubleOrNull
                        ?.times(1000.0)
                        ?.roundToLong()
                    val tokenStartMs = (lineStartMs + tokenOffsetMs).coerceAtLeast(0L)
                    val tokenEndMs = when {
                        nextOffsetMs != null -> (lineStartMs + nextOffsetMs).coerceAtLeast(tokenStartMs)
                        lineEndMs >= 0L -> lineEndMs.coerceAtLeast(tokenStartMs)
                        else -> tokenStartMs
                    }
                    words.add(
                        LyricWord(
                            text = tokenText,
                            startTimeMs = tokenStartMs,
                            endTimeMs = tokenEndMs
                        )
                    )
                }

                lines.add(
                    LyricLine(
                        timeMs = lineStartMs.takeIf { it >= 0L },
                        text = text,
                        words = words
                    )
                )
            }

            if (lines.isEmpty()) null else ParsedLyrics(lines = lines, isSynced = true)
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseLyricsText(text: String): ParsedLyrics? {
        val lyrics = sanitizedLyricsText(text) ?: return null
        val parsed = LrcParser.parse(lyrics)
        return parsed.takeIf { it.lines.isNotEmpty() }
    }

    internal fun parseTimedLyrics(entries: List<TimedLyricEntry>): ParsedLyrics? {
        val lines = ArrayList<LyricLine>(entries.size)
        for (entry in entries) {
            val text = LyricsQueryNormalizer.text(entry.text) ?: continue
            lines.add(LyricLine(timeMs = entry.startTimeMs.takeIf { it >= 0L }, text = text))
        }

        if (lines.isEmpty()) return null

        return ParsedLyrics(
            lines = lines.sortedBy { it.timeMs ?: Long.MAX_VALUE },
            isSynced = true
        )
    }

    private fun sanitizedLyricsText(text: String?): String? {
        val normalized = text
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.substringBefore(DISCLAIMER_MARKER)
            ?.trim()
            ?: return null

        return LyricsQueryNormalizer.text(normalized)
    }
}

internal fun ParsedLyrics.toCacheLyrics(
    trackId: Long = 0L,
    trackName: String,
    artistName: String?,
    albumName: String?,
    durationSec: Int?,
    instrumental: Boolean = false,
    richSyncBody: String? = null
): MusixmatchLyrics {
    val synced = if (isSynced) {
        lines.mapNotNull { line ->
            val timeMs = line.timeMs ?: return@mapNotNull null
            "${formatLrcTimestamp(timeMs)} ${line.text.trim()}"
        }.joinToString("\n").takeIf { it.isNotBlank() }
    } else {
        null
    }

    val plain = if (isSynced) {
        null
    } else {
        lines.joinToString("\n") { it.text.trim() }.takeIf { it.isNotBlank() }
    }

    return MusixmatchLyrics(
        trackId = trackId,
        trackName = trackName,
        artistName = artistName.orEmpty(),
        albumName = albumName.orEmpty(),
        durationSec = durationSec ?: 0,
        instrumental = instrumental,
        plainLyrics = plain,
        syncedLyricsLrc = synced,
        richSyncBody = richSyncBody
    )
}

private fun formatLrcTimestamp(timeMs: Long): String {
    val totalCs = (timeMs.coerceAtLeast(0L) / 10L)
    val minutes = totalCs / 6000L
    val seconds = (totalCs / 100L) % 60L
    val centiseconds = totalCs % 100L
    return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centiseconds)
}
