package com.oss.euphoriae.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.oss.euphoriae.data.model.GameRecommendationReason
import com.oss.euphoriae.data.model.GameScoreRecord
import com.oss.euphoriae.data.model.GameSongRecommendation
import com.oss.euphoriae.data.model.Song
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GamesScreen(
    topScores: List<GameScoreRecord>,
    recommendations: List<GameSongRecommendation>,
    currentPlayingSong: Song?,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF07111F),
            Color(0xFF111827),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.12f),
                    radius = size.minDimension * 0.34f
                ),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.22f, size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF22D3EE).copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.28f),
                    radius = size.minDimension * 0.30f
                ),
                radius = size.minDimension * 0.30f,
                center = Offset(size.width * 0.82f, size.height * 0.28f)
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Games",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Jump straight from discovery into play mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = innerPadding.calculateTopPadding() + 10.dp,
                    end = 20.dp,
                    bottom = innerPadding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "leaderboard") {
                    LeaderboardCard(topScores = topScores)
                }

                item(key = "recommendations_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Recommended next runs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Built from your most played songs, recent additions, and tracks living inside your playlists.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.76f)
                        )
                    }
                }

                if (recommendations.isEmpty()) {
                    item(key = "recommendations_empty") {
                        EmptyRecommendationsCard()
                    }
                } else {
                    items(
                        items = recommendations.take(12),
                        key = { recommendation -> recommendation.song.lazyItemKey(0) }
                    ) { recommendation ->
                        GameRecommendationCard(
                            recommendation = recommendation,
                            isCurrent = currentPlayingSong?.id == recommendation.song.id,
                            onClick = { onSongClick(recommendation.song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardCard(topScores: List<GameScoreRecord>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.10f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arcade leaderboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Top three highest scores across every finished run.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }

            if (topScores.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.16f))
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "No scores yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Pick a recommended song, choose a game mode, and claim the first podium slot.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            } else {
                PodiumChart(topScores = topScores)
            }
        }
    }
}

@Composable
private fun PodiumChart(topScores: List<GameScoreRecord>) {
    val ordered = listOf(
        topScores.getOrNull(1),
        topScores.getOrNull(0),
        topScores.getOrNull(2)
    )
    val maxScore = max(topScores.maxOfOrNull { it.score } ?: 1, 1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 210.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ordered.forEachIndexed { index, score ->
                val podiumRank = when (index) {
                    0 -> 2
                    1 -> 1
                    else -> 3
                }
                val ratio = (score?.score?.toFloat() ?: 0f) / maxScore.toFloat()
                PodiumBar(
                    rank = podiumRank,
                    score = score,
                    heightRatio = ratio
                )
            }
        }
    }
}

@Composable
private fun RowScope.PodiumBar(
    rank: Int,
    score: GameScoreRecord?,
    heightRatio: Float
) {
    val podiumHeight = when (rank) {
        1 -> 172.dp
        2 -> 136.dp
        else -> 112.dp
    }
    val accent = when (rank) {
        1 -> Color(0xFFF59E0B)
        2 -> Color(0xFF8B5CF6)
        else -> Color(0xFF22D3EE)
    }

    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScoreAvatar(score = score, accent = accent)

        Text(
            text = score?.songTitle ?: "Open slot",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Text(
            text = score?.modeLabel ?: "Waiting",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.66f),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((podiumHeight * heightRatio.coerceIn(0.22f, 1f)).coerceAtLeast(72.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.94f), accent.copy(alpha = 0.52f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF020617)
                )
                Text(
                    text = score?.score?.toString() ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF020617)
                )
            }
        }
    }
}

@Composable
private fun ScoreAvatar(score: GameScoreRecord?, accent: Color) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.16f)
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            if (score?.songArtUri != null) {
                AsyncImage(
                    model = score.songArtUri,
                    contentDescription = score.songTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyRecommendationsCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.76f)
            )
            Text(
                text = "No recommendations yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "Play more songs or build a playlist to let the games hub surface better matches.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.70f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameRecommendationCard(
    recommendation: GameSongRecommendation,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val song = recommendation.song

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCurrent) Color(0xFF1D4ED8).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.76f)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrent) {
                        GlassBadge(text = "Now playing", accent = Color(0xFF60A5FA))
                    }
                }

                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recommendation.reasons.forEach { reason ->
                        GlassBadge(
                            text = reason.label,
                            accent = when (reason) {
                                GameRecommendationReason.MOST_PLAYED -> Color(0xFFF59E0B)
                                GameRecommendationReason.RECENTLY_ADDED -> Color(0xFF22D3EE)
                                GameRecommendationReason.FROM_PLAYLISTS -> Color(0xFFA78BFA)
                            }
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.84f)
            )
        }
    }
}

@Composable
private fun GlassBadge(
    text: String,
    accent: Color
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
