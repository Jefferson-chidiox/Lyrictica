package com.lyrictica.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lyrictica.karaoke.GameModeOption
import com.oss.euphoriae.data.model.Song

internal data class GameLaunchRequest(
    val songId: Long,
    val mode: GameModeOption,
    val nonce: Long = System.currentTimeMillis()
)

private data class GameLaunchCard(
    val option: GameModeOption,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val available: Boolean
)

@Composable
internal fun GameLaunchPickerDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSelectMode: (GameModeOption) -> Unit
) {
    val cards = listOf(
        GameLaunchCard(
            option = GameModeOption.KARAOKE,
            title = "Karaoke",
            subtitle = "Word-sync singing lane with instrumental playback.",
            accent = Color(0xFF22D3EE),
            available = true
        ),
        GameLaunchCard(
            option = GameModeOption.REVERSE_BEAT,
            title = "Reverse Beat",
            subtitle = "Fullscreen slice run with beat and lyric targets.",
            accent = Color(0xFFA78BFA),
            available = true
        ),
        GameLaunchCard(
            option = GameModeOption.ECHO_DROP,
            title = "Echo Drop",
            subtitle = "Reserved for a future call-and-response mode.",
            accent = Color(0xFFF59E0B),
            available = false
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF08111F),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Choose a game mode",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${song.title} • ${song.artist}",
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }

                    Text(
                        text = "Pick the lane and Lyrictica will jump straight into the selected song.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(cards) { card ->
                            GameLaunchModeCard(
                                card = card,
                                onClick = {
                                    if (card.available) {
                                        onSelectMode(card.option)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameLaunchModeCard(
    card: GameLaunchCard,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(230.dp)
            .heightIn(min = 154.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, card.accent.copy(alpha = if (card.available) 0.54f else 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(card.accent.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = card.accent.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = when (card.option) {
                            GameModeOption.KARAOKE -> Icons.Default.KeyboardVoice
                            GameModeOption.REVERSE_BEAT -> Icons.Default.TrackChanges
                            GameModeOption.ECHO_DROP -> Icons.Default.GraphicEq
                        },
                        contentDescription = null,
                        tint = card.accent,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(
                        text = card.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (card.available) "Ready now" else "Coming soon",
                        color = card.accent.copy(alpha = if (card.available) 1f else 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = card.subtitle,
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (card.available) "Tap to launch" else "Unavailable",
                color = card.accent.copy(alpha = if (card.available) 1f else 0.55f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
