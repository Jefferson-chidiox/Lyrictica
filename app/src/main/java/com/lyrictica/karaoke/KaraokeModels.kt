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
        label = "Easy",
        description = "Keep the artist voice in the mix while timing and pitch are still scored.",
        keepsArtistVoice = true,
        hidesLyricsUntilMatched = false
    ),
    VOICELESS(
        label = "Voiceless",
        description = "Mute the artist voice with the prepared instrumental while lyrics stay visible.",
        keepsArtistVoice = false,
        hidesLyricsUntilMatched = false
    ),
    HARD(
        label = "Hard",
        description = "Mute the artist voice and only reveal lyrics as you sing the active words correctly and on time.",
        keepsArtistVoice = false,
        hidesLyricsUntilMatched = true
    );

    val requiresPreparedBackingTrack: Boolean
        get() = !keepsArtistVoice
}

data class KaraokeUiState(
    val challengeEnabled: Boolean = false,
    val challengeProfile: KaraokeChallengeProfile = KaraokeChallengeProfile.VOICELESS,
    val sessionPhase: KaraokeSessionPhase = KaraokeSessionPhase.IDLE,
    val livesRemaining: Int = 3,
    val maxLives: Int = 3,
    val combo: Int = 0,
    val clearedLines: Int = 0,
    val countdownSeconds: Int = 0,
    val statusMessage: String? = null,
    val microphoneRequired: Boolean = false,
    val microphoneUnsupported: Boolean = false,
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
    val revealedWordIndexByLine: Map<Int, Int> = emptyMap()
) {
    val isSessionActive: Boolean
        get() = sessionPhase != KaraokeSessionPhase.IDLE

    val isCountingDown: Boolean
        get() = countdownSeconds > 0

    val challengeActive: Boolean
        get() = challengeEnabled && !microphoneRequired && !microphoneUnsupported

    val challengeUsesPreparedBackingTrack: Boolean
        get() = challengeEnabled && challengeProfile.requiresPreparedBackingTrack
}
