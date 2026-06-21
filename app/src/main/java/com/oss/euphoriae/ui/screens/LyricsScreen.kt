package com.oss.euphoriae.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oss.euphoriae.data.model.LyricLine
import com.oss.euphoriae.data.model.Lyrics

@Composable
fun LyricsScreen(
    lyrics: Lyrics?,
    currentLyricIndex: Int,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to current line
    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex >= 0 && lyrics != null) {
            // center the item by offsetting
            // 300dp padding at top, so index 0 is at 300dp. 
            // scroll to item tries to put it at 0 + padding.
            listState.animateScrollToItem(currentLyricIndex, scrollOffset = -300) 
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)) // Darken album art
            .clickable(enabled = false) {} 
    ) {
        if (lyrics == null || lyrics.lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No lyrics available",
                    style = MaterialTheme.typography.displaySmall, // Bigger text
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 400.dp, bottom = 400.dp), // Massive padding for centering
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp) // Spacing between lines
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isActive = index == currentLyricIndex
                    val isPast = index < currentLyricIndex
                    
                    val targetAlpha = if (isActive) 1f else 0.5f
                    val targetScale = if (isActive) 1.05f else 0.95f
                    val targetBlur = if (isActive) 0.dp else 2.dp // Blur inactive lines slightly
                    
                    val alpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(500))
                    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(500))
                    // Compose Blur doesn't animate well usually, but let's try or just fallback to opacity
                    
                    LyricItem(
                        line = line,
                        isActive = isActive,
                        modifier = Modifier
                            .graphicsLayer(
                                alpha = alpha,
                                scaleX = scale,
                                scaleY = scale
                            )
                            //.blur(if (isActive) 0.dp else 1.dp) // Blur can be expensive
                            .padding(horizontal = 32.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
        
    }
}

@Composable
fun LyricItem(
    line: LyricLine,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = line.text,
        style = MaterialTheme.typography.headlineMedium.copy(
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold
        ),
        color = Color.White, // Color handled by alpha wrapper
        textAlign = TextAlign.Start, // Apple Music aligns left usually
        modifier = modifier
    )
}
