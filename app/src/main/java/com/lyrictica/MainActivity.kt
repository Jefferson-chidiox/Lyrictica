package com.lyrictica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyrictica.audio.PlaybackQueue
import com.lyrictica.theme.toColorScheme
import com.lyrictica.ui.GameLaunchPickerDialog
import com.lyrictica.ui.GameLaunchRequest
import com.lyrictica.ui.VisualizerScreen
import com.lyrictica.visualizer.VisualizerViewModel
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.Destination
import com.oss.euphoriae.EuphoriaeMainApp
import com.oss.euphoriae.data.preferences.AudioPreferences
import com.oss.euphoriae.data.preferences.DarkModeOption
import com.oss.euphoriae.data.preferences.ThemeColorOption
import com.oss.euphoriae.data.preferences.ThemePreferences
import com.oss.euphoriae.ui.theme.Typography
import com.oss.euphoriae.ui.viewmodel.MusicViewModel

private enum class RootScreen {
    VISUALIZER,
    LIBRARY
}

class MainActivity : ComponentActivity() {

    private val visualizerViewModel: VisualizerViewModel by viewModels()
    private val musicViewModel: MusicViewModel by viewModels()
    private val themePreferences by lazy { ThemePreferences(applicationContext) }
    private val audioPreferences by lazy { AudioPreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeColor by themePreferences.themeColor.collectAsStateWithLifecycle(
                initialValue = ThemeColorOption.DYNAMIC
            )
            val darkModeOption by themePreferences.darkMode.collectAsStateWithLifecycle(
                initialValue = DarkModeOption.SYSTEM
            )
            val visualizerPalette by visualizerViewModel.appTheme.collectAsStateWithLifecycle()

            val darkTheme = when (darkModeOption) {
                DarkModeOption.SYSTEM -> isSystemInDarkTheme()
                DarkModeOption.LIGHT -> false
                DarkModeOption.DARK -> true
            }

            LaunchedEffect(themeColor) {
                visualizerViewModel.setThemeColor(themeColor)
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as ComponentActivity).window
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            val appColorScheme = visualizerPalette.toColorScheme(darkTheme)

            MaterialTheme(
                colorScheme = appColorScheme,
                typography = Typography
            ) {
                LyricticaApp(
                    visualizerViewModel = visualizerViewModel,
                    musicViewModel = musicViewModel,
                    themePreferences = themePreferences,
                    audioPreferences = audioPreferences,
                    currentThemeColor = themeColor,
                    currentDarkMode = darkModeOption
                )
            }
        }
    }
}

