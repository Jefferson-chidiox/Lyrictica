package com.lyrictica.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lyrictica.karaoke.GameModeOption
import com.lyrictica.visualizer.VisualizerPalette
import com.lyrictica.visualizer.VisualizerViewModel
import com.lyrictica.visualizer.WaveVisualizer
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.search.SearchAvailability

private const val VISUALIZER_AMBIENT_IDLE_DELAY_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VisualizerScreen(
    viewModel: VisualizerViewModel,
    gameLaunchRequest: GameLaunchRequest? = null,
    onGameLaunchConsumed: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onOpenGamesMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val nowPlayingState by viewModel.nowPlayingState.collectAsState()
    val analysisStatus by viewModel.analysisStatus.collectAsState()
    val lyricsUiState by viewModel.lyricsUiState.collectAsState()
    val karaokeState by viewModel.karaokeUiState.collectAsState()
    val availableLyrics by viewModel.availableLyrics.collectAsState()
    val translationState by viewModel.translationState.collectAsState()
    val smoothedFeatures by viewModel.smoothedFeatures.collectAsState()
    val screenTheme by viewModel.screenTheme.collectAsState()
    val context = LocalContext.current
    val headphonesConnected = rememberHeadphoneConnectionState()
    val gesturePreferences = remember(context) {
        com.lyrictica.visualizer.VisualizerGesturePreferences(context.applicationContext)
    }
    var hasMicPermission by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val karaokeMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.clearKaraokeMessage()
            viewModel.startKaraokeSession(challengeMode = true)
        } else {
            viewModel.onKaraokePermissionDenied()
        }
    }

    var showGestureTutorial by rememberSaveable { mutableStateOf(false) }
    var showVideoPanel by remember { mutableStateOf(false) }
    var showGameDialog by rememberSaveable { mutableStateOf(false) }
    var showGamePanel by rememberSaveable { mutableStateOf(false) }
    var selectedGameMode by rememberSaveable { mutableStateOf<GameModeOption?>(null) }
    var showQueuePanel by rememberSaveable { mutableStateOf(false) }
    var lyricsTooltipAnchor by remember { mutableStateOf<Rect?>(null) }
    var showLyricsTooltip by remember { mutableStateOf(false) }
    var hasPromptedGestureTutorialThisSession by rememberSaveable {
        mutableStateOf(gesturePreferences.hasSeenGestureTutorial())
    }
    var seekVisualState by remember { mutableStateOf<SeekVisualState?>(null) }
    var ambientModeActive by remember { mutableStateOf(false) }
    var lastUiInteractionAtMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    val musixmatchArtistState by viewModel.musixmatchArtistState.collectAsState()

    val activeSong = nowPlayingState.currentSong
    val titleText = when {
        musixmatchArtistState.isVisible && musixmatchArtistState.isLoading -> "Loading Artist..."
        musixmatchArtistState.isVisible && musixmatchArtistState.artistName != null -> musixmatchArtistState.artistName!!
        musixmatchArtistState.isVisible && musixmatchArtistState.error != null -> "Error"
        else -> activeSong?.title ?: playbackState.trackName
    }
    val artistText = when {
        musixmatchArtistState.isVisible && musixmatchArtistState.isLoading -> "Fetching metadata from Musixmatch..."
        musixmatchArtistState.isVisible && musixmatchArtistState.metadataText != null -> musixmatchArtistState.metadataText!!
        musixmatchArtistState.isVisible && musixmatchArtistState.error != null -> musixmatchArtistState.error!!
        else -> activeSong?.artist?.takeIf { it.isNotBlank() }
    }
    val albumText = if (musixmatchArtistState.isVisible) null else activeSong?.album?.takeIf { it.isNotBlank() }
    val resolvedLyrics = availableLyrics ?: lyricsUiState.parsed
    val hasAvailableLyrics = resolvedLyrics != null
    val lyricsPanelVisible = lyricsUiState.lyricsVisible && (lyricsUiState.isLoading || hasAvailableLyrics)
    val isSongLoaded = activeSong != null || playbackState.trackName != "No track selected"
    val canSwitchTracks = nowPlayingState.queue.size > 1
    val reverseBeatFullscreen = showGamePanel && selectedGameMode == GameModeOption.REVERSE_BEAT
    val ambientModeEligible = isSongLoaded && playbackState.isPlaying && !playbackState.ended && !showVideoPanel && !showGamePanel && !showQueuePanel && !showGameDialog && !musixmatchArtistState.isVisible && !showGestureTutorial && seekVisualState == null

    fun registerUiInteraction() {
        lastUiInteractionAtMs = SystemClock.elapsedRealtime()
        ambientModeActive = false
        showLyricsTooltip = false
    }

    fun closeVideoPanel() {
        registerUiInteraction()
        showVideoPanel = false
    }

    fun closeGamePanel() {
        registerUiInteraction()
        showGamePanel = false
        when (selectedGameMode) {
            GameModeOption.KARAOKE -> viewModel.exitKaraokeSession(pausePlayback = true)
            GameModeOption.REVERSE_BEAT -> viewModel.pausePlayback()
            else -> viewModel.pausePlayback()
        }
    }

    fun enterVideoMode() {
        registerUiInteraction()
        showGameDialog = false
        if (musixmatchArtistState.isVisible) {
            viewModel.toggleMusixmatchArtistInfo()
        }
        showQueuePanel = false
        if (showGamePanel) {
            closeGamePanel()
        }
        viewModel.pausePlayback()
        if (!showVideoPanel) {
            showVideoPanel = true
        }
    }

    fun enterGameMode(option: GameModeOption) {
        registerUiInteraction()
        selectedGameMode = option
        showGameDialog = false
        if (musixmatchArtistState.isVisible) {
            viewModel.toggleMusixmatchArtistInfo()
        }
        showQueuePanel = false
        if (showVideoPanel) {
            closeVideoPanel()
        }
        if (!showGamePanel) {
            showGamePanel = true
        }
        viewModel.pausePlayback()
        viewModel.clearKaraokeMessage()
    }

    fun toggleLyricsOverlay() {
        registerUiInteraction()
        showGameDialog = false
        if (musixmatchArtistState.isVisible) {
            viewModel.toggleMusixmatchArtistInfo()
        }
        showQueuePanel = false
        viewModel.toggleLyricsVisibility()
    }

    LaunchedEffect(gameLaunchRequest?.nonce, activeSong?.id) {
        val request = gameLaunchRequest ?: return@LaunchedEffect
        val song = activeSong ?: return@LaunchedEffect
        if (request.songId > 0L && song.id != request.songId) return@LaunchedEffect
        enterGameMode(request.mode)
        onGameLaunchConsumed()
    }

    LaunchedEffect(isSongLoaded, hasPromptedGestureTutorialThisSession, showGamePanel, gameLaunchRequest?.nonce) {
        if (gameLaunchRequest != null || showGamePanel) return@LaunchedEffect
        if (isSongLoaded && !hasPromptedGestureTutorialThisSession && !showGestureTutorial) {
            hasPromptedGestureTutorialThisSession = true
            showGestureTutorial = true
        }
    }

    BackHandler {
        when {
            showGestureTutorial -> {
                // Intentionally consume back while the tutorial is visible.
            }
            showGameDialog -> showGameDialog = false
            showQueuePanel -> showQueuePanel = false
            showGamePanel -> closeGamePanel()
            showVideoPanel -> closeVideoPanel()
            else -> {
                registerUiInteraction()
                onMenuClick()
            }
        }
    }

    LaunchedEffect(headphonesConnected) {
        viewModel.setKaraokeHeadphonesConnected(headphonesConnected)
    }

    LaunchedEffect(activeSong?.id) {
        seekVisualState = null
        ambientModeActive = false
        lastUiInteractionAtMs = SystemClock.elapsedRealtime()
    }

    LaunchedEffect(ambientModeEligible, activeSong?.id) {
        if (ambientModeEligible) {
            ambientModeActive = false
            lastUiInteractionAtMs = SystemClock.elapsedRealtime()
        } else {
            ambientModeActive = false
        }
    }

    LaunchedEffect(ambientModeEligible, lastUiInteractionAtMs, activeSong?.id) {
        if (!ambientModeEligible) return@LaunchedEffect
        delay(VISUALIZER_AMBIENT_IDLE_DELAY_MS)
        if (ambientModeEligible && SystemClock.elapsedRealtime() - lastUiInteractionAtMs >= VISUALIZER_AMBIENT_IDLE_DELAY_MS) {
            showLyricsTooltip = false
            ambientModeActive = true
        }
    }

    LaunchedEffect(showVideoPanel) {
        viewModel.setInlineVideoVisible(showVideoPanel)
    }

    LaunchedEffect(isSongLoaded, showVideoPanel, showGamePanel, showGameDialog, musixmatchArtistState.isVisible, showGestureTutorial, playbackState.isPlaying, lyricsPanelVisible, lyricsUiState.isLoading, resolvedLyrics, activeSong?.id, showQueuePanel, ambientModeActive) {
        showLyricsTooltip = false

        val lyricsAreAvailableOrLoading = lyricsUiState.isLoading || hasAvailableLyrics
        val shouldShowTooltip = isSongLoaded && lyricsAreAvailableOrLoading && !showVideoPanel && !showGamePanel && !showQueuePanel && !showGameDialog && !musixmatchArtistState.isVisible && !showGestureTutorial && playbackState.isPlaying && !lyricsPanelVisible && !ambientModeActive
        if (!shouldShowTooltip) return@LaunchedEffect

        while (isActive && isSongLoaded && lyricsAreAvailableOrLoading && !showVideoPanel && !showGamePanel && !showQueuePanel && !showGameDialog && !musixmatchArtistState.isVisible && !showGestureTutorial && playbackState.isPlaying && !lyricsPanelVisible && !ambientModeActive) {
            delay(kotlin.random.Random.nextLong(18_000L, 42_000L))
            if (!isActive || showVideoPanel || showGamePanel || showQueuePanel || showGameDialog || musixmatchArtistState.isVisible || showGestureTutorial || !playbackState.isPlaying || lyricsPanelVisible || ambientModeActive) break

            showLyricsTooltip = true
            try {
                delay(Random.nextLong(2_500L, 4_500L))
            } finally {
                showLyricsTooltip = false
            }
        }
    }

    val controlTint = screenTheme.controlText.copy(alpha = 0.72f)
    val mutedTint = screenTheme.mutedText.copy(alpha = 0.40f)
    val lyricsBackdropActive = playbackState.trackName != "No track selected" && 
        (lyricsPanelVisible || (showGamePanel && selectedGameMode == GameModeOption.KARAOKE)) && 
        !showVideoPanel && !showQueuePanel && !reverseBeatFullscreen
    val accent = screenTheme.sliderActiveTrack
    val topContentHorizontalPadding = if (showVideoPanel || showGamePanel) 14.dp else 20.dp
    val displayedTimelinePosition by animateFloatAsState(
        targetValue = (seekVisualState?.positionMs ?: playbackState.currentPosition).toFloat(),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 90),
        label = "timelinePosition"
    )
    val seekSpeedLabel = seekVisualState?.let {
        val direction = if (it.direction == SeekDirection.BACKWARD) "REWIND" else "FAST SEEK"
        "$direction ${"%.1f".format(it.speedMultiplier)}x"
    }
    val surfaceFocusAlpha by animateFloatAsState(
        targetValue = when {
            showVideoPanel || showGamePanel -> 0.45f
            seekVisualState != null -> 1f
            else -> 0f
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 160),
        label = "surfaceFocusAlpha"
    )
    val topCardBorder = Color.White.copy(alpha = 0.08f + (0.18f * surfaceFocusAlpha))
    val topCardShadow = (6f * surfaceFocusAlpha).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(screenTheme.backgroundTop, screenTheme.backgroundBottom)
                    ),
                    size = size
                )

                val glowRadius = minOf(size.width, size.height) * 1.05f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(screenTheme.ambientGlow.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.20f),
                        radius = glowRadius
                    ),
                    size = size
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(screenTheme.ambientGlow.copy(alpha = 0.06f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.88f),
                        radius = size.height * 0.76f
                    ),
                    size = size
                )
            }
    ) {
        MusicArtworkBackdrop(
            artworkUri = if (showVideoPanel || reverseBeatFullscreen || (!showVideoPanel && !showGamePanel)) {
                null
            } else {
                if (musixmatchArtistState.isVisible) musixmatchArtistState.imageUrl else activeSong?.albumArtUri
            },
            theme = screenTheme,
            isLyricsBackdropActive = lyricsBackdropActive,
            modifier = Modifier.fillMaxSize()
        )

        WaveVisualizer(
            features = smoothedFeatures,
            theme = screenTheme,
            modifier = Modifier.fillMaxSize()
        )

        InvisibleWaveGestureOverlay(
            theme = screenTheme,
            isEnabled = isSongLoaded && !showVideoPanel && !showGamePanel && !ambientModeActive,
            currentPositionMs = playbackState.currentPosition,
            durationMs = playbackState.duration,
            onPrevious = {
                registerUiInteraction()
                viewModel.playPrevious()
            },
            onTogglePlayPause = {
                registerUiInteraction()
                viewModel.togglePlayPause()
            },
            onNext = {
                registerUiInteraction()
                viewModel.playNext()
            },
            onSeekTo = {
                registerUiInteraction()
                viewModel.seekTo(it)
            },
            onSeekVisualStateChange = {
                registerUiInteraction()
                seekVisualState = it
            },
            modifier = Modifier.fillMaxSize()
        )

        // Removed top-level QueuePanel overlay

        if (showGamePanel && selectedGameMode == GameModeOption.KARAOKE) {
            val isSessionActive = karaokeState.isSessionActive
            KaraokeGameSurface(
                theme = screenTheme,
                availableLyrics = availableLyrics,
                karaokeState = karaokeState,
                onChallengeToggle = { viewModel.setKaraokeChallengeEnabled(it) },
                onChallengeProfileSelected = { viewModel.setKaraokeChallengeProfile(it) },
                onStartKaraoke = {
                    if (karaokeState.challengeEnabled) {
                        hasMicPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMicPermission) {
                            viewModel.startKaraokeSession(challengeMode = true)
                        } else {
                            karaokeMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        viewModel.startKaraokeSession(challengeMode = false)
                    }
                },
                onExitGameMode = { closeGamePanel() },
                onDismissMessage = { viewModel.clearKaraokeMessage() },
                modifier = Modifier
                    .align(if (isSessionActive) Alignment.TopCenter else Alignment.Center)
                    .fillMaxWidth()
                    .padding(
                        top = if (isSessionActive) 146.dp else 0.dp,
                        start = 18.dp,
                        end = 18.dp,
                        bottom = if (isSessionActive) 0.dp else 40.dp
                    )
            )
        }

        if (showGamePanel && selectedGameMode == GameModeOption.REVERSE_BEAT) {
            val nextSongIndex = if (nowPlayingState.queue.isNotEmpty()) {
                (nowPlayingState.currentQueueIndex + 1) % nowPlayingState.queue.size
            } else {
                -1
            }
            val nextSong = if (canSwitchTracks && nextSongIndex in nowPlayingState.queue.indices) {
                nowPlayingState.queue[nextSongIndex]
            } else {
                null
            }

            ReverseBeatGameSurface(
                theme = screenTheme,
                songLoaded = activeSong != null,
                songInstanceKey = "${nowPlayingState.currentQueueIndex}:${activeSong?.id ?: -1L}:${activeSong?.data ?: titleText}",
                songSeed = (activeSong?.id?.toInt() ?: titleText.hashCode()),
                trackTitle = titleText,
                artistText = artistText,
                songSource = activeSong?.data?.takeIf { it.isNotBlank() },
                availableLyrics = availableLyrics,
                lyricsLoading = lyricsUiState.isLoading,
                playbackPositionMs = playbackState.currentPosition,
                playbackIsPlaying = playbackState.isPlaying,
                playbackEnded = playbackState.ended,
                features = smoothedFeatures,
                onStartRun = {
                    viewModel.seekTo(0L)
                    viewModel.resumePlayback()
                },
                onPauseRun = { viewModel.pausePlayback() },
                onResumeRun = { viewModel.resumePlayback() },
                onRestartRun = {
                    viewModel.seekTo(0L)
                    viewModel.resumePlayback()
                },
                onRunFinished = { score ->
                    viewModel.recordGameScore(GameModeOption.REVERSE_BEAT, score)
                },
                canAdvanceToNextSong = canSwitchTracks,
                nextSongTitle = nextSong?.title,
                nextSongArtist = nextSong?.artist,
                onPlayNextSong = {
                    registerUiInteraction()
                    viewModel.selectVideoModeNextTrack()
                },
                onExitGameMode = { closeGamePanel() },
                onOpenGamesMenu = {
                    closeGamePanel()
                    onOpenGamesMenu()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!reverseBeatFullscreen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .then(
                    if (showVideoPanel || showGamePanel) {
                        Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(screenTheme.topScrimStart, screenTheme.topScrimEnd)
                            )
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(top = 48.dp, start = topContentHorizontalPadding, end = topContentHorizontalPadding, bottom = 20.dp)
        ) {
            playbackState.error?.let { errorMsg ->
                GlassBanner(
                    text = errorMsg,
                    textColor = Color(0xFFFEE2E2),
                    borderColor = Color(0x80EF4444),
                    fillColor = Color(0x807F1D1D)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            analysisStatus.error?.let { errorMsg ->
                GlassBanner(
                    text = errorMsg,
                    textColor = Color(0xFFFEF3C7),
                    borderColor = Color(0x80F59E0B),
                    fillColor = Color(0x805A3D0C)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (!showVideoPanel && !showGamePanel) {
                if (!ambientModeActive) {
                MinimalVisualizerPlaybackChrome(
                    titleText = titleText,
                    artistText = artistText,
                    artworkUri = if (musixmatchArtistState.isVisible) musixmatchArtistState.imageUrl else activeSong?.albumArtUri,
                    activeSong = activeSong,
                    lyricsState = lyricsUiState,
                    lyricsVisible = lyricsPanelVisible,
                    hasAvailableLyrics = hasAvailableLyrics,
                    playbackIsLoading = playbackState.isLoading,
                    isSongLoaded = isSongLoaded,
                    isPlaying = playbackState.isPlaying,
                    isShuffleOn = nowPlayingState.isShuffleOn,
                    repeatMode = nowPlayingState.repeatMode,
                    durationMs = playbackState.duration,
                    displayedTimelinePosition = displayedTimelinePosition,
                    seekSpeedLabel = seekSpeedLabel,
                    theme = screenTheme,
                    accent = accent,
                    controlTint = controlTint,
                    gameSelected = showGameDialog || showGamePanel,
                    aboutSelected = musixmatchArtistState.isVisible,
                    musixmatchArtistState = musixmatchArtistState,
                    translationState = translationState,
                    onTranslationLanguageSelected = {
                        registerUiInteraction()
                        viewModel.setTranslationLanguage(it)
                    },
                    onLyricsToggle = { toggleLyricsOverlay() },
                    onLyricsAnchorChanged = { lyricsTooltipAnchor = it },
                    onAutoFollowChange = {
                        registerUiInteraction()
                        viewModel.setLyricsAutoFollow(it)
                    },
                    seekPreviewPositionMs = seekVisualState?.positionMs,
                    isSeeking = seekVisualState != null,
                    onMenuClick = {
                        registerUiInteraction()
                        onMenuClick()
                    },
                    onSeekTo = {
                        registerUiInteraction()
                        viewModel.seekTo(it)
                    },
                    onPrevious = {
                        registerUiInteraction()
                        viewModel.playPrevious()
                    },
                    onPlayPause = {
                        registerUiInteraction()
                        viewModel.togglePlayPause()
                    },
                    onNext = {
                        registerUiInteraction()
                        viewModel.playNext()
                    },
                    onShuffleToggle = {
                        registerUiInteraction()
                        viewModel.toggleShuffle()
                    },
                    onRepeatToggle = {
                        registerUiInteraction()
                        viewModel.toggleRepeat()
                    },
                    onVideoClick = { enterVideoMode() },
                    onGameClick = {
                        if (musixmatchArtistState.isVisible) { viewModel.toggleMusixmatchArtistInfo() }
                        viewModel.pausePlayback()
                        showGameDialog = true
                    },
                    onAboutClick = {
                        registerUiInteraction()
                        showGameDialog = false
                        viewModel.toggleMusixmatchArtistInfo()
                    },
                    modifier = Modifier.weight(1f, fill = true)
                )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.03f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, topCardBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = topCardShadow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (showVideoPanel) 12.dp else 14.dp, vertical = if (showVideoPanel) 10.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                when {
                                    showVideoPanel -> closeVideoPanel()
                                    else -> closeGamePanel()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = when {
                                    showVideoPanel -> "Back to player"
                                    else -> "Exit game mode"
                                },
                                tint = if (showVideoPanel || showGamePanel) screenTheme.controlText else controlTint,
                                modifier = Modifier
                                    .size(if (showVideoPanel) 28.dp else 26.dp)
                                    .rotate(90f)
                            )
                        }

                        Spacer(modifier = Modifier.width(if (showVideoPanel) 12.dp else 14.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(if (showVideoPanel) 3.dp else 0.dp)
                        ) {
                            if (showVideoPanel) {
                                Text(
                                    text = "VIDEO MODE",
                                    color = accent.copy(alpha = 0.92f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                    maxLines = 1
                                )

                                Text(
                                    text = titleText,
                                    color = screenTheme.controlText.copy(alpha = 0.94f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = artistText ?: "Switch tracks without leaving the player",
                                    color = screenTheme.controlText.copy(alpha = 0.58f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "GAME MODE",
                                    color = accent.copy(alpha = 0.92f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                    maxLines = 1
                                )

                                Text(
                                    text = titleText,
                                    color = screenTheme.controlText.copy(alpha = 0.94f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = artistText ?: "Return to playback when you're ready",
                                    color = screenTheme.controlText.copy(alpha = 0.58f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (showVideoPanel) {
                            Spacer(modifier = Modifier.width(10.dp))

                            FilledTonalIconButton(
                                onClick = { viewModel.selectVideoModePreviousTrack() },
                                enabled = canSwitchTracks,
                                modifier = Modifier.size(40.dp),
                                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = screenTheme.controlText,
                                    disabledContainerColor = Color.White.copy(alpha = 0.04f),
                                    disabledContentColor = screenTheme.controlText.copy(alpha = 0.28f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous track",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FilledTonalIconButton(
                                onClick = { viewModel.selectVideoModeNextTrack() },
                                enabled = canSwitchTracks,
                                modifier = Modifier.size(40.dp),
                                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = screenTheme.controlText,
                                    disabledContainerColor = Color.White.copy(alpha = 0.04f),
                                    disabledContentColor = screenTheme.controlText.copy(alpha = 0.28f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next track",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Spacer(modifier = Modifier.width(14.dp))
                        }

                        IconButton(
                            onClick = {
                                if (showVideoPanel) {
                                    closeVideoPanel()
                                } else {
                                    registerUiInteraction()
                                }
                                onMenuClick()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open songs list",
                                tint = controlTint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .rotate(90f)
                            )
                        }
                    }
                }


            }


            if (!ambientModeActive && showLyricsTooltip && lyricsTooltipAnchor != null) {
                val anchor = lyricsTooltipAnchor!!
                val tooltipWidthDp = 164.dp
                val tooltipWidthPx = with(LocalDensity.current) { tooltipWidthDp.roundToPx() }
                val horizontalPaddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
                val verticalSpacingPx = with(LocalDensity.current) { 8.dp.roundToPx() }
                val offsetX = (anchor.right.roundToInt() - tooltipWidthPx).coerceAtLeast(horizontalPaddingPx)
                val offsetY = anchor.bottom.roundToInt() + verticalSpacingPx

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(offsetX, offsetY),
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                        clippingEnabled = false
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.80f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp
                    ) {
                        Text(
                            text = "tap for lyrics",
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (showVideoPanel) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White.copy(alpha = 0.035f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, topCardBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = topCardShadow + 4.dp
                ) {
                    YouTubeVideoPanel(
                        song = activeSong,
                        theme = screenTheme,
                        onPlaybackChanged = { isPlaying, positionMs, durationMs ->
                            viewModel.onInlineVideoPlaybackChanged(
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                durationMs = durationMs
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (analysisStatus.isBusy && playbackState.trackName != "No track selected") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "analyzing audio",
                    color = mutedTint,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Light
                )
            }

            if (showGestureTutorial) {
                GestureTutorialDialog(
                    theme = screenTheme,
                    isPlaying = playbackState.isPlaying,
                    onDontShowAgain = {
                        gesturePreferences.markGestureTutorialSeen()
                        showGestureTutorial = false
                    }
                )
            }


            if (showGameDialog) {
                GameModeDialog(
                    theme = screenTheme,
                    selectedGame = selectedGameMode,
                    onDismiss = { showGameDialog = false },
                    onSelectGame = { enterGameMode(it) }
                )
            }
        }
        }

        if (ambientModeActive) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (lyricsPanelVisible) {
                    LyricsOverlay(
                        lyricsState = if (lyricsUiState.parsed == null && resolvedLyrics != null) {
                            lyricsUiState.copy(parsed = resolvedLyrics)
                        } else {
                            lyricsUiState
                        },
                        theme = screenTheme,
                        onAutoFollowChange = {},
                        onLineClick = null,
                        visibleLines = 5,
                        maxPanelHeightFraction = 0.84f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(ambientModeActive) {
                            detectTapGestures(onTap = { registerUiInteraction() })
                        }
                )
            }
        }

        if (seekVisualState != null) {
            SeekFeedbackHud(
                seekVisualState = seekVisualState,
                theme = screenTheme,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalVisualizerPlaybackChrome(
    titleText: String,
    artistText: String?,
    artworkUri: String?,
    activeSong: Song?,
    lyricsState: com.lyrictica.lyrics.LyricsUiState,
    lyricsVisible: Boolean,
    hasAvailableLyrics: Boolean,
    playbackIsLoading: Boolean,
    isSongLoaded: Boolean,
    isPlaying: Boolean,
    isShuffleOn: Boolean,
    repeatMode: Int,
    durationMs: Long,
    displayedTimelinePosition: Float,
    seekSpeedLabel: String?,
    theme: VisualizerPalette,
    accent: Color,
    controlTint: Color,
    gameSelected: Boolean,
    aboutSelected: Boolean,
    musixmatchArtistState: com.lyrictica.visualizer.MusixmatchArtistState,
    translationState: com.lyrictica.visualizer.TranslationState,
    onTranslationLanguageSelected: (String?) -> Unit,
    onLyricsToggle: () -> Unit,
    onLyricsAnchorChanged: (Rect) -> Unit,
    onAutoFollowChange: (Boolean) -> Unit,
    seekPreviewPositionMs: Long?,
    isSeeking: Boolean,
    onMenuClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onVideoClick: () -> Unit,
    onGameClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = if (lyricsVisible) {
            430.dp
        } else {
            (maxHeight - 320.dp).coerceIn(232.dp, 340.dp)
        }
        val lyricsStatusHintText = when {
            !isSongLoaded || lyricsVisible || lyricsState.isLoading -> null
            hasAvailableLyrics -> "Lyrics off"
            else -> "Lyrics unavailable"
        }
        val lyricsStatusHintKey = lyricsStatusHintText?.let { hint ->
            "$hint|${activeSong?.id ?: titleText}|${artistText.orEmpty()}"
        }
        var showLyricsStatusHint by remember { mutableStateOf(false) }
        LaunchedEffect(lyricsStatusHintKey) {
            showLyricsStatusHint = false
            if (lyricsStatusHintKey == null) return@LaunchedEffect
            showLyricsStatusHint = true
            delay(2400)
            showLyricsStatusHint = false
        }
        val lyricsStatusHintAlpha by animateFloatAsState(
            targetValue = if (showLyricsStatusHint && lyricsStatusHintText != null) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
            label = "lyricsStatusHintAlpha"
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (aboutSelected) {
                    AboutMetadataPanel(
                        artistState = musixmatchArtistState,
                        theme = theme,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SongArtwork(
                        artworkUri = artworkUri,
                        dimmed = lyricsVisible,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                FilledTonalIconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(38.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.24f),
                        contentColor = Color.White.copy(alpha = 0.92f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Close visualizer",
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (lyricsVisible) {
                    LyricsOverlay(
                        lyricsState = lyricsState,
                        theme = theme,
                        onAutoFollowChange = onAutoFollowChange,
                        onLineClick = { timeMs -> onSeekTo(timeMs) },
                        seekPreviewPositionMs = seekPreviewPositionMs,
                        isSeeking = isSeeking,
                        visibleLines = 5,
                        topReservedHeight = 32.dp,
                        bottomReservedHeight = 24.dp,
                        maxPanelHeightFraction = 0.92f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (lyricsStatusHintText != null && lyricsStatusHintAlpha > 0.01f) {
                    Text(
                        text = lyricsStatusHintText,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .graphicsLayer(alpha = lyricsStatusHintAlpha)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.24f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                if (activeSong != null) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FilledTonalIconButton(
                            onClick = onLyricsToggle,
                            modifier = Modifier
                                .size(38.dp)
                                .onGloballyPositioned { coords ->
                                    onLyricsAnchorChanged(coords.boundsInWindow())
                                },
                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.24f),
                                contentColor = if (lyricsVisible) accent else Color.White.copy(alpha = 0.92f)
                            )
                        ) {
                            Icon(
                                imageVector = if (lyricsVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = if (lyricsVisible) "Hide lyrics overlay" else "Show lyrics overlay",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (lyricsVisible && hasAvailableLyrics) {
                            var showTranslationMenu by remember { mutableStateOf(false) }
                            Box {
                                FilledTonalIconButton(
                                    onClick = { showTranslationMenu = true },
                                    modifier = Modifier.size(38.dp),
                                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.24f),
                                        contentColor = if (translationState.languageCode != null) accent else Color.White.copy(alpha = 0.92f)
                                    )
                                ) {
                                    if (translationState.isLoading) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = accent,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        androidx.compose.material3.Text(
                                            text = "A/文",
                                            color = if (translationState.languageCode != null) accent else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                }

                                androidx.compose.material3.DropdownMenu(
                                    expanded = showTranslationMenu,
                                    onDismissRequest = { showTranslationMenu = false },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.85f))
                                ) {
                                    val languages = mapOf(
                                        null to "Original",
                                        "es" to "Spanish",
                                        "fr" to "French",
                                        "de" to "German",
                                        "it" to "Italian",
                                        "pt" to "Portuguese"
                                    )
                                    languages.forEach { (code, name) ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    name, 
                                                    color = if (translationState.languageCode == code) accent else Color.White,
                                                    fontSize = 14.sp
                                                ) 
                                            },
                                            onClick = {
                                                onTranslationLanguageSelected(code)
                                                showTranslationMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        color = Color.White.copy(alpha = 0.96f),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!artistText.isNullOrBlank()) {
                        Text(
                            text = artistText,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (playbackIsLoading) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = controlTint
                    )
                }
            }

            if (lyricsVisible) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.weight(0.68f, fill = true))
            }

            VisualizerNavRow(
                theme = theme,
                videoSelected = false,
                gameSelected = gameSelected,
                aboutSelected = aboutSelected,
                onMenuClick = onMenuClick,
                onVideoClick = onVideoClick,
                onGameClick = onGameClick,
                onAboutClick = onAboutClick,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            Slider(
                value = if (durationMs > 0L) displayedTimelinePosition else 0f,
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..(if (durationMs > 0L) durationMs.toFloat() else 100f),
                enabled = durationMs > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = theme.sliderActiveTrack,
                    inactiveTrackColor = theme.sliderInactiveTrack
                ),
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatGestureTime(displayedTimelinePosition.toLong()),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp
                )

                if (seekSpeedLabel != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = seekSpeedLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = accent.copy(alpha = 0.14f),
                            labelColor = accent
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = formatGestureTime(durationMs),
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.7.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShuffleToggle,
                    enabled = isSongLoaded,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = if (isShuffleOn) "Disable shuffle" else "Enable shuffle",
                        tint = when {
                            !isSongLoaded -> controlTint.copy(alpha = 0.28f)
                            isShuffleOn -> accent
                            else -> controlTint
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onPrevious,
                        enabled = isSongLoaded,
                        modifier = Modifier.size(54.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White.copy(alpha = 0.90f),
                            disabledContainerColor = Color.White.copy(alpha = 0.04f),
                            disabledContentColor = Color.White.copy(alpha = 0.28f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(22.dp))

                    FilledIconButton(
                        onClick = onPlayPause,
                        enabled = isSongLoaded,
                        modifier = Modifier.size(76.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = accent
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(22.dp))

                    FilledTonalIconButton(
                        onClick = onNext,
                        enabled = isSongLoaded,
                        modifier = Modifier.size(54.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White.copy(alpha = 0.90f),
                            disabledContainerColor = Color.White.copy(alpha = 0.04f),
                            disabledContentColor = Color.White.copy(alpha = 0.28f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRepeatToggle,
                    enabled = isSongLoaded,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = when (repeatMode) {
                            0 -> "Enable repeat"
                            1 -> "Switch to repeat one"
                            else -> "Disable repeat"
                        },
                        tint = when {
                            !isSongLoaded -> controlTint.copy(alpha = 0.28f)
                            repeatMode > 0 -> accent
                            else -> controlTint
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (!lyricsVisible) {
                Spacer(modifier = Modifier.weight(0.32f, fill = true))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GestureTransportStrip(
    theme: VisualizerPalette,
    isEnabled: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val densityFactor = LocalDensity.current.density
    val currentPositionState by rememberUpdatedState(currentPositionMs)
    val onSeekToState by rememberUpdatedState(onSeekTo)
    val accent = theme.sliderActiveTrack
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    var isScrubbing by remember { mutableStateOf(false) }
    var previewPositionMs by remember {
        mutableStateOf(currentPositionMs.coerceIn(0L, safeDurationMs))
    }

    LaunchedEffect(currentPositionMs, safeDurationMs, isScrubbing) {
        if (!isScrubbing) {
            previewPositionMs = currentPositionMs.coerceIn(0L, safeDurationMs)
        }
    }

    val displayedPositionMs = if (isScrubbing) previewPositionMs else currentPositionMs.coerceIn(0L, safeDurationMs)
    val progress = if (safeDurationMs > 0L) {
        displayedPositionMs.toFloat() / safeDurationMs.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)
    val borderColor = if (isScrubbing) accent.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.10f)
    val centerLabel = if (isPlaying) "PAUSE" else "PLAY"
    val centerHint = "TAP"

    Surface(
        modifier = modifier.pointerInput(isEnabled, safeDurationMs, densityFactor) {
            if (!isEnabled || safeDurationMs <= 0L) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    isScrubbing = true
                    previewPositionMs = currentPositionState.coerceIn(0L, safeDurationMs)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { _, dragAmount ->
                    previewPositionMs = gestureSeekPosition(
                        basePositionMs = previewPositionMs,
                        dragAmountPx = dragAmount.x,
                        durationMs = safeDurationMs,
                        density = densityFactor
                    )
                    onSeekToState(previewPositionMs)
                },
                onDragEnd = {
                    isScrubbing = false
                    previewPositionMs = currentPositionState.coerceIn(0L, safeDurationMs)
                },
                onDragCancel = {
                    isScrubbing = false
                    previewPositionMs = currentPositionState.coerceIn(0L, safeDurationMs)
                }
            )
        },
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GestureZone(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    theme = theme,
                    isEnabled = isEnabled,
                    icon = Icons.Default.SkipPrevious,
                    hint = "DOUBLE TAP",
                    label = "PREVIOUS",
                    isPrimary = false,
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPrevious()
                    }
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.08f))
                )

                GestureZone(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp),
                    theme = theme,
                    isEnabled = isEnabled,
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    hint = centerHint,
                    label = centerLabel,
                    isPrimary = true,
                    onTap = onTogglePlayPause
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.08f))
                )

                GestureZone(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 4.dp),
                    theme = theme,
                    isEnabled = isEnabled,
                    icon = Icons.Default.SkipNext,
                    hint = "DOUBLE TAP",
                    label = "NEXT",
                    isPrimary = false,
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNext()
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatGestureTime(displayedPositionMs),
                    color = theme.controlText.copy(alpha = 0.92f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = formatGestureTime(safeDurationMs),
                    color = theme.mutedText.copy(alpha = 0.82f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.4.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(theme.sliderInactiveTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(theme.sliderActiveTrack)
                )
            }

            Text(
                text = if (isScrubbing) "SEEKING" else "HOLD & DRAG TO SEEK",
                color = theme.mutedText.copy(alpha = if (isScrubbing) 0.96f else 0.72f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun GestureZone(
    modifier: Modifier = Modifier,
    theme: VisualizerPalette,
    isEnabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hint: String,
    label: String,
    isPrimary: Boolean,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null
) {
    val contentColor = if (isEnabled) theme.controlText else theme.mutedText.copy(alpha = 0.42f)
    val accentColor = theme.sliderActiveTrack
    val backgroundColor = when {
        !isEnabled -> Color.White.copy(alpha = 0.015f)
        isPrimary -> accentColor.copy(alpha = if (label == "PLAY" || label == "PAUSE") 0.16f else 0.12f)
        else -> Color.White.copy(alpha = 0.03f)
    }
    val borderColor = when {
        !isEnabled -> Color.White.copy(alpha = 0.05f)
        isPrimary -> accentColor.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .pointerInput(isEnabled, onTap, onDoubleTap) {
                if (!isEnabled || (onTap == null && onDoubleTap == null)) return@pointerInput
                detectTapGestures(
                    onTap = { onTap?.invoke() },
                    onDoubleTap = { onDoubleTap?.invoke() }
                )
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = hint,
                color = theme.mutedText.copy(alpha = if (isEnabled) 0.78f else 0.40f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(5.dp))
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isPrimary) accentColor else contentColor,
                modifier = Modifier.size(if (isPrimary) 34.dp else 24.dp)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                color = if (isPrimary) theme.controlText.copy(alpha = 0.98f) else contentColor,
                fontSize = if (isPrimary) 11.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatGestureTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}



@Composable
private fun MusicArtworkBackdrop(
    artworkUri: String?,
    theme: VisualizerPalette,
    isLyricsBackdropActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (artworkUri.isNullOrBlank()) return

    val context = LocalContext.current
    val fallbackBrush = Brush.verticalGradient(
        colors = listOf(
            theme.backgroundTop,
            theme.backgroundBottom
        )
    )
    val artworkAlpha by animateFloatAsState(
        targetValue = if (isLyricsBackdropActive) 0.5f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 320),
        label = "artworkBackdropAlpha"
    )
    val backdropDimAlpha by animateFloatAsState(
        targetValue = if (isLyricsBackdropActive) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 320),
        label = "artworkBackdropDim"
    )

    BoxWithConstraints(modifier = modifier) {
        val frameHeight = maxHeight * 0.52f

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(frameHeight)
                .clip(RoundedCornerShape(0.dp))
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = artworkAlpha,
                modifier = if (isLyricsBackdropActive) Modifier.fillMaxSize().blur(24.dp) else Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(fallbackBrush)
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(fallbackBrush)
                    )
                }
            )

            if (backdropDimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    theme.backgroundTop.copy(alpha = 0.20f * backdropDimAlpha),
                                    Color.Black.copy(alpha = 0.5f * backdropDimAlpha),
                                    theme.backgroundBottom.copy(alpha = 0.25f * backdropDimAlpha)
                                )
                            )
                        )
                        .drawBehind {
                            if (backdropDimAlpha <= 0f) return@drawBehind

                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        theme.ambientGlow.copy(alpha = 0.05f * backdropDimAlpha),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                                    radius = size.height * 0.78f
                                ),
                                size = size
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    fillColor: Color,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = fillColor,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}

@Composable
private fun GlassBanner(
    text: String,
    textColor: Color,
    borderColor: Color,
    fillColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(fillColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SongArtwork(
    artworkUri: String?,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (artworkUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(42.dp)
                    )
                }
            } else {
                SubcomposeAsyncImage(
                    model = artworkUri,
                    contentDescription = "Album art",
                    modifier = if (dimmed) Modifier.fillMaxSize().blur(16.dp) else Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.82f),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.82f),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                )
            }

            if (dimmed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutMetadataPanel(
    artistState: com.lyrictica.visualizer.MusixmatchArtistState,
    theme: VisualizerPalette,
    modifier: Modifier = Modifier
) {
    val accent = theme.sliderActiveTrack
    val track = artistState.trackRecord

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.14f),
                    accent.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.06f)
                )
            )
        ),
        shadowElevation = 20.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle gradient backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.06f),
                                Color.Transparent,
                                accent.copy(alpha = 0.03f)
                            )
                        )
                    )
            )

            if (artistState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = accent,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Fetching from Musixmatch…",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (artistState.error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.42f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = artistState.error,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // --- Artist Header ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Artist avatar
                        val avatarUrl = artistState.imageUrl
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = accent.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                accent.copy(alpha = 0.32f)
                            )
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                SubcomposeAsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Artist image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = accent
                                            )
                                        }
                                    },
                                    error = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.62f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.62f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artistState.artistName ?: "Unknown Artist",
                                color = Color.White.copy(alpha = 0.96f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!artistState.artistCountry.isNullOrBlank()) {
                                Text(
                                    text = artistState.artistCountry,
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        if (artistState.artistRating > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accent.copy(alpha = 0.16f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, accent.copy(alpha = 0.24f)
                                )
                            ) {
                                Text(
                                    text = "★ ${artistState.artistRating}",
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    if (!artistState.artistDescription.isNullOrBlank()) {
                        Text(
                            text = artistState.artistDescription,
                            color = Color.White.copy(alpha = 0.76f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // --- Divider ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.10f),
                                        accent.copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.10f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // --- Track Info ---
                    if (track != null) {
                        Text(
                            text = "TRACK INFO",
                            color = accent.copy(alpha = 0.72f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        )

                        // Track name & album
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = track.trackName,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.albumName.isNotBlank()) {
                                Text(
                                    text = track.albumName,
                                    color = Color.White.copy(alpha = 0.56f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Metadata chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Duration
                            if (track.durationSec > 0) {
                                MetadataChip(
                                    label = "Duration",
                                    value = formatDurationChip(track.durationSec),
                                    accent = accent
                                )
                            }

                            // Track Rating
                            if (track.trackRating > 0) {
                                MetadataChip(
                                    label = "Popularity",
                                    value = "${track.trackRating}/100",
                                    accent = accent
                                )
                            }

                            // Favourites
                            if (track.numFavourite > 0) {
                                MetadataChip(
                                    label = "Favourites",
                                    value = formatCompactNumber(track.numFavourite),
                                    accent = accent
                                )
                            }

                            // Explicit
                            if (track.explicit) {
                                MetadataChip(
                                    label = "Content",
                                    value = "Explicit",
                                    accent = Color(0xFFEF4444)
                                )
                            }

                            // Instrumental
                            if (track.instrumental) {
                                MetadataChip(
                                    label = "Type",
                                    value = "Instrumental",
                                    accent = accent
                                )
                            }

                            // Lyrics availability
                            if (track.hasLyrics || track.hasSubtitles || track.hasRichsync) {
                                val syncLevel = when {
                                    track.hasRichsync -> "Word-Sync"
                                    track.hasSubtitles -> "Line-Sync"
                                    else -> "Plain"
                                }
                                MetadataChip(
                                    label = "Lyrics",
                                    value = syncLevel,
                                    accent = accent
                                )
                            }
                        }

                        // Genres
                        if (track.genres.isNotEmpty()) {
                            Text(
                                text = "GENRES",
                                color = accent.copy(alpha = 0.72f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                track.genres.forEach { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.White.copy(alpha = 0.07f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp, Color.White.copy(alpha = 0.10f)
                                        )
                                    ) {
                                        Text(
                                            text = genre,
                                            color = Color.White.copy(alpha = 0.82f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 5.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- Twitter ---
                    if (!artistState.artistTwitterUrl.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "𝕏",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = artistState.artistTwitterUrl,
                                color = Color.White.copy(alpha = 0.54f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Powered-by footer
                    Text(
                        text = "Powered by Musixmatch",
                        color = Color.White.copy(alpha = 0.28f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataChip(
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.uppercase(),
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                maxLines = 1
            )
            Text(
                text = value,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun formatDurationChip(durationSec: Int): String {
    val minutes = durationSec / 60
    val seconds = durationSec % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatCompactNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> "%.1fM".format(num / 1_000_000.0)
        num >= 1_000 -> "%.1fK".format(num / 1_000.0)
        else -> num.toString()
    }
}

@Composable
private fun QueuePanel(
    queue: List<com.oss.euphoriae.data.model.Song>,
    currentIndex: Int,
    theme: com.lyrictica.visualizer.VisualizerPalette,
    onSongClick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Queue",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(queue.size) { index ->
                    val song = queue[index]
                    val isPlaying = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPlaying) theme.ambientGlow.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSongClick(index) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (isPlaying) theme.ambientGlow else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
                            )
                            if (song.artist.isNotBlank()) {
                                Text(
                                    text = song.artist,
                                    color = if (isPlaying) theme.ambientGlow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (isPlaying) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Playing",
                                tint = theme.ambientGlow,
                                modifier = Modifier.padding(start = 8.dp).size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
