package com.oss.euphoriae

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lyrictica.audio.PlaybackQueue
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.data.preferences.DarkModeOption
import com.oss.euphoriae.data.preferences.ThemeColorOption
import com.oss.euphoriae.data.preferences.ThemePreferences
import com.oss.euphoriae.data.preferences.AudioPreferences
import com.oss.euphoriae.ui.components.MiniPlayer
import com.oss.euphoriae.ui.screens.EqualizerScreen
import com.oss.euphoriae.ui.screens.ExploreResultsScreen
import com.oss.euphoriae.ui.screens.ExploreScreen
import com.oss.euphoriae.ui.screens.GamesScreen
import com.oss.euphoriae.ui.screens.HomeScreen
import com.oss.euphoriae.ui.screens.NowPlayingScreen
import com.oss.euphoriae.ui.screens.PlaylistDetailScreen
import com.oss.euphoriae.ui.screens.PlaylistScreen
import com.oss.euphoriae.ui.screens.OnlinePlaylistDetailScreen
import com.oss.euphoriae.ui.screens.QueueDetailScreen
import com.oss.euphoriae.ui.screens.SettingsScreen
import com.oss.euphoriae.ui.screens.SongsScreen
import com.oss.euphoriae.ui.theme.EuphoriaeTheme
import com.oss.euphoriae.ui.viewmodel.MusicViewModel
import com.oss.euphoriae.engine.AudioEngine
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.toSong
import com.oss.euphoriae.data.model.AudiusPlaylist
import com.oss.euphoriae.search.MusixmatchSearchResult

class MainActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "extra_open_now_playing"
    }
    
    private val themePreferences by lazy { ThemePreferences(applicationContext) }
    private val audioPreferences by lazy { AudioPreferences(applicationContext) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val shouldOpenNowPlaying = intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true
        
        setContent {
            val themeColor by themePreferences.themeColor.collectAsStateWithLifecycle(
                initialValue = ThemeColorOption.DYNAMIC
            )
            val darkModeOption by themePreferences.darkMode.collectAsStateWithLifecycle(
                initialValue = DarkModeOption.SYSTEM
            )
            
            val darkTheme = when (darkModeOption) {
                DarkModeOption.SYSTEM -> isSystemInDarkTheme()
                DarkModeOption.LIGHT -> false
                DarkModeOption.DARK -> true
            }
            
            EuphoriaeTheme(
                darkTheme = darkTheme,
                themeColor = themeColor
            ) {
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
                
                EuphoriaeMainApp(
                    themePreferences = themePreferences,
                    audioPreferences = audioPreferences,
                    currentThemeColor = themeColor,
                    currentDarkMode = darkModeOption,
                    initialShowNowPlaying = shouldOpenNowPlaying
                )
            }
        }
    }
}

private const val AlbumsRoute = "albums"

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    HOME("home", "Home", Icons.Default.Home, "Home"),
    EXPLORE("explore", "Explore", Icons.Default.Public, "Explore"),
    GAMES("games", "Games", Icons.Default.SportsEsports, "Games"),
    PLAYLISTS("playlists", "Playlists", Icons.AutoMirrored.Default.PlaylistPlay, "Playlists"),
    EQUALIZER("equalizer", "Equalizer", Icons.Default.GraphicEq, "Equalizer")
}

internal fun visibleDestinations(onlineTracksEnabled: Boolean): List<Destination> =
    if (onlineTracksEnabled) {
        Destination.entries
    } else {
        Destination.entries.filterNot { it == Destination.EXPLORE }
    }

private fun destinationIndex(route: String?, destinations: List<Destination>): Int =
    destinations.indexOfFirst { it.route == route }

private fun isExploreRoute(route: String?): Boolean =
    route == Destination.EXPLORE.route || route?.startsWith("explore_results") == true

