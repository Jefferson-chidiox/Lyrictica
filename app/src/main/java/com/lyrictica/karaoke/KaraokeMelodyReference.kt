package com.lyrictica.karaoke

import com.lyrictica.lyrics.ParsedLyrics
import java.security.MessageDigest
import kotlin.math.absoluteValue
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
internal data class KaraokeMelodyWordReference(
    val lineIndex: Int,
    val wordIndex: Int,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val expectedPitchHz: Float?,
    val averageConfidence: Float,
    val sampleCount: Int
)

@Serializable
internal data class KaraokeMelodyReference(
    val wordRefs: List<KaraokeMelodyWordReference>
) {
    private data class LinePitchRequirement(
        val requiredInTuneMs: Long
    )

    private val wordMap by lazy {
        wordRefs.associateBy { it.lineIndex to it.wordIndex }
    }

    private val lineRequirements by lazy {
        mutableMapOf<Int, LinePitchRequirement>().apply {
            wordRefs
                .groupBy { it.lineIndex }
                .forEach { (lineIndex, refs) ->
                    buildLineRequirement(refs)?.let { put(lineIndex, it) }
                }
        }
    }

    fun wordFor(lineIndex: Int, wordIndex: Int): KaraokeMelodyWordReference? = wordMap[lineIndex to wordIndex]

    fun hasMelodyForLine(lineIndex: Int): Boolean = lineRequirements.containsKey(lineIndex)

    fun requiredInTuneMs(lineIndex: Int): Long = lineRequirements[lineIndex]?.requiredInTuneMs ?: 0L

    val hasAnyReference: Boolean
        get() = wordRefs.any { it.expectedPitchHz != null }

    private fun buildLineRequirement(refs: List<KaraokeMelodyWordReference>): LinePitchRequirement? {
        val melodicRefs = refs.filter { it.expectedPitchHz != null }
        if (melodicRefs.isEmpty()) return null

        val coveredDurationMs = melodicRefs.sumOf(::lyricDurationMs)
        val totalDurationMs = refs.sumOf(::lyricDurationMs).coerceAtLeast(1L)
        val coverageRatio = coveredDurationMs.toFloat() / totalDurationMs.toFloat()
        val averageConfidence = melodicRefs.map { it.averageConfidence }.average().toFloat()

        // Mixed-track local pitch extraction is noisy. Only force pitch scoring when a line has
        // enough confident melodic coverage; otherwise the challenge falls back to voiced timing.
        val pitchScorable = coveredDurationMs >= 220L &&
            coverageRatio >= 0.40f &&
            averageConfidence >= 0.45f
        if (!pitchScorable) return null

        val requiredInTuneMs = max(
            140L,
            min(720L, (coveredDurationMs * 0.55f).toLong())
        ).coerceAtMost(coveredDurationMs)

        return LinePitchRequirement(requiredInTuneMs = requiredInTuneMs)
    }

    private fun lyricDurationMs(ref: KaraokeMelodyWordReference): Long {
        return (ref.endTimeMs - ref.startTimeMs).coerceAtLeast(80L)
    }

    companion object {
        fun lyricsFingerprint(parsed: ParsedLyrics): String {
            val payload = buildString {
                append(parsed.isSynced)
                parsed.lines.forEachIndexed { lineIndex, line ->
                    append('|').append(lineIndex).append(':').append(line.text)
                    line.words.forEachIndexed { wordIndex, word ->
                        append('[')
                            .append(wordIndex)
                            .append(':')
                            .append(word.text)
                            .append('@')
                            .append(word.startTimeMs)
                            .append('-')
                            .append(word.endTimeMs)
                            .append(']')
                    }
                }
            }
            val bytes = MessageDigest.getInstance("SHA-1").digest(payload.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

internal data class KaraokePitchMatch(
    val centsError: Float,
    val inTune: Boolean,
    val rating: String
)

internal object KaraokePitchMath {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun comparePitch(actualHz: Float, expectedHz: Float, toleranceCents: Float = 115f): KaraokePitchMatch {
        val cents = octaveAgnosticCentsError(actualHz, expectedHz)
        val centsAbs = cents.absoluteValue
        val rating = when {
            centsAbs <= 30f -> "Perfect"
            centsAbs <= 65f -> "Great"
            centsAbs <= 115f -> "Nice Try"
            else -> "Off!"
        }
        return KaraokePitchMatch(centsError = cents, inTune = centsAbs <= toleranceCents, rating = rating)
    }

    fun centsError(actualHz: Float, expectedHz: Float): Float {
        if (actualHz <= 0f || expectedHz <= 0f) return Float.POSITIVE_INFINITY
        return (1200.0 * log2((actualHz / expectedHz).toDouble())).toFloat()
    }

    fun noteLabel(pitchHz: Float?): String? {
        val hz = pitchHz ?: return null
        if (hz <= 0f) return null
        val midi = (69 + 12 * log2((hz / 440f).toDouble())).roundToInt()
        val note = noteNames[(midi % 12 + 12) % 12]
        val octave = (midi / 12) - 1
        return "$note$octave"
    }

    private fun octaveAgnosticCentsError(actualHz: Float, expectedHz: Float): Float {
        val cents = centsError(actualHz, expectedHz)
        if (!cents.isFinite()) return cents

        var normalized = cents % 1200f
        if (normalized > 600f) normalized -= 1200f
        if (normalized < -600f) normalized += 1200f
        return normalized
    }
}