@Composable
private fun LyricticaApp(
    visualizerViewModel: VisualizerViewModel,
    musicViewModel: MusicViewModel,
    themePreferences: ThemePreferences,
    audioPreferences: AudioPreferences,
    currentThemeColor: ThemeColorOption,
    currentDarkMode: DarkModeOption
) {
    val playbackQueueState by visualizerViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val visualizerPlaybackState by visualizerViewModel.playbackState.collectAsStateWithLifecycle()
    val registeredQueues by visualizerViewModel.queueLibrary.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf(RootScreen.LIBRARY) }
    var selectedGameSong by remember { mutableStateOf<Song?>(null) }
    var pendingGameLaunch by remember { mutableStateOf<GameLaunchRequest?>(null) }
    var pendingLibraryDestination by remember { mutableStateOf<Destination?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, visualizerViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                visualizerViewModel.savePlaybackSession()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val miniPlayerSong = playbackQueueState.currentSong ?: if (visualizerPlaybackState.trackName != "No track selected") {
                Song(
                    id = 0,
                    title = visualizerPlaybackState.trackName,
                    duration = visualizerPlaybackState.duration
                )
            } else {
                null
            }
            val miniPlayerProgress = if (visualizerPlaybackState.duration > 0) {
                visualizerPlaybackState.currentPosition.toFloat() / visualizerPlaybackState.duration.toFloat()
            } else {
                0f
            }
            val currentPlayingQueue = playbackQueueState.queueSource?.let { source ->
                registeredQueues.firstOrNull { it.key == source.key }
                    ?: PlaybackQueue(source = source, songs = playbackQueueState.queue)
            }

            EuphoriaeMainApp(
                viewModel = musicViewModel,
                themePreferences = themePreferences,
                audioPreferences = audioPreferences,
                currentThemeColor = currentThemeColor,
                currentDarkMode = currentDarkMode,
                initialDestination = Destination.HOME,
                requestedDestination = pendingLibraryDestination,
                onRequestedDestinationHandled = { pendingLibraryDestination = null },
                onSongClick = { song, queue ->
                    visualizerViewModel.playSong(song, queue)
                    currentScreen = RootScreen.VISUALIZER
                },
                currentPlayingSong = miniPlayerSong,
                currentPlayingQueue = currentPlayingQueue,
                currentPlayingQueueIndex = playbackQueueState.currentQueueIndex,
                registeredQueues = registeredQueues,
                currentPlayingIsPlaying = visualizerPlaybackState.isPlaying,
                currentPlayingProgress = miniPlayerProgress,
                onMiniPlayerPlayPause = { visualizerViewModel.togglePlayPause() },
                onMiniPlayerSkipNext = { visualizerViewModel.playNext() },
                onMiniPlayerClick = { currentScreen = RootScreen.VISUALIZER },
                onOpenVisualizer = { currentScreen = RootScreen.VISUALIZER },
                onPreviewMusixmatchResult = { result ->
                    visualizerViewModel.showLyricsPreview(result)
                    currentScreen = RootScreen.VISUALIZER
                },
                onOpenGameSong = { song ->
                    selectedGameSong = song
                },
                onScanClick = { musicViewModel.scanMusic() },
                onAddToFavorites = { musicViewModel.addToFavorites(it) },
                onDeleteSong = { song ->
                    musicViewModel.deleteSong(song)
                    visualizerViewModel.removeSongFromQueues(song.id)
                },
                onRemoveFromQueue = { queueKey, songId -> visualizerViewModel.removeSongFromQueue(queueKey, songId) },
                onDeleteQueue = { key -> visualizerViewModel.deleteQueue(key) },
                onRegisterQueue = { visualizerViewModel.registerQueue(it) },
                onReorderQueue = { key, fromIndex, toIndex -> visualizerViewModel.reorderQueue(key, fromIndex, toIndex) }
            )

            AnimatedVisibility(
                visible = currentScreen == RootScreen.VISUALIZER,
                modifier = Modifier.fillMaxSize(),
                enter = expandVertically(
                    animationSpec = tween(280),
                    expandFrom = Alignment.Bottom
                ) + fadeIn(animationSpec = tween(220)),
                exit = shrinkVertically(
                    animationSpec = tween(280),
                    shrinkTowards = Alignment.Bottom
                ) + fadeOut(animationSpec = tween(180))
            ) {
                VisualizerScreen(
                    viewModel = visualizerViewModel,
                    gameLaunchRequest = pendingGameLaunch,
                    onGameLaunchConsumed = { pendingGameLaunch = null },
                    onMenuClick = { currentScreen = RootScreen.LIBRARY },
                    onOpenGamesMenu = {
                        pendingLibraryDestination = Destination.GAMES
                        currentScreen = RootScreen.LIBRARY
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (selectedGameSong != null) {
                val targetSong = selectedGameSong!!
                GameLaunchPickerDialog(
                    song = targetSong,
                    onDismiss = { selectedGameSong = null },
                    onSelectMode = { mode ->
                        pendingGameLaunch = GameLaunchRequest(
                            songId = targetSong.id,
                            mode = mode
                        )
                        visualizerViewModel.playSong(
                            targetSong,
                            PlaybackQueue.custom(
                                key = "games:${mode.name.lowercase()}:${targetSong.id}",
                                label = targetSong.title,
                                songs = listOf(targetSong)
                            )
                        )
                        currentScreen = RootScreen.VISUALIZER
                        selectedGameSong = null
                    }
                )
            }
        }
    }
}