@Composable
fun EuphoriaeMainApp(
    viewModel: MusicViewModel = viewModel(),
    themePreferences: ThemePreferences,
    audioPreferences: AudioPreferences,
    currentThemeColor: ThemeColorOption,
    currentDarkMode: DarkModeOption,
    initialDestination: Destination = Destination.HOME,
    requestedDestination: Destination? = null,
    onRequestedDestinationHandled: () -> Unit = {},
    onSongClick: (Song, PlaybackQueue) -> Unit = { song, queue -> viewModel.playSongFromList(song, queue.songs) },
    currentPlayingSong: Song? = null,
    currentPlayingQueue: PlaybackQueue? = null,
    currentPlayingQueueIndex: Int = -1,
    currentPlayingIsPlaying: Boolean = false,
    currentPlayingProgress: Float = 0f,
    registeredQueues: List<PlaybackQueue> = emptyList(),
    onMiniPlayerPlayPause: () -> Unit = { viewModel.togglePlayPause() },
    onMiniPlayerSkipNext: () -> Unit = { viewModel.playNext() },
    onMiniPlayerClick: () -> Unit = {},
    onOpenVisualizer: () -> Unit = {},
    onPreviewMusixmatchResult: (MusixmatchSearchResult) -> Unit = {},
    onOpenGameSong: (Song) -> Unit = { song -> onSongClick(song, PlaybackQueue.custom(key = "game:${song.id}", label = song.title, songs = listOf(song))) },
    onDestinationChange: (Destination) -> Unit = {},
    onScanClick: () -> Unit = { viewModel.scanMusic() },
    onSelectFolder: (Uri) -> Unit = { viewModel.scanFolder(it) },
    onToggleFavorite: (Song) -> Unit = { viewModel.toggleFavorite(it) },
    onAddToFavorites: (Song) -> Unit = { viewModel.addToFavorites(it) },
    onDeleteSong: (Song) -> Unit = { viewModel.deleteSong(it) },
    onRemoveFromQueue: (String, Long) -> Unit = { _, _ -> },
    onDeleteQueue: (String) -> Unit = { _ -> },
    onCreatePlaylistFromSongs: (String, List<Song>) -> Unit = { name, songs -> viewModel.createPlaylistFromSongs(name, songs) },
    onAddSongsToPlaylist: (Long, List<Song>) -> Unit = { playlistId, songs -> viewModel.addSongsToPlaylist(playlistId, songs) },
    onRegisterQueue: (PlaybackQueue) -> Unit = {},
    onReorderQueue: (String, Int, Int) -> Unit = { _, _, _ -> },
    initialShowNowPlaying: Boolean = false
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableDestinations = remember(uiState.isOnlineTracksEnabled) {
        visibleDestinations(uiState.isOnlineTracksEnabled)
    }
    val startDestination = remember(initialDestination, availableDestinations) {
        availableDestinations.firstOrNull { it == initialDestination }
            ?: availableDestinations.firstOrNull()
            ?: Destination.HOME
    }
    var selectedDestination by remember(startDestination) { mutableStateOf(startDestination) }

    fun navigateToDestination(destination: Destination) {
        val targetDestination = availableDestinations.firstOrNull { it == destination }
            ?: availableDestinations.firstOrNull()
            ?: Destination.HOME
        selectedDestination = targetDestination
        navController.navigate(targetDestination.route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToAdjacentDestination(step: Int, route: String?) {
        val currentIndex = destinationIndex(route, availableDestinations)
        if (currentIndex < 0) return
        val targetDestination = availableDestinations.getOrNull(currentIndex + step) ?: return
        navigateToDestination(targetDestination)
    }
    
    var showNowPlaying by remember { mutableStateOf(initialShowNowPlaying) }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val swipeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    
    val showBottomBar = currentRoute != "now_playing"

    LaunchedEffect(currentRoute, availableDestinations) {
        val destination = availableDestinations.firstOrNull { it.route == currentRoute } ?: return@LaunchedEffect
        if (selectedDestination != destination) {
            selectedDestination = destination
            onDestinationChange(destination)
        }
    }

    LaunchedEffect(uiState.isOnlineTracksEnabled, currentRoute) {
        if (!uiState.isOnlineTracksEnabled && isExploreRoute(currentRoute)) {
            navigateToDestination(Destination.HOME)
        } else if (!uiState.isOnlineTracksEnabled && selectedDestination == Destination.EXPLORE) {
            selectedDestination = Destination.HOME
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(requestedDestination) {
        requestedDestination?.let { destination ->
            navigateToDestination(destination)
            onRequestedDestinationHandled()
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    if (showNowPlaying && uiState.currentSong != null) {
        NowPlayingScreen(
            song = uiState.currentSong!!,
            isPlaying = uiState.isPlaying,
            progress = uiState.progress,
            isShuffleOn = uiState.isShuffleOn,
            repeatMode = uiState.repeatMode,
            playlists = uiState.playlists,
            onBackClick = { showNowPlaying = false },
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onPreviousClick = { viewModel.playPrevious() },
            onNextClick = { viewModel.playNext() },
            onShuffleClick = { viewModel.toggleShuffle() },
            onRepeatClick = { viewModel.toggleRepeat() },
            onProgressChange = { viewModel.seekTo(it) },
            onAddToPlaylist = { playlistId -> 
                uiState.currentSong?.let { song ->
                    viewModel.addSongToPlaylist(playlistId, song.id)
                }
            },
            lyrics = uiState.lyrics,
            currentLyricIndex = uiState.currentLyricIndex
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentRoute, swipeThresholdPx) {
                    var swipeDistancePx = 0f
                    var gestureStartRoute: String? = null

                    detectHorizontalDragGestures(
                        onDragStart = {
                            swipeDistancePx = 0f
                            gestureStartRoute = currentRoute
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeDistancePx += dragAmount
                        },
                        onDragEnd = {
                            when {
                                swipeDistancePx <= -swipeThresholdPx -> {
                                    navigateToAdjacentDestination(1, gestureStartRoute)
                                }

                                swipeDistancePx >= swipeThresholdPx -> {
                                    navigateToAdjacentDestination(-1, gestureStartRoute)
                                }
                            }
                            swipeDistancePx = 0f
                            gestureStartRoute = null
                        },
                        onDragCancel = {
                            swipeDistancePx = 0f
                            gestureStartRoute = null
                        }
                    )
                },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    val miniPlayerSong = currentPlayingSong ?: uiState.currentSong
                    val miniPlayerIsPlaying = if (currentPlayingSong != null) currentPlayingIsPlaying else uiState.isPlaying
                    val miniPlayerProgress = if (currentPlayingSong != null) currentPlayingProgress else uiState.progress
                    val miniPlayerPlayPause: () -> Unit = if (currentPlayingSong != null) onMiniPlayerPlayPause else { { viewModel.togglePlayPause() } }
                    val miniPlayerSkipNext: () -> Unit = if (currentPlayingSong != null) onMiniPlayerSkipNext else { { viewModel.playNext() } }
                    val miniPlayerClick: () -> Unit = if (currentPlayingSong != null) onMiniPlayerClick else { { showNowPlaying = true } }

                    AnimatedVisibility(
                        visible = miniPlayerSong != null && showBottomBar,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        MiniPlayer(
                            currentSong = miniPlayerSong,
                            isPlaying = miniPlayerIsPlaying,
                            progress = miniPlayerProgress,
                            onPlayPauseClick = miniPlayerPlayPause,
                            onSkipNextClick = miniPlayerSkipNext,
                            onClick = miniPlayerClick
                        )
                    }
                    
                    if (showBottomBar) {
                        NavigationBar(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            tonalElevation = 0.dp
                        ) {
                            availableDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = selectedDestination == destination,
                                    onClick = {
                                        navigateToDestination(destination)
                                    },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = destination.contentDescription
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { contentPadding ->
            AppNavHost(
                navController = navController,
                startDestination = startDestination,
                uiState = uiState,
                onSongClick = onSongClick,
                onScanClick = onScanClick,
                onSelectFolder = onSelectFolder,
                onToggleFavorite = onToggleFavorite,
                onAddToFavorites = onAddToFavorites,
                onDeleteSong = onDeleteSong,
                onRemoveFromQueue = onRemoveFromQueue,
                onDeleteQueue = onDeleteQueue,
                currentPlayingSong = currentPlayingSong ?: uiState.currentSong,
                currentPlayingQueue = currentPlayingQueue,
                currentPlayingQueueIndex = currentPlayingQueueIndex,
                registeredQueues = registeredQueues,
                onOpenVisualizer = onOpenVisualizer,
                onPreviewMusixmatchResult = onPreviewMusixmatchResult,
                onOpenGameSong = onOpenGameSong,
                onSearchQueryChange = { viewModel.searchSongs(it) },
                onHomeSearchSubmit = { viewModel.submitHomeSearch(it) },
                onClearHomeSearch = { viewModel.clearHomeSearch() },
                onCreatePlaylist = { viewModel.createPlaylist(it) },
                onCreatePlaylistFromSongs = onCreatePlaylistFromSongs,
                onAddSongsToPlaylist = onAddSongsToPlaylist,
                onCreatePlaylistFromAlbum = { viewModel.createPlaylistFromAlbum(it) },
                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                onLoadPlaylistSongs = { viewModel.loadPlaylistSongs(it) },
                playlistSongs = uiState.playlistSongs,
                audioEffectsManager = viewModel.audioEffectsManager,
                audioEngine = viewModel.audioEngine,
                audioPreferences = audioPreferences,
                onPlaybackParamsChange = { t, p -> viewModel.setPlaybackParameters(t, p) },
                currentThemeColor = currentThemeColor,
                onThemeColorChange = { option ->
                    themePreferences.setThemeColor(option)
                },
                currentDarkMode = currentDarkMode,
                onDarkModeChange = { option ->
                    themePreferences.setDarkMode(option)
                },
                onRegisterQueue = onRegisterQueue,
                onReorderQueue = onReorderQueue,
                onOnlineTracksEnabledChange = { enabled -> viewModel.setOnlineTracksEnabled(enabled) },
                onLoadExploreTracks = { sectionId, optionId -> viewModel.getExploreTracks(sectionId, optionId) },
                onLoadOnlinePlaylistTracks = { playlistId -> viewModel.getOnlinePlaylistTracks(playlistId) },
                modifier = Modifier.padding(contentPadding)
            )
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Destination,
    uiState: com.oss.euphoriae.ui.viewmodel.MusicUiState,
    onSongClick: (Song, PlaybackQueue) -> Unit,
    onScanClick: () -> Unit,
    onSelectFolder: (Uri) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToFavorites: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onRemoveFromQueue: (String, Long) -> Unit,
    onDeleteQueue: (String) -> Unit,
    currentPlayingSong: Song? = null,
    currentPlayingQueue: PlaybackQueue? = null,
    currentPlayingQueueIndex: Int = -1,
    registeredQueues: List<PlaybackQueue> = emptyList(),
    onOpenVisualizer: () -> Unit = {},
    onPreviewMusixmatchResult: (MusixmatchSearchResult) -> Unit = {},
    onOpenGameSong: (Song) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onHomeSearchSubmit: (String) -> Unit,
    onClearHomeSearch: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onCreatePlaylistFromSongs: (String, List<Song>) -> Unit,
    onAddSongsToPlaylist: (Long, List<Song>) -> Unit,
    onCreatePlaylistFromAlbum: (com.oss.euphoriae.data.model.Album) -> Unit,
    onDeletePlaylist: (com.oss.euphoriae.data.model.Playlist) -> Unit,
    onLoadPlaylistSongs: (Long) -> Unit,
    playlistSongs: List<Song>,
    audioEffectsManager: com.oss.euphoriae.data.`class`.AudioEffectsManager,
    audioEngine: AudioEngine?,
    audioPreferences: AudioPreferences,
    onPlaybackParamsChange: (Float, Float) -> Unit,
    currentThemeColor: ThemeColorOption,
    onThemeColorChange: (ThemeColorOption) -> Unit,
    currentDarkMode: DarkModeOption,
    onDarkModeChange: (DarkModeOption) -> Unit,
    onRegisterQueue: (PlaybackQueue) -> Unit = {},
    onReorderQueue: (String, Int, Int) -> Unit = { _, _, _ -> },
    onOnlineTracksEnabledChange: (Boolean) -> Unit,
    onLoadExploreTracks: (String, String) -> kotlinx.coroutines.flow.Flow<List<OnlineTrack>>,
    onLoadOnlinePlaylistTracks: (String) -> kotlinx.coroutines.flow.Flow<List<OnlineTrack>>,
    modifier: Modifier = Modifier
) {
    var selectedPlaylist by remember { mutableStateOf<com.oss.euphoriae.data.model.Playlist?>(null) }
    var selectedQueueKey by rememberSaveable { mutableStateOf(currentPlayingQueue?.key ?: "") }

    val allSongsQueue = remember(uiState.songs) {
        PlaybackQueue.allSongs(uiState.songs)
    }
    val recentlyAddedQueue = remember(uiState.recentlyAddedSongs) {
        PlaybackQueue.recentlyAdded(uiState.recentlyAddedSongs)
    }
    val mostPlayedThisWeekQueue = remember(uiState.mostPlayedThisWeek) {
        PlaybackQueue.mostPlayedWeek(uiState.mostPlayedThisWeek)
    }
    val mostPlayedThisMonthQueue = remember(uiState.mostPlayedThisMonth) {
        PlaybackQueue.mostPlayedMonth(uiState.mostPlayedThisMonth)
    }
    val mostPlayedAllTimeQueue = remember(uiState.mostPlayedAllTime) {
        PlaybackQueue.mostPlayedAllTime(uiState.mostPlayedAllTime)
    }
    val favoriteQueue = remember(uiState.favoriteSongs) {
        PlaybackQueue.favorites(uiState.favoriteSongs)
    }
    val notPlayedQueue = remember(uiState.notPlayedSongs) {
        PlaybackQueue.notPlayed(uiState.notPlayedSongs)
    }
    val currentQueue = currentPlayingQueue ?: currentPlayingSong?.let {
        PlaybackQueue.custom(
            key = "single:${it.id}",
            label = it.title,
            songs = listOf(it)
        )
    } ?: PlaybackQueue.custom(
        key = "empty_queue",
        label = "Now Playing Queue",
        songs = emptyList()
    )

    val availableQueues = remember(
        currentQueue,
        allSongsQueue,
        recentlyAddedQueue,
        mostPlayedThisWeekQueue,
        mostPlayedThisMonthQueue,
        mostPlayedAllTimeQueue,
        favoriteQueue,
        notPlayedQueue,
        registeredQueues
    ) {
        buildList {
            addAll(registeredQueues)
            add(currentQueue)
            add(allSongsQueue)
            add(recentlyAddedQueue)
            add(mostPlayedThisWeekQueue)
            add(mostPlayedThisMonthQueue)
            add(mostPlayedAllTimeQueue)
            add(favoriteQueue)
            add(notPlayedQueue)
        }.distinctBy { it.key }
    }

    val selectedQueue = availableQueues.firstOrNull { it.key == selectedQueueKey } ?: currentQueue

    val openQueueScreen: (PlaybackQueue) -> Unit = { queue ->
        onRegisterQueue(queue)
        selectedQueueKey = queue.key
        navController.navigate("queue_detail")
    }

    val playOnlineSelection: (OnlineTrack, List<OnlineTrack>) -> Unit = { selectedTrack, queueTracks ->
        if (selectedTrack.isPlayable) {
            val queueSongs = queueTracks
                .filter { it.isPlayable }
                .map { it.toSong() }
            val selectedSong = selectedTrack.toSong()
            val queue = PlaybackQueue.custom(
                key = "${selectedTrack.provider.name.lowercase()}_lane:${selectedTrack.id.ifBlank { selectedSong.id.toString() }}",
                label = selectedTrack.title,
                songs = if (queueSongs.isEmpty()) listOf(selectedSong) else queueSongs
            )
            onSongClick(selectedSong, queue)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(Destination.HOME.route) {
            HomeScreen(
                songs = uiState.songs,
                playlists = uiState.playlists,
                currentPlayingQueue = currentQueue,
                currentPlayingSong = currentPlayingSong,
                currentPlayingQueueIndex = currentPlayingQueueIndex,
                recentlyAddedQueue = recentlyAddedQueue,
                mostPlayedThisWeekQueue = mostPlayedThisWeekQueue,
                mostPlayedThisMonthQueue = mostPlayedThisMonthQueue,
                mostPlayedAllTimeQueue = mostPlayedAllTimeQueue,
                favoriteQueue = favoriteQueue,
                notPlayedQueue = notPlayedQueue,
                isScanning = uiState.isScanning,
                onlineShelves = uiState.onlineShelves,
                isOnlineTracksEnabled = uiState.isOnlineTracksEnabled,
                homeSearchState = uiState.homeSearch,
                onOnlineTrackClick = { track ->
                    playOnlineSelection(track, listOf(track))
                },
                onOnlinePlaylistClick = { playlist ->
                    navController.navigate("online_playlist_detail/${Uri.encode(playlist.id)}/${Uri.encode(playlist.playlistName)}")
                },
                onHomeSearchSubmit = onHomeSearchSubmit,
                onClearHomeSearch = onClearHomeSearch,
                onMusixmatchResultClick = onPreviewMusixmatchResult,
                onSongClick = onSongClick,
                onOpenQueueClick = openQueueScreen,
                onOpenPlaylistsClick = { navController.navigate(Destination.PLAYLISTS.route) },
                onToggleFavorite = onToggleFavorite,
                onCreatePlaylistFromSongs = onCreatePlaylistFromSongs,
                onAddSongsToPlaylist = onAddSongsToPlaylist,
                onAddToFavorites = onAddToFavorites,
                onDeleteSong = onDeleteSong,
                onScanClick = onScanClick,
                onSelectFolder = onSelectFolder,
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("queue_detail") {
            QueueDetailScreen(
                initialQueue = selectedQueue,
                activeQueueKey = currentQueue.key,
                currentPlayingSongId = currentPlayingSong?.id,
                playlists = uiState.playlists,
                onBackClick = { navController.popBackStack() },
                onQueueSelected = { queue ->
                    selectedQueueKey = queue.key
                },
                onQueueKeySelected = { key ->
                    selectedQueueKey = key
                },
                onSongClick = onSongClick,
                onReorderQueue = onReorderQueue,
                onRemoveFromQueue = onRemoveFromQueue,
                onAddToFavorites = onAddToFavorites,
                onDeleteSong = onDeleteSong,
                onCreatePlaylistFromSongs = onCreatePlaylistFromSongs,
                onAddSongsToPlaylist = onAddSongsToPlaylist,
                onDeleteQueue = onDeleteQueue,
                onSaveQueue = onRegisterQueue,
                savedQueues = registeredQueues
            )
        }
        composable(Destination.EXPLORE.route) {
            ExploreScreen(
                onOpenOption = { sectionId, optionId ->
                    navController.navigate("explore_results/${Uri.encode(sectionId)}/${Uri.encode(optionId)}")
                },
                onlineTracksEnabled = uiState.isOnlineTracksEnabled
            )
        }
        composable("explore_results/{sectionId}/{optionId}") { backStackEntry ->
            val sectionId = Uri.decode(backStackEntry.arguments?.getString("sectionId").orEmpty())
            val optionId = Uri.decode(backStackEntry.arguments?.getString("optionId").orEmpty())
            ExploreResultsScreen(
                sectionId = sectionId,
                optionId = optionId,
                loadTracks = onLoadExploreTracks,
                onBackClick = { navController.popBackStack() },
                onTrackClick = playOnlineSelection,
                onlineTracksEnabled = uiState.isOnlineTracksEnabled
            )
        }
        composable("online_playlist_detail/{playlistId}/{playlistName}") { backStackEntry ->
            val playlistId = Uri.decode(backStackEntry.arguments?.getString("playlistId").orEmpty())
            val playlistName = Uri.decode(backStackEntry.arguments?.getString("playlistName").orEmpty())
            OnlinePlaylistDetailScreen(
                playlistId = playlistId,
                playlistName = playlistName,
                loadTracks = { onLoadOnlinePlaylistTracks(playlistId) },
                onBackClick = { navController.popBackStack() },
                onTrackClick = playOnlineSelection
            )
        }
        composable("all_songs") {
            SongsScreen(
                songs = uiState.songs,
                playlists = uiState.playlists,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onSongClick = onSongClick,
                onCreatePlaylistFromSongs = onCreatePlaylistFromSongs,
                onAddSongsToPlaylist = onAddSongsToPlaylist,
                currentPlayingSong = currentPlayingSong,
                onScanClick = onScanClick,
                onSelectFolder = onSelectFolder,
                onAddToFavorites = onAddToFavorites,
                onDeleteSong = onDeleteSong
            )
        }
        composable(Destination.GAMES.route) {
            GamesScreen(
                topScores = uiState.gameTopScores,
                recommendations = uiState.gameRecommendations,
                currentPlayingSong = currentPlayingSong,
                onSongClick = onOpenGameSong
            )
        }
        composable(AlbumsRoute) {
            com.oss.euphoriae.ui.screens.AlbumsScreen(
                albums = uiState.albums,
                onCreatePlaylistFromAlbum = onCreatePlaylistFromAlbum,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Destination.PLAYLISTS.route) {
            PlaylistScreen(
                playlists = uiState.playlists,
                onPlaylistClick = { playlist ->
                    selectedPlaylist = playlist
                    onLoadPlaylistSongs(playlist.id)
                    navController.navigate("playlist_detail")
                },
                onOpenAllSongs = { navController.navigate("all_songs") },
                onOpenAlbums = { navController.navigate(AlbumsRoute) },
                onCreatePlaylist = onCreatePlaylist,
                onDeletePlaylist = { playlist -> onDeletePlaylist(playlist) }
            )
        }
        composable("playlist_detail") {
            selectedPlaylist?.let { playlist ->
                PlaylistDetailScreen(
                    playlist = playlist,
                    songs = playlistSongs,
                    playlists = uiState.playlists,
                    currentPlayingSong = currentPlayingSong,
                    onBackClick = { navController.popBackStack() },
                    onSongClick = onSongClick,
                    onCreatePlaylistFromSongs = onCreatePlaylistFromSongs,
                    onAddSongsToPlaylist = onAddSongsToPlaylist,
                    onAddToFavorites = onAddToFavorites,
                    onDeleteSong = onDeleteSong,
                    onDeletePlaylist = {
                        onDeletePlaylist(playlist)
                        navController.popBackStack()
                    }
                )
            }
        }
        composable(Destination.EQUALIZER.route) {
            EqualizerScreen(
                audioEffectsManager = audioEffectsManager,
                audioEngine = audioEngine,
                audioPreferences = audioPreferences,
                onPlaybackParamsChange = onPlaybackParamsChange
            )
        }
        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                currentThemeColor = currentThemeColor,
                onThemeColorChange = onThemeColorChange,
                currentDarkMode = currentDarkMode,
                onDarkModeChange = onDarkModeChange,
                currentOnlineTracksEnabled = uiState.isOnlineTracksEnabled,
                onOnlineTracksEnabledChange = onOnlineTracksEnabledChange
            )
        }
    }
}
