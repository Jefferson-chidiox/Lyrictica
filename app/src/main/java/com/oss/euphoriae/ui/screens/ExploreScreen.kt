package com.oss.euphoriae.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oss.euphoriae.data.model.OnlineTrack
import kotlinx.coroutines.flow.Flow
import com.oss.euphoriae.explore.ExploreCatalog
import com.oss.euphoriae.explore.ExploreCategory
import com.oss.euphoriae.explore.ExploreOption
import com.oss.euphoriae.explore.ExploreSection
import com.oss.euphoriae.ui.components.OnlineTrackListItem

private fun getOptionAccentColor(title: String): Color {
    val hash = title.hashCode()
    val colors = listOf(
        Color(0xFFFF5722), // Orange
        Color(0xFF2196F3), // Blue
        Color(0xFF9C27B0), // Purple
        Color(0xFFFFC107), // Yellow
        Color(0xFFE53935), // Red
        Color(0xFF4CAF50), // Green
        Color(0xFF00BCD4), // Cyan
        Color(0xFFE91E63), // Pink
        Color(0xFF8BC34A), // Lime Green
        Color(0xFF3F51B5)  // Indigo
    )
    val index = kotlin.math.abs(hash) % colors.size
    return colors[index]
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    onOpenOption: (sectionId: String, optionId: String) -> Unit,
    onlineTracksEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Explore",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (!onlineTracksEnabled) {
                item {
                    ExploreEmptyState(
                        title = "Online tracks are off",
                        message = "Turn them back on in Settings to browse Spinamp and NCS discovery lanes.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            items(ExploreCatalog.sections, key = { it.id }) { section ->
                val pairs = remember(section) { section.options.chunked(2) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    pairs.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { option ->
                                ExploreOptionCard(
                                    option = option,
                                    onClick = { onOpenOption(section.id, option.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun ExploreOptionCard(
    option: ExploreOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = remember(option.title) { getOptionAccentColor(option.title) }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    val textColor = MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = cardColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreResultsScreen(
    sectionId: String,
    optionId: String,
    loadTracks: (sectionId: String, optionId: String) -> Flow<List<OnlineTrack>>,
    onBackClick: () -> Unit,
    onTrackClick: (track: OnlineTrack, queue: List<OnlineTrack>) -> Unit,
    onlineTracksEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val option = remember(sectionId, optionId) {
        ExploreCatalog.findOption(sectionId, optionId)
    }
    val payload by produceState(
        initialValue = ExploreResultsPayload(loading = true),
        key1 = sectionId,
        key2 = optionId
    ) {
        if (!onlineTracksEnabled) {
            value = ExploreResultsPayload(loading = false, tracks = emptyList())
            return@produceState
        }

        try {
            loadTracks(sectionId, optionId).collect { tracks ->
                value = ExploreResultsPayload(
                    loading = false,
                    tracks = tracks
                )
            }
        } catch (error: Exception) {
            value = ExploreResultsPayload(
                loading = false,
                error = error.message ?: "Could not load explore results."
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
                            text = option?.title ?: "Explore Results",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = option?.subtitle ?: "Mixed online discovery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("Back")
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
                ExploreEmptyState(
                    title = "Could not load this lane",
                    message = payload.error ?: "Unknown error",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            payload.tracks.isEmpty() -> {
                ExploreEmptyState(
                    title = if (onlineTracksEnabled) "No tracks yet" else "Online tracks are off",
                    message = if (onlineTracksEnabled) {
                        "Try another lane or refresh later for a different blend."
                    } else {
                        "Turn them back on in Settings to browse remote discovery lanes."
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "${payload.tracks.size} streamable results",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(payload.tracks, key = { it.provider.name + it.id + it.streamUrl }) { track ->
                        OnlineTrackListItem(
                            track = track,
                            onClick = { onTrackClick(track, payload.tracks) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreEmptyState(
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

private data class ExploreResultsPayload(
    val loading: Boolean = false,
    val tracks: List<OnlineTrack> = emptyList(),
    val error: String? = null
)
