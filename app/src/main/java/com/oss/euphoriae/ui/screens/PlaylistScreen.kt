package com.oss.euphoriae.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.ui.components.PlaylistCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onOpenAllSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: ((Playlist) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var longPressedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Playlists",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = {
                    Icon(Icons.Default.Add, contentDescription = null)
                },
                text = { Text("New Playlist") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(
                key = "all_songs_card",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
            ) {
                PlaylistShortcutCard(
                    title = "All Songs",
                    subtitle = "Open the full library list from here instead of the bottom nav.",
                    icon = Icons.Default.LibraryMusic,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onOpenAllSongs
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item(
                key = "albums_card",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
            ) {
                PlaylistShortcutCard(
                    title = "Albums",
                    subtitle = "Browse the album wall from here and build playlists from whole releases.",
                    icon = Icons.Default.Album,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.74f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenAlbums
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item(
                key = "playlists_header",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
            ) {
                 Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (playlists.isEmpty()) {
                item(
                    key = "playlists_empty",
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No playlists yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Create your first playlist to organize your music.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onLongClick = if (onDeletePlaylist != null) {
                            { longPressedPlaylist = playlist }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item(
                key = "playlists_footer",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
            ) {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCreateDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (longPressedPlaylist != null && onDeletePlaylist != null) {
        val target = longPressedPlaylist!!
        AlertDialog(
            onDismissRequest = { longPressedPlaylist = null },
            icon = {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Playlist?") },
            text = {
                Text("Are you sure you want to delete \"${target.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        longPressedPlaylist = null
                        onDeletePlaylist(target)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { longPressedPlaylist = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaylistShortcutCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.84f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor
            )
        }
    }
}
