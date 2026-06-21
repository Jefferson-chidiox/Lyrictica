package com.oss.euphoriae.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lyrictica.audio.PlaybackQueue
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.ui.components.AddSongsToPlaylistDialog
import com.oss.euphoriae.ui.components.DeleteSongDialog
import com.oss.euphoriae.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSongClick: (Song, PlaybackQueue) -> Unit,
    onCreatePlaylistFromSongs: (String, List<Song>) -> Unit,
    onAddSongsToPlaylist: (Long, List<Song>) -> Unit,
    onAddToFavorites: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    currentPlayingSong: Song? = null,
    onScanClick: () -> Unit = {},
    onSelectFolder: (android.net.Uri) -> Unit = {},
    autoScrollTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val selectedSongIds = remember { mutableStateListOf<Long>() }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var addToPlaylistTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTargetSong by remember { mutableStateOf<Song?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var songsSortMode by rememberSaveable { mutableStateOf(SongsSortMode.ALPHABETICAL) }
    val sortedSongs = remember(songs, songsSortMode) {
        sortSongsForDisplay(songs, songsSortMode)
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
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

    val playbackQueue = remember(sortedSongs, searchQuery) {
        if (searchQuery.isBlank()) {
            PlaybackQueue.allSongs(sortedSongs)
        } else {
            PlaybackQueue.custom(
                key = "search:${searchQuery.trim().lowercase()}",
                label = "Search Results",
                songs = sortedSongs
            )
        }
    }

    val selectedSongs = sortedSongs.filter { selectedSongIds.contains(it.id) }
    val hasSelection = selectedSongs.isNotEmpty()
    var lastHandledScrollTrigger by remember { mutableIntStateOf(Int.MIN_VALUE) }

    fun clearSelection() {
        selectedSongIds.clear()
        showCreatePlaylistDialog = false
        showAddToPlaylistDialog = false
        addToPlaylistTarget = null
        deleteTargetSong = null
        newPlaylistName = ""
    }

    fun selectSong(song: Song) {
        if (!selectedSongIds.contains(song.id)) {
            selectedSongIds.add(song.id)
        }
    }

    fun toggleSongSelection(song: Song) {
        if (selectedSongIds.contains(song.id)) {
            selectedSongIds.remove(song.id)
        } else {
            selectedSongIds.add(song.id)
        }
    }

    val currentSongIds = remember(songs) { songs.mapTo(mutableSetOf()) { it.id } }
    LaunchedEffect(currentSongIds) {
        val removedAny = selectedSongIds.removeAll { it !in currentSongIds }
        if (selectedSongIds.isEmpty() && removedAny) {
            showCreatePlaylistDialog = false
            showAddToPlaylistDialog = false
            addToPlaylistTarget = null
            deleteTargetSong = null
            newPlaylistName = ""
        }
    }

    LaunchedEffect(autoScrollTrigger, currentPlayingSong?.id, sortedSongs.map { it.id }) {
        val targetSong = currentPlayingSong ?: return@LaunchedEffect
        if (autoScrollTrigger == lastHandledScrollTrigger) return@LaunchedEffect

        val songIndex = sortedSongs.indexOfFirst { it.id == targetSong.id }
        if (songIndex >= 0) {
            listState.animateScrollToItem(songIndex + 2)
            lastHandledScrollTrigger = autoScrollTrigger
        }
    }

    fun requestPermissionOrScan() {
        if (hasPermission) {
            onScanClick()
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (hasSelection) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedSongs.size} selected",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { showCreatePlaylistDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Create")
                                }
                                TextButton(
                                    onClick = { showAddToPlaylistDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Add")
                                }
                                TextButton(
                                    onClick = { selectedSongs.forEach(onAddToFavorites) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Favorites")
                                }
                            }
                        } else {
                            Text(
                                text = "Songs",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (hasSelection) {
                            IconButton(onClick = ::clearSelection) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            TextButton(
                                onClick = { songsSortMode = songsSortMode.next() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = songsSortMode.buttonLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search songs...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
        }
    ) { innerPadding ->
        if (sortedSongs.isEmpty()) {
            EmptySongsState(
                hasPermission = hasPermission,
                onActionClick = ::requestPermissionOrScan,
                onSelectFolderClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item(key = "songs_shuffle") {
                    FilledTonalButton(
                        onClick = {
                            if (sortedSongs.isNotEmpty()) {
                                onSongClick(sortedSongs.random(), playbackQueue)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Shuffle All (${sortedSongs.size} songs)",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }

                item(key = "songs_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${sortedSongs.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (hasSelection) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap more songs to select them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                itemsIndexed(sortedSongs, key = { index, song -> song.lazyItemKey(index) }) { _, song ->
                    val isSelected = selectedSongIds.contains(song.id)
                    SongListItem(
                        song = song,
                        onClick = {
                            if (hasSelection) {
                                toggleSongSelection(song)
                            } else {
                                onSongClick(song, playbackQueue)
                            }
                        },
                        onLongClick = {
                            selectSong(song)
                        },
                        isPlaying = currentPlayingSong?.id == song.id,
                        isSelected = isSelected,
                        onAddToPlaylists = { addToPlaylistTarget = song },
                        onCreatePlaylist = { onCreatePlaylistFromSongs(song.title, listOf(song)) },
                        onAddToFavorites = { onAddToFavorites(song) },
                        onDeletePermanently = { deleteTargetSong = song },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                newPlaylistName = ""
            },
            title = { Text("Create Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Create a playlist from ${selectedSongs.size} selected songs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newPlaylistName.trim()
                        if (trimmed.isNotBlank() && selectedSongs.isNotEmpty()) {
                            onCreatePlaylistFromSongs(trimmed, selectedSongs)
                            clearSelection()
                        }
                    },
                    enabled = newPlaylistName.isNotBlank() && selectedSongs.isNotEmpty()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Add to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No playlists yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Create one first, then add these songs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = playlist.name,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        onAddSongsToPlaylist(playlist.id, selectedSongs)
                                        clearSelection()
                                        showAddToPlaylistDialog = false
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (playlists.isEmpty()) {
                    TextButton(onClick = {
                        showAddToPlaylistDialog = false
                        showCreatePlaylistDialog = true
                    }) {
                        Text("Create Playlist")
                    }
                } else {
                    TextButton(onClick = { showAddToPlaylistDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            dismissButton = if (playlists.isEmpty()) {
                {
                    TextButton(onClick = { showAddToPlaylistDialog = false }) {
                        Text("Cancel")
                    }
                }
            } else {
                null
            }
        )
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

@Composable
private fun EmptySongsState(
    hasPermission: Boolean,
    onActionClick: () -> Unit,
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
            onClick = onActionClick,
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
            OutlinedButton(
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
