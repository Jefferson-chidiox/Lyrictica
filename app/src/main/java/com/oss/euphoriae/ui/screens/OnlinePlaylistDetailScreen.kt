package com.oss.euphoriae.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.ui.components.OnlineTrackListItem
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    loadTracks: () -> Flow<List<OnlineTrack>>,
    onBackClick: () -> Unit,
    onTrackClick: (track: OnlineTrack, queue: List<OnlineTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    val payload by produceState(
        initialValue = OnlinePlaylistPayload(loading = true),
        key1 = playlistId
    ) {
        try {
            loadTracks().collect { tracks ->
                value = OnlinePlaylistPayload(
                    loading = false,
                    tracks = tracks
                )
            }
        } catch (error: Exception) {
            value = OnlinePlaylistPayload(
                loading = false,
                error = error.message ?: "Could not load mix tracks."
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = playlistName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Community Mix",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        when {
            payload.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            payload.error != null -> {
                OnlinePlaylistEmptyState(
                    title = "Could not load this mix",
                    message = payload.error ?: "Unknown error",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            payload.tracks.isEmpty() -> {
                OnlinePlaylistEmptyState(
                    title = "No tracks in this mix",
                    message = "This online playlist doesn't contain any playable tracks right now.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            else -> {
                val playableTracks = remember(payload.tracks) {
                    payload.tracks.filter { it.isPlayable }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item(key = "mix_actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (playableTracks.isNotEmpty()) {
                                        onTrackClick(playableTracks.first(), playableTracks)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = playableTracks.isNotEmpty()
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
                                    if (playableTracks.isNotEmpty()) {
                                        onTrackClick(playableTracks.random(), playableTracks.shuffled())
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = playableTracks.isNotEmpty()
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

                    items(playableTracks, key = { it.provider.name + it.id + it.streamUrl }) { track ->
                        OnlineTrackListItem(
                            track = track,
                            onClick = { onTrackClick(track, playableTracks) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlinePlaylistEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class OnlinePlaylistPayload(
    val loading: Boolean = false,
    val tracks: List<OnlineTrack> = emptyList(),
    val error: String? = null
)
