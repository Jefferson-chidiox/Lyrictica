package com.oss.euphoriae.ui.screens

import com.oss.euphoriae.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class SongsSortModeTest {
    @Test
    fun alphabeticalModeSortsByTitle() {
        val songs = listOf(
            Song(id = 3, title = "beta"),
            Song(id = 1, title = "Alpha"),
            Song(id = 2, title = "charlie")
        )

        val sorted = sortSongsForDisplay(songs, SongsSortMode.ALPHABETICAL)

        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun dateAddedModeSortsNewestFirst() {
        val songs = listOf(
            Song(id = 3, title = "beta", dateAdded = 1000L),
            Song(id = 1, title = "Alpha", dateAdded = 3000L),
            Song(id = 2, title = "charlie", dateAdded = 2000L)
        )

        val sorted = sortSongsForDisplay(songs, SongsSortMode.DATE_ADDED)

        assertEquals(listOf(1L, 2L, 3L), sorted.map { it.id })
    }

    @Test
    fun dateModifiedModeFallsBackToDateAdded() {
        val songs = listOf(
            Song(id = 1, title = "Alpha", dateAdded = 1000L, dateModified = 0L),
            Song(id = 2, title = "Beta", dateAdded = 2000L, dateModified = 9000L),
            Song(id = 3, title = "Gamma", dateAdded = 3000L, dateModified = 0L)
        )

        val sorted = sortSongsForDisplay(songs, SongsSortMode.DATE_MODIFIED)

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun nextModeCyclesThroughAllOptions() {
        assertEquals(SongsSortMode.DATE_ADDED, SongsSortMode.ALPHABETICAL.next())
        assertEquals(SongsSortMode.DATE_MODIFIED, SongsSortMode.DATE_ADDED.next())
        assertEquals(SongsSortMode.ALPHABETICAL, SongsSortMode.DATE_MODIFIED.next())
    }
}
