package com.lyrictica.karaoke

enum class GameModeOption {
    KARAOKE,
    REVERSE_BEAT,
    ECHO_DROP
}

enum class KaraokeSessionPhase {
    IDLE,
    COUNTDOWN,
    PLAYING,
    MISSED,
    FAILED,
    SUCCESS
}

enum class KaraokeChallengeProfile(
    val label: String,
    val description: String,
    val keepsArtistVoice: Boolean,
    val hidesLyricsUntilMatched: Boolean
) {
    EASY(
        label = "Lyric Match",
        description = "Use the microphone to check whether each lyric line was sung closely enough while the original track keeps playing.",
        keepsArtistVoice = true,
        hidesLyricsUntilMatched = false
    ),
    VOICELESS(
        label = "Legacy Voiceless",
        description = "Legacy prepared-backing-track challenge profile.",
        keepsArtistVoice = false,
        hidesLyricsUntilMatched = false
    ),
    HARD(
        label = "Legacy Hard",
        description = "Legacy lyric-reveal challenge profile.",
        keepsArtistVoice = false,
        hidesLyricsUntilMatched = true
    );

    val requiresPreparedBackingTrack: Boolean
        get() = !keepsArtistVoice
}

data class KaraokeUiState(
    val challengeEnabled: Boolean = false,
    val challengeProfile: KaraokeChallengeProfile = KaraokeChallengeProfile.EASY,
    val sessionPhase: KaraokeSessionPhase = KaraokeSessionPhase.IDLE,
    val livesRemaining: Int = 3,
    val maxLives: Int = 3,
    val combo: Int = 0,
    val clearedLines: Int = 0,
    val countdownSeconds: Int = 0,
    val statusMessage: String? = null,
    val microphoneRequired: Boolean = false,
    val microphoneUnsupported: Boolean = false,
    val headphonesConnected: Boolean = true,
    val challengePausedForHeadphones: Boolean = false,
    val stemProviderAvailable: Boolean = false,
    val preparationInProgress: Boolean = false,
    val backingTrackReady: Boolean = false,
    val cachedStemReady: Boolean = false,
    val usingPreparedBackingTrack: Boolean = false,
    val melodyLoading: Boolean = false,
    val melodyReady: Boolean = false,
    val latestPitchHz: Float? = null,
    val latestConfidence: Float = 0f,
    val latestRms: Float = 0f,
    val targetPitchHz: Float? = null,
    val targetNoteLabel: String? = null,
    val pitchErrorCents: Float? = null,
    val pitchMatched: Boolean = false,
    val pitchRating: String? = null,
    val activeLineIndex: Int = -1,
    val activeWordIndex: Int = -1,
    val revealedWordIndexByLine: Map<Int, Int> = emptyMap(),
    val failedLineIndices: Set<Int> = emptySet()
) {
    val isSessionActive: Boolean
        get() = sessionPhase != KaraokeSessionPhase.IDLE

    val isCountingDown: Boolean
        get() = countdownSeconds > 0

    val challengeActive: Boolean
        get() = challengeEnabled && headphonesConnected && !challengePausedForHeadphones && !microphoneRequired && !microphoneUnsupported

    val challengeUsesPreparedBackingTrack: Boolean
        get() = challengeEnabled && challengeProfile.requiresPreparedBackingTrack

    val challengeReady: Boolean
        get() = !challengeEnabled || (headphonesConnected && !microphoneRequired && !microphoneUnsupported)
}
