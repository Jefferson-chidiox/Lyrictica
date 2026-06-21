package com.lyrictica.lyrics

import kotlin.math.abs

internal object MusixmatchSearchRanker {

    fun rank(
        meta: TrackMetadataExtractor.Metadata,
        results: List<MusixmatchTrackRecord>
    ): List<MusixmatchTrackRecord> {
        return results.sortedWith(
            compareByDescending<MusixmatchTrackRecord> { hasLyrics(it) }
                .thenByDescending { scoreCandidate(meta, it) }
        )
    }

    fun hasLyrics(record: MusixmatchTrackRecord): Boolean {
        return record.hasSubtitles || record.hasRichsync || record.hasLyrics
    }

    private fun scoreCandidate(meta: TrackMetadataExtractor.Metadata, rec: MusixmatchTrackRecord): Int {
        var score = 0

        val dur = meta.durationSec
        if (dur != null && rec.durationSec > 0) {
            val delta = abs(dur - rec.durationSec)
            score += when {
                delta <= 2 -> 120
                delta <= 5 -> 70
                delta <= 10 -> 35
                else -> 0
            }
            score -= (delta * 2)
        }

        score += stringScore(meta.title, rec.trackName) * 3
        meta.artist?.let { score += stringScore(it, rec.artistName) * 2 }
        meta.album?.let { score += stringScore(it, rec.albumName) }

        if (rec.hasSubtitles) score += 30
        if (rec.hasRichsync) score += 25
        if (rec.hasLyrics) score += 10
        if (rec.instrumental) score -= 30
        score += rec.trackRating / 10

        return score
    }

    private fun stringScore(a: String, b: String): Int {
        val na = norm(a)
        val nb = norm(b)
        if (na.isBlank() || nb.isBlank()) return 0
        return when {
            na == nb -> 30
            nb.contains(na) || na.contains(nb) -> 18
            else -> {
                val common = na.split(' ').toSet().intersect(nb.split(' ').toSet()).size
                common * 4
            }
        }
    }

    private fun norm(s: String): String {
        return s.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
