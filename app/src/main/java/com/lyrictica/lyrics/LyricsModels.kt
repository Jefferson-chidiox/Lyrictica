package com.lyrictica.lyrics

data class LyricWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

data class LyricLine(
    val timeMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList()
)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean
) {
    val hasWordSync: Boolean
        get() = lines.any { line -> line.words.any { word -> word.text.isNotBlank() } }
}

data class LyricsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val parsed: ParsedLyrics? = null,
    val currentLineIndex: Int = -1,
    val autoFollow: Boolean = true,
    val lyricsVisibilityPreference: Boolean = false,
    val lyricsVisible: Boolean = false
)

internal fun LyricsUiState.withLyricsVisibilityPreference(visible: Boolean): LyricsUiState = copy(
    lyricsVisibilityPreference = visible,
    lyricsVisible = visible
)

internal fun LyricsUiState.hiddenLyrics(): LyricsUiState = copy(
    isLoading = false,
    error = null,
    parsed = null,
    currentLineIndex = -1,
    autoFollow = true,
    lyricsVisibilityPreference = false,
    lyricsVisible = false
)

internal fun LyricsUiState.loadingLyrics(): LyricsUiState = copy(
    isLoading = true,
    error = null,
    parsed = null,
    currentLineIndex = -1,
    lyricsVisible = lyricsVisibilityPreference
)

internal fun LyricsUiState.withLyricsResult(parsed: ParsedLyrics?): LyricsUiState = copy(
    isLoading = false,
    error = if (parsed == null) "No lyrics found" else null,
    parsed = parsed,
    currentLineIndex = -1,
    autoFollow = true,
    lyricsVisible = lyricsVisibilityPreference
)

internal fun LyricsUiState.clearedLyrics(): LyricsUiState = copy(
    isLoading = false,
    error = null,
    parsed = null,
    currentLineIndex = -1,
    autoFollow = true,
    lyricsVisible = lyricsVisibilityPreference
)
