package com.lyrictica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lyrictica.visualizer.VisualizerPalette
import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class SeekZone {
    LEFT,
    CENTER,
    RIGHT
}

internal enum class SeekDirection {
    BACKWARD,
    FORWARD
}

internal data class SeekVisualState(
    val zone: SeekZone,
    val anchorPx: Offset,
    val positionMs: Long,
    val durationMs: Long,
    val speedMultiplier: Float,
    val direction: SeekDirection
)

internal data class GestureSeekMetrics(
    val positionMs: Long,
    val speedMultiplier: Float,
    val direction: SeekDirection
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InvisibleWaveGestureOverlay(
    theme: VisualizerPalette,
    isEnabled: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekVisualStateChange: (SeekVisualState?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val zoneWidthPx = totalWidthPx / 3f

        Row(modifier = Modifier.fillMaxSize()) {
            InvisibleWaveGestureZone(
                theme = theme,
                zone = SeekZone.LEFT,
                zoneLeftPx = 0f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                isEnabled = isEnabled,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onClick = {},
                onDoubleClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPrevious()
                },
                onSeekTo = onSeekTo,
                onSeekVisualStateChange = onSeekVisualStateChange
            )

            InvisibleWaveGestureZone(
                theme = theme,
                zone = SeekZone.CENTER,
                zoneLeftPx = zoneWidthPx,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                isEnabled = isEnabled,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onClick = onTogglePlayPause,
                onDoubleClick = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onSeekVisualStateChange = onSeekVisualStateChange
            )

            InvisibleWaveGestureZone(
                theme = theme,
                zone = SeekZone.RIGHT,
                zoneLeftPx = zoneWidthPx * 2f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                isEnabled = isEnabled,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onClick = {},
                onDoubleClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNext()
                },
                onSeekTo = onSeekTo,
                onSeekVisualStateChange = onSeekVisualStateChange
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InvisibleWaveGestureZone(
    theme: VisualizerPalette,
    zone: SeekZone,
    zoneLeftPx: Float,
    modifier: Modifier = Modifier,
    isEnabled: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onSeekTo: (Long) -> Unit,
    onSeekVisualStateChange: (SeekVisualState?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val currentPositionState by rememberUpdatedState(currentPositionMs)
    val onSeekToState by rememberUpdatedState(onSeekTo)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    var previewPositionMs by remember {
        mutableStateOf(currentPositionMs.coerceIn(0L, safeDurationMs))
    }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPositionMs, safeDurationMs, dragging) {
        if (!dragging) {
            previewPositionMs = currentPositionMs.coerceIn(0L, safeDurationMs)
        }
    }

    val zoneGlow by animateFloatAsState(
        targetValue = if (pressed || dragging) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "seekZoneGlow"
    )

    val zoneColor = theme.sliderActiveTrack
    val zoneBrush = Brush.verticalGradient(
        colors = listOf(
            zoneColor.copy(alpha = 0.12f * zoneGlow),
            zoneColor.copy(alpha = 0.04f * zoneGlow),
            Color.Transparent
        )
    )

    Box(
        modifier = modifier
            .background(zoneBrush)
            .border(
                BorderStroke(
                    1.dp,
                    zoneColor.copy(alpha = 0.10f * zoneGlow)
                )
            )
            .combinedClickable(
                enabled = isEnabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick?.invoke() },
                onDoubleClick = { onDoubleClick?.invoke() }
            )
            .pointerInput(isEnabled, safeDurationMs, density, zoneLeftPx) {
                if (!isEnabled || safeDurationMs <= 0L) return@pointerInput

                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        dragging = true
                        previewPositionMs = currentPositionState.coerceIn(0L, safeDurationMs)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSeekVisualStateChange(
                            SeekVisualState(
                                zone = zone,
                                anchorPx = Offset(zoneLeftPx + startOffset.x, startOffset.y),
                                positionMs = previewPositionMs,
                                durationMs = safeDurationMs,
                                speedMultiplier = 1f,
                                direction = SeekDirection.FORWARD
                            )
                        )
                    },
                    onDrag = { change, dragAmount ->
                        val metrics = gestureSeekMetrics(
                            basePositionMs = previewPositionMs,
                            dragAmountPx = dragAmount.x,
                            durationMs = safeDurationMs,
                            density = density
                        )
                        previewPositionMs = metrics.positionMs
                        onSeekToState(metrics.positionMs)
                        onSeekVisualStateChange(
                            SeekVisualState(
                                zone = zone,
                                anchorPx = Offset(zoneLeftPx + change.position.x, change.position.y),
                                positionMs = metrics.positionMs,
                                durationMs = safeDurationMs,
                                speedMultiplier = metrics.speedMultiplier,
                                direction = metrics.direction
                            )
                        )
                    },
                    onDragEnd = {
                        dragging = false
                        onSeekVisualStateChange(null)
                    },
                    onDragCancel = {
                        dragging = false
                        onSeekVisualStateChange(null)
                    }
                )
            }
    )
}

