package com.lyrictica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyrictica.visualizer.VisualizerPalette
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun SeekFeedbackHud(
    seekVisualState: SeekVisualState?,
    theme: VisualizerPalette,
    modifier: Modifier = Modifier
) {
    var lastState by remember { mutableStateOf<SeekVisualState?>(null) }
    LaunchedEffect(seekVisualState) {
        seekVisualState?.let { lastState = it }
    }

    AnimatedVisibility(
        visible = seekVisualState != null,
        enter = fadeIn(animationSpec = tween(90)) + scaleIn(initialScale = 0.96f, animationSpec = tween(90)),
        exit = fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(120)),
        modifier = modifier
    ) {
        val activeState = lastState ?: return@AnimatedVisibility
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            val contentWidth: Dp = minOf(maxWidth * 0.82f, 280.dp)
            val timelineHeight: Dp = 44.dp
            val dropWidth: Dp = 88.dp
            val dropHeight: Dp = 94.dp
            val gap: Dp = 6.dp
            val totalHeightPx = with(density) { (timelineHeight + gap + dropHeight).toPx() }
            val contentWidthPx = with(density) { contentWidth.toPx() }
            val marginPx = with(density) { 12.dp.toPx() }
            val xPx = (activeState.anchorPx.x - contentWidthPx / 2f)
                .coerceIn(marginPx, max(marginPx, screenWidthPx - contentWidthPx - marginPx))
            val yPx = (activeState.anchorPx.y - totalHeightPx)
                .coerceIn(marginPx, max(marginPx, screenHeightPx - totalHeightPx - marginPx))

            Column(
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .width(contentWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                SeekTimelineCard(
                    theme = theme,
                    positionMs = activeState.positionMs,
                    durationMs = activeState.durationMs,
                    direction = activeState.direction,
                    speedMultiplier = activeState.speedMultiplier,
                    modifier = Modifier
                        .size(width = contentWidth, height = timelineHeight)
                )

                SeekSpeedDrop(
                    theme = theme,
                    speedMultiplier = activeState.speedMultiplier,
                    direction = activeState.direction,
                    modifier = Modifier.size(width = dropWidth, height = dropHeight)
                )
            }
        }
    }
}

@Composable
private fun SeekTimelineCard(
    theme: VisualizerPalette,
    positionMs: Long,
    durationMs: Long,
    direction: SeekDirection,
    speedMultiplier: Float,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f
    val directionAlpha by animateFloatAsState(
        targetValue = if (speedMultiplier > 1f) 1f else 0.85f,
        animationSpec = tween(80),
        label = "seekTimelineAlpha"
    )
    val directionLabel = when (direction) {
        SeekDirection.BACKWARD -> "REWIND"
        SeekDirection.FORWARD -> "FORWARD"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, theme.pillBorder.copy(alpha = 0.50f)),
        shadowElevation = 14.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatSeekTime(positionMs),
                    color = theme.controlText.copy(alpha = 0.92f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = directionLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = theme.sliderActiveTrack.copy(alpha = 0.16f * directionAlpha),
                        labelColor = theme.sliderActiveTrack
                    ),
                    modifier = Modifier.height(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatSeekTime(durationMs),
                    color = theme.controlText.copy(alpha = 0.52f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(theme.sliderInactiveTrack.copy(alpha = 0.65f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    theme.sliderActiveTrack.copy(alpha = 0.95f),
                                    theme.ambientGlow.copy(alpha = 0.82f)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun SeekSpeedDrop(
    theme: VisualizerPalette,
    speedMultiplier: Float,
    direction: SeekDirection,
    modifier: Modifier = Modifier
) {
    val label = "${speedMultiplier.roundToInt().coerceIn(1, 4)}x"
    val tint = when (direction) {
        SeekDirection.BACKWARD -> theme.ambientGlow
        SeekDirection.FORWARD -> theme.sliderActiveTrack
    }
    val dropBrush = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = 0.98f),
            tint.copy(alpha = 0.80f),
            tint.copy(alpha = 0.72f)
        )
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(UpsideDownDropShape)
                .background(dropBrush)
                .padding(top = 10.dp, bottom = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (direction == SeekDirection.BACKWARD) Icons.Default.SkipPrevious else Icons.Default.SkipNext,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

private val UpsideDownDropShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, h)
    cubicTo(w * 0.24f, h * 0.72f, 0f, h * 0.42f, 0f, h * 0.18f)
    cubicTo(0f, h * 0.05f, w * 0.18f, 0f, w * 0.5f, 0f)
    cubicTo(w * 0.82f, 0f, w, h * 0.05f, w, h * 0.18f)
    cubicTo(w, h * 0.42f, w * 0.76f, h * 0.72f, w * 0.5f, h)
    close()
}

private fun formatSeekTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
