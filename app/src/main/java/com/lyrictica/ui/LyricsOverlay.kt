package com.lyrictica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyrictica.lyrics.LyricsSync
import com.lyrictica.lyrics.LyricsUiState
import com.lyrictica.visualizer.VisualizerPalette
import kotlinx.coroutines.delay

@Composable
fun LyricsOverlay(
    lyricsState: LyricsUiState,
    theme: VisualizerPalette,
    onAutoFollowChange: (Boolean) -> Unit,
    onLineClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    seekPreviewPositionMs: Long? = null,
    isSeeking: Boolean = false,
    // Push the lyric panel lower so it sits between the top chrome and the wave bed.
    blankAreaEndFraction: Float = 0.92f,
    visibleLines: Int = 5,
    topReservedHeight: Dp = 0.dp,
    bottomReservedHeight: Dp = 0.dp,
    maxPanelHeightFraction: Float = 0.75f
) {
    val lineHeightDp: Dp = 72.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val parsed = lyricsState.parsed
    val listState = rememberLazyListState()
    val seekPositionMs = if (isSeeking) seekPreviewPositionMs else null
    val displayLineIndex = if (seekPositionMs != null && parsed?.isSynced == true) {
        LyricsSync.currentIndex(parsed.lines, seekPositionMs)
    } else {
        lyricsState.currentLineIndex
    }
    val displayLineProgress = if (seekPositionMs != null && parsed?.isSynced == true) {
        lyricsSeekProgress(parsed.lines, seekPositionMs, displayLineIndex)
    } else {
        0f
    }
    val seekTranslationPx by animateFloatAsState(
        targetValue = if (seekPositionMs != null && parsed?.isSynced == true) {
            -displayLineProgress * with(density) { lineHeightDp.toPx() }
        } else {
            0f
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 90),
        label = "lyricsSeekTranslation"
    )

    // Auto-hide error message after a few seconds for cleaner UI.
    var showError by remember { mutableStateOf(true) }
    val hasError = !lyricsState.isLoading && parsed == null && lyricsState.error != null
    LaunchedEffect(hasError, lyricsState.error) {
        if (hasError) {
            showError = true
            delay(3000)
            showError = false
        } else {
            showError = true
        }
    }

    // Auto-follow: keep current line centered in the 5-line viewport.
    // The LazyColumn already has symmetric content padding, so a zero-offset scroll
    // places the active line in the visual center instead of the last visible row.
    LaunchedEffect(displayLineIndex, lyricsState.autoFollow, parsed?.isSynced, isSeeking) {
        if (parsed == null || !parsed.isSynced) return@LaunchedEffect
        if (!lyricsState.autoFollow && !isSeeking) return@LaunchedEffect
        val idx = displayLineIndex
        if (idx < 0) return@LaunchedEffect

        if (isSeeking) {
            listState.animateScrollToItem(idx.coerceAtLeast(0))
        } else {
            listState.scrollToItem(idx.coerceAtLeast(0))
        }
    }

    // UX: if user scrolls manually, pause auto-follow (user will hit FOLLOW to resume).
    // Using vertical drag detection for smoother, more natural scrolling feel.
    val userDragModifier = Modifier.pointerInput(lyricsState.autoFollow, parsed?.isSynced, isSeeking) {
        if (!lyricsState.autoFollow || isSeeking) return@pointerInput
        if (parsed == null || !parsed.isSynced) return@pointerInput
        detectVerticalDragGestures(
            onDragStart = { onAutoFollowChange(false) },
            onVerticalDrag = { _, _ -> /* consume drag for smooth scrolling */ }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val desiredPanelHeight = lineHeightDp * visibleLines
        val panelHeight = minOf(desiredPanelHeight, maxHeight * maxPanelHeightFraction)
        val maxTopPadding = (maxHeight - panelHeight).coerceAtLeast(0.dp)
        val topPadding = if (topReservedHeight > 0.dp || bottomReservedHeight > 0.dp) {
            val availableHeight = (maxHeight - topReservedHeight - bottomReservedHeight).coerceAtLeast(panelHeight)
            val centeredTopPadding = topReservedHeight + ((availableHeight - panelHeight) / 2f)
            centeredTopPadding.coerceIn(0.dp, maxTopPadding)
        } else {
            val desiredTopPadding = ((maxHeight * blankAreaEndFraction) / 2f) - (panelHeight / 2f)
            desiredTopPadding.coerceIn(0.dp, maxTopPadding)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding, start = 20.dp, end = 20.dp)
                .height(panelHeight)
        ) {
            when {
                lyricsState.isLoading && parsed == null -> {
                    val subtleColor = theme.mutedText
                    Column(horizontalAlignment = Alignment.Start) {
                        CircularProgressIndicator(
                            color = subtleColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "LOADING LYRICS",
                            color = subtleColor,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                parsed == null -> {
                    if (showError) {
                        val subtleColor = theme.mutedText
                        Text(
                            text = lyricsState.error?.takeUnless { it.equals("null", ignoreCase = true) } ?: "",
                            color = subtleColor,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(horizontal = 0.dp)
                        )
                    }
                }

                else -> {
                    val inactiveColor = theme.lyricsInactive
                    val activeColor = theme.lyricsActive
                    // Content padding allows scrolling past first/last lines for better UX.
                    val contentPadding = lineHeightDp * ((visibleLines - 1) / 2f)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationY = seekTranslationPx }
                            .then(userDragModifier),
                        state = listState,
                        horizontalAlignment = Alignment.Start,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = contentPadding,
                            bottom = contentPadding
                        )
                    ) {
                        itemsIndexed(parsed.lines) { index, line ->
                            val isCurrent = parsed.isSynced && index == displayLineIndex
                            val clickModifier = if (onLineClick != null && line.timeMs != null) {
                                Modifier.clickable { onLineClick(line.timeMs) }
                            } else Modifier
                            Text(
                                text = line.text.takeUnless { it.equals("null", ignoreCase = true) }?.ifBlank { " " } ?: " ",
                                color = if (isCurrent) activeColor else inactiveColor,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Start,
                                softWrap = true,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = if (isCurrent) 0.62f else 0.46f),
                                        offset = Offset(0f, 3f),
                                        blurRadius = if (isCurrent) 18f else 12f
                                    )
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = lineHeightDp)
                                    .padding(vertical = 8.dp)
                                    .padding(horizontal = 0.dp)
                                    .then(clickModifier)
                            )
                        }
                    }

                    // FOLLOW affordance.
                    if (!lyricsState.autoFollow && parsed.isSynced && !isSeeking) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "FOLLOW",
                                color = theme.controlText.copy(alpha = 0.82f),
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.pillBackground)
                                    .border(1.dp, theme.pillBorder, RoundedCornerShape(10.dp))
                                    .clickable { onAutoFollowChange(true) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun lyricsSeekProgress(
    lines: List<com.lyrictica.lyrics.LyricLine>,
    positionMs: Long,
    currentIndex: Int
): Float {
    if (currentIndex < 0 || currentIndex >= lines.size) return 0f
    val currentTime = lines[currentIndex].timeMs ?: return 0f
    val nextTime = lines.getOrNull(currentIndex + 1)?.timeMs ?: return 0f
    val span = (nextTime - currentTime).coerceAtLeast(1L).toFloat()
    return ((positionMs - currentTime) / span).coerceIn(0f, 1f)
}