@Composable
internal fun GestureTutorialDialog(
    theme: VisualizerPalette,
    isPlaying: Boolean,
    onDontShowAgain: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f))
                .padding(20.dp),
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
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(theme.sliderActiveTrack.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = theme.sliderActiveTrack,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Invisible wave controls",
                                color = theme.controlText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "The wave field is split into hidden touch zones.",
                                color = theme.mutedText,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    GestureTutorialItem(
                        icon = Icons.Default.SkipPrevious,
                        title = "Left",
                        description = "Double tap for previous",
                        accent = theme.sliderActiveTrack,
                        muted = theme.mutedText
                    )

                    GestureTutorialItem(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        title = "Center",
                        description = "Tap to play or pause",
                        accent = theme.sliderActiveTrack,
                        muted = theme.mutedText
                    )

                    GestureTutorialItem(
                        icon = Icons.Default.SkipNext,
                        title = "Right",
                        description = "Double tap for next",
                        accent = theme.sliderActiveTrack,
                        muted = theme.mutedText
                    )

                    GestureTutorialItem(
                        icon = Icons.Default.MusicNote,
                        title = "Drag",
                        description = "Hold and drag to seek faster — the timeline and lyrics move with you.",
                        accent = theme.sliderActiveTrack,
                        muted = theme.mutedText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDontShowAgain) {
                            Text("Don't show again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureTutorialItem(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color,
    muted: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                color = muted.copy(alpha = 0.90f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun gestureSeekMetrics(
    basePositionMs: Long,
    dragAmountPx: Float,
    durationMs: Long,
    density: Float
): GestureSeekMetrics {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    if (safeDurationMs == 0L || density <= 0f) {
        return GestureSeekMetrics(
            positionMs = basePositionMs.coerceIn(0L, safeDurationMs),
            speedMultiplier = 1f,
            direction = SeekDirection.FORWARD
        )
    }

    val dragDp = dragAmountPx / density
    val direction = if (dragDp < 0f) SeekDirection.BACKWARD else SeekDirection.FORWARD
    val speedMultiplier = ((abs(dragDp) / 24f).toInt() + 1).coerceIn(1, 4).toFloat()
    val deltaMs = (dragDp * 12f * speedMultiplier).roundToLong()
    return GestureSeekMetrics(
        positionMs = (basePositionMs + deltaMs).coerceIn(0L, safeDurationMs),
        speedMultiplier = speedMultiplier,
        direction = direction
    )
}

internal fun gestureSeekPosition(
    basePositionMs: Long,
    dragAmountPx: Float,
    durationMs: Long,
    density: Float
): Long = gestureSeekMetrics(
    basePositionMs = basePositionMs,
    dragAmountPx = dragAmountPx,
    durationMs = durationMs,
    density = density
).positionMs
