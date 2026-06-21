package com.lyrictica.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUiStateTest {
    @Test
    fun startsHiddenByDefault() {
        val state = LyricsUiState()

        assertFalse(state.lyricsVisibilityPreference)
        assertFalse(state.lyricsVisible)
    }

    private val parsedLyrics = ParsedLyrics(
        lines = listOf(LyricLine(timeMs = 0L, text = "First line")),
        isSynced = true
    )

    @Test
    fun keepsOpenStateWhenNoLyricsAreFound() {
        val state = LyricsUiState(
            parsed = parsedLyrics,
            lyricsVisibilityPreference = true,
            lyricsVisible = true
        )

        val updated = state.withLyricsResult(null)

        assertTrue(updated.lyricsVisible)
        assertTrue(updated.lyricsVisibilityPreference)
        assertEquals("No lyrics found", updated.error)
    }

    @Test
    fun restoresVisiblePreferenceWhileNextSongLoads() {
        val autoHiddenState = LyricsUiState(
            lyricsVisibilityPreference = true,
            lyricsVisible = false
        )

        val loading = autoHiddenState.loadingLyrics()

        assertTrue(loading.isLoading)
        assertTrue(loading.lyricsVisible)
    }

    @Test
    fun hidesLyricsCompletelyForAutoAdvance() {
        val state = LyricsUiState(
            isLoading = true,
            error = "stale error",
            parsed = parsedLyrics,
            currentLineIndex = 0,
            autoFollow = false,
            lyricsVisibilityPreference = true,
            lyricsVisible = true
        )

        val hidden = state.hiddenLyrics()

        assertFalse(hidden.lyricsVisibilityPreference)
        assertFalse(hidden.lyricsVisible)
        assertFalse(hidden.isLoading)
        assertNull(hidden.error)
        assertNull(hidden.parsed)
        assertEquals(-1, hidden.currentLineIndex)
        assertTrue(hidden.autoFollow)
    }

    @Test
    fun keepsLyricsHiddenAfterManualHideEvenWhenLyricsExist() {
        val state = LyricsUiState(
            parsed = parsedLyrics,
            lyricsVisibilityPreference = true,
            lyricsVisible = true
        )

        val manuallyHidden = state.withLyricsVisibilityPreference(false)
        val reloaded = manuallyHidden.withLyricsResult(parsedLyrics)

        assertFalse(manuallyHidden.lyricsVisible)
        assertFalse(reloaded.lyricsVisible)
        assertFalse(reloaded.lyricsVisibilityPreference)
    }
}
