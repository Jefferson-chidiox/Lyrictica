package com.oss.euphoriae.ui.screens

import com.oss.euphoriae.data.model.Song

enum class SongsSortMode(
    val buttonLabel: String,
    val contentDescription: String
) {
    ALPHABETICAL(
        buttonLabel = "A-Z",
        contentDescription = "Sort songs alphabetically"
    ),
    DATE_ADDED(
        buttonLabel = "Added",
        contentDescription = "Sort songs by date added"
    ),
    DATE_MODIFIED(
        buttonLabel = "Modified",
        contentDescription = "Sort songs by date modified"
    );

    fun next(): SongsSortMode = when (this) {
        ALPHABETICAL -> DATE_ADDED
        DATE_ADDED -> DATE_MODIFIED
        DATE_MODIFIED -> ALPHABETICAL
    }
}

fun sortSongsForDisplay(
    songs: List<Song>,
    sortMode: SongsSortMode
): List<Song> {
    return when (sortMode) {
        SongsSortMode.ALPHABETICAL -> songs.sortedWith(
            compareBy<Song> { it.title.lowercase() }
                .thenBy { it.title }
                .thenByDescending { it.id }
        )

        SongsSortMode.DATE_ADDED -> songs.sortedWith(
            compareByDescending<Song> { it.dateAdded }
                .thenByDescending { it.id }
        )

        SongsSortMode.DATE_MODIFIED -> songs.sortedWith(
            compareByDescending<Song> { it.effectiveModifiedDate() }
                .thenByDescending { it.id }
        )
    }
}

private fun Song.effectiveModifiedDate(): Long =
    dateModified.takeIf { it > 0L } ?: dateAdded
