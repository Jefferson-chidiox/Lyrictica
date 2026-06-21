package com.lyrictica.video

import com.oss.euphoriae.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeVideoServiceTest {
    @Test
    fun buildsFocusedSearchQuery() {
        val song = Song(
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours"
        )

        assertEquals(
            "Blinding Lights The Weeknd After Hours official music video",
            song.toYouTubeSearchQuery()
        )
    }

    @Test
    fun omitsUnknownMetadata() {
        val song = Song(
            title = "Blinding Lights",
            artist = "Unknown Artist",
            album = "Unknown Album"
        )

        assertEquals(
            "Blinding Lights official music video",
            song.toYouTubeSearchQuery()
        )
    }
}
