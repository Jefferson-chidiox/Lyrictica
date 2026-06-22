package com.lyrictica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import kotlin.random.Random
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
    val parsed = availableLyrics
    val hasWordSync = parsed?.hasWordSync == true
    val canStart = if (karaokeState.challengeEnabled) {
        hasWordSync && karaokeState.challengeReady
    } else {
        hasWordSync &&
            !karaokeState.preparationInProgress &&
            !karaokeState.melodyLoading &&
            (karaokeState.stemProviderAvailable || karaokeState.backingTrackReady)
    }

    // Heart loss feedback mechanics
    val lives = karaokeState.livesRemaining
    val active = karaokeState.isSessionActive
    var previousLives by remember { mutableStateOf(lives) }
    var showFlash by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(karaokeState.sessionPhase, karaokeState.challengeEnabled, karaokeState.livesRemaining) {
        if (karaokeState.challengeEnabled && karaokeState.sessionPhase == KaraokeSessionPhase.SUCCESS && karaokeState.livesRemaining > 0) {
            showConfetti = true
            kotlinx.coroutines.delay(2400L)
            showConfetti = false
        } else if (karaokeState.sessionPhase != KaraokeSessionPhase.SUCCESS) {
            showConfetti = false
        }
    }

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
                                    text = "Sing along normally, or turn on Challenge mode to verify lyric lines from your mic.",
                                    color = theme.mutedText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            FilledTonalIconButton(onClick = onExitGameMode, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = "Exit game mode")
                            }
                        }

                        KaraokeChallengeSettingsPanel(
                            theme = theme,
                            karaokeState = karaokeState,
                            onChallengeToggle = onChallengeToggle,
                            onChallengeProfileSelected = onChallengeProfileSelected
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = if (karaokeState.challengeEnabled) "Challenge on" else "Instrumental karaoke",
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
                                        text = if (!karaokeState.challengeEnabled) {
                                            when {
                                                karaokeState.preparationInProgress -> "Removing vocals"
                                                karaokeState.usingPreparedBackingTrack -> "Instrumental live"
                                                karaokeState.backingTrackReady -> "Instrumental ready"
                                                karaokeState.stemProviderAvailable -> "Vocals removed on start"
                                                else -> "Instrumental unavailable"
                                            }
                                        } else if (karaokeState.headphonesConnected) {
                                            "Headphones ready"
                                        } else {
                                            "Headphones required"
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
                                        text = if (karaokeState.challengeEnabled) {
                                            if (karaokeState.microphoneRequired) "Mic permission needed" else "Line matching"
                                        } else {
                                            when {
                                                karaokeState.melodyLoading -> "Checking cache"
                                                karaokeState.melodyReady -> "Pitch chart cached"
                                                else -> "Sing-along ready"
                                            }
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
                            Text(karaokeStartLabel(karaokeState.challengeEnabled))
                        }
                    }
                }
            } else {
                // ACTIVE GAMEPLAY MODE
                val failedLineIndices = karaokeState.failedLineIndices

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
                                val lineFailed = index in failedLineIndices
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
                                        Text(
                                            text = line.text,
                                            color = if (lineFailed) Color(0xFFEF4444) else theme.lyricsActive,
                                            fontSize = 24.sp,
                                            lineHeight = 32.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            style = TextStyle(
                                                shadow = Shadow(
                                                    color = Color.Black.copy(alpha = 0.62f),
                                                    offset = Offset(0f, 3f),
                                                    blurRadius = 18f
                                                )
                                            )
                                        )
                                    } else {
                                        tokens.forEachIndexed { wordIndex, word ->
                                            if (word.text.isBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                            } else {
                                                val activeWord = wordIndex == karaokeState.activeWordIndex
                                                val wordColor = when {
                                                    lineFailed -> Color(0xFFEF4444)
                                                    activeWord -> theme.sliderActiveTrack
                                                    else -> theme.lyricsActive
                                                }
                                                Text(
                                                    text = word.text,
                                                    color = wordColor,
                                                    fontSize = 24.sp,
                                                    lineHeight = 32.sp,
                                                    fontWeight = if (activeWord && !lineFailed) FontWeight.Bold else FontWeight.SemiBold,
                                                    style = TextStyle(
                                                        shadow = Shadow(
                                                            color = Color.Black.copy(alpha = if (activeWord && !lineFailed) 0.82f else 0.62f),
                                                            offset = Offset(0f, 3f),
                                                            blurRadius = if (activeWord && !lineFailed) 22f else 18f
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val lineColor = if (index in failedLineIndices) Color(0xFFEF4444) else theme.lyricsInactive
                                Text(
                                    text = line.text.takeUnless { it.equals("null", ignoreCase = true) }?.ifBlank { " " } ?: " ",
                                    color = lineColor,
                                    fontSize = 24.sp,
                                    lineHeight = 32.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Start,
                                    softWrap = true,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.46f),
                                            offset = Offset(0f, 3f),
                                            blurRadius = 12f
                                        )
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = lineHeightDp)
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                        item {
                            Text(
                                text = "lyrics by musixmatch",
                                color = theme.lyricsInactive.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Start
                            )
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
                        text = when {
                            karaokeState.challengePausedForHeadphones -> "Paused"
                            karaokeState.challengeEnabled && karaokeState.livesRemaining == 0 -> "Run over"
                            karaokeState.challengeEnabled -> "Listening"
                            else -> "Sing-along"
                        },
                        color = if (karaokeState.challengePausedForHeadphones || (karaokeState.challengeEnabled && karaokeState.livesRemaining == 0)) {
                            Color(0xFFEF4444)
                        } else {
                            theme.mutedText.copy(alpha = 0.82f)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

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

                if (karaokeState.statusMessage != null) {
                    KaraokeStatusMessage(theme = theme, message = karaokeState.statusMessage)
                }
            }
        }

        if (karaokeState.isCountingDown && karaokeState.sessionPhase == KaraokeSessionPhase.COUNTDOWN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Text(
                            text = karaokeState.countdownSeconds.toString(),
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (karaokeState.challengeEnabled) "Headphones on. Mic ready. Go on the beat."
                        else "Music resumes in a moment",
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (showConfetti) {
            SuccessConfettiOverlay(modifier = Modifier.fillMaxSize())
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
                    textRating(text = "Challenge mode (Coming Soon)", color = theme.controlText.copy(alpha = 0.38f), fontSize = 14.sp)
                    Text(
                        text = "Headphones required. Lyrictica listens in the background and checks each lyric line without interrupting the song.",
                        color = theme.mutedText.copy(alpha = 0.38f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = false,
                    onCheckedChange = null,
                    enabled = false
                )
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
    if (karaokeState.challengeEnabled) {
        return when {
            !karaokeState.headphonesConnected -> "Plug in headphones before starting challenge mode so the mic hears you instead of the song."
            karaokeState.microphoneRequired -> "Allow microphone access so Lyrictica can listen for each lyric line."
            karaokeState.microphoneUnsupported -> "This device cannot provide speech recognition for challenge mode right now."
            else -> "Challenge mode keeps the song moving forward, turns missed lines red, and costs a heart each time a line does not match."
        }
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

private fun karaokeStartLabel(challengeEnabled: Boolean): String = if (challengeEnabled) "Start challenge" else "Start karaoke"

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

@Composable
private fun SuccessConfettiOverlay(modifier: Modifier = Modifier) {
    val particles = remember {
        List(52) { index ->
            ConfettiParticle(
                startX = Random.nextFloat(),
                startY = Random.nextFloat() * 0.18f,
                drift = Random.nextFloat() * 0.24f - 0.12f,
                size = 6f + Random.nextFloat() * 12f,
                delay = Random.nextFloat() * 0.28f,
                color = listOf(
                    Color(0xFFFBBF24),
                    Color(0xFF60A5FA),
                    Color(0xFF34D399),
                    Color(0xFFF472B6),
                    Color(0xFFFB7185)
                )[index % 5]
            )
        }
    }
    var targetProgress by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 2200),
        label = "confettiProgress"
    )

    LaunchedEffect(Unit) {
        targetProgress = 1f
    }

    Canvas(modifier = modifier) {
        particles.forEach { particle ->
            val localProgress = ((progress - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
            if (localProgress <= 0f) return@forEach
            val x = (particle.startX + particle.drift * localProgress) * size.width
            val y = (particle.startY + (0.16f + localProgress * 0.88f)) * size.height
            drawCircle(
                color = particle.color.copy(alpha = (1f - localProgress * 0.55f).coerceIn(0.25f, 1f)),
                radius = particle.size,
                center = Offset(x, y)
            )
        }
    }
}

private data class ConfettiParticle(
    val startX: Float,
    val startY: Float,
    val drift: Float,
    val size: Float,
    val delay: Float,
    val color: Color
)
