package com.oss.euphoriae.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import com.lyrictica.audio.PlaybackQueue
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.data.model.HomeFeedItem
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.AudiusPlaylist
import com.oss.euphoriae.data.model.toSong
import com.oss.euphoriae.search.HomeSearchUiState
import com.oss.euphoriae.search.MusixmatchSearchResult
import com.oss.euphoriae.search.SearchAvailabilityPlatform
import com.oss.euphoriae.ui.components.AddSongsToPlaylistDialog
import com.oss.euphoriae.ui.components.DeleteSongDialog
import com.oss.euphoriae.ui.components.OnlineFeedHeader
import com.oss.euphoriae.ui.components.OnlinePlaylistShelfRow
import com.oss.euphoriae.ui.components.OnlineTrackShelfGrid
import com.oss.euphoriae.ui.components.PlaylistCardCompact
import com.oss.euphoriae.ui.components.SongListItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    currentPlayingQueue: PlaybackQueue?,
    currentPlayingSong: Song?,
    currentPlayingQueueIndex: Int,
    recentlyAddedQueue: PlaybackQueue,
    mostPlayedThisWeekQueue: PlaybackQueue,
    mostPlayedThisMonthQueue: PlaybackQueue,
    mostPlayedAllTimeQueue: PlaybackQueue,
    favoriteQueue: PlaybackQueue,
    notPlayedQueue: PlaybackQueue,
    isScanning: Boolean,
    onlineShelves: List<HomeFeedItem> = emptyList(),
    isOnlineTracksEnabled: Boolean = true,
    homeSearchState: HomeSearchUiState = HomeSearchUiState(),
    onOnlineTrackClick: (OnlineTrack) -> Unit = {},
    onOnlinePlaylistClick: (AudiusPlaylist) -> Unit = {},
    onHomeSearchSubmit: (String) -> Unit = {},
    onClearHomeSearch: () -> Unit = {},
    onMusixmatchResultClick: (MusixmatchSearchResult) -> Unit = {},
    onSongClick: (Song, PlaybackQueue) -> Unit,
    onOpenQueueClick: (PlaybackQueue) -> Unit,
    onOpenPlaylistsClick: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onCreatePlaylistFromSongs: (String, List<Song>) -> Unit,
    onAddSongsToPlaylist: (Long, List<Song>) -> Unit,
    onAddToFavorites: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onScanClick: () -> Unit,
    onSelectFolder: (android.net.Uri) -> Unit = {},
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            onScanClick()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onSelectFolder(uri)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val favoriteSongIds = remember(favoriteQueue.songs) {
        favoriteQueue.songs.mapTo(mutableSetOf()) { it.id }
    }
    var addToPlaylistTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTargetSong by remember { mutableStateOf<Song?>(null) }
    var draftSearchQuery by rememberSaveable { mutableStateOf(homeSearchState.activeQuery) }
    val selectedSongIds = remember { androidx.compose.runtime.snapshots.SnapshotStateList<Long>() }
    val hasSelection = selectedSongIds.isNotEmpty()

    fun clearSelection() {
        selectedSongIds.clear()
    }

    fun toggleSongSelection(song: Song) {
        if (selectedSongIds.contains(song.id)) {
            selectedSongIds.remove(song.id)
        } else {
            selectedSongIds.add(song.id)
        }
    }

    val configuration = LocalConfiguration.current
    val heroHeight = configuration.screenHeightDp.dp * 0.65f
    val screenWidth = configuration.screenWidthDp.dp
    val listState = rememberLazyListState()
    val onRequestScan = {
        if (hasPermission) {
            onScanClick()
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    LaunchedEffect(homeSearchState.activeQuery) {
        if (homeSearchState.activeQuery.isBlank()) {
            draftSearchQuery = ""
        }
    }

    val activeSearchQuery = homeSearchState.activeQuery.trim()
    val isSearching = activeSearchQuery.isNotBlank()
    val searchResults = homeSearchState.results

    val visibleCurrentPlayingQueue = remember(currentPlayingQueue, activeSearchQuery) {
        currentPlayingQueue?.filteredForHome(activeSearchQuery)
    }
    val visibleRecentlyAddedQueue = remember(recentlyAddedQueue, activeSearchQuery) {
        recentlyAddedQueue.filteredForHome(activeSearchQuery)
    }
    val visibleMostPlayedThisWeekQueue = remember(mostPlayedThisWeekQueue, activeSearchQuery) {
        mostPlayedThisWeekQueue.filteredForHome(activeSearchQuery)
    }
    val visibleMostPlayedThisMonthQueue = remember(mostPlayedThisMonthQueue, activeSearchQuery) {
        mostPlayedThisMonthQueue.filteredForHome(activeSearchQuery)
    }
    val visibleMostPlayedAllTimeQueue = remember(mostPlayedAllTimeQueue, activeSearchQuery) {
        mostPlayedAllTimeQueue.filteredForHome(activeSearchQuery)
    }
    val visibleFavoriteQueue = remember(favoriteQueue, activeSearchQuery) {
        favoriteQueue.filteredForHome(activeSearchQuery)
    }
    val visibleNotPlayedQueue = remember(notPlayedQueue, activeSearchQuery) {
        notPlayedQueue.filteredForHome(activeSearchQuery)
    }
    val localSearchQueue = remember(searchResults.localSongs, activeSearchQuery) {
        PlaybackQueue.custom(
            key = "home_search_local:${activeSearchQuery.lowercase()}",
            label = "Local Results",
            songs = searchResults.localSongs
        )
    }
    val audiusSearchQueue = remember(searchResults.audius, activeSearchQuery) {
        PlaybackQueue.custom(
            key = "home_search_audius:${activeSearchQuery.lowercase()}",
            label = "Spinamp Results",
            songs = searchResults.audius.map { it.toSong() }
        )
    }
    val ncsSearchQueue = remember(searchResults.ncs, activeSearchQuery) {
        PlaybackQueue.custom(
            key = "home_search_ncs:${activeSearchQuery.lowercase()}",
            label = "NCS Results",
            songs = searchResults.ncs.map { it.toSong() }
        )
    }

    val collapseDistancePx = with(LocalDensity.current) { (heroHeight * 0.38f).toPx() }
    val collapseTarget by remember(listState, collapseDistancePx, isSearching) {
        derivedStateOf {
            when {
                isSearching -> 1f
                listState.firstVisibleItemIndex > 0 -> 1f
                collapseDistancePx <= 0f -> 0f
                else -> (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    val collapseProgress by animateFloatAsState(
        targetValue = collapseTarget,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "homeCollapseProgress"
    )

    LaunchedEffect(isSearching, homeSearchState.isLoading) {
        if (isSearching || homeSearchState.isLoading) {
            listState.animateScrollToItem(1)
        }
    }

    Scaffold(
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HomeHeroBackground(
                heroHeight = heroHeight,
                screenWidth = screenWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(heroHeight))
                }

                when {
                    songs.isEmpty() && !isSearching -> {
                        item {
                            if (isScanning) {
                                ScanningMusicState(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                            } else {
                                EmptyMusicState(
                                    hasPermission = hasPermission,
                                    onScanClick = onRequestScan,
                                    onSelectFolderClick = { folderPickerLauncher.launch(null) },
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    isSearching -> {
                        item {
                            SearchStrategyCard(
                                query = activeSearchQuery,
                                musixmatchCount = searchResults.musixmatch.size,
                                audiusCount = searchResults.audius.size,
                                ncsCount = searchResults.ncs.size,
                                localCount = searchResults.localSongs.size,
                                isOnlineTracksEnabled = isOnlineTracksEnabled,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        if (homeSearchState.isLoading) {
                            item {
                                SearchLoadingState(
                                    query = activeSearchQuery,
                                    isOnlineTracksEnabled = isOnlineTracksEnabled,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                            }
                        } else if (searchResults.isEmpty) {
                            item {
                                NoSearchResultsState(
                                    query = activeSearchQuery,
                                    isOnlineTracksEnabled = isOnlineTracksEnabled,
                                    onClearSearch = {
                                        draftSearchQuery = ""
                                        onClearHomeSearch()
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            if (searchResults.musixmatch.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Musixmatch Lyrics (${searchResults.musixmatch.size})",
                                        subtitle = "Lyrics-first matches. Tap a result to open lyrics or jump straight into local playback."
                                    )
                                }

                                itemsIndexed(
                                    items = searchResults.musixmatch,
                                    key = { index, result -> "musixmatch:${result.trackId}:${index}" }
                                ) { _, result ->
                                    MusixmatchResultCard(
                                        result = result,
                                        isOnlineTracksEnabled = isOnlineTracksEnabled,
                                        onClick = {
                                            val localMatch = result.localMatch
                                            if (localMatch != null) {
                                                val prioritizedQueue = PlaybackQueue.custom(
                                                    key = "home_search_local_pick:${activeSearchQuery.lowercase()}:${localMatch.id}",
                                                    label = "Local Match",
                                                    songs = listOf(localMatch) + searchResults.localSongs.filterNot { it.id == localMatch.id }
                                                )
                                                onSongClick(localMatch, prioritizedQueue)
                                            } else {
                                                onMusixmatchResultClick(result)
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            if (searchResults.audius.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Spinamp (${searchResults.audius.size})",
                                        subtitle = "Streamable Spinamp matches for the visualizer."
                                    )
                                }

                                itemsIndexed(
                                    items = searchResults.audius.map { it.toSong() },
                                    key = { index, song -> song.lazyItemKey(index) }
                                ) { _, song ->
                                    SongListItem(
                                        song = song,
                                        onClick = { onSongClick(song, audiusSearchQueue) },
                                        isPlaying = currentPlayingSong?.id == song.id,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }

                            if (searchResults.ncs.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "NCS (${searchResults.ncs.size})",
                                        subtitle = "Creator-safe tracks that open directly in the visualizer."
                                    )
                                }

                                itemsIndexed(
                                    items = searchResults.ncs.map { it.toSong() },
                                    key = { index, song -> song.lazyItemKey(index) }
                                ) { _, song ->
                                    SongListItem(
                                        song = song,
                                        onClick = { onSongClick(song, ncsSearchQueue) },
                                        isPlaying = currentPlayingSong?.id == song.id,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }

                            if (searchResults.localSongs.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Local Music (${searchResults.localSongs.size})",
                                        subtitle = "Your device library stays last so lyrics and online matches lead the decision first."
                                    )
                                }

                                itemsIndexed(
                                    items = searchResults.localSongs,
                                    key = { index, song -> song.lazyItemKey(index) }
                                ) { _, song ->
                                    SongListItem(
                                        song = song,
                                        onClick = {
                                            if (hasSelection) {
                                                toggleSongSelection(song)
                                            } else {
                                                onSongClick(song, localSearchQueue)
                                            }
                                        },
                                        isPlaying = currentPlayingSong?.id == song.id,
                                        isSelected = selectedSongIds.contains(song.id),
                                        onLongClick = { toggleSongSelection(song) },
                                        onAddToPlaylists = { addToPlaylistTarget = song },
                                        onCreatePlaylist = { onCreatePlaylistFromSongs(song.title, listOf(song)) },
                                        onAddToFavorites = { onAddToFavorites(song) },
                                        onDeletePermanently = { deleteTargetSong = song },
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // ── Online Shelves ────────────────────────────
                        // Order: Online Header → Primary Shelf → Now Playing → Remaining Online Shelves → Your Library

                        val onlineContentShelves = onlineShelves.filterNot { it is HomeFeedItem.OnlineHeader }
                        val hasOnlineShelves = onlineContentShelves.isNotEmpty()

                        if (hasOnlineShelves) {
                            item(key = "online_header") {
                                OnlineFeedHeader()
                            }

                            val featuredShelf = onlineContentShelves.first()
                            item(key = featuredShelf.stableId) {
                                when (featuredShelf) {
                                    is HomeFeedItem.OnlineTrackShelf -> OnlineTrackShelfGrid(
                                        shelf = featuredShelf,
                                        onTrackClick = onOnlineTrackClick
                                    )

                                    is HomeFeedItem.OnlinePlaylistShelf -> OnlinePlaylistShelfRow(
                                        shelf = featuredShelf,
                                        onPlaylistClick = onOnlinePlaylistClick
                                    )
                                    is HomeFeedItem.OnlineHeader -> Unit
                                }
                            }
                        }

                        nowPlayingQueueSection(
                            queue = visibleCurrentPlayingQueue,
                            currentPlayingSongId = currentPlayingSong?.id,
                            currentPlayingQueueIndex = currentPlayingQueueIndex,
                            favoriteSongIds = favoriteSongIds,
                            onToggleFavorite = onToggleFavorite,
                            onOpenQueueClick = onOpenQueueClick,
                            onSongClick = onSongClick,
                            onSongLongClick = { song -> addToPlaylistTarget = song }
                        )

                        for (feedItem in onlineContentShelves.drop(1)) {
                            item(key = feedItem.stableId) {
                                when (feedItem) {
                                    is HomeFeedItem.OnlineTrackShelf -> OnlineTrackShelfGrid(
                                        shelf = feedItem,
                                        onTrackClick = onOnlineTrackClick
                                    )

                                    is HomeFeedItem.OnlinePlaylistShelf -> OnlinePlaylistShelfRow(
                                        shelf = feedItem,
                                        onPlaylistClick = onOnlinePlaylistClick
                                    )
                                    is HomeFeedItem.OnlineHeader -> Unit
                                }
                            }
                        }

                        // ── Your Library ──────────────────────────────

                        if (hasOnlineShelves) {
                            item(key = "library_divider") {
                                SectionHeader(title = "Your Library")
                            }
                        }

                        songRowSection(
                            queue = visibleRecentlyAddedQueue,
                            title = "Recently Added",
                            onSongClick = onSongClick,
                            onToggleFavorite = onToggleFavorite,
                            favoriteSongIds = favoriteSongIds,
                            currentPlayingSongId = currentPlayingSong?.id,
                            onOpenQueueClick = onOpenQueueClick,
                            emptyTitle = "No music yet",
                            emptyMessage = "Scan your device to populate this row.",
                            emptyIcon = Icons.Default.LibraryMusic,
                            onSongLongClick = { song -> addToPlaylistTarget = song }
                        )



                        songRowSection(
                            queue = visibleMostPlayedAllTimeQueue,
                            title = "Most Played • All Time",
                            onSongClick = onSongClick,
                            onToggleFavorite = onToggleFavorite,
                            favoriteSongIds = favoriteSongIds,
                            currentPlayingSongId = currentPlayingSong?.id,
                            onOpenQueueClick = onOpenQueueClick,
                            emptyTitle = "No play history yet",
                            emptyMessage = "Play songs to see your most listened tracks.",
                            emptyIcon = Icons.AutoMirrored.Filled.TrendingUp,
                            onSongLongClick = { song -> addToPlaylistTarget = song }
                        )

                        songRowSection(
                            queue = visibleFavoriteQueue,
                            title = "Favorites",
                            onSongClick = onSongClick,
                            onToggleFavorite = onToggleFavorite,
                            favoriteSongIds = favoriteSongIds,
                            currentPlayingSongId = currentPlayingSong?.id,
                            onOpenQueueClick = onOpenQueueClick,
                            emptyTitle = "No favorites yet",
                            emptyMessage = "Tap the star on any song to add it here.",
                            emptyIcon = Icons.Default.FavoriteBorder,
                            onSongLongClick = { song -> addToPlaylistTarget = song }
                        )

                        songRowSection(
                            queue = visibleNotPlayedQueue,
                            title = "Not Played",
                            onSongClick = onSongClick,
                            onToggleFavorite = onToggleFavorite,
                            favoriteSongIds = favoriteSongIds,
                            currentPlayingSongId = currentPlayingSong?.id,
                            onOpenQueueClick = onOpenQueueClick,
                            emptyTitle = "Everything played",
                            emptyMessage = "All songs in your library have been played.",
                            emptyIcon = Icons.Default.MusicNote,
                            onSongLongClick = { song -> addToPlaylistTarget = song }
                        )

                        if (playlists.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Your Playlists",
                                    onActionClick = onOpenPlaylistsClick,
                                    actionContentDescription = "Open playlists"
                                )
                            }

                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    items(playlists, key = { playlist -> playlist.id }) { playlist ->
                                        PlaylistCardCompact(
                                            playlist = playlist,
                                            onClick = { }
                                        )
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(28.dp)) }
                        }

                        item {
                            SectionHeader(title = "All Songs (${songs.size})")
                        }

                        itemsIndexed(
                            items = songs.take(10),
                            key = { index, song -> song.lazyItemKey(index) }
                        ) { _, song ->
                            SongListItem(
                                song = song,
                                onClick = {
                                    if (hasSelection) {
                                        toggleSongSelection(song)
                                    } else {
                                        onSongClick(song, PlaybackQueue.allSongs(songs))
                                    }
                                },
                                isPlaying = currentPlayingSong?.id == song.id,
                                isSelected = selectedSongIds.contains(song.id),
                                onLongClick = { toggleSongSelection(song) },
                                onAddToPlaylists = { addToPlaylistTarget = song },
                                onCreatePlaylist = { onCreatePlaylistFromSongs(song.title, listOf(song)) },
                                onAddToFavorites = { onAddToFavorites(song) },
                                onDeletePermanently = { deleteTargetSong = song },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            HomeHeroSection(
                searchQuery = draftSearchQuery,
                onSearchQueryChange = { draftSearchQuery = it },
                onSearchSubmit = {
                    val query = draftSearchQuery.trim()
                    if (query.isBlank()) {
                        draftSearchQuery = ""
                        onClearHomeSearch()
                    } else {
                        onHomeSearchSubmit(query)
                    }
                },
                onClearSearch = {
                    draftSearchQuery = ""
                    onClearHomeSearch()
                },
                isSearchLoading = homeSearchState.isLoading,
                isOnlineTracksEnabled = isOnlineTracksEnabled,
                isScanning = isScanning,
                onScanClick = onRequestScan,
                onSettingsClick = onSettingsClick,
                heroHeight = heroHeight,
                screenWidth = screenWidth,
                collapseProgress = collapseProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
    }


    if (addToPlaylistTarget != null) {
        val targetSong = addToPlaylistTarget!!
        AddSongsToPlaylistDialog(
            playlists = playlists,
            songs = listOf(targetSong),
            onDismiss = { addToPlaylistTarget = null },
            onPlaylistSelected = { playlist ->
                onAddSongsToPlaylist(playlist.id, listOf(targetSong))
                addToPlaylistTarget = null
            },
            onCreatePlaylistClick = {
                onCreatePlaylistFromSongs(targetSong.title, listOf(targetSong))
                addToPlaylistTarget = null
            }
        )
    }

    if (deleteTargetSong != null) {
        val targetSong = deleteTargetSong!!
        DeleteSongDialog(
            song = targetSong,
            onDismiss = { deleteTargetSong = null },
            onConfirm = {
                onDeleteSong(targetSong)
                deleteTargetSong = null
            }
        )
    }
}

private fun Song.matchesHomeQuery(query: String): Boolean {
    if (query.isBlank()) return true

    return title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        album.contains(query, ignoreCase = true)
}

private fun Playlist.matchesHomeQuery(query: String): Boolean {
    if (query.isBlank()) return true

    return name.contains(query, ignoreCase = true)
}

private fun PlaybackQueue.filteredForHome(query: String): PlaybackQueue {
    if (query.isBlank()) return this

    return withSongs(songs.filter { it.matchesHomeQuery(query) })
}


private fun LazyListScope.nowPlayingQueueSection(
    queue: PlaybackQueue?,
    currentPlayingSongId: Long?,
    currentPlayingQueueIndex: Int,
    favoriteSongIds: Set<Long>,
    onToggleFavorite: (Song) -> Unit,
    onOpenQueueClick: (PlaybackQueue) -> Unit,
    onSongClick: (Song, PlaybackQueue) -> Unit,
    hideIfEmpty: Boolean = false,
    onSongLongClick: ((Song) -> Unit)? = null
) {
    val queueSongs = queue?.songs.orEmpty()
    if (hideIfEmpty && queueSongs.isEmpty()) {
        return
    }

    item {
        SectionHeader(
            title = "Now Playing Queue",
            subtitle = queue?.source?.label ?: "Nothing playing",
            onActionClick = queue?.let { { onOpenQueueClick(it) } },
            actionContentDescription = "Open queue"
        )
    }

    item {
        val queueState = rememberLazyGridState()
        val queueScrollIndex = queueSongs.indexOfFirst { it.id == currentPlayingSongId }
            .takeIf { it >= 0 }
            ?: currentPlayingQueueIndex

        LaunchedEffect(
            queue?.key,
            queueScrollIndex,
            queueSongs.map { it.id }
        ) {
            if (queue != null && queueScrollIndex in queueSongs.indices) {
                queueState.animateScrollToItem(queueScrollIndex)
            }
        }

        if (queueSongs.isEmpty()) {
            EmptySectionCard(
                title = "No active queue",
                message = "Play a song from playlists, albums, or your library to populate the queue.",
                icon = Icons.Default.MusicNote,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(4),
                state = queueState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(260.dp)
            ) {
                itemsIndexed(
                    items = queueSongs,
                    key = { index, song -> song.lazyItemKey(index) }
                ) { _, song ->
                    SongListItem(
                        song = song,
                        onClick = { queue?.let { onSongClick(song, it) } },
                        isPlaying = currentPlayingSongId == song.id,
                        onLongClick = onSongLongClick?.let { { it(song) } },
                        onAddToFavorites = { onToggleFavorite(song) },
                        modifier = Modifier.width(320.dp)
                    )
                }
            }
        }
    }

    item { Spacer(modifier = Modifier.height(28.dp)) }
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

@Composable
private fun HomeHeroBackground(
    heroHeight: Dp,
    screenWidth: Dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "home_background_logo")
    val wavePhaseState = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing)
        ),
        label = "home_background_wave_phase"
    )
    val waveDriftState = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "home_background_wave_drift"
    )
    val themeCycleState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing)
        ),
        label = "home_background_theme_cycle"
    )
    val logoSize = minOf(heroHeight * 0.78f, screenWidth * 0.94f)

    Box(
        modifier = modifier.height(heroHeight)
    ) {
        HomeLogoOrb(
            wavePhaseProvider = { wavePhaseState.value },
            waveDriftProvider = { waveDriftState.value },
            themeCycleProvider = { themeCycleState.value },
            modifier = Modifier
                .align(Alignment.Center)
                .size(logoSize)
        )
    }
}

@Composable
private fun HomeHeroSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClearSearch: () -> Unit,
    isSearchLoading: Boolean,
    isOnlineTracksEnabled: Boolean,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    heroHeight: Dp,
    screenWidth: Dp,
    collapseProgress: Float,
    modifier: Modifier = Modifier
) {
    val logoSize = minOf(heroHeight * 0.78f, screenWidth * 0.94f)
    val topBarHeight = 58.dp
    val topBarHorizontalPadding = 12.dp
    val topBarButtonSpacing = 10.dp
    val actionButtonsWidth = (40.dp * 2) + topBarButtonSpacing
    val expandedSearchWidth = minOf(screenWidth * 0.66f, logoSize * 0.78f)
    val collapsedSearchWidth = maxOf(
        160.dp,
        minOf(screenWidth - (topBarHorizontalPadding * 2) - actionButtonsWidth - 14.dp, 360.dp)
    )
    val expandedSearchStart = maxOf(0.dp, (screenWidth - expandedSearchWidth) / 2)
    val collapsedSearchStart = topBarHorizontalPadding + 6.dp
    val searchTop = lerpDp(heroHeight * 0.49f, (topBarHeight - 42.dp) / 2, collapseProgress)
    val searchStart = lerpDp(expandedSearchStart, collapsedSearchStart, collapseProgress)
    val titleTop = lerpDp(heroHeight * 0.40f, 18.dp, collapseProgress)
    val searchWidth = lerpDp(expandedSearchWidth, collapsedSearchWidth, collapseProgress)
    val titleAlpha = (1f - collapseProgress * 1.2f).coerceIn(0f, 1f)
    // Top bar is transparent when search is in hero; fades to default color as user scrolls
    val topBarAlpha = collapseProgress.coerceIn(0f, 1f)
    val topBarColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f * topBarAlpha)
    // "Lyrictica" label fades out as the search bar rises into the top bar
    val brandAlpha = (1f - collapseProgress * 1.8f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.height(heroHeight)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topBarHeight),
            color = topBarColor
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = topBarHorizontalPadding)
            ) {
                // Brand name on the left edge, visible only while search bar is in the hero
                if (brandAlpha > 0f) {
                    Text(
                        text = "Lyrictica",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = brandAlpha),
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(topBarButtonSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeHeroActionButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh library",
                        onClick = onScanClick,
                        isBusy = isScanning
                    )

                    HomeHeroActionButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings",
                        onClick = onSettingsClick
                    )
                }
            }
        }

        Text(
            text = "For the love of music",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = titleTop)
                .alpha(titleAlpha)
        )

        HomeSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            onSearchClick = onSearchSubmit,
            onClearClick = onClearSearch,
            isLoading = isSearchLoading,
            isOnlineTracksEnabled = isOnlineTracksEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = searchStart, top = searchTop)
                .width(searchWidth)
        )
    }
}


@Composable
private fun HomeHeroActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isBusy: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                CircleShape
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
private fun HomeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onClearClick: () -> Unit,
    isLoading: Boolean,
    isOnlineTracksEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isBlank()) {
                            Text(
                                text = if (isOnlineTracksEnabled) {
                                    "Search Musixmatch, Spinamp, NCS and local"
                                } else {
                                    "Search Musixmatch and local"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                            )
                        }

                        innerTextField()
                    }

                    if (value.isNotBlank()) {
                        IconButton(
                            onClick = onClearClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.84f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSearchClick,
                        enabled = value.trim().isNotBlank() && !isLoading,
                        modifier = Modifier.size(28.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Run search",
                                modifier = Modifier.size(16.dp),
                                tint = if (value.trim().isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}


@Composable
private fun HomeLogoOrb(
    wavePhaseProvider: () -> Float,
    waveDriftProvider: () -> Float,
    themeCycleProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surface
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val isLightTheme = surface.luminance() > 0.5f

    val palette = remember(primary, tertiary, error, surface) {
        listOf(
            lerp(primary, Color(0xFF69E28F), 0.76f),
            lerp(primary, Color(0xFF79B9FF), 0.78f),
            lerp(tertiary, Color(0xFFA07BFF), 0.80f),
            lerp(error, Color(0xFFFF6C7C), 0.74f)
        )
    }

    val orbBase = remember(surface, primaryContainer, isLightTheme) {
        if (isLightTheme) {
            lerp(surface, primaryContainer, 0.42f)
        } else {
            lerp(surface, Color(0xFF06101F), 0.90f)
        }
    }
    val orbMid = remember(surface, secondaryContainer, isLightTheme) {
        if (isLightTheme) {
            lerp(surface, secondaryContainer, 0.56f)
        } else {
            lerp(surface, Color(0xFF0A1730), 0.84f)
        }
    }
    val orbHighlight = if (isLightTheme) onSurfaceVariant else Color.White

    Canvas(modifier = modifier) {
        val themeCycle = themeCycleProvider()
        val wavePhase = wavePhaseProvider()
        val waveDrift = waveDriftProvider()

        fun cycleColor(offset: Float): Color {
            val progress = (((themeCycle + offset) % 1f) + 1f) % 1f
            val scaled = progress * palette.size
            val index = scaled.toInt().coerceIn(0, palette.lastIndex)
            val nextIndex = (index + 1) % palette.size
            val localT = scaled - index
            return lerp(palette[index], palette[nextIndex], localT)
        }

        val active = cycleColor(0f)
        val support = cycleColor(0.22f)
        val support2 = cycleColor(0.48f)
        val orbGlow = active.copy(alpha = if (isLightTheme) 0.08f else 0.12f)
        val rimGlow = lerp(active, orbHighlight, if (isLightTheme) 0.18f else 0.35f)

        fun highlightAlpha(lightAlpha: Float, darkAlpha: Float): Color =
            orbHighlight.copy(alpha = if (isLightTheme) lightAlpha else darkAlpha)

        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = radius * 0.88f
        val circleRect = Rect(
            left = center.x - innerRadius,
            top = center.y - innerRadius,
            right = center.x + innerRadius,
            bottom = center.y + innerRadius
        )
        val orbPath = Path().apply {
            addOval(circleRect)
        }
        val waveWidth = innerRadius * 1.92f
        val left = center.x - waveWidth / 2f
        val baseY = center.y + innerRadius * 0.01f + waveDrift * innerRadius * 0.018f

        fun buildWavePath(
            yOffset: Float,
            amplitude: Float,
            frequency: Float,
            phaseOffset: Float
        ): Path = Path().apply {
            moveTo(left, baseY + yOffset)
            val steps = 32
            for (step in 1..steps) {
                val t = step / steps.toFloat()
                val x = left + waveWidth * t
                val angle = (t * 2.0 * PI * frequency) + wavePhase + phaseOffset
                val y = baseY + yOffset + sin(angle).toFloat() * amplitude
                lineTo(x, y)
            }
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orbGlow.copy(alpha = 0.16f), Color.Transparent),
                center = center,
                radius = innerRadius * 1.36f
            ),
            radius = innerRadius * 1.26f,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    highlightAlpha(0.06f, 0.12f),
                    orbMid,
                    orbBase
                ),
                center = Offset(center.x - innerRadius * 0.36f, center.y - innerRadius * 0.42f),
                radius = innerRadius * 1.34f
            ),
            radius = innerRadius,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(active.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(center.x - innerRadius * 0.58f, center.y + innerRadius * 0.12f),
                radius = innerRadius * 1.18f
            ),
            radius = innerRadius,
            center = center
        )

        drawArc(
            color = highlightAlpha(0.12f, 0.18f),
            startAngle = 206f,
            sweepAngle = 112f,
            useCenter = false,
            topLeft = Offset(center.x - innerRadius * 0.98f, center.y - innerRadius * 0.98f),
            size = Size(innerRadius * 1.96f, innerRadius * 1.96f),
            style = Stroke(width = innerRadius * 0.085f, cap = StrokeCap.Round)
        )

        drawArc(
            color = highlightAlpha(0.04f, 0.07f),
            startAngle = 214f,
            sweepAngle = 88f,
            useCenter = false,
            topLeft = Offset(center.x - innerRadius * 0.74f, center.y - innerRadius * 0.82f),
            size = Size(innerRadius * 1.30f, innerRadius * 1.20f),
            style = Stroke(width = innerRadius * 0.15f, cap = StrokeCap.Round)
        )

        clipPath(orbPath) {
            val backWaveA = buildWavePath(
                yOffset = -innerRadius * 0.16f,
                amplitude = innerRadius * 0.11f,
                frequency = 0.78f,
                phaseOffset = 0.42f
            )
            val backWaveB = buildWavePath(
                yOffset = innerRadius * 0.04f,
                amplitude = innerRadius * 0.14f,
                frequency = 0.92f,
                phaseOffset = 2.10f
            )
            val midWave = buildWavePath(
                yOffset = 0f,
                amplitude = innerRadius * 0.10f,
                frequency = 1.18f,
                phaseOffset = 0.18f
            )
            val frontWaveA = buildWavePath(
                yOffset = innerRadius * 0.02f,
                amplitude = innerRadius * 0.12f,
                frequency = 1.26f,
                phaseOffset = 1.06f
            )
            val frontWaveB = buildWavePath(
                yOffset = innerRadius * 0.06f,
                amplitude = innerRadius * 0.11f,
                frequency = 1.10f,
                phaseOffset = 2.32f
            )

            drawPath(
                path = backWaveA,
                brush = Brush.linearGradient(
                    colors = listOf(active.copy(alpha = 0.06f), support.copy(alpha = 0.18f), active.copy(alpha = 0.06f)),
                    start = Offset(left, baseY),
                    end = Offset(left + waveWidth, baseY)
                ),
                style = Stroke(width = innerRadius * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawPath(
                path = backWaveB,
                brush = Brush.linearGradient(
                    colors = listOf(support2.copy(alpha = 0.05f), support.copy(alpha = 0.16f), support2.copy(alpha = 0.05f)),
                    start = Offset(left, baseY),
                    end = Offset(left + waveWidth, baseY)
                ),
                style = Stroke(width = innerRadius * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawPath(
                path = midWave,
                color = highlightAlpha(0.05f, 0.08f),
                style = Stroke(width = innerRadius * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = midWave,
                brush = Brush.linearGradient(
                    colors = listOf(
                        active.copy(alpha = 0.20f),
                        highlightAlpha(0.28f, 0.82f),
                        support.copy(alpha = 0.24f)
                    ),
                    start = Offset(left, baseY),
                    end = Offset(left + waveWidth, baseY)
                ),
                style = Stroke(width = innerRadius * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawPath(
                path = frontWaveA,
                color = highlightAlpha(0.04f, 0.06f),
                style = Stroke(width = innerRadius * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = frontWaveA,
                brush = Brush.linearGradient(
                    colors = listOf(
                        active.copy(alpha = 0.16f),
                        highlightAlpha(0.24f, 0.74f),
                        support2.copy(alpha = 0.18f)
                    ),
                    start = Offset(left, baseY),
                    end = Offset(left + waveWidth, baseY)
                ),
                style = Stroke(width = innerRadius * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawPath(
                path = frontWaveB,
                brush = Brush.linearGradient(
                    colors = listOf(
                        support.copy(alpha = 0.10f),
                        highlightAlpha(0.18f, 0.50f),
                        active.copy(alpha = 0.12f)
                    ),
                    start = Offset(left, baseY),
                    end = Offset(left + waveWidth, baseY)
                ),
                style = Stroke(width = innerRadius * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawCircle(
            color = rimGlow.copy(alpha = 0.82f),
            radius = innerRadius,
            center = center,
            style = Stroke(width = 2.2f)
        )

        drawCircle(
            color = highlightAlpha(0.10f, 0.14f),
            radius = innerRadius * 0.985f,
            center = center,
            style = Stroke(width = 1.1f)
        )
    }
}


@Composable
private fun ScanningMusicState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            strokeWidth = 3.dp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Scanning your library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This may take a moment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchStrategyCard(
    query: String,
    musixmatchCount: Int,
    audiusCount: Int,
    ncsCount: Int,
    localCount: Int,
    isOnlineTracksEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Search results for \"${query.trim()}\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isOnlineTracksEnabled) {
                    "Musixmatch leads with lyrics-first matches, followed by Spinamp, NCS, and your local library."
                } else {
                    "Online providers are off, so results stay focused on lyrics-first matches and your local library."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchBadge(text = "Musixmatch $musixmatchCount")
                if (isOnlineTracksEnabled) {
                    SearchBadge(text = "Spinamp $audiusCount")
                    SearchBadge(text = "NCS $ncsCount")
                } else {
                    SearchBadge(text = "Online Off")
                }
                SearchBadge(text = "Local $localCount")
            }
        }
    }
}

@Composable
private fun SearchLoadingState(
    query: String,
    isOnlineTracksEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Searching for \"${query.trim()}\"",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isOnlineTracksEnabled) {
                "Checking Musixmatch first, then Spinamp, NCS, and your local library."
            } else {
                "Checking Musixmatch and your local library."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusixmatchResultCard(
    result: MusixmatchSearchResult,
    isOnlineTracksEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(result.artist, result.album.takeIf { it.isNotBlank() })
                    .filterNotNull()
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchBadge(text = if (result.hasSyncedLyrics) "Word-sync" else "Lyrics")
                if (result.localMatch != null) {
                    SearchBadge(text = SearchAvailabilityPlatform.LOCAL.displayName)
                }
                result.availableSources.forEach { source ->
                    SearchBadge(text = source.platform.displayName)
                }
            }
            Text(
                text = if (result.localMatch != null) {
                    "Tap to play the local track in the visualizer."
                } else if (result.availableSources.isNotEmpty()) {
                    "Tap to open lyrics, then jump into Spinamp or NCS from the visualizer."
                } else if (!isOnlineTracksEnabled) {
                    "Online provider playback is off, so this opens lyrics preview only."
                } else {
                    "Tap to open lyrics preview in the visualizer."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun NoSearchResultsState(
    query: String,
    isOnlineTracksEnabled: Boolean,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No matches for \"${query.trim()}\"",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isOnlineTracksEnabled) {
                "Try another song or artist. We checked Musixmatch, Spinamp, NCS, and your local library."
            } else {
                "Try another song or artist. We checked Musixmatch and your local library."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(onClick = onClearSearch) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Clear search")
        }
    }
}

private fun LazyListScope.songRowSection(
    queue: PlaybackQueue,
    title: String,
    onSongClick: (Song, PlaybackQueue) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    favoriteSongIds: Set<Long>,
    currentPlayingSongId: Long?,
    onOpenQueueClick: (PlaybackQueue) -> Unit,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    hideIfEmpty: Boolean = false,
    onSongLongClick: ((Song) -> Unit)? = null
) {
    if (hideIfEmpty && queue.songs.isEmpty()) {
        return
    }

    item {
        SectionHeader(
            title = title,
            onActionClick = { onOpenQueueClick(queue) },
            actionContentDescription = "Open ${queue.source.label}"
        )
    }

    item {
        val items = queue.songs
        if (items.isEmpty()) {
            EmptySectionCard(
                title = emptyTitle,
                message = emptyMessage,
                icon = emptyIcon,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                itemsIndexed(items, key = { index, song -> song.lazyItemKey(index) }) { _, song ->
                    SongCard(
                        song = song,
                        isPlaying = currentPlayingSongId == song.id,
                        isFavorite = favoriteSongIds.contains(song.id),
                        onFavoriteClick = { onToggleFavorite(song) },
                        onClick = { onSongClick(song, queue) },
                        onLongClick = onSongLongClick?.let { { it(song) } },
                        modifier = Modifier.width(156.dp)
                    )
                }
            }
        }
    }

    item { Spacer(modifier = Modifier.height(28.dp)) }
}
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionContentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Accent bar — anchors this header to the scroll row below it
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(999.dp)
                )
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                )
            }
        }
    }
}

@Composable
private fun EmptySectionCard(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.width(220.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongCard(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val cardShape = RoundedCornerShape(20.dp)
    val cardBorder = if (isPlaying) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    ElevatedCard(
        modifier = if (cardBorder != null) {
            modifier
                .border(cardBorder, cardShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        } else {
            modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        },
        shape = cardShape
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    val painter = rememberAsyncImagePainter(model = song.albumArtUri)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (painter.state !is AsyncImagePainter.State.Success) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    IconButton(onClick = onFavoriteClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isPlaying) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = "Playing",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyMusicState(
    hasPermission: Boolean,
    onScanClick: () -> Unit,
    onSelectFolderClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No Music Found",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (hasPermission) {
                "Tap the button below to scan your device for music"
            } else {
                "Grant permission to access your music library"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(
            onClick = onScanClick,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(
                imageVector = if (hasPermission) Icons.Default.LibraryMusic else Icons.Default.FolderOpen,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasPermission) "Scan Music" else "Grant Permission",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        if (!hasPermission) {
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onSelectFolderClick,
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Select Music Folder",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
