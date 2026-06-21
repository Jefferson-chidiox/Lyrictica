package com.lyrictica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyrictica.karaoke.GameModeOption
import com.lyrictica.visualizer.VisualizerPalette

private data class GameCard(
    val option: GameModeOption,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val available: Boolean
)

@Composable
internal fun GameModeDialog(
    theme: VisualizerPalette,
    selectedGame: GameModeOption?,
    onDismiss: () -> Unit,
    onSelectGame: (GameModeOption) -> Unit
) {
    val cards = remember(theme) {
        listOf(
            GameCard(
                option = GameModeOption.KARAOKE,
                title = "Karaoke",
                subtitle = "Word-sync instrumental sing-along.",
                accent = theme.sliderActiveTrack,
                available = true
            ),
            GameCard(
                option = GameModeOption.REVERSE_BEAT,
                title = "Reverse Beat",
                subtitle = "Fullscreen lyric and beat slicing.",
                accent = theme.lyricsActive,
                available = true
            ),
            GameCard(
                option = GameModeOption.ECHO_DROP,
                title = "Echo Drop",
                subtitle = "Future call-and-response lane.",
                accent = theme.ambientGlow,
                available = false
            )
        )
    }

    FeatureDialogShell(
        theme = theme,
        icon = Icons.Default.GraphicEq,
        title = "Game Selector",
        subtitle = "Choose the play space for the selected song.",
        onDismiss = onDismiss
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards) { card ->
                GameModeCard(
                    card = card,
                    selected = selectedGame == card.option,
                    onClick = {
                        if (card.available) {
                            onSelectGame(card.option)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GameModeCard(
    card: GameCard,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .heightIn(min = 132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) card.accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) card.accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(card.accent.copy(alpha = 0.14f), CircleShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (card.option) {
                            GameModeOption.KARAOKE -> Icons.Default.KeyboardVoice
                            GameModeOption.REVERSE_BEAT -> Icons.Default.TrackChanges
                            GameModeOption.ECHO_DROP -> Icons.Default.GraphicEq
                        },
                        contentDescription = null,
                        tint = card.accent
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = card.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (card.available) "Ready" else "Coming soon",
                        color = card.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = card.subtitle,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.heightIn(min = 16.dp))
        }
    }
}
