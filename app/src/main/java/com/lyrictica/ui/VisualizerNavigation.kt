package com.lyrictica.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lyrictica.visualizer.VisualizerPalette
import com.oss.euphoriae.BuildConfig

@Composable
internal fun VisualizerNavRow(
    theme: VisualizerPalette,
    videoSelected: Boolean,
    gameSelected: Boolean,
    aboutSelected: Boolean,
    onMenuClick: () -> Unit,
    onVideoClick: () -> Unit,
    onGameClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeContainer = theme.sliderActiveTrack.copy(alpha = 0.20f)
    val inactiveContainer = Color.White.copy(alpha = 0.05f)
    val activeContent = Color.White
    val inactiveContent = theme.controlText.copy(alpha = 0.74f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavIconButton(
            icon = Icons.Default.Menu,
            contentDescription = "Songs",
            selected = false,
            activeContainer = activeContainer,
            inactiveContainer = inactiveContainer,
            activeContent = activeContent,
            inactiveContent = inactiveContent,
            onClick = onMenuClick
        )
        NavIconButton(
            icon = Icons.Default.OndemandVideo,
            contentDescription = "Video",
            selected = videoSelected,
            activeContainer = activeContainer,
            inactiveContainer = inactiveContainer,
            activeContent = activeContent,
            inactiveContent = inactiveContent,
            onClick = onVideoClick
        )
        NavIconButton(
            icon = Icons.Default.SportsEsports,
            contentDescription = "Game",
            selected = gameSelected,
            activeContainer = activeContainer,
            inactiveContainer = inactiveContainer,
            activeContent = activeContent,
            inactiveContent = inactiveContent,
            onClick = onGameClick
        )
        NavIconButton(
            icon = Icons.Default.Info,
            contentDescription = "About",
            selected = aboutSelected,
            activeContainer = activeContainer,
            inactiveContainer = inactiveContainer,
            activeContent = activeContent,
            inactiveContent = inactiveContent,
            onClick = onAboutClick
        )
    }
}

@Composable
private fun NavIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    activeContainer: Color,
    inactiveContainer: Color,
    activeContent: Color,
    inactiveContent: Color,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (selected) activeContainer else inactiveContainer,
            contentColor = if (selected) activeContent else inactiveContent
        ),
        modifier = Modifier.size(46.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun FeatureDialogShell(
    theme: VisualizerPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
) {
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
                .background(Color.Black.copy(alpha = 0.50f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = theme.backgroundBottom.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, theme.pillBorder.copy(alpha = 0.50f)),
                shadowElevation = 24.dp,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(theme.sliderActiveTrack.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = theme.sliderActiveTrack,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = theme.controlText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                color = theme.mutedText,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            actions()
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = theme.controlText
                                )
                            }
                        }
                    }

                    body()
                }
            }
        }
    }
}

@Composable
internal fun AboutDialog(
    theme: VisualizerPalette,
    onDismiss: () -> Unit
) {
    FeatureDialogShell(
        theme = theme,
        icon = Icons.Default.Info,
        title = "About Lyrictica",
        subtitle = "Visualizer-first playback, lyrics, and future play spaces.",
        onDismiss = onDismiss
    ) {
        Text(
            text = "Lyrictica keeps the music, the motion, and the controls on one polished screen.",
            color = theme.controlText,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = theme.sliderActiveTrack.copy(alpha = 0.14f),
                labelColor = theme.sliderActiveTrack
            )
        )

        Text(
            text = "Lyrics, video, game, and about all stay inside the visualizer shell.",
            color = theme.mutedText,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
internal fun GameDialog(
    theme: VisualizerPalette,
    onDismiss: () -> Unit
) {
    FeatureDialogShell(
        theme = theme,
        icon = Icons.Default.SportsEsports,
        title = "Game",
        subtitle = "A playful surface for the visualizer is coming soon.",
        onDismiss = onDismiss
    ) {
        Text(
            text = "This screen is reserved for a future visualizer game mode.",
            color = theme.controlText,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Text(
            text = "For now, playback stays uninterrupted and the full visualizer remains the focus.",
            color = theme.mutedText,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        TextButton(onClick = onDismiss) {
            Text("Got it")
        }
    }
}
