package com.oss.euphoriae.search

import com.lyrictica.lyrics.MusixmatchTrackRecord
import com.oss.euphoriae.data.model.OnlineSource
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSearchOrganizerTest {

    @Test
    fun `rankLocalSongs prioritizes strongest title and artist match`() {
        val songs = listOf(
            Song(id = 1, title = "Alive", artist = "Artist A", album = "One"),
            Song(id = 2, title = "Keep Alive", artist = "Artist B", album = "Two"),
            Song(id = 3, title = "Different Song", artist = "Alive Person", album = "Three")
        )

        val ranked = HomeSearchOrganizer.rankLocalSongs(query = "alive", songs = songs, limit = 3)

        assertEquals(listOf(1L, 2L, 3L), ranked.map { it.id })
    }

    @Test
    fun `buildMusixmatchResults filters instrumentals and wires local plus provider matches`() {
        val localSong = Song(
            id = 41,
            title = "Alive",
            artist = "Deliriousninja",
            album = "Local Album",
            albumArtUri = "content://art/local"
        )
        val audiusTrack = OnlineTrack(
            id = "a1",
            provider = OnlineSource.AUDIUS,
            title = "Alive",
            artist = "Deliriousninja",
            streamUrl = "https://audius.example/alive",
            artworkUrl = "https://image/audius.jpg"
        )
        val ncsTrack = OnlineTrack(
            id = "n1",
            provider = OnlineSource.NCS,
            title = "Alive",
            artist = "CADMIUM",
            streamUrl = "https://ncs.example/alive"
        )
        val raw = listOf(
            MusixmatchTrackRecord(
                trackId = 1,
                trackName = "Alive",
                artistName = "Deliriousninja",
                albumName = "Search Album",
                durationSec = 187,
                instrumental = false,
                hasLyrics = true,
                hasSubtitles = true,
                hasRichsync = false,
                trackRating = 90
            ),
            MusixmatchTrackRecord(
                trackId = 2,
                trackName = "Alive Instrumental",
                artistName = "Noise",
                albumName = "Instrumental",
                durationSec = 187,
                instrumental = true,
                hasLyrics = true,
                hasSubtitles = false,
                hasRichsync = false,
                trackRating = 80
            )
        )

        val ranked = HomeSearchOrganizer.buildMusixmatchResults(
            query = "alive",
            rawResults = raw,
            localSongs = listOf(localSong),
            audiusTracks = listOf(audiusTrack),
            ncsTracks = listOf(ncsTrack),
            limit = 5
        )

        assertEquals(1, ranked.size)
        assertNotNull(ranked.first().localMatch)
        assertEquals("content://art/local", ranked.first().artworkUri)
        assertEquals(
            listOf(SearchAvailabilityPlatform.AUDIUS),
            ranked.first().availableSources.map { it.platform }
        )
    }

    @Test
    fun `scoreTrackIdentity rewards exact title and artist matches`() {
        val exact = scoreTrackIdentity(
            titleA = "Alive",
            artistA = "Deliriousninja",
            titleB = "Alive",
            artistB = "Deliriousninja"
        )
        val partial = scoreTrackIdentity(
            titleA = "Alive",
            artistA = "Deliriousninja",
            titleB = "Keep Alive",
            artistB = "Someone Else"
        )

        assertTrue(exact > partial)
        assertTrue(isStrongTrackMatch(exact))
    }
}
