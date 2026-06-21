package com.oss.euphoriae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityNavigationTest {

    @Test
    fun `visible destinations hide explore when online tracks are off`() {
        val destinations = visibleDestinations(onlineTracksEnabled = false)

        assertEquals(4, destinations.size)
        assertFalse(destinations.contains(Destination.EXPLORE))
        assertEquals(
            listOf(
                Destination.HOME,
                Destination.GAMES,
                Destination.PLAYLISTS,
                Destination.EQUALIZER
            ),
            destinations
        )
    }

    @Test
    fun `visible destinations include explore when online tracks are on`() {
        val destinations = visibleDestinations(onlineTracksEnabled = true)

        assertTrue(destinations.contains(Destination.EXPLORE))
        assertEquals(Destination.entries.toList(), destinations)
    }
}
