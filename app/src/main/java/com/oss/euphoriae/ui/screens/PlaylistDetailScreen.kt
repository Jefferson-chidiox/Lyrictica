package com.oss.euphoriae.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lyrictica.audio.PlaybackQueue
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.ui.components.AddSongsToPlaylistDialog
import com.oss.euphoriae.ui.components.DeleteSongDialog
import com.oss.euphoriae.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    playlists: List<Playlist>,
    currentPlayingSong: Song?,
    onBackClick: () -> Unit,
    onSongClick: (Song, PlaybackQueue) -> Unit,
    onCreatePlaylistFromSongs: (String, List<Song>) -> Unit,
    onAddSongsToPlaylist: (Long, List<Song>) -> Unit,
    onAddToFavorites: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onDeletePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var addToPlaylistTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTargetSong by remember { mutableStateOf<Song?>(null) }
    val selectedSongIds = remember { androidx.compose.runtime.snapshots.SnapshotStateList<Long>() }
    val hasSelection = selectedSongIds.isNotEmpty()

    fun toggleSongSelection(song: Song) {
        if (selectedSongIds.contains(song.id)) {
            selectedSongIds.remove(song.id)
        } else {
            selectedSongIds.add(song.id)
        }
    }

    val playbackQueue = remember(playlist.id, playlist.name, songs) {
        PlaybackQueue.playlist(playlist, songs)
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = playlist.name,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${songs.size} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Playlist"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (songs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No songs in this playlist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add songs from the Now Playing screen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item(key = "playlist_actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { 
                                if (songs.isNotEmpty()) {
                                    onSongClick(songs.first(), playbackQueue)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All")
                        }
                        
                        OutlinedButton(
                            onClick = { 
                                if (songs.isNotEmpty()) {
                                    onSongClick(songs.random(), playbackQueue)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle")
                        }
                    }
                }
                
                itemsIndexed(songs, key = { index, song -> song.lazyItemKey(index) }) { _, song ->
                    SongListItem(
                        song = song,
                        onClick = {
                            if (hasSelection) {
                                toggleSongSelection(song)
                            } else {
                                onSongClick(song, playbackQueue)
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
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Playlist?") },
            text = { 
                Text("Are you sure you want to delete \"${playlist.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (addToPlaylistTarget != null) {
        val targetSong = addToPlaylistTarget!!
        AddSongsToPlaylistDialog(
            playlists = playlists,
            songs = listOf(targetSong),
            onDismiss = { addToPlaylistTarget = null },
            onPlaylistSelected = { playlistItem ->
                onAddSongsToPlaylist(playlistItem.id, listOf(targetSong))
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
