package com.lyrictica.karaoke

import kotlin.math.ceil

internal object KaraokeLineSpeechMatcher {
    data class MatchResult(
        val matchedWords: Int,
        val expectedWords: Int,
        val orderedCoverage: Float,
        val matchedTranscript: String?,
        val isMatch: Boolean
    )

    fun evaluate(expectedLine: String, transcriptAlternatives: Collection<String>): MatchResult {
        val expectedWords = normalizedWords(expectedLine)
        if (expectedWords.isEmpty()) {
            return MatchResult(
                matchedWords = 0,
                expectedWords = 0,
                orderedCoverage = 1f,
                matchedTranscript = null,
                isMatch = true
            )
        }

        val best = transcriptAlternatives
            .map { alternative ->
                val actualWords = normalizedWords(alternative)
                val orderedMatches = orderedMatchCount(expectedWords, actualWords)
                Candidate(
                    transcript = normalizeText(alternative),
                    orderedMatches = orderedMatches,
                    orderedCoverage = orderedMatches / expectedWords.size.toFloat()
                )
            }
            .maxWithOrNull(compareBy<Candidate> { it.orderedMatches }.thenBy { it.orderedCoverage })

        val matchedWords = best?.orderedMatches ?: 0
        val coverage = best?.orderedCoverage ?: 0f
        return MatchResult(
            matchedWords = matchedWords,
            expectedWords = expectedWords.size,
            orderedCoverage = coverage,
            matchedTranscript = best?.transcript,
            isMatch = matchedWords >= requiredMatches(expectedWords.size)
        )
    }

    fun normalizeText(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    internal fun normalizedWords(input: String): List<String> {
        return normalizeText(input)
            .split(' ')
            .filter { it.isNotBlank() }
    }

    private fun orderedMatchCount(expectedWords: List<String>, actualWords: List<String>): Int {
        if (expectedWords.isEmpty() || actualWords.isEmpty()) return 0

        val dp = Array(expectedWords.size + 1) { IntArray(actualWords.size + 1) }
        for (expectedIndex in expectedWords.indices.reversed()) {
            for (actualIndex in actualWords.indices.reversed()) {
                dp[expectedIndex][actualIndex] = if (expectedWords[expectedIndex] == actualWords[actualIndex]) {
                    1 + dp[expectedIndex + 1][actualIndex + 1]
                } else {
                    maxOf(dp[expectedIndex + 1][actualIndex], dp[expectedIndex][actualIndex + 1])
                }
            }
        }
        return dp[0][0]
    }

    private fun requiredMatches(expectedWordCount: Int): Int {
        return when {
            expectedWordCount <= 1 -> 1
            expectedWordCount == 2 -> 2
            expectedWordCount == 3 -> 2
            else -> ceil(expectedWordCount * 0.6f).toInt().coerceAtLeast(2)
        }
    }

    private data class Candidate(
        val transcript: String,
        val orderedMatches: Int,
        val orderedCoverage: Float
    )
}
