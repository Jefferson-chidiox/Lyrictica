package com.lyrictica.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsSearchRankerTest {

    @Test
    fun prefersLyricCandidateOverBlankExactMatch() {
        val meta = TrackMetadataExtractor.Metadata(
            title = "Intro",
            artist = "Alan Walker",
            album = "Different World",
            durationSec = 200
        )

        val blankExact = MusixmatchTrackRecord(
            trackId = 1L,
            trackName = "Intro",
            artistName = "Alan Walker",
            albumName = "Different World",
            durationSec = 200,
            instrumental = true,
            hasLyrics = false,
            hasSubtitles = false,
            hasRichsync = false,
            trackRating = 80
        )

        val lyricCandidate = MusixmatchTrackRecord(
            trackId = 2L,
            trackName = "Intro",
            artistName = "Alan Walker",
            albumName = "Different World (Deluxe)",
            durationSec = 215,
            instrumental = false,
            hasLyrics = true,
            hasSubtitles = false,
            hasRichsync = false,
            trackRating = 60
        )

        val ordered = MusixmatchSearchRanker.rank(meta, listOf(blankExact, lyricCandidate))

        assertEquals(2L, ordered.first().trackId)
        assertEquals(1L, ordered.last().trackId)
    }

    @Test
    fun keepsBestBlankCandidateFirstWhenNoLyricsExist() {
        val meta = TrackMetadataExtractor.Metadata(
            title = "Intro",
            artist = "Alan Walker",
            album = "Different World",
            durationSec = 200
        )

        val worseBlank = MusixmatchTrackRecord(
            trackId = 1L,
            trackName = "Intro (Live)",
            artistName = "Alan Walker",
            albumName = "Different World",
            durationSec = 200,
            instrumental = true,
            hasLyrics = false,
            hasSubtitles = false,
            hasRichsync = false,
            trackRating = 20
        )

        val bestBlank = MusixmatchTrackRecord(
            trackId = 2L,
            trackName = "Intro",
            artistName = "Alan Walker",
            albumName = "Different World",
            durationSec = 200,
            instrumental = true,
            hasLyrics = false,
            hasSubtitles = false,
            hasRichsync = false,
            trackRating = 40
        )

        val ordered = MusixmatchSearchRanker.rank(meta, listOf(worseBlank, bestBlank))

        assertEquals(2L, ordered.first().trackId)
        assertEquals(1L, ordered.last().trackId)
    }
}
