package com.lyrictica.visualizer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lyrictica.audio.AnalysisStatus
import com.lyrictica.audio.AudioAnalyzer
import com.lyrictica.audio.AudioFeatures
import com.lyrictica.audio.PlaybackQueue
import com.lyrictica.audio.asSavedQueueSnapshot
import com.lyrictica.audio.PlaybackSession
import com.lyrictica.audio.PlaybackSessionStore
import com.lyrictica.audio.PlaybackTrackSnapshot
import com.lyrictica.audio.PlaybackUiState
import com.lyrictica.audio.PlayerController
import com.lyrictica.audio.QueueSource
import com.lyrictica.audio.mergeOrRegisterQueue
import com.lyrictica.audio.toPlaybackSnapshot
import com.lyrictica.karaoke.GameModeOption
import com.lyrictica.karaoke.KaraokeChallengeProfile
import com.lyrictica.karaoke.KaraokeLineWindow
import com.lyrictica.karaoke.KaraokeMelodyReference
import com.lyrictica.karaoke.KaraokeMicMonitor
import com.lyrictica.karaoke.KaraokePitchMath
import com.lyrictica.karaoke.KaraokePreparationProgress
import com.lyrictica.karaoke.KaraokePreparationStage
import com.lyrictica.karaoke.KaraokeSessionPhase
import com.lyrictica.karaoke.KaraokeTimingJudge
import com.lyrictica.karaoke.KaraokeUiState
import com.lyrictica.karaoke.MicrophonePitchSample
import com.lyrictica.lyrics.LyricsRepository
import com.lyrictica.lyrics.LyricsSync
import com.lyrictica.lyrics.LyricsUiState
import com.lyrictica.lyrics.ParsedLyrics
import com.lyrictica.lyrics.clearedLyrics
import com.lyrictica.lyrics.hiddenLyrics
import com.lyrictica.lyrics.loadingLyrics
import com.lyrictica.lyrics.withLyricsResult
import com.lyrictica.lyrics.withLyricsVisibilityPreference
import com.lyrictica.visualizer.VisualizerPalette
import com.lyrictica.theme.NotificationThemeBridge
import com.lyrictica.theme.harmonize
import com.lyrictica.theme.seedColor
import com.oss.euphoriae.EuphoriaeApp
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.data.model.toSong
import com.oss.euphoriae.data.preferences.ThemeColorOption
import com.oss.euphoriae.search.MusixmatchSearchResult
import com.oss.euphoriae.search.SearchAvailability
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private const val KARAOKE_LINE_EVALUATION_GRACE_MS = 420L

data class VisualizerPlaybackState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = -1,
    val queueSource: QueueSource? = null,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = 0
)

data class InlineVideoPlaybackState(
    val isVisible: Boolean = false,
    val hasStarted: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class LyricsPreviewState(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUri: String? = null,
    val availableSources: List<SearchAvailability> = emptyList()
)

data class MusixmatchArtistState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val artistName: String? = null,
    val metadataText: String? = null,
    val imageUrl: String? = null,
    val error: String? = null
)

data class TranslationState(
    val languageCode: String? = null,
    val isLoading: Boolean = false,
    val translations: Map<String, ParsedLyrics> = emptyMap(),
    val error: String? = null
)

class VisualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerController = PlayerController(application)
    private val app = application as EuphoriaeApp
    private val audioAnalysisStore = app.audioAnalysisStore
    private val karaokeAssetsRepository = app.karaokeAssetsRepository
    private val audioAnalyzer = AudioAnalyzer(audioAnalysisStore)
    private val lyricsRepository = LyricsRepository(application)
    private val musicRepository = app.musicRepository
    private val moodEngine = VisualizerMoodEngine()
    private val playbackSessionStore = PlaybackSessionStore(application)

    private val _themeColor = MutableStateFlow(ThemeColorOption.BLUE)

    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState
    val analysisStatus: StateFlow<AnalysisStatus> = audioAnalyzer.status

    private val _lyricsUiState = MutableStateFlow(LyricsUiState())
    val lyricsUiState: StateFlow<LyricsUiState> = _lyricsUiState.asStateFlow()

    private val _availableLyrics = MutableStateFlow<ParsedLyrics?>(null)
    val availableLyrics: StateFlow<ParsedLyrics?> = _availableLyrics.asStateFlow()

    private val _smoothedFeatures = MutableStateFlow(AudioFeatures())
    val smoothedFeatures: StateFlow<AudioFeatures> = _smoothedFeatures.asStateFlow()

    private val _screenTheme = MutableStateFlow(moodEngine.currentTheme.harmonize(_themeColor.value.seedColor()))
    val screenTheme: StateFlow<VisualizerPalette> = _screenTheme.asStateFlow()

    private val _appTheme = MutableStateFlow(moodEngine.currentTheme.harmonize(_themeColor.value.seedColor()))
    val appTheme: StateFlow<VisualizerPalette> = _appTheme.asStateFlow()

    private val _nowPlayingState = MutableStateFlow(VisualizerPlaybackState())
    val nowPlayingState: StateFlow<VisualizerPlaybackState> = _nowPlayingState.asStateFlow()

    private val _queueLibrary = MutableStateFlow<List<PlaybackQueue>>(emptyList())
    val queueLibrary: StateFlow<List<PlaybackQueue>> = _queueLibrary.asStateFlow()

    private val _inlineVideoPlayback = MutableStateFlow(InlineVideoPlaybackState())
    val inlineVideoPlayback: StateFlow<InlineVideoPlaybackState> = _inlineVideoPlayback.asStateFlow()

    private val _karaokeUiState = MutableStateFlow(KaraokeUiState())
    val karaokeUiState: StateFlow<KaraokeUiState> = _karaokeUiState.asStateFlow()

    private val _lyricsPreviewState = MutableStateFlow<LyricsPreviewState?>(null)
    val lyricsPreviewState: StateFlow<LyricsPreviewState?> = _lyricsPreviewState.asStateFlow()

    private val _musixmatchArtistState = MutableStateFlow(MusixmatchArtistState())
    val musixmatchArtistState: StateFlow<MusixmatchArtistState> = _musixmatchArtistState.asStateFlow()

    private val _translationState = MutableStateFlow(TranslationState())
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()

    private val musixmatchClient = com.lyrictica.lyrics.MusixmatchClient(userAgent = "Lyrictica/1.0")

    private var lyricsJob: Job? = null
    private var melodyJob: Job? = null
    private var karaokeCountdownJob: Job? = null
    private var karaokeLineWindows: List<KaraokeLineWindow> = emptyList()
    private var karaokeMelodyReference: KaraokeMelodyReference? = null
    private var karaokeInstrumentalUri: Uri? = null
    private var karaokeOriginalSourceUri: Uri? = null
    private var pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
    private var karaokePlaybackRestoreState: KaraokePlaybackRestoreState? = null
    private val karaokeLineVoicedMs = linkedMapOf<Int, Long>()
    private val karaokeLineInTuneMs = linkedMapOf<Int, Long>()
    private val karaokeResolvedLines = linkedSetOf<Int>()
    private var latestMicSample = MicrophonePitchSample(rms = 0f, pitchHz = null, confidence = 0f, voiced = false)
    private var lastKaraokePositionMs: Long? = null
    private val karaokeMicMonitor = KaraokeMicMonitor { sample ->
        latestMicSample = sample
        _karaokeUiState.update {
            it.copy(
                latestPitchHz = sample.pitchHz,
                latestConfidence = sample.confidence,
                latestRms = sample.rms
            )
        }
    }

    private data class LyricsRequest(
        val uri: Uri,
        val fallbackDisplayName: String?,
        val artist: String? = null,
        val album: String? = null,
        val durationSec: Int? = null
    )

    private data class KaraokePlaybackRestoreState(
        val queueSnapshots: List<PlaybackTrackSnapshot>,
        val currentIndex: Int,
        val sourceUri: Uri,
        val repeatMode: Int
    )

    private enum class PendingKaraokeStartMode {
        NONE,
        SING_ALONG,
        CHALLENGE
    }

    init {
        _karaokeUiState.update {
            it.copy(stemProviderAvailable = karaokeAssetsRepository.isProviderConfigured)
        }
        restorePersistedSession()
        observeCurrentSong()

        // Drive analyzer at ~60Hz so playback position and feature sampling stay in sync.
        viewModelScope.launch {
            var currentBass = 0f
            var currentMid = 0f
            var currentPresence = 0f
            var currentTreble = 0f
            var lastEndedState = false
            var lastAppMood = moodEngine.currentMood
            var lastMediaId: String? = null

            while (isActive) {
                val state = playerController.uiState.value
                val videoState = _inlineVideoPlayback.value
                val audioPositionMs = if (state.isPlaying) {
                    playerController.getCurrentPositionMs()
                } else {
                    state.currentPosition
                }
                val effectivePositionMs = if (videoState.hasStarted) {
                    videoState.positionMs
                } else {
                    audioPositionMs
                }
                val effectiveDurationMs = if (videoState.hasStarted && videoState.durationMs > 0L) {
                    videoState.durationMs
                } else {
                    state.duration
                }
                val effectiveIsPlaying = if (videoState.hasStarted) {
                    videoState.isPlaying
                } else {
                    state.isPlaying
                }

                audioAnalyzer.onPlaybackPosition(
                    positionMs = effectivePositionMs,
                    isPlaying = effectiveIsPlaying
                )

                val raw = audioAnalyzer.features.value
                val currentMediaId = playerController.getCurrentMediaId()
                if (currentMediaId != lastMediaId) {
                    lastMediaId = currentMediaId
                    syncCurrentSongFromMediaId(currentMediaId)
                }

                // Attack instantly; only decay gently. This keeps transients locked to the beat.
                currentBass = followEnvelope(currentBass, raw.bass, decay = 0.22f)
                currentMid = followEnvelope(currentMid, raw.mid, decay = 0.20f)
                currentPresence = followEnvelope(currentPresence, raw.presence, decay = 0.18f)
                currentTreble = followEnvelope(currentTreble, raw.treble, decay = 0.16f)

                _smoothedFeatures.value = AudioFeatures(
                    bass = currentBass,
                    mid = currentMid,
                    presence = currentPresence,
                    treble = currentTreble
                )

                _screenTheme.value = moodEngine.update(
                    features = raw,
                    positionMs = effectivePositionMs,
                    durationMs = effectiveDurationMs,
                    isPlaying = effectiveIsPlaying
                ).harmonize(_themeColor.value.seedColor())
                NotificationThemeBridge.updateFromPalette(_screenTheme.value)

                if (moodEngine.currentMood != lastAppMood) {
                    lastAppMood = moodEngine.currentMood
                    _appTheme.value = _screenTheme.value
                }

                // Lyrics sync (if we have synced lyrics)
                val parsed = resolvedLyrics()
                if (parsed != null && parsed.isSynced) {
                    val idx = LyricsSync.currentIndex(parsed.lines, effectivePositionMs)
                    if (idx != _lyricsUiState.value.currentLineIndex) {
                        _lyricsUiState.value = _lyricsUiState.value.copy(currentLineIndex = idx)
                    }
                }

                // If lyrics aren't synced, keep highlight disabled.
                if (parsed != null && !parsed.isSynced && _lyricsUiState.value.currentLineIndex != -1) {
                    _lyricsUiState.value = _lyricsUiState.value.copy(currentLineIndex = -1)
                }

                updateKaraokeSession(
                    positionMs = effectivePositionMs,
                    isPlaying = effectiveIsPlaying,
                    parsedAvailable = parsed != null
                )

                if (state.ended && !lastEndedState) {
                    lastEndedState = true
                    handleSongEnded()
                } else if (!state.ended) {
                    lastEndedState = false
                }

                delay(16L) // ~60Hz
            }
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        playSong(song, PlaybackQueue.custom("legacy:${song.id}", "Queue", if (queue.isEmpty()) listOf(song) else queue))
    }

    fun playSong(song: Song, queue: PlaybackQueue) {
        clearLyricsPreview(restoreCurrentTrack = false)
        val targetSongs = if (queue.songs.isEmpty()) listOf(song) else queue.songs
        val matchedIndex = targetSongs.indexOfFirst {
            (song.id > 0L && it.id == song.id) ||
            (song.data.isNotEmpty() && it.data == song.data) ||
            (it.title.equals(song.title, ignoreCase = true) && it.artist.equals(song.artist, ignoreCase = true))
        }

        val finalIndex = if (matchedIndex >= 0) matchedIndex else 0
        val finalQueue = queue.withSongs(targetSongs)

        upsertQueue(finalQueue.asSavedQueueSnapshot())

        _nowPlayingState.update {
            it.copy(
                currentSong = targetSongs[finalIndex],
                queue = targetSongs,
                currentQueueIndex = finalIndex,
                queueSource = finalQueue.source
            )
        }
        playSongAtIndex(finalIndex)
        persistPlaybackSession()

        // Fetch related songs in background if it's a single song queue
        if (targetSongs.size <= 1) {
            viewModelScope.launch {
                try {
                    val related = when {
                        song.album == "NCS" -> {
                            app.ncsRepository.getRelatedTracks(genre = song.genre, limit = 20)
                                .filterNot { it.title.equals(song.title, ignoreCase = true) }
                                .map { it.toSong() }
                        }

                        song.data.startsWith("http://") || song.data.startsWith("https://") || song.album == "Audius" -> {
                            app.audiusRepository.getRelatedTracks(genre = song.genre, limit = 20)
                                .filterNot { it.title.equals(song.title, ignoreCase = true) }
                                .map { it.toSong() }
                        }

                        else -> musicRepository.getRelatedSongs(song)
                    }

                    if (related.isNotEmpty()) {
                        val fullRelatedSongs = listOf(song) + related
                        val relatedQueue = PlaybackQueue.custom(
                            key = "related:${song.id}",
                            label = "Related to ${song.title}",
                            songs = fullRelatedSongs
                        )
                        upsertQueue(relatedQueue.asSavedQueueSnapshot())

                        if (_nowPlayingState.value.currentSong?.id == song.id) {
                            _nowPlayingState.update {
                                it.copy(
                                    queue = fullRelatedSongs,
                                    queueSource = relatedQueue.source
                                )
                            }
                            val snapshots = fullRelatedSongs.map { snapshotForSong(it) ?: return@launch }
                            val currentPosition = playerController.getCurrentPositionMs()
                            val isPlaying = playbackState.value.isPlaying
                            playerController.playSnapshots(
                                snapshots = snapshots,
                                startIndex = 0,
                                autoPlay = isPlaying,
                                startPositionMs = currentPosition,
                                replaceExisting = true
                            )
                            persistPlaybackSession()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VisualizerViewModel", "Failed to build related queue", e)
                }
            }
        }
    }

    fun playUri(uri: Uri) {
        clearLyricsPreview(restoreCurrentTrack = false)
        _nowPlayingState.value = VisualizerPlaybackState()
        moodEngine.reset()
        val harmonized = moodEngine.currentTheme.harmonize(_themeColor.value.seedColor())
        _screenTheme.value = harmonized
        _appTheme.value = harmonized
        NotificationThemeBridge.updateFromPalette(harmonized)
        audioAnalyzer.load(uri)
        startPlayback(
            uri = uri,
            displayName = null,
            lyricsFallbackTitle = playbackState.value.trackName
        )
        persistPlaybackSession()
    }

    fun recordGameScore(mode: GameModeOption, score: Int) {
        if (score <= 0) return
        val song = _nowPlayingState.value.currentSong ?: return
        viewModelScope.launch {
            runCatching {
                musicRepository.recordGameScore(song = song, mode = mode.name, score = score)
            }.onFailure { error ->
                android.util.Log.e("VisualizerViewModel", "Failed to record $mode score for ${song.id}", error)
            }
        }
    }

    fun showLyricsPreview(result: MusixmatchSearchResult) {
        pausePlayback()
        _lyricsPreviewState.value = LyricsPreviewState(
            title = result.title,
            artist = result.artist,
            album = result.album.takeIf { it.isNotBlank() },
            artworkUri = result.artworkUri,
            availableSources = result.availableSources
        )
        loadPreviewLyrics(
            trackName = result.title,
            artist = result.artist,
            album = result.album.takeIf { it.isNotBlank() },
            durationSec = (result.durationMs / 1000L).toInt().takeIf { it > 0 }
        )
    }

    fun clearLyricsPreview() {
        clearLyricsPreview(restoreCurrentTrack = true)
    }

    fun toggleMusixmatchArtistInfo() {
        val currentState = _musixmatchArtistState.value
        if (currentState.isVisible) {
            _musixmatchArtistState.value = currentState.copy(isVisible = false)
            return
        }

        val activeArtist = nowPlayingState.value.currentSong?.artist?.takeIf { it.isNotBlank() }
        if (activeArtist == null) {
            _musixmatchArtistState.value = MusixmatchArtistState(
                isVisible = true,
                error = "No artist selected"
            )
            return
        }

        _musixmatchArtistState.value = MusixmatchArtistState(
            isVisible = true,
            isLoading = true
        )

        viewModelScope.launch {
            try {
                val metadata = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    musixmatchClient.getArtistMetadata(activeArtist)
                }

                if (metadata != null) {
                    val metadataParts = listOfNotNull(
                        metadata.country.takeIf { it.isNotBlank() }?.let { "Country: $it" },
                        metadata.rating.takeIf { it > 0 }?.let { "Rating: $it" }
                    )
                    
                    _musixmatchArtistState.value = MusixmatchArtistState(
                        isVisible = true,
                        isLoading = false,
                        artistName = metadata.name,
                        metadataText = metadataParts.joinToString(" • ").takeIf { it.isNotEmpty() } ?: "No additional metadata",
                        imageUrl = metadata.imageUrl
                    )
                } else {
                    _musixmatchArtistState.value = MusixmatchArtistState(
                        isVisible = true,
                        isLoading = false,
                        error = "Failed to fetch metadata for $activeArtist"
                    )
                }
            } catch (e: Exception) {
                _musixmatchArtistState.value = MusixmatchArtistState(
                    isVisible = true,
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun playPreviewSource(source: SearchAvailability) {
        val preview = _lyricsPreviewState.value
        val queueSongs = buildList {
            add(source.song)
            preview?.availableSources
                ?.map { it.song }
                ?.filterNot { candidate ->
                    candidate.id == source.song.id ||
                        (candidate.data.isNotBlank() && candidate.data == source.song.data)
                }
                ?.forEach(::add)
        }
        playSong(
            song = source.song,
            queue = PlaybackQueue.custom(
                key = "lyrics_preview:${preview?.title ?: source.song.title}:${source.song.id}",
                label = preview?.title ?: source.song.title,
                songs = queueSongs
            )
        )
    }

    fun togglePlayPause() {
        val state = playbackState.value
        if (state.isPlaying) {
            playerController.pause()
        } else {
            playerController.play()
        }
        persistPlaybackSession()
    }

    fun pausePlayback() {
        playerController.pause()
        persistPlaybackSession(forcePaused = true)
    }

    fun resumePlayback() {
        playerController.play()
        persistPlaybackSession()
    }

    fun setKaraokeChallengeEnabled(enabled: Boolean) {
        if (!enabled) {
            pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
        }
        val current = _karaokeUiState.value
        val usingPreparedBackingTrack = syncKaraokeChallengePlayback(
            enabled = enabled,
            profile = current.challengeProfile,
            sessionActive = current.isSessionActive,
            currentlyUsingPreparedBackingTrack = current.usingPreparedBackingTrack
        )
        _karaokeUiState.update {
            it.copy(
                challengeEnabled = enabled,
                microphoneRequired = false,
                microphoneUnsupported = false,
                usingPreparedBackingTrack = if (it.isSessionActive) usingPreparedBackingTrack else false,
                statusMessage = when {
                    it.preparationInProgress -> it.statusMessage
                    enabled -> challengeModeStatusMessage(
                        enabled = true,
                        profile = it.challengeProfile,
                        melodyReady = it.melodyReady,
                        stemProviderAvailable = it.stemProviderAvailable,
                        backingTrackReady = it.backingTrackReady
                    )
                    else -> null
                }
            )
        }
    }

    fun setKaraokeChallengeProfile(profile: KaraokeChallengeProfile) {
        val current = _karaokeUiState.value
        val usingPreparedBackingTrack = syncKaraokeChallengePlayback(
            enabled = current.challengeEnabled,
            profile = profile,
            sessionActive = current.isSessionActive,
            currentlyUsingPreparedBackingTrack = current.usingPreparedBackingTrack
        )
        _karaokeUiState.update {
            it.copy(
                challengeProfile = profile,
                usingPreparedBackingTrack = if (it.isSessionActive) usingPreparedBackingTrack else false,
                statusMessage = when {
                    it.preparationInProgress -> it.statusMessage
                    it.challengeEnabled -> challengeModeStatusMessage(
                        enabled = true,
                        profile = profile,
                        melodyReady = it.melodyReady,
                        stemProviderAvailable = it.stemProviderAvailable,
                        backingTrackReady = it.backingTrackReady
                    )
                    else -> it.statusMessage
                }
            )
        }
    }

    fun onKaraokePermissionDenied() {
        _karaokeUiState.update {
            it.copy(
                microphoneRequired = true,
                statusMessage = "Microphone access is needed for challenge mode."
            )
        }
    }

    fun onKaraokeMicrophoneUnavailable() {
        _karaokeUiState.update {
            it.copy(
                microphoneUnsupported = true,
                statusMessage = "This device does not expose a usable microphone."
            )
        }
    }

    fun clearKaraokeMessage() {
        _karaokeUiState.update {
            it.copy(
                microphoneRequired = false,
                microphoneUnsupported = false,
                statusMessage = if (it.preparationInProgress) it.statusMessage else null
            )
        }
    }

    fun prepareKaraokeAssets() {
        prepareKaraokeAssets(autoStartMode = PendingKaraokeStartMode.NONE)
    }

    private fun prepareKaraokeAssets(autoStartMode: PendingKaraokeStartMode) {
        val request = currentLyricsRequest()
        val parsed = resolvedLyrics()
        val karaokeState = _karaokeUiState.value
        if (request == null) {
            pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
            _karaokeUiState.update { it.copy(statusMessage = "Load a song before preparing karaoke assets.") }
            return
        }
        if (parsed == null || !parsed.isSynced || !parsed.hasWordSync) {
            pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
            _karaokeUiState.update {
                it.copy(statusMessage = "Word-synced lyrics are required before karaoke assets can be prepared.")
            }
            return
        }
        if (!karaokeState.stemProviderAvailable && !karaokeState.backingTrackReady) {
            pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
            _karaokeUiState.update {
                it.copy(statusMessage = "LALAL.AI karaoke prep is not configured on this build.")
            }
            return
        }

        pendingKaraokeStartAfterPreparation = autoStartMode
        val preparingMessage = when {
            karaokeState.backingTrackReady -> when (autoStartMode) {
                PendingKaraokeStartMode.CHALLENGE -> "Finishing the cached challenge melody map"
                PendingKaraokeStartMode.SING_ALONG -> "Loading the cached karaoke instrumental"
                PendingKaraokeStartMode.NONE -> "Refreshing the cached karaoke assets"
            }
            karaokeState.stemProviderAvailable -> when (autoStartMode) {
                PendingKaraokeStartMode.CHALLENGE -> "Uploading track to LALAL.AI for challenge mode"
                PendingKaraokeStartMode.SING_ALONG -> "Uploading track to LALAL.AI to remove the artist vocal"
                PendingKaraokeStartMode.NONE -> "Uploading track to LALAL.AI for karaoke prep"
            }
            else -> "Preparing karaoke assets"
        }

        melodyJob?.cancel()
        karaokeOriginalSourceUri = request.uri
        _karaokeUiState.update {
            it.copy(
                preparationInProgress = true,
                melodyLoading = true,
                statusMessage = preparingMessage
            )
        }

        melodyJob = viewModelScope.launch {
            val result = karaokeAssetsRepository.prepare(
                sourceUri = request.uri,
                lyrics = parsed,
                onProgress = ::applyKaraokePreparationProgress
            )
            if (!isActive) return@launch
            applyKaraokeAssetResult(
                result = result,
                sourceUri = request.uri,
                finalMessage = when {
                    result.assets.failureReason != null -> result.assets.failureReason
                    autoStartMode == PendingKaraokeStartMode.CHALLENGE -> "LALAL.AI karaoke assets ready — starting challenge"
                    autoStartMode == PendingKaraokeStartMode.SING_ALONG -> "Instrumental karaoke ready — starting now"
                    else -> "Karaoke assets cached on this device"
                }
            )
        }
    }

    fun startKaraokeSession(challengeMode: Boolean = _karaokeUiState.value.challengeEnabled) {
        val parsed = resolvedLyrics()
        if (parsed == null) {
            pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
            _karaokeUiState.update { it.copy(statusMessage = "Lyrics are still loading for this track.") }
            return
        }
        if (!parsed.isSynced || !parsed.hasWordSync) {
            pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
            _karaokeUiState.update {
                it.copy(statusMessage = "This song does not have Musixmatch word-sync lyrics yet.")
            }
            return
        }

        val karaokeState = _karaokeUiState.value
        if (!challengeMode) {
            when {
                karaokeState.melodyLoading && karaokeInstrumentalUri == null -> {
                    pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.SING_ALONG
                    _karaokeUiState.update {
                        it.copy(statusMessage = "Checking cached karaoke assets. Karaoke will start when the instrumental is ready.")
                    }
                    return
                }
                karaokeInstrumentalUri == null -> {
                    if (!karaokeState.stemProviderAvailable && !karaokeState.backingTrackReady) {
                        pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
                        _karaokeUiState.update {
                            it.copy(statusMessage = "Karaoke without vocals is not available on this build because LALAL.AI is not configured.")
                        }
                        return
                    }
                    prepareKaraokeAssets(autoStartMode = PendingKaraokeStartMode.SING_ALONG)
                    return
                }
            }
        }

        if (challengeMode && karaokeState.melodyLoading) {
            pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.CHALLENGE
            _karaokeUiState.update {
                it.copy(statusMessage = "Preparing the challenge melody map. Karaoke will start when it is ready.")
            }
            return
        }
        if (challengeMode && karaokeMelodyReference == null) {
            if (!karaokeState.stemProviderAvailable && !karaokeState.backingTrackReady) {
                pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
                _karaokeUiState.update {
                    it.copy(statusMessage = "Challenge mode needs LALAL.AI karaoke prep, but this build is not configured for it.")
                }
                return
            }
            prepareKaraokeAssets(autoStartMode = PendingKaraokeStartMode.CHALLENGE)
            return
        }

        pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
        karaokeLineWindows = KaraokeTimingJudge.buildLineWindows(parsed)
        if (karaokeLineWindows.isEmpty()) {
            _karaokeUiState.update {
                it.copy(statusMessage = "Word-synced lyrics are not complete enough for karaoke mode.")
            }
            return
        }

        karaokeCountdownJob?.cancel()
        karaokeMicMonitor.stop()
        karaokeLineVoicedMs.clear()
        karaokeLineInTuneMs.clear()
        karaokeResolvedLines.clear()
        val startPositionMs = playerController.getCurrentPositionMs().coerceAtLeast(0L)
        lastKaraokePositionMs = startPositionMs
        latestMicSample = MicrophonePitchSample(rms = 0f, pitchHz = null, confidence = 0f, voiced = false)

        if (challengeMode && !karaokeMicMonitor.start()) {
            _karaokeUiState.update {
                it.copy(
                    challengeEnabled = true,
                    microphoneUnsupported = true,
                    statusMessage = "Microphone capture could not start on this device."
                )
            }
            return
        }

        val challengeProfile = karaokeState.challengeProfile
        val usingPreparedBackingTrack = karaokeInstrumentalUri != null &&
            (!challengeMode || challengeProfile.requiresPreparedBackingTrack)

        playerController.pause()
        if (usingPreparedBackingTrack) {
            engageKaraokePlaybackOverride(karaokeInstrumentalUri!!, startPositionMs)
        } else {
            playerController.seekTo(startPositionMs)
        }

        val previous = _karaokeUiState.value
        _karaokeUiState.value = KaraokeUiState(
            challengeEnabled = challengeMode,
            challengeProfile = challengeProfile,
            sessionPhase = KaraokeSessionPhase.COUNTDOWN,
            livesRemaining = 3,
            maxLives = 3,
            combo = 0,
            clearedLines = 0,
            countdownSeconds = 3,
            statusMessage = when {
                !challengeMode -> "Instrumental karaoke"
                challengeProfile == KaraokeChallengeProfile.EASY -> "Easy challenge — artist vocals stay in the mix"
                challengeProfile == KaraokeChallengeProfile.VOICELESS -> "Voiceless challenge — headphones recommended"
                else -> "Hard challenge — sing to reveal the words"
            },
            microphoneRequired = false,
            microphoneUnsupported = false,
            stemProviderAvailable = previous.stemProviderAvailable,
            preparationInProgress = false,
            backingTrackReady = previous.backingTrackReady,
            cachedStemReady = previous.cachedStemReady,
            usingPreparedBackingTrack = usingPreparedBackingTrack,
            melodyLoading = false,
            melodyReady = karaokeMelodyReference != null,
            latestPitchHz = null,
            latestConfidence = 0f,
            latestRms = 0f,
            targetPitchHz = null,
            targetNoteLabel = null,
            pitchErrorCents = null,
            pitchMatched = false,
            pitchRating = null,
            activeLineIndex = 0,
            activeWordIndex = 0,
            revealedWordIndexByLine = emptyMap()
        )

        startKaraokeCountdown(
            seconds = 3,
            phase = KaraokeSessionPhase.COUNTDOWN,
            statusMessage = when {
                !challengeMode -> "Vocals removed — ready when you are"
                challengeProfile == KaraokeChallengeProfile.HARD -> "Sing on cue to reveal each word"
                else -> "Hit the cue and stay in tune"
            }
        ) {
            playerController.play()
            _karaokeUiState.update {
                it.copy(
                    sessionPhase = KaraokeSessionPhase.PLAYING,
                    statusMessage = if (it.challengeProfile == KaraokeChallengeProfile.HARD && it.challengeActive) {
                        "Sing it clean to reveal each word"
                    } else {
                        "Sing the highlighted words"
                    }
                )
            }
            persistPlaybackSession()
        }
    }

    fun exitKaraokeSession(pausePlayback: Boolean, restoreOriginalTrack: Boolean = true) {
        karaokeCountdownJob?.cancel()
        karaokeCountdownJob = null
        pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
        karaokeMicMonitor.stop()
        karaokeLineVoicedMs.clear()
        karaokeLineInTuneMs.clear()
        karaokeResolvedLines.clear()
        lastKaraokePositionMs = null
        latestMicSample = MicrophonePitchSample(rms = 0f, pitchHz = null, confidence = 0f, voiced = false)

        val previous = _karaokeUiState.value
        val restorePositionMs = playerController.getCurrentPositionMs().coerceAtLeast(0L)
        if (restoreOriginalTrack) {
            restoreKaraokePlaybackOverride(restorePositionMs, pausePlayback)
        } else {
            karaokePlaybackRestoreState = null
            if (pausePlayback) {
                playerController.pause()
            }
        }

        _karaokeUiState.value = KaraokeUiState(
            challengeEnabled = false,
            challengeProfile = previous.challengeProfile,
            stemProviderAvailable = previous.stemProviderAvailable,
            preparationInProgress = false,
            backingTrackReady = previous.backingTrackReady,
            cachedStemReady = previous.cachedStemReady,
            usingPreparedBackingTrack = false,
            melodyLoading = previous.melodyLoading,
            melodyReady = previous.melodyReady,
            statusMessage = if (previous.backingTrackReady) "Using cached karaoke instrumental" else null,
            revealedWordIndexByLine = emptyMap()
        )

        if (pausePlayback) {
            persistPlaybackSession(forcePaused = true)
        }
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
        persistPlaybackSession()
    }

    fun setInlineVideoVisible(isVisible: Boolean) {
        _inlineVideoPlayback.update { current ->
            if (isVisible) {
                current.copy(isVisible = true)
            } else {
                InlineVideoPlaybackState()
            }
        }
    }

    fun onInlineVideoPlaybackChanged(
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val safeDurationMs = durationMs.coerceAtLeast(0L)

        if (!isPlaying && safePositionMs == 0L && safeDurationMs == 0L) {
            _inlineVideoPlayback.update {
                it.copy(
                    isVisible = true,
                    hasStarted = false,
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = 0L
                )
            }
            return
        }

        _inlineVideoPlayback.update { current ->
            current.copy(
                isVisible = true,
                hasStarted = current.hasStarted || isPlaying,
                isPlaying = isPlaying,
                positionMs = safePositionMs,
                durationMs = safeDurationMs
            )
        }

        if (isPlaying && playbackState.value.isPlaying) {
            playerController.pause()
            persistPlaybackSession(forcePaused = true)
        }
    }

    fun setLyricsAutoFollow(enabled: Boolean) {
        _lyricsUiState.value = _lyricsUiState.value.copy(autoFollow = enabled)
    }

    fun toggleLyricsVisibility() {
        val current = _lyricsUiState.value
        val cachedLyrics = resolvedLyrics()
        val shouldHideLyrics = current.lyricsVisibilityPreference && (
            current.lyricsVisible || current.isLoading || cachedLyrics != null
        )

        if (shouldHideLyrics) {
            hideLyricsState()
        } else {
            val visibleState = current.withLyricsVisibilityPreference(true)
            _lyricsUiState.value = if (cachedLyrics != null) {
                visibleState.withLyricsResult(cachedLyrics)
            } else {
                visibleState.loadingLyrics()
            }
            if (cachedLyrics == null) {
                refreshLyricsForCurrentTrack()
            }
        }
    }

    fun setThemeColor(themeColor: ThemeColorOption) {
        _themeColor.value = themeColor
        val harmonized = moodEngine.currentTheme.harmonize(themeColor.seedColor())
        _screenTheme.value = harmonized
        _appTheme.value = harmonized
        NotificationThemeBridge.updateFromPalette(harmonized)
    }

    fun registerQueue(queue: PlaybackQueue) {
        upsertQueue(queue)
    }

    fun deleteQueue(key: String) {
        val current = _queueLibrary.value
        _queueLibrary.value = current.filterNot { it.key == key }
    }

    fun removeSongFromQueue(queueKey: String, songId: Long) {
        val existing = queueByKey(queueKey) ?: return
        val removedIndex = existing.songs.indexOfFirst { it.id == songId }
        if (removedIndex < 0) return

        val updatedSongs = existing.songs.filterNot { it.id == songId }
        val updatedQueue = existing.copy(
            songs = updatedSongs,
            isCustomOrder = true,
            lastUsedAt = System.currentTimeMillis()
        )
        upsertQueue(updatedQueue)

        if (_nowPlayingState.value.queueSource?.key == queueKey) {
            syncActiveQueueAfterRemoval(existing, updatedQueue, removedIndex)
        }
    }

    fun removeSongFromQueues(songId: Long) {
        val currentQueues = _queueLibrary.value
        val activeKey = _nowPlayingState.value.queueSource?.key
        val activeExisting = activeKey?.let { key -> currentQueues.firstOrNull { it.key == key } }
        val activeIndex = activeExisting?.songs?.indexOfFirst { it.id == songId } ?: -1

        if (activeExisting != null && activeIndex >= 0) {
            val updatedActive = activeExisting.copy(
                songs = activeExisting.songs.filterNot { it.id == songId },
                isCustomOrder = true,
                lastUsedAt = System.currentTimeMillis()
            )
            upsertQueue(updatedActive)
            syncActiveQueueAfterRemoval(activeExisting, updatedActive, activeIndex)
        }

        val filteredQueues = _queueLibrary.value.map { queue ->
            if (queue.key == activeKey && activeExisting != null && activeIndex >= 0) {
                queue
            } else {
                val filteredSongs = queue.songs.filterNot { it.id == songId }
                if (filteredSongs.size == queue.songs.size) {
                    queue
                } else {
                    queue.copy(
                        songs = filteredSongs,
                        isCustomOrder = true,
                        lastUsedAt = System.currentTimeMillis()
                    )
                }
            }
        }

        _queueLibrary.value = filteredQueues.sortedByDescending { it.lastUsedAt }
    }

    fun getRegisteredQueues(): List<PlaybackQueue> = _queueLibrary.value

    fun queueForKey(key: String): PlaybackQueue? = queueByKey(key)

    fun reorderQueue(key: String, fromIndex: Int, toIndex: Int) {
        val existing = queueByKey(key) ?: return
        val updated = existing.reorder(fromIndex, toIndex)
        upsertQueue(updated)

        val activeKey = _nowPlayingState.value.queueSource?.key
        if (activeKey == key) {
            _nowPlayingState.update { state ->
                val currentSongId = state.currentSong?.id
                val currentIndex = updated.songs.indexOfFirst { it.id == currentSongId }
                    .takeIf { it >= 0 }
                    ?: state.currentQueueIndex.coerceIn(0, (updated.songs.lastIndex).coerceAtLeast(0))

                state.copy(
                    queue = updated.songs,
                    currentQueueIndex = currentIndex,
                    queueSource = updated.source
                )
            }
            persistPlaybackSession()
        }
    }

    fun reorderActiveQueue(fromIndex: Int, toIndex: Int) {
        val activeKey = _nowPlayingState.value.queueSource?.key ?: return
        reorderQueue(activeKey, fromIndex, toIndex)
    }

    private fun queueByKey(key: String): PlaybackQueue? = _queueLibrary.value.firstOrNull { it.key == key }

    private fun upsertQueue(queue: PlaybackQueue) {
        val current = _queueLibrary.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.key == queue.key }
        val resolved = if (existingIndex >= 0) {
            val existing = current[existingIndex]
            if (queue.isCustomOrder) {
                queue.copy(lastUsedAt = maxOf(existing.lastUsedAt, queue.lastUsedAt))
            } else {
                mergeOrRegisterQueue(existing, queue)
            }
        } else {
            queue
        }

        if (existingIndex >= 0) {
            current[existingIndex] = resolved
        } else {
            current.add(resolved)
        }

        val sortedList = current.sortedByDescending { it.lastUsedAt }
        
        // Enforce max 12 saved queues
        val savedQueues = sortedList.filter { (it.source as? com.lyrictica.audio.QueueSource.Custom)?.category == "Saved Queue" }
        val finalQueues = if (savedQueues.size > 12) {
            val oldestQueuesToRemove = savedQueues.drop(12).map { it.key }
            sortedList.filterNot { it.key in oldestQueuesToRemove }
        } else {
            sortedList
        }

        _queueLibrary.value = finalQueues
    }

    private fun syncActiveQueueAfterRemoval(
        existingQueue: PlaybackQueue,
        updatedQueue: PlaybackQueue,
        removedIndex: Int
    ) {
        val state = _nowPlayingState.value
        val currentIndex = state.currentQueueIndex.takeIf { it in existingQueue.songs.indices }
            ?: existingQueue.songs.indexOfFirst { it.id == state.currentSong?.id }

        if (updatedQueue.songs.isEmpty()) {
            _nowPlayingState.update {
                it.copy(
                    currentSong = null,
                    queue = emptyList(),
                    currentQueueIndex = -1,
                    queueSource = updatedQueue.source
                )
            }
            playerController.clearPlayback()
            persistPlaybackSession()
            return
        }

        val nextIndex = when {
            currentIndex < 0 -> 0
            removedIndex < currentIndex -> (currentIndex - 1).coerceAtLeast(0)
            removedIndex == currentIndex -> currentIndex.coerceAtMost(updatedQueue.songs.lastIndex)
            else -> currentIndex.coerceAtMost(updatedQueue.songs.lastIndex)
        }

        val snapshots = updatedQueue.songs.mapNotNull { snapshotForSong(it) }
        val currentPosition = playbackState.value.currentPosition
        val shouldPreservePosition = removedIndex != currentIndex

        _nowPlayingState.update {
            it.copy(
                queue = updatedQueue.songs,
                currentQueueIndex = nextIndex,
                currentSong = updatedQueue.songs.getOrNull(nextIndex),
                queueSource = updatedQueue.source
            )
        }

        if (snapshots.size == updatedQueue.songs.size) {
            playerController.playSnapshots(
                snapshots = snapshots,
                startIndex = nextIndex,
                autoPlay = playbackState.value.isPlaying,
                startPositionMs = if (shouldPreservePosition) currentPosition else 0L,
                replaceExisting = true
            )
        }

        persistPlaybackSession()
    }

    fun playNext() {
        advanceToNextTrack(autoPlay = true)
    }

    fun playPrevious() {
        if (playerController.uiState.value.currentPosition > 3000) {
            playerController.seekTo(0)
            return
        }

        advanceToPreviousTrack(autoPlay = true)
    }

    fun selectVideoModePreviousTrack() {
        advanceToPreviousTrack(autoPlay = false)
    }

    fun selectVideoModeNextTrack() {
        advanceToNextTrack(autoPlay = false)
    }

    fun toggleShuffle() {
        _nowPlayingState.update { it.copy(isShuffleOn = !it.isShuffleOn) }
        persistPlaybackSession()
    }

    fun toggleRepeat() {
        _nowPlayingState.update {
            val newMode = (it.repeatMode + 1) % 3
            val exoRepeatMode = when (newMode) {
                2 -> androidx.media3.common.Player.REPEAT_MODE_ONE
                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
            playerController.setRepeatMode(exoRepeatMode)
            it.copy(repeatMode = newMode)
        }
        persistPlaybackSession()
    }

    private fun updateKaraokeSession(
        positionMs: Long,
        isPlaying: Boolean,
        parsedAvailable: Boolean
    ) {
        val karaokeState = _karaokeUiState.value
        if (!karaokeState.isSessionActive) return

        val parsed = resolvedLyrics()
        val activeWord = parsed?.lines?.takeIf { parsedAvailable }?.let { LyricsSync.currentWordPosition(it, positionMs) }
        val activeWordReference = activeWord?.let { karaokeMelodyReference?.wordFor(it.lineIndex, it.wordIndex) }
        val latestPitchHz = latestMicSample.pitchHz
        val activePitchMatch = if (activeWordReference?.expectedPitchHz != null && latestPitchHz != null) {
            KaraokePitchMath.comparePitch(latestPitchHz, activeWordReference.expectedPitchHz)
        } else {
            null
        }

        if (activeWord != null || karaokeState.targetPitchHz != null || karaokeState.pitchErrorCents != null) {
            _karaokeUiState.update {
                it.copy(
                    activeLineIndex = activeWord?.lineIndex ?: it.activeLineIndex,
                    activeWordIndex = activeWord?.wordIndex ?: it.activeWordIndex,
                    targetPitchHz = activeWordReference?.expectedPitchHz,
                    targetNoteLabel = KaraokePitchMath.noteLabel(activeWordReference?.expectedPitchHz),
                    pitchErrorCents = activePitchMatch?.centsError,
                    pitchMatched = activePitchMatch?.inTune ?: false,
                    pitchRating = activePitchMatch?.rating
                )
            }
        }

        val shouldRevealActiveWord = when {
            !karaokeState.challengeActive || !karaokeState.challengeProfile.hidesLyricsUntilMatched || activeWord == null -> false
            activeWordReference?.expectedPitchHz != null -> activePitchMatch?.inTune == true
            else -> latestMicSample.voiced
        }
        if (shouldRevealActiveWord && activeWord != null) {
            _karaokeUiState.update {
                val revealedWordIndex = it.revealedWordIndexByLine[activeWord.lineIndex] ?: -1
                if (activeWord.wordIndex <= revealedWordIndex) {
                    it
                } else {
                    it.copy(
                        revealedWordIndexByLine = it.revealedWordIndexByLine + (activeWord.lineIndex to activeWord.wordIndex)
                    )
                }
            }
        }

        if (karaokeState.sessionPhase != KaraokeSessionPhase.PLAYING || !isPlaying) {
            lastKaraokePositionMs = positionMs
            return
        }

        val lastPosition = lastKaraokePositionMs ?: positionMs
        val deltaMs = (positionMs - lastPosition).coerceIn(0L, 96L)
        lastKaraokePositionMs = positionMs

        val currentLineIndex = activeWord?.lineIndex ?: _lyricsUiState.value.currentLineIndex
        if (currentLineIndex >= 0 && latestMicSample.voiced && deltaMs > 0L) {
            karaokeLineVoicedMs[currentLineIndex] = (karaokeLineVoicedMs[currentLineIndex] ?: 0L) + deltaMs
        }
        if (currentLineIndex >= 0 && activePitchMatch?.inTune == true && deltaMs > 0L) {
            karaokeLineInTuneMs[currentLineIndex] = (karaokeLineInTuneMs[currentLineIndex] ?: 0L) + deltaMs
        }

        for (window in karaokeLineWindows) {
            if (window.lineIndex in karaokeResolvedLines) continue
            if (positionMs <= window.endTimeMs + KARAOKE_LINE_EVALUATION_GRACE_MS) break

            val voicedDuration = karaokeLineVoicedMs[window.lineIndex] ?: 0L
            val inTuneDuration = karaokeLineInTuneMs[window.lineIndex] ?: 0L
            val lineHasMelody = karaokeMelodyReference?.hasMelodyForLine(window.lineIndex) == true
            val linePassed = when {
                !karaokeState.challengeActive -> true
                lineHasMelody -> inTuneDuration >= (karaokeMelodyReference?.requiredInTuneMs(window.lineIndex) ?: Long.MAX_VALUE)
                else -> voicedDuration >= window.requiredVoicedMs
            }

            if (linePassed) {
                karaokeResolvedLines += window.lineIndex
                _karaokeUiState.update {
                    it.copy(
                        combo = if (it.challengeActive) it.combo + 1 else it.combo,
                        clearedLines = karaokeResolvedLines.size,
                        statusMessage = when {
                            !it.challengeActive -> "In sync"
                            it.challengeProfile.hidesLyricsUntilMatched && lineHasMelody -> "On pitch — line revealed"
                            it.challengeProfile.hidesLyricsUntilMatched -> "Locked in — line revealed"
                            lineHasMelody -> "On pitch"
                            else -> "Locked in"
                        },
                        revealedWordIndexByLine = if (it.challengeProfile.hidesLyricsUntilMatched) {
                            it.revealedWordIndexByLine + (window.lineIndex to Int.MAX_VALUE)
                        } else {
                            it.revealedWordIndexByLine
                        }
                    )
                }
                continue
            }

            handleKaraokeMiss(window)
            break
        }
    }

    private fun handleKaraokeMiss(window: KaraokeLineWindow) {
        val currentState = _karaokeUiState.value
        if (!currentState.challengeActive) return

        playerController.pause()
        val remainingLives = (currentState.livesRemaining - 1).coerceAtLeast(0)
        val rewindLineIndex = KaraokeTimingJudge.rewindTargetLineIndex(window.lineIndex)
        val rewindPositionMs = KaraokeTimingJudge.windowForLine(karaokeLineWindows, rewindLineIndex)?.startTimeMs ?: 0L

        if (remainingLives == 0) {
            resetKaraokeProgressFrom(0)
            playerController.seekTo(0L)
            _karaokeUiState.update {
                it.copy(
                    sessionPhase = KaraokeSessionPhase.FAILED,
                    livesRemaining = 0,
                    combo = 0,
                    countdownSeconds = 5,
                    statusMessage = "No lives left — restarting the song"
                )
            }
            startKaraokeCountdown(
                seconds = 5,
                phase = KaraokeSessionPhase.FAILED,
                statusMessage = "Five-second reset"
            ) {
                playerController.seekTo(0L)
                playerController.play()
                _karaokeUiState.update {
                    it.copy(
                        sessionPhase = KaraokeSessionPhase.PLAYING,
                        livesRemaining = it.maxLives,
                        combo = 0,
                        clearedLines = 0,
                        statusMessage = "Fresh run — take it from the top"
                    )
                }
                persistPlaybackSession()
            }
            return
        }

        resetKaraokeProgressFrom(rewindLineIndex)
        playerController.seekTo(rewindPositionMs)
        _karaokeUiState.update {
            it.copy(
                sessionPhase = KaraokeSessionPhase.MISSED,
                livesRemaining = remainingLives,
                combo = 0,
                countdownSeconds = 5,
                statusMessage = "Missed the cue — back two lines"
            )
        }
        startKaraokeCountdown(
            seconds = 5,
            phase = KaraokeSessionPhase.MISSED,
            statusMessage = "Catch the re-entry"
        ) {
            playerController.play()
            _karaokeUiState.update {
                it.copy(
                    sessionPhase = KaraokeSessionPhase.PLAYING,
                    statusMessage = "Back in — follow the highlighted words"
                )
            }
            persistPlaybackSession()
        }
    }

    private fun handleKaraokeSuccess() {
        karaokeCountdownJob?.cancel()
        karaokeMicMonitor.stop()
        val finalState = _karaokeUiState.value
        _karaokeUiState.update {
            it.copy(
                sessionPhase = KaraokeSessionPhase.SUCCESS,
                countdownSeconds = 0,
                pitchErrorCents = null,
                pitchMatched = false,
                pitchRating = null,
                statusMessage = if (it.challengeActive) "Track cleared — beautiful run" else "Song complete"
            )
        }
        recordGameScore(GameModeOption.KARAOKE, karaokeCompletionScore(finalState))
    }

    private fun karaokeCompletionScore(state: KaraokeUiState): Int {
        val lineScore = state.clearedLines * if (state.challengeActive) 150 else 100
        val comboScore = state.combo * if (state.challengeActive) 28 else 16
        val lifeBonus = state.livesRemaining * 120
        val completionBonus = if (state.challengeActive) 420 else 180
        return lineScore + comboScore + lifeBonus + completionBonus
    }

    private fun startKaraokeCountdown(
        seconds: Int,
        phase: KaraokeSessionPhase,
        statusMessage: String,
        onFinish: () -> Unit
    ) {
        karaokeCountdownJob?.cancel()
        karaokeCountdownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _karaokeUiState.update {
                    it.copy(
                        sessionPhase = phase,
                        countdownSeconds = remaining,
                        statusMessage = statusMessage
                    )
                }
                delay(1000L)
            }
            _karaokeUiState.update { it.copy(countdownSeconds = 0) }
            onFinish()
        }
    }

    private fun resetKaraokeProgressFrom(startLineIndex: Int) {
        karaokeResolvedLines.removeAll { it >= startLineIndex }
        karaokeLineVoicedMs.keys.removeAll { it >= startLineIndex }
        karaokeLineInTuneMs.keys.removeAll { it >= startLineIndex }
        lastKaraokePositionMs = KaraokeTimingJudge.windowForLine(karaokeLineWindows, startLineIndex)?.startTimeMs ?: 0L
        _karaokeUiState.update {
            it.copy(
                clearedLines = karaokeResolvedLines.size,
                activeLineIndex = startLineIndex,
                activeWordIndex = 0,
                pitchErrorCents = null,
                pitchMatched = false,
                pitchRating = null,
                revealedWordIndexByLine = it.revealedWordIndexByLine.filterKeys { index -> index < startLineIndex }
            )
        }
    }

    override fun onCleared() {
        persistPlaybackSession()
        super.onCleared()
        lyricsJob?.cancel()
        melodyJob?.cancel()
        karaokeCountdownJob?.cancel()
        karaokeMicMonitor.release()
        audioAnalyzer.release()
        playerController.release()
    }

    private fun observeCurrentSong() {
        viewModelScope.launch {
            _nowPlayingState.map { it.currentSong }
                .distinctUntilChanged()
                .collect { song ->
                    if (song == null) {
                        lyricsJob?.cancel()
                        clearMelodyReference()
                        exitKaraokeSession(pausePlayback = false, restoreOriginalTrack = false)
                        _availableLyrics.value = null
                        _lyricsUiState.value = _lyricsUiState.value.clearedLyrics()
                    } else {
                        if (_karaokeUiState.value.isSessionActive) {
                            exitKaraokeSession(pausePlayback = true, restoreOriginalTrack = false)
                        }
                        loadLyricsFor(
                            uri = Uri.parse(song.toPlaybackSnapshot().uri),
                            fallbackDisplayName = song.title,
                            artist = song.artist,
                            album = song.album,
                            durationSec = (song.duration / 1000).toInt()
                        )
                    }
                }
        }
    }

    private fun syncCurrentSongFromMediaId(mediaId: String?) {
        if (mediaId.isNullOrBlank()) return
        val queue = _nowPlayingState.value.queue
        if (queue.isEmpty()) return

        val matchedIndex = queue.indexOfFirst {
            it.id.toString() == mediaId || it.data == mediaId
        }
        if (matchedIndex < 0) return

        val matchedSong = queue[matchedIndex]
        val currentState = _nowPlayingState.value
        if (currentState.currentSong?.id == matchedSong.id && currentState.currentQueueIndex == matchedIndex) return

        _nowPlayingState.update {
            it.copy(
                currentSong = matchedSong,
                currentQueueIndex = matchedIndex
            )
        }
    }

    private fun advanceToNextTrack(autoPlay: Boolean) {
        val state = _nowPlayingState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        val currentIndex = resolveCurrentIndex(state)
        val nextIndex = if (state.isShuffleOn) {
            queue.indices.random()
        } else {
            (currentIndex + 1) % queue.size
        }

        _nowPlayingState.update { it.copy(currentQueueIndex = nextIndex, currentSong = queue[nextIndex]) }
        playSongAtIndex(nextIndex, autoPlay = autoPlay)
    }

    private fun advanceToPreviousTrack(autoPlay: Boolean) {
        val state = _nowPlayingState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        val currentIndex = resolveCurrentIndex(state)
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else queue.lastIndex

        _nowPlayingState.update { it.copy(currentQueueIndex = prevIndex, currentSong = queue[prevIndex]) }
        playSongAtIndex(prevIndex, autoPlay = autoPlay)
    }

    fun playQueueItem(index: Int) {
        val state = _nowPlayingState.value
        val queue = state.queue
        if (index !in queue.indices) return
        _nowPlayingState.update { it.copy(currentQueueIndex = index, currentSong = queue[index]) }
        playSongAtIndex(index, autoPlay = true)
    }

    private fun playSongAtIndex(
        index: Int,
        autoPlay: Boolean = true
    ) {
        val state = _nowPlayingState.value
        val queue = state.queue
        if (queue.isEmpty() || index !in queue.indices) return

        val song = queue[index]
        val snapshots = queue.map { snapshotForSong(it) ?: return }
        val activeSnapshot = snapshots.getOrNull(index) ?: return
        val uri = Uri.parse(activeSnapshot.uri)

        moodEngine.reset()
        val harmonized = moodEngine.currentTheme.harmonize(_themeColor.value.seedColor())
        _screenTheme.value = harmonized
        _appTheme.value = harmonized
        NotificationThemeBridge.updateFromPalette(harmonized)
        audioAnalyzer.load(uri)
        playerController.setRepeatMode(
            when (state.repeatMode) {
                2 -> androidx.media3.common.Player.REPEAT_MODE_ONE
                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
        )
        playerController.playSnapshots(
            snapshots = snapshots,
            startIndex = index,
            autoPlay = autoPlay,
            startPositionMs = 0L,
            replaceExisting = true
        )
        recordSongPlay(song)
        persistPlaybackSession()
    }

    private fun startPlayback(
        uri: Uri,
        displayName: String?,
        lyricsFallbackTitle: String?
    ) {
        val exoRepeatMode = when (_nowPlayingState.value.repeatMode) {
            2 -> androidx.media3.common.Player.REPEAT_MODE_ONE
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }
        playerController.setRepeatMode(exoRepeatMode)
        playerController.playUri(uri, displayName)
        loadLyricsFor(uri, lyricsFallbackTitle)
    }

    private fun recordSongPlay(song: Song) {
        if (song.id <= 0L) return
        viewModelScope.launch {
            runCatching {
                musicRepository.recordSongPlay(song.id)
            }.onFailure { error ->
                android.util.Log.e("VisualizerViewModel", "Failed to record play for ${song.id}", error)
            }
        }
    }

    private fun currentLyricsRequest(): LyricsRequest? {
        val currentSong = _nowPlayingState.value.currentSong
        if (currentSong != null) {
            val snapshot = snapshotForSong(currentSong) ?: return null
            return LyricsRequest(
                uri = Uri.parse(snapshot.uri),
                fallbackDisplayName = snapshot.title.ifBlank { currentSong.title },
                artist = snapshot.artist?.takeIf { it.isNotBlank() } ?: currentSong.artist,
                album = snapshot.album?.takeIf { it.isNotBlank() } ?: currentSong.album,
                durationSec = (snapshot.durationMs / 1000).toInt().takeIf { it > 0 } 
                    ?: (currentSong.duration / 1000).toInt().takeIf { it > 0 }
            )
        }

        val currentUri = playerController.getCurrentUri() ?: return null
        val trackName = playbackState.value.trackName.takeUnless {
            it.isBlank() || it == "No track selected"
        }
        return LyricsRequest(
            uri = currentUri,
            fallbackDisplayName = trackName
        )
    }

    private fun refreshLyricsForCurrentTrack() {
        val request = currentLyricsRequest() ?: return
        loadLyricsFor(
            uri = request.uri,
            fallbackDisplayName = request.fallbackDisplayName,
            artist = request.artist,
            album = request.album,
            durationSec = request.durationSec
        )
    }

    private fun resolvedLyrics(): ParsedLyrics? {
        val transState = _translationState.value
        val lang = transState.languageCode
        if (lang != null) {
            val trans = transState.translations[lang]
            if (trans != null) return trans
        }
        return _availableLyrics.value ?: _lyricsUiState.value.parsed
    }

    fun setTranslationLanguage(languageCode: String?) {
        val currentTrackId = _availableLyrics.value?.musixmatchTrackId ?: _lyricsUiState.value.parsed?.musixmatchTrackId
        if (languageCode == null || currentTrackId == null) {
            _translationState.update { it.copy(languageCode = null) }
            return
        }

        val currentState = _translationState.value
        if (currentState.translations.containsKey(languageCode)) {
            _translationState.update { it.copy(languageCode = languageCode, error = null) }
            return
        }

        _translationState.update { it.copy(languageCode = languageCode, isLoading = true, error = null) }

        viewModelScope.launch {
            val translation = runCatching {
                lyricsRepository.fetchTranslation(currentTrackId, languageCode)
            }.getOrNull()

            if (translation != null) {
                _translationState.update {
                    it.copy(
                        isLoading = false,
                        translations = it.translations + (languageCode to translation)
                    )
                }
            } else {
                _translationState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load translation"
                    )
                }
            }
        }
    }

    private fun clearMelodyReference() {
        melodyJob?.cancel()
        melodyJob = null
        pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
        karaokeMelodyReference = null
        karaokeInstrumentalUri = null
        karaokeOriginalSourceUri = null
        _karaokeUiState.update {
            it.copy(
                stemProviderAvailable = karaokeAssetsRepository.isProviderConfigured,
                preparationInProgress = false,
                backingTrackReady = false,
                cachedStemReady = false,
                usingPreparedBackingTrack = false,
                statusMessage = null,
                melodyLoading = false,
                melodyReady = false,
                targetPitchHz = null,
                targetNoteLabel = null,
                pitchErrorCents = null,
                pitchMatched = false,
                pitchRating = null
            )
        }
    }

    private fun prepareMelodyReference(uri: Uri, parsed: ParsedLyrics?) {
        karaokeOriginalSourceUri = uri
        if (parsed == null || !parsed.hasWordSync) {
            clearMelodyReference()
            return
        }

        melodyJob?.cancel()
        karaokeMelodyReference = null
        karaokeInstrumentalUri = null
        _karaokeUiState.update {
            it.copy(
                stemProviderAvailable = karaokeAssetsRepository.isProviderConfigured,
                melodyLoading = true,
                preparationInProgress = false,
                melodyReady = false,
                targetPitchHz = null,
                targetNoteLabel = null,
                pitchErrorCents = null,
                pitchMatched = false,
                pitchRating = null
            )
        }

        melodyJob = viewModelScope.launch {
            val result = karaokeAssetsRepository.loadCachedAssets(
                sourceUri = uri,
                lyrics = parsed,
                onProgress = ::applyKaraokePreparationProgress
            )

            if (!isActive) return@launch

            applyKaraokeAssetResult(
                result = result,
                sourceUri = uri,
                finalMessage = result.assets.failureReason
            )
        }
    }

    private fun applyKaraokePreparationProgress(progress: KaraokePreparationProgress) {
        _karaokeUiState.update {
            it.copy(
                stemProviderAvailable = karaokeAssetsRepository.isProviderConfigured,
                preparationInProgress = progress.stage == KaraokePreparationStage.UPLOADING ||
                    progress.stage == KaraokePreparationStage.WAITING ||
                    progress.stage == KaraokePreparationStage.DOWNLOADING ||
                    progress.stage == KaraokePreparationStage.CACHING,
                backingTrackReady = it.backingTrackReady,
                melodyLoading = true,
                statusMessage = progress.progressPercent
                    ?.takeIf { it in 1..99 }
                    ?.let { percent -> "${progress.message} ($percent%)" }
                    ?: progress.message
            )
        }
    }

    private fun applyKaraokeAssetResult(
        result: com.lyrictica.karaoke.KaraokeAssetsResult,
        sourceUri: Uri,
        finalMessage: String?
    ) {
        karaokeOriginalSourceUri = sourceUri
        karaokeMelodyReference = result.melodyReference?.takeIf { it.hasAnyReference }
        karaokeInstrumentalUri = result.assets.instrumentalFile?.takeIf { it.isFile }?.let(Uri::fromFile)
        val autoStartMode = pendingKaraokeStartAfterPreparation
        pendingKaraokeStartAfterPreparation = PendingKaraokeStartMode.NONE
        val resolvedMessage = when {
            autoStartMode == PendingKaraokeStartMode.CHALLENGE &&
                result.assets.failureReason == null &&
                karaokeMelodyReference == null -> {
                "Karaoke assets are ready, but this track still lacks a stable melody map for challenge mode."
            }
            else -> finalMessage
        }

        _karaokeUiState.update {
            it.copy(
                stemProviderAvailable = result.assets.stemProviderAvailable,
                preparationInProgress = false,
                backingTrackReady = result.assets.backingTrackReady,
                cachedStemReady = result.assets.cachedStems,
                usingPreparedBackingTrack = it.isSessionActive &&
                    karaokeInstrumentalUri != null &&
                    (!it.challengeEnabled || it.challengeProfile.requiresPreparedBackingTrack),
                melodyLoading = false,
                melodyReady = karaokeMelodyReference != null,
                statusMessage = resolvedMessage ?: challengeModeStatusMessage(
                    enabled = it.challengeEnabled,
                    profile = it.challengeProfile,
                    melodyReady = karaokeMelodyReference != null,
                    stemProviderAvailable = result.assets.stemProviderAvailable,
                    backingTrackReady = result.assets.backingTrackReady
                ),
                targetPitchHz = null,
                targetNoteLabel = null,
                pitchErrorCents = null,
                pitchMatched = false,
                pitchRating = null
            )
        }

        when (autoStartMode) {
            PendingKaraokeStartMode.CHALLENGE -> {
                if (karaokeMelodyReference != null) {
                    startKaraokeSession(challengeMode = true)
                }
            }
            PendingKaraokeStartMode.SING_ALONG -> {
                if (result.assets.failureReason == null) {
                    startKaraokeSession(challengeMode = false)
                }
            }
            PendingKaraokeStartMode.NONE -> Unit
        }
    }

    private fun challengeModeStatusMessage(
        enabled: Boolean,
        profile: KaraokeChallengeProfile,
        melodyReady: Boolean,
        stemProviderAvailable: Boolean,
        backingTrackReady: Boolean
    ): String? {
        if (!enabled) return null
        return when {
            melodyReady -> when (profile) {
                KaraokeChallengeProfile.EASY -> "Easy challenge armed — artist vocals stay in while your timing and pitch are scored."
                KaraokeChallengeProfile.VOICELESS -> "Voiceless challenge armed — the instrumental is ready for your run."
                KaraokeChallengeProfile.HARD -> "Hard challenge armed — vocals stay muted and lyrics reveal only when you lock in."
            }
            stemProviderAvailable || backingTrackReady -> when (profile) {
                KaraokeChallengeProfile.EASY -> "Easy challenge keeps the artist vocal and will finish its melody map when you start."
                KaraokeChallengeProfile.VOICELESS -> "Voiceless challenge will finish the instrumental prep when you start."
                KaraokeChallengeProfile.HARD -> "Hard challenge will finish the instrumental prep and lyric reveal scoring when you start."
            }
            else -> when (profile) {
                KaraokeChallengeProfile.EASY -> "Easy challenge still needs LALAL.AI karaoke prep for pitch scoring, but this build is not configured for it."
                KaraokeChallengeProfile.VOICELESS -> "Voiceless challenge needs LALAL.AI karaoke prep, but this build is not configured for it."
                KaraokeChallengeProfile.HARD -> "Hard challenge needs LALAL.AI karaoke prep, but this build is not configured for it."
            }
        }
    }

    private fun syncKaraokeChallengePlayback(
        enabled: Boolean,
        profile: KaraokeChallengeProfile,
        sessionActive: Boolean,
        currentlyUsingPreparedBackingTrack: Boolean
    ): Boolean {
        if (!sessionActive) return false
        val shouldUsePreparedBackingTrack = enabled && profile.requiresPreparedBackingTrack && karaokeInstrumentalUri != null
        val positionMs = playerController.getCurrentPositionMs().coerceAtLeast(0L)
        when {
            shouldUsePreparedBackingTrack && !currentlyUsingPreparedBackingTrack -> {
                engageKaraokePlaybackOverride(karaokeInstrumentalUri!!, positionMs)
            }
            !shouldUsePreparedBackingTrack && currentlyUsingPreparedBackingTrack -> {
                restoreKaraokePlaybackOverride(positionMs, pausePlayback = false)
            }
        }
        return shouldUsePreparedBackingTrack
    }

    private fun engageKaraokePlaybackOverride(instrumentalUri: Uri, startPositionMs: Long) {
        val currentRequest = currentLyricsRequest() ?: return
        val state = _nowPlayingState.value
        val queueSnapshots = if (state.queue.isNotEmpty()) {
            state.queue.mapNotNull(::snapshotForSong)
        } else {
            listOfNotNull(snapshotForCurrentTrack())
        }
        val currentIndex = if (state.queue.isNotEmpty()) {
            resolveCurrentIndex(state).coerceAtLeast(0)
        } else {
            0
        }
        if (queueSnapshots.isEmpty()) return

        karaokePlaybackRestoreState = KaraokePlaybackRestoreState(
            queueSnapshots = queueSnapshots,
            currentIndex = currentIndex,
            sourceUri = currentRequest.uri,
            repeatMode = state.repeatMode
        )

        val baseSnapshot = snapshotForCurrentTrack() ?: queueSnapshots.getOrNull(currentIndex) ?: return
        val overrideSnapshot = baseSnapshot.copy(uri = instrumentalUri.toString())
        audioAnalyzer.load(instrumentalUri)
        playerController.setRepeatMode(exoRepeatModeFor(state.repeatMode))
        playerController.playSnapshots(
            snapshots = listOf(overrideSnapshot),
            startIndex = 0,
            autoPlay = false,
            startPositionMs = startPositionMs,
            replaceExisting = true
        )
    }

    private fun restoreKaraokePlaybackOverride(positionMs: Long, pausePlayback: Boolean) {
        val restoreState = karaokePlaybackRestoreState ?: run {
            karaokeInstrumentalUri = null
            karaokeOriginalSourceUri?.let(audioAnalyzer::load)
            if (pausePlayback) playerController.pause()
            return
        }

        karaokePlaybackRestoreState = null
        karaokeInstrumentalUri = null
        audioAnalyzer.load(restoreState.sourceUri)
        playerController.setRepeatMode(exoRepeatModeFor(restoreState.repeatMode))
        playerController.playSnapshots(
            snapshots = restoreState.queueSnapshots,
            startIndex = restoreState.currentIndex.coerceIn(0, restoreState.queueSnapshots.lastIndex),
            autoPlay = !pausePlayback,
            startPositionMs = positionMs.coerceAtLeast(0L),
            replaceExisting = true
        )
        if (pausePlayback) {
            playerController.pause()
        }
    }

    private fun exoRepeatModeFor(mode: Int): Int {
        return when (mode) {
            2 -> androidx.media3.common.Player.REPEAT_MODE_ONE
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }

    private fun hideLyricsState() {
        lyricsJob?.cancel()
        _lyricsUiState.value = _lyricsUiState.value.hiddenLyrics()
    }

    private fun clearLyricsPreview(restoreCurrentTrack: Boolean) {
        val hadPreview = _lyricsPreviewState.value != null
        _lyricsPreviewState.value = null
        if (!hadPreview) return

        lyricsJob?.cancel()
        _availableLyrics.value = null
        clearMelodyReference()

        if (restoreCurrentTrack) {
            val request = currentLyricsRequest()
            if (request != null) {
                loadLyricsFor(
                    uri = request.uri,
                    fallbackDisplayName = request.fallbackDisplayName,
                    artist = request.artist,
                    album = request.album,
                    durationSec = request.durationSec
                )
                return
            }
        }

        _lyricsUiState.value = _lyricsUiState.value.clearedLyrics()
    }

    private fun loadPreviewLyrics(
        trackName: String,
        artist: String? = null,
        album: String? = null,
        durationSec: Int? = null
    ) {
        lyricsJob?.cancel()
        _availableLyrics.value = null
        clearMelodyReference()
        _lyricsUiState.value = _lyricsUiState.value
            .copy(lyricsVisibilityPreference = false, lyricsVisible = false)
            .loadingLyrics()

        lyricsJob = viewModelScope.launch {
            val parsed = runCatching {
                lyricsRepository.fetchLyrics(
                    trackName = trackName,
                    artist = artist,
                    album = album,
                    durationSec = durationSec
                )
            }.getOrNull()

            if (!isActive) return@launch

            _availableLyrics.value = parsed
            _lyricsUiState.value = _lyricsUiState.value.withLyricsResult(parsed)
        }
    }

    private fun loadLyricsFor(
        uri: Uri,
        fallbackDisplayName: String?,
        artist: String? = null,
        album: String? = null,
        durationSec: Int? = null
    ) {
        lyricsJob?.cancel()
        _availableLyrics.value = null
        _translationState.value = TranslationState()
        clearMelodyReference()
        karaokeOriginalSourceUri = uri
        _lyricsUiState.value = _lyricsUiState.value.loadingLyrics()

        lyricsJob = viewModelScope.launch {
            val parsed = runCatching {
                lyricsRepository.fetchLyrics(
                    uri = uri,
                    fallbackDisplayName = fallbackDisplayName,
                    artist = artist,
                    album = album,
                    durationSec = durationSec
                )
            }.getOrNull()

            if (!isActive) return@launch

            _availableLyrics.value = parsed
            _lyricsUiState.value = if (_lyricsUiState.value.lyricsVisibilityPreference) {
                _lyricsUiState.value.withLyricsResult(parsed)
            } else {
                _lyricsUiState.value.hiddenLyrics()
            }
            prepareMelodyReference(uri, parsed)
        }
    }

    private fun handleSongEnded() {
        if (_karaokeUiState.value.isSessionActive) {
            handleKaraokeSuccess()
            persistPlaybackSession(forcePaused = true)
            return
        }

        val state = _nowPlayingState.value
        val queue = state.queue

        when (state.repeatMode) {
            2 -> {
                playerController.seekTo(0)
                playerController.play()
            }
            1 -> {
                hideLyricsState()
                playNext()
            }
            else -> {
                val currentIndex = resolveCurrentIndex(state)
                if (currentIndex < queue.lastIndex) {
                    hideLyricsState()
                    playNext()
                } else {
                    // Leave the track ended and let the UI present it as paused at the end.
                    persistPlaybackSession(forcePaused = true)
                }
            }
        }
    }

    fun savePlaybackSession() {
        persistPlaybackSession()
    }

    private fun restorePersistedSession() {
        val session = playbackSessionStore.load() ?: return

        runCatching {
            val queueSnapshots = if (session.queue.isNotEmpty()) {
                session.queue
            } else {
                listOf(session.current)
            }
            val queue = queueSnapshots.map { it.toSong() }

            val queueIndex = session.currentQueueIndex.takeIf { it in queue.indices }
                ?: queue.indexOfFirst { it.id > 0L && session.current.songId != null && it.id == session.current.songId }
                    .takeIf { it >= 0 }
                ?: 0

            val activeSong = queue.getOrNull(queueIndex) ?: session.current.toSong()
            val activeSnapshot = queueSnapshots.getOrNull(queueIndex) ?: session.current

            val restoredSource = session.queueSourceKey?.let { key ->
                QueueSource.Custom(
                    key = key,
                    label = session.queueSourceLabel ?: "Queue",
                    category = session.queueSourceCategory ?: "Queue"
                )
            } ?: QueueSource.Custom(
                key = "restored:$queueIndex",
                label = session.queueSourceLabel ?: "Queue",
                category = session.queueSourceCategory ?: "Queue"
            )

            val restoredQueue = PlaybackQueue(
                source = restoredSource,
                songs = queue,
                isCustomOrder = session.queueSourceKey != null,
                lastUsedAt = System.currentTimeMillis()
            )
            upsertQueue(restoredQueue)

            _nowPlayingState.value = VisualizerPlaybackState(
                currentSong = activeSong,
                queue = queue,
                currentQueueIndex = queueIndex,
                queueSource = restoredSource,
                isShuffleOn = session.isShuffleOn,
                repeatMode = session.repeatMode
            )

            val uri = Uri.parse(activeSnapshot.uri)
            moodEngine.reset()
            val harmonized = moodEngine.currentTheme.harmonize(_themeColor.value.seedColor())
            _screenTheme.value = harmonized
            _appTheme.value = harmonized
            NotificationThemeBridge.updateFromPalette(harmonized)
            audioAnalyzer.load(uri)
            playerController.setRepeatMode(
                if (session.repeatMode == 2) androidx.media3.common.Player.REPEAT_MODE_ONE
                else androidx.media3.common.Player.REPEAT_MODE_OFF
            )
            playerController.playSnapshots(
                snapshots = queueSnapshots,
                startIndex = queueIndex,
                autoPlay = session.isPlaying,
                startPositionMs = session.positionMs,
                replaceExisting = false
            )
        }.onFailure {
            playbackSessionStore.clear()
        }
    }

    private fun persistPlaybackSession(forcePaused: Boolean = false) {
        val currentSnapshot = snapshotForCurrentTrack() ?: run {
            playbackSessionStore.clear()
            return
        }
        val state = _nowPlayingState.value
        val session = PlaybackSession(
            current = currentSnapshot,
            positionMs = playbackState.value.currentPosition,
            isPlaying = if (forcePaused) false else playbackState.value.isPlaying,
            queue = state.queue.mapNotNull { snapshotForSong(it) },
            currentQueueIndex = state.currentQueueIndex,
            queueSourceKey = state.queueSource?.key,
            queueSourceLabel = state.queueSource?.label,
            queueSourceCategory = state.queueSource?.category,
            isShuffleOn = state.isShuffleOn,
            repeatMode = state.repeatMode
        )
        playbackSessionStore.save(session)
    }

    private fun snapshotForCurrentTrack(): PlaybackTrackSnapshot? {
        val currentSong = _nowPlayingState.value.currentSong
        if (currentSong != null) {
            val snapshot = snapshotForSong(currentSong)
            if (snapshot != null) return snapshot
        }

        val currentUri = playerController.getCurrentUri() ?: return null
        val playback = playbackState.value
        if (playback.trackName == "No track selected") return null

        return PlaybackTrackSnapshot(
            uri = currentUri.toString(),
            title = playback.trackName,
            artist = currentSong?.artist,
            album = currentSong?.album,
            albumArtUri = currentSong?.albumArtUri,
            durationMs = playback.duration,
            songId = currentSong?.id?.takeIf { it > 0L }
        )
    }

    private fun snapshotForSong(song: Song): PlaybackTrackSnapshot? {
        return when {
            song.id > 0L -> song.toPlaybackSnapshot()
            song.data.isNotBlank() -> PlaybackTrackSnapshot(
                uri = song.data,
                title = song.title,
                artist = song.artist,
                album = song.album,
                albumArtUri = song.albumArtUri,
                durationMs = song.duration,
                songId = null
            )
            else -> null
        }
    }

    private fun resolveCurrentIndex(state: VisualizerPlaybackState): Int {
        if (state.queue.isEmpty()) return -1

        val directIndex = state.currentQueueIndex.takeIf { it in state.queue.indices }
        if (directIndex != null) return directIndex

        val songIndex = state.queue.indexOfFirst { it.id == state.currentSong?.id }
        return if (songIndex >= 0) songIndex else 0
    }

    private fun followEnvelope(
        current: Float,
        target: Float,
        decay: Float
    ): Float {
        return if (target >= current) {
            target
        } else {
            current + (target - current) * decay
        }
    }

}
