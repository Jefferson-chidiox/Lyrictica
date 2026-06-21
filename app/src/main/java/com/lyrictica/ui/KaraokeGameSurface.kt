package com.lyrictica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lyrictica.karaoke.KaraokeChallengeProfile
import com.lyrictica.karaoke.KaraokeSessionPhase
import com.lyrictica.karaoke.KaraokeUiState
import com.lyrictica.lyrics.ParsedLyrics
import com.lyrictica.visualizer.VisualizerPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KaraokeGameSurface(
    theme: VisualizerPalette,
    availableLyrics: ParsedLyrics?,
    karaokeState: KaraokeUiState,
    onChallengeToggle: (Boolean) -> Unit,
    onChallengeProfileSelected: (KaraokeChallengeProfile) -> Unit,
    onStartKaraoke: () -> Unit,
    onExitGameMode: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val challengeModeAvailable = false
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }

    val parsed = availableLyrics
    val hasWordSync = parsed?.hasWordSync == true
    val canStart = hasWordSync &&
        !karaokeState.preparationInProgress &&
        !karaokeState.melodyLoading &&
        (karaokeState.stemProviderAvailable || karaokeState.backingTrackReady)

    // Heart loss feedback mechanics
    val lives = karaokeState.livesRemaining
    val active = karaokeState.isSessionActive
    var previousLives by remember { mutableStateOf(lives) }
    var showFlash by remember { mutableStateOf(false) }

    LaunchedEffect(lives, active) {
        if (active && lives < previousLives && lives < karaokeState.maxLives) {
            showFlash = true
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
                toneGen.startTone(android.media.ToneGenerator.TONE_SUP_ERROR, 350)
            } catch (e: Exception) {
                // Ignore failure
            }
        }
        previousLives = lives
    }

    // Full-screen crimson warning flash on life loss
    if (showFlash) {
        val alpha by animateFloatAsState(
            targetValue = 0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 800),
            finishedListener = { showFlash = false },
            label = "flashAnimation"
        )
        Popup(
            onDismissRequest = {},
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.22f * alpha))
            )
        }
    }

    // Outer Container is transparent and borderless
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!karaokeState.isSessionActive) {
                // PREP / SETUP MODE - Centered Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1E293B).copy(alpha = 0.65f),
                                    Color(0xFF0F172A).copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(theme.pillBorder.copy(alpha = 0.60f), theme.pillBorder.copy(alpha = 0.20f))
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "KARAOKE",
                                    color = theme.controlText,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.4.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Vocals are removed automatically when karaoke starts.",
                                    color = theme.mutedText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {}, enabled = false) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = "Challenge mode unavailable",
                                        tint = theme.mutedText.copy(alpha = 0.38f)
                                    )
                                }
                                FilledTonalIconButton(onClick = onExitGameMode, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Exit game mode")
                                }
                            }
                        }

                        Text(
                            text = "Challenge mode is temporarily unavailable.",
                            color = theme.mutedText.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        AnimatedVisibility(visible = settingsExpanded && challengeModeAvailable) {
                            KaraokeChallengeSettingsPanel(
                                theme = theme,
                                karaokeState = karaokeState,
                                onChallengeToggle = onChallengeToggle,
                                onChallengeProfileSelected = onChallengeProfileSelected
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "Instrumental karaoke",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = theme.sliderActiveTrack.copy(alpha = 0.14f),
                                    labelColor = theme.sliderActiveTrack
                                )
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = when {
                                            karaokeState.preparationInProgress -> "Removing vocals"
                                            karaokeState.usingPreparedBackingTrack -> "Instrumental live"
                                            karaokeState.backingTrackReady -> "Instrumental ready"
                                            karaokeState.stemProviderAvailable -> "Vocals removed on start"
                                            else -> "Instrumental unavailable"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = theme.controlText.copy(alpha = 0.86f)
                                )
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = when {
                                            karaokeState.melodyLoading -> "Checking cache"
                                            karaokeState.melodyReady -> "Pitch chart cached"
                                            else -> "Challenge unavailable"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = theme.controlText.copy(alpha = 0.86f)
                                )
                            )
                        }

                        Text(
                            text = karaokeOverviewMessage(karaokeState = karaokeState, hasWordSync = hasWordSync),
                            color = theme.controlText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        if (karaokeState.statusMessage != null) {
                            KaraokeStatusMessage(theme = theme, message = karaokeState.statusMessage)
                        }

                        FilledTonalButton(
                            onClick = {
                                onDismissMessage()
                                if (!hasWordSync) return@FilledTonalButton
                                onStartKaraoke()
                            },
                            enabled = canStart,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(karaokeStartLabel())
                        }
                    }
                }
            } else {
                // ACTIVE GAMEPLAY MODE
                val revealLyricsAsYouSing = karaokeState.challengeActive && karaokeState.challengeProfile.hidesLyricsUntilMatched
                val pitchRating = karaokeState.pitchRating
                val ratingColor = when (pitchRating) {
                    "Perfect" -> Color(0xFFFBBF24)
                    "Great" -> Color(0xFF34D399)
                    "Nice Try" -> Color(0xFF60A5FA)
                    "Off!" -> Color(0xFFF87171)
                    else -> if (revealLyricsAsYouSing) theme.sliderActiveTrack.copy(alpha = 0.82f) else theme.mutedText.copy(alpha = 0.6f)
                }

                // Scrollable Lyrics Panel mimicking LyricsOverlay
                if (parsed != null) {
                    val activeLineIndex = karaokeState.activeLineIndex
                    val listState = rememberLazyListState()

                    LaunchedEffect(activeLineIndex) {
                        if (activeLineIndex >= 0) {
                            listState.animateScrollToItem(activeLineIndex.coerceAtLeast(0))
                        }
                    }

                    val lineHeightDp = 72.dp
                    val visibleLines = 5
                    val contentPadding = lineHeightDp * ((visibleLines - 1) / 2f)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(lineHeightDp * visibleLines),
                        state = listState,
                        horizontalAlignment = Alignment.Start,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = contentPadding,
                            bottom = contentPadding
                        )
                    ) {
                        itemsIndexed(parsed.lines) { index, line ->
                            val isCurrent = index == activeLineIndex
                            if (isCurrent) {
                                val revealedWordIndex = karaokeState.revealedWordIndexByLine[index] ?: -1
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = lineHeightDp)
                                        .padding(vertical = 8.dp)
                                ) {
                                    val tokens = line.words
                                    if (tokens.isEmpty()) {
                                        val lineVisible = !revealLyricsAsYouSing || revealedWordIndex >= 0
                                        Text(
                                            text = line.text,
                                            color = if (lineVisible) theme.lyricsActive else Color.Transparent,
                                            fontSize = 24.sp,
                                            lineHeight = 32.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            style = if (lineVisible) {
                                                TextStyle(
                                                    shadow = Shadow(
                                                        color = Color.Black.copy(alpha = 0.62f),
                                                        offset = Offset(0f, 3f),
                                                        blurRadius = 18f
                                                    )
                                                )
                                            } else {
                                                TextStyle()
                                            }
                                        )
                                    } else {
                                        tokens.forEachIndexed { wordIndex, word ->
                                            if (word.text.isBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                            } else {
                                                val activeWord = wordIndex == karaokeState.activeWordIndex
                                                val wordVisible = !revealLyricsAsYouSing || wordIndex <= revealedWordIndex
                                                Text(
                                                    text = word.text,
                                                    color = if (!wordVisible) {
                                                        Color.Transparent
                                                    } else if (activeWord) {
                                                        theme.sliderActiveTrack
                                                    } else {
                                                        theme.lyricsActive
                                                    },
                                                    fontSize = 24.sp,
                                                    lineHeight = 32.sp,
                                                    fontWeight = if (activeWord) FontWeight.Bold else FontWeight.SemiBold,
                                                    style = if (wordVisible) {
                                                        TextStyle(
                                                            shadow = Shadow(
                                                                color = Color.Black.copy(alpha = if (activeWord) 0.82f else 0.62f),
                                                                offset = Offset(0f, 3f),
                                                                blurRadius = if (activeWord) 22f else 18f
                                                            )
                                                        )
                                                    } else {
                                                        TextStyle()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val lineVisible = !revealLyricsAsYouSing || karaokeState.revealedWordIndexByLine[index] == Int.MAX_VALUE
                                Text(
                                    text = line.text.takeUnless { it.equals("null", ignoreCase = true) }?.ifBlank { " " } ?: " ",
                                    color = if (lineVisible) theme.lyricsInactive else Color.Transparent,
                                    fontSize = 24.sp,
                                    lineHeight = 32.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Start,
                                    softWrap = true,
                                    style = if (lineVisible) {
                                        TextStyle(
                                            shadow = Shadow(
                                                color = Color.Black.copy(alpha = 0.46f),
                                                offset = Offset(0f, 3f),
                                                blurRadius = 12f
                                            )
                                        )
                                    } else {
                                        TextStyle()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = lineHeightDp)
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(karaokeState.maxLives) { index ->
                                val alive = index < karaokeState.livesRemaining
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = if (alive) theme.sliderActiveTrack else Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (karaokeState.combo > 0) {
                            Text(
                                text = "Combo ${karaokeState.combo}",
                                color = theme.controlText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = pitchRating ?: if (revealLyricsAsYouSing) "Reveal mode" else "Listening",
                        color = ratingColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Challenge mode unavailable",
                                tint = theme.mutedText.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onExitGameMode,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Exit game mode",
                                tint = theme.controlText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = settingsExpanded && challengeModeAvailable) {
                    KaraokeChallengeSettingsPanel(
                        theme = theme,
                        karaokeState = karaokeState,
                        onChallengeToggle = onChallengeToggle,
                        onChallengeProfileSelected = onChallengeProfileSelected
                    )
                }

                if (karaokeState.statusMessage != null) {
                    KaraokeStatusMessage(theme = theme, message = karaokeState.statusMessage)
                }
            }
        }

        // Redesigned Countdown/Miss overlay
        if (karaokeState.isCountingDown) {
            val isMissed = karaokeState.sessionPhase == KaraokeSessionPhase.MISSED
            val isFailed = karaokeState.sessionPhase == KaraokeSessionPhase.FAILED
            val overlayBg = if (isMissed || isFailed) {
                Color.Black.copy(alpha = 0.82f)
            } else {
                Color.Black.copy(alpha = 0.52f)
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(overlayBg)
                    .drawBehind {
                        if (isMissed || isFailed) {
                            drawRect(
                                color = Color.Red.copy(alpha = 0.08f),
                                size = size
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (isMissed || isFailed) {
                        Text(
                            text = if (isFailed) "RUN FAILED" else "CUE MISSED",
                            color = Color(0xFFEF4444),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(2.dp, if (isMissed || isFailed) Color(0xFFEF4444).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Text(
                            text = karaokeState.countdownSeconds.toString(),
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = when {
                            isFailed -> "No lives remaining — restarting the track"
                            isMissed -> "Lost 1 Heart — rewinding 2 lines for re-entry"
                            else -> "Music resumes in a moment"
                        },
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    if (isMissed || isFailed) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            repeat(karaokeState.maxLives) { index ->
                                val alive = index < karaokeState.livesRemaining
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = if (alive) theme.sliderActiveTrack else Color.White.copy(alpha = 0.16f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun textRating(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun KaraokeChallengeSettingsPanel(
    theme: VisualizerPalette,
    karaokeState: KaraokeUiState,
    onChallengeToggle: (Boolean) -> Unit,
    onChallengeProfileSelected: (KaraokeChallengeProfile) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(theme.pillBorder.copy(alpha = 0.50f), theme.pillBorder.copy(alpha = 0.15f))
                ),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    textRating(text = "Challenge mode", color = theme.controlText, fontSize = 14.sp)
                    Text(
                        text = "Uses the device microphone, costs lives on misses, rewinds two lyric lines, and relaunches after a five-second countdown.",
                        color = theme.mutedText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = karaokeState.challengeEnabled,
                    onCheckedChange = onChallengeToggle
                )
            }

            AnimatedVisibility(visible = karaokeState.challengeEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Challenge flavor",
                        color = theme.controlText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    KaraokeChallengeProfile.values().forEach { profile ->
                        val selected = karaokeState.challengeProfile == profile
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) {
                                        theme.sliderActiveTrack.copy(alpha = 0.14f)
                                    } else {
                                        Color.White.copy(alpha = 0.04f)
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) {
                                        theme.sliderActiveTrack.copy(alpha = 0.72f)
                                    } else {
                                        theme.pillBorder.copy(alpha = 0.24f)
                                    },
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { onChallengeProfileSelected(profile) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = profile.label,
                                    color = if (selected) theme.sliderActiveTrack else theme.controlText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = profile.description,
                                    color = theme.mutedText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun karaokeOverviewMessage(
    karaokeState: KaraokeUiState,
    hasWordSync: Boolean
): String {
    if (!hasWordSync) {
        return "Word-synced lyrics are still missing for this track, so karaoke cannot launch yet."
    }
    if (karaokeState.preparationInProgress) {
        return "LALAL.AI is removing the artist vocal and caching the karaoke instrumental on this device."
    }
    if (karaokeState.melodyLoading) {
        return "Checking this device for cached karaoke assets."
    }
    if (karaokeState.backingTrackReady) {
        return "Instrumental karaoke is ready. Tap start and the song will play without the artist voice."
    }
    if (karaokeState.stemProviderAvailable) {
        return "Tap start and Lyrictica will prepare the instrumental, remove the artist vocal, and launch karaoke when it is ready."
    }
    return "Karaoke without vocals is unavailable on this build because LALAL.AI is not configured."
}

private fun karaokeStartLabel(): String = "Start karaoke"

@Composable
private fun KaraokeStatusMessage(
    theme: VisualizerPalette,
    message: String
) {
    Text(
        text = message,
        color = theme.sliderActiveTrack,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(theme.sliderActiveTrack.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
