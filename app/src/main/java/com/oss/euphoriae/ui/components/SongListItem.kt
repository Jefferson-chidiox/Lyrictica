package com.oss.euphoriae.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import com.oss.euphoriae.data.model.Song
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onAddToPlaylists: (() -> Unit)? = null,
    onCreatePlaylist: (() -> Unit)? = null,
    onAddToFavorites: (() -> Unit)? = null,
    onDeletePermanently: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }
    val border = if (isSelected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        color = containerColor,
        border = border
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = {
                Text(
                    text = "${song.artist} • ${song.album}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                val painter = rememberAsyncImagePainter(model = song.albumArtUri)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        isPlaying -> {
                            MusicVisualizer(
                                isPlaying = true,
                                barCount = 3,
                                barColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        isSelected -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        else -> {
                            Text(
                                text = formatDuration(song.duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (
                        onAddToPlaylists != null ||
                        onCreatePlaylist != null ||
                        onAddToFavorites != null ||
                        onDeletePermanently != null ||
                        onRemoveFromQueue != null
                    ) {
                        SongOverflowMenu(
                            onAddToPlaylists = onAddToPlaylists,
                            onCreatePlaylist = onCreatePlaylist,
                            onAddToFavorites = onAddToFavorites,
                            onDeletePermanently = onDeletePermanently,
                            onRemoveFromQueue = onRemoveFromQueue
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun SongOverflowMenu(
    onAddToPlaylists: (() -> Unit)?,
    onCreatePlaylist: (() -> Unit)?,
    onAddToFavorites: (() -> Unit)?,
    onDeletePermanently: (() -> Unit)?,
    onRemoveFromQueue: (() -> Unit)?
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options"
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            if (onAddToPlaylists != null) {
                DropdownMenuItem(
                    text = { Text("Add to playlists") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onAddToPlaylists()
                    }
                )
            }

            if (onCreatePlaylist != null) {
                DropdownMenuItem(
                    text = { Text("Create playlist") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onCreatePlaylist()
                    }
                )
            }

            if (onAddToFavorites != null) {
                DropdownMenuItem(
                    text = { Text("Add to favorites") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onAddToFavorites()
                    }
                )
            }

            if (onRemoveFromQueue != null) {
                DropdownMenuItem(
                    text = { Text("Remove from queue") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRemoveFromQueue()
                    }
                )
            }

            if (onDeletePermanently != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Delete permanently",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDeletePermanently()
                    }
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}
