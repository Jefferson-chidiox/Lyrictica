package com.lyrictica.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.oss.euphoriae.R
import com.lyrictica.audio.AudioFeatures
import com.lyrictica.game.reversebeat.ReverseBeatBurstRender
import com.lyrictica.game.reversebeat.ReverseBeatChart
import com.lyrictica.game.reversebeat.ReverseBeatCalloutTone
import com.lyrictica.game.reversebeat.ReverseBeatChartBuilder
import com.lyrictica.game.reversebeat.ReverseBeatChartMode
import com.lyrictica.game.reversebeat.ReverseBeatGameRuntime
import com.lyrictica.game.reversebeat.ReverseBeatTargetKind
import com.lyrictica.game.reversebeat.ReverseBeatHitWordRender
import com.lyrictica.game.reversebeat.ReverseBeatPerformanceProfile
import com.lyrictica.game.reversebeat.ReverseBeatPhase
import com.lyrictica.game.reversebeat.ReverseBeatTargetRender
import com.lyrictica.game.reversebeat.createReverseBeatKorgeConfig
import com.lyrictica.game.reversebeat.resolveReverseBeatPerformanceProfile
import com.lyrictica.lyrics.ParsedLyrics
import com.lyrictica.visualizer.VisualizerPalette
import korlibs.korge.android.KorgeAndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class ReverseBeatChartPhase {
    IDLE,
    LOADING,
    READY,
    ERROR
}

private data class ReverseBeatChartUiState(
    val phase: ReverseBeatChartPhase,
    val chart: ReverseBeatChart? = null,
    val message: String
)

@Composable
internal fun ReverseBeatGameSurface(
    theme: VisualizerPalette,
    songLoaded: Boolean,
    songSeed: Int,
    trackTitle: String,
    artistText: String?,
    songSource: String?,
    availableLyrics: ParsedLyrics?,
    lyricsLoading: Boolean,
    playbackPositionMs: Long,
    playbackIsPlaying: Boolean,
    playbackEnded: Boolean,
    features: AudioFeatures,
    onStartRun: () -> Unit,
    onPauseRun: () -> Unit,
    onResumeRun: () -> Unit,
    onRestartRun: () -> Unit,
    onRunFinished: (Int) -> Unit,
    onExitGameMode: () -> Unit,
    onOpenGamesMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val performanceProfile = remember(context) {
        resolveReverseBeatPerformanceProfile(context.applicationContext)
    }
    val runtime = remember(songSeed, performanceProfile) {
        ReverseBeatGameRuntime(songSeed, performanceProfile)
    }
    val uiState by runtime.uiState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(theme) {
        runtime.themeTopColor = theme.backgroundTop.toArgb()
        runtime.themeCenterColor = theme.ambientGlow.toArgb()
        runtime.themeBottomColor = theme.backgroundBottom.toArgb()
    }
    val playbackIsPlayingState by rememberUpdatedState(playbackIsPlaying)
    val scope = rememberCoroutineScope()
    val chartBuilder = remember(context) { ReverseBeatChartBuilder(context.applicationContext) }
    var chartState by remember(songSeed) {
        mutableStateOf(
            ReverseBeatChartUiState(
                phase = ReverseBeatChartPhase.IDLE,
                message = "Preparing reverse beat chart."
            )
        )
    }
    DisposableEffect(runtime) {
        onDispose {
            runtime.abandonRun()
        }
    }

    LaunchedEffect(songLoaded, songSource, availableLyrics, lyricsLoading, songSeed) {
        when {
            !songLoaded -> {
                chartState = ReverseBeatChartUiState(
                    phase = ReverseBeatChartPhase.IDLE,
                    message = "Load a song before opening Reverse Beat."
                )
                runtime.loadChart(null)
            }
            lyricsLoading && (availableLyrics?.hasWordSync != true) -> {
                chartState = ReverseBeatChartUiState(
                    phase = ReverseBeatChartPhase.LOADING,
                    message = "Waiting for lyrics first so Reverse Beat can prefer word sync."
                )
                runtime.loadChart(null)
            }
            else -> {
                chartState = ReverseBeatChartUiState(
                    phase = ReverseBeatChartPhase.LOADING,
                    message = if (availableLyrics?.hasWordSync == true) {
                        "Building a lyric-synced slice chart."
                    } else {
                        "Building a beat-synced slice chart."
                    }
                )
                val chart = runCatching {
                    withContext(Dispatchers.Default) {
                        chartBuilder.build(
                            songSource = songSource,
                            lyrics = availableLyrics,
                            songSeed = songSeed
                        )
                    }
                }.getOrNull()
                if (!isActive) return@LaunchedEffect
                runtime.loadChart(chart)
                chartState = if (chart != null) {
                    ReverseBeatChartUiState(
                        phase = ReverseBeatChartPhase.READY,
                        chart = chart,
                        message = chart.summary
                    )
                } else {
                    ReverseBeatChartUiState(
                        phase = ReverseBeatChartPhase.ERROR,
                        message = "No lyric-sync or beat chart could be prepared for this song."
                    )
                }
            }
        }
    }

    LaunchedEffect(playbackPositionMs, playbackIsPlaying, playbackEnded, features, runtime) {
        runtime.onPlaybackSample(
            positionMs = playbackPositionMs,
            isPlaying = playbackIsPlaying,
            features = features,
            ended = playbackEnded
        )
    }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == ReverseBeatPhase.FINISHED) {
            onRunFinished(uiState.score)
            if (playbackIsPlayingState) {
                onPauseRun()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        theme.backgroundTop.copy(alpha = 0.96f),
                        theme.backgroundBottom.copy(alpha = 0.98f)
                    )
                )
            )
    ) {
        key(runtime) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    KorgeAndroidView(viewContext).apply {
                        scope.launch(Dispatchers.Main.immediate) {
                            loadModule(createReverseBeatKorgeConfig(runtime))
                        }
                    }
                },
                update = {},
                onRelease = {
                    runtime.pauseRun()
                }
            )
        }

        ReverseBeatStage(
            runtime = runtime,
            performanceProfile = performanceProfile,
            onSwipeStart = { x, y -> runtime.beginSwipe(x, y) },
            onSwipeMove = { x, y -> runtime.extendSwipe(x, y) },
            onSwipeEnd = { runtime.endSwipe() },
            modifier = Modifier.fillMaxSize()
        )

        ReverseBeatHud(
            theme = theme,
            uiState = uiState,
            onPauseRun = {
                runtime.pauseRun()
                onPauseRun()
            },
            modifier = Modifier.fillMaxSize()
        )

        when {
            chartState.phase == ReverseBeatChartPhase.LOADING || chartState.phase == ReverseBeatChartPhase.IDLE || chartState.phase == ReverseBeatChartPhase.ERROR || uiState.phase == ReverseBeatPhase.READY -> {
                ReverseBeatCenterCard(
                    theme = theme,
                    chartState = chartState,
                    uiState = uiState,
                    trackTitle = trackTitle,
                    onStart = {
                        runtime.startRun()
                        onStartRun()
                    },
                    onExitGameMode = {
                        runtime.abandonRun()
                        onExitGameMode()
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        if (uiState.phase == ReverseBeatPhase.PAUSED || uiState.phase == ReverseBeatPhase.FINISHED) {
            ReverseBeatPauseCard(
                theme = theme,
                phase = uiState.phase,
                trackTitle = trackTitle,
                artistText = artistText,
                chartMode = uiState.chartMode,
                score = uiState.score,
                bestCombo = uiState.bestCombo,
                catches = uiState.catches,
                misses = uiState.misses,
                progress = uiState.progress,
                onResume = {
                    if (uiState.phase == ReverseBeatPhase.PAUSED) {
                        runtime.resumeRun()
                        onResumeRun()
                    }
                },
                onRestart = {
                    runtime.startRun()
                    onRestartRun()
                },
                onMainMenu = {
                    runtime.abandonRun()
                    onOpenGamesMenu()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ReverseBeatStage(
    runtime: ReverseBeatGameRuntime,
    performanceProfile: ReverseBeatPerformanceProfile,
    onSwipeStart: (Double, Double) -> Unit,
    onSwipeMove: (Double, Double) -> Unit,
    onSwipeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var frame by remember(runtime) { mutableStateOf(runtime.snapshot()) }

    LaunchedEffect(runtime) {
        while (isActive) {
            withFrameNanos {
                val snapshot = runtime.snapshot()
                if (snapshot != frame) {
                    frame = snapshot
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val toWorldX = ReverseBeatGameRuntime.WORLD_WIDTH / widthPx
        val toWorldY = ReverseBeatGameRuntime.WORLD_HEIGHT / heightPx
        val scaleX = widthPx / ReverseBeatGameRuntime.WORLD_WIDTH.toFloat()
        val scaleY = heightPx / ReverseBeatGameRuntime.WORLD_HEIGHT.toFloat()
        val painter = painterResource(id = R.drawable.circle_01)
        val burstPainter = painterResource(id = R.drawable.magic_03)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onSwipeStart(offset.x * toWorldX, offset.y * toWorldY)
                        },
                        onDragCancel = { onSwipeEnd() },
                        onDragEnd = { onSwipeEnd() },
                        onDrag = { change, _ ->
                            change.consume()
                            onSwipeMove(change.position.x * toWorldX, change.position.y * toWorldY)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trail = frame.trail
                if (trail.size >= 2) {
                    for (index in 0 until trail.lastIndex) {
                        val start = trail[index]
                        val end = trail[index + 1]
                        val alpha = minOf(start.alpha, end.alpha)
                        val startOffset = Offset((start.x * scaleX).toFloat(), (start.y * scaleY).toFloat())
                        val endOffset = Offset((end.x * scaleX).toFloat(), (end.y * scaleY).toFloat())
                        val width = ((trail.size - index).coerceAtLeast(2) * 3.2f) + (frame.swipePulse * 10f)
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF67E8F9).copy(alpha = alpha * 0.82f),
                                    Color(0xFFA855F7).copy(alpha = alpha)
                                ),
                                start = startOffset,
                                end = endOffset
                            ),
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = width,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            frame.targets.forEach { target ->
                ReverseBeatTarget(
                    target = target,
                    painter = painter,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    performanceProfile = performanceProfile
                )
            }

            frame.bursts.forEach { burst ->
                ReverseBeatBurst(
                    burst = burst,
                    painter = burstPainter,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    performanceProfile = performanceProfile
                )
            }

            frame.hitWords.forEach { hitWord ->
                ReverseBeatHitWord(
                    word = hitWord,
                    scaleX = scaleX,
                    scaleY = scaleY
                )
            }
        }
    }
}

@Composable
private fun ReverseBeatTarget(
    target: ReverseBeatTargetRender,
    painter: androidx.compose.ui.graphics.painter.Painter,
    scaleX: Float,
    scaleY: Float,
    performanceProfile: ReverseBeatPerformanceProfile
) {
    val density = LocalDensity.current
    val screenRadiusPx = (target.radius * ((scaleX + scaleY) * 0.5f)).toFloat()
    val sizePx = (screenRadiusPx * 2.1f).coerceAtLeast(48f)
    val sizeDp = with(density) { sizePx.toDp() }
    val accent = targetAccentColor(target)
    val textColor = when (target.kind) {
        ReverseBeatTargetKind.BOMB -> Color(0xFFFFFBEB)
        ReverseBeatTargetKind.POWER_DOUBLE_SCORE,
        ReverseBeatTargetKind.POWER_LINE_CLEAR,
        ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> Color.White
        ReverseBeatTargetKind.BALL -> Color(0xFF03111D)
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (target.x * scaleX - (sizePx / 2f)).roundToInt(),
                    y = (target.y * scaleY - (sizePx / 2f)).roundToInt()
                )
            }
            .size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accent.copy(alpha = 0.18f + (target.alpha * 0.10f)), CircleShape)
        )
        if (performanceProfile.showSecondaryTargetGlow) {
            Box(
                modifier = Modifier
                    .size(with(density) { (sizePx * 0.82f).toDp() })
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.74f),
                                accent.copy(alpha = 0.16f)
                            )
                        ),
                        CircleShape
                    )
            )
        }
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(
                when (target.kind) {
                    ReverseBeatTargetKind.BOMB -> Color(0xFF111827).copy(alpha = 0.96f)
                    ReverseBeatTargetKind.POWER_DOUBLE_SCORE,
                    ReverseBeatTargetKind.POWER_LINE_CLEAR,
                    ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> accent.copy(alpha = 0.96f)
                    ReverseBeatTargetKind.BALL -> Color.White.copy(alpha = 0.96f)
                }
            ),
            alpha = target.alpha
        )
        Text(
            text = when (target.kind) {
                ReverseBeatTargetKind.BOMB -> "BOMB"
                ReverseBeatTargetKind.POWER_DOUBLE_SCORE -> "×2"
                ReverseBeatTargetKind.POWER_LINE_CLEAR -> "⚡"
                ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> "✿"
                ReverseBeatTargetKind.BALL -> target.label ?: ""
            },
            color = textColor,
            fontSize = when {
                target.kind == ReverseBeatTargetKind.BOMB -> 9.sp
                target.kind.isPowerUp -> 18.sp
                (target.label?.length ?: 0) >= 10 -> 8.sp
                (target.label?.length ?: 0) >= 7 -> 9.sp
                else -> 10.sp
            },
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .graphicsLayer(alpha = target.alpha)
        )
    }
}

@Composable
private fun ReverseBeatBurst(
    burst: ReverseBeatBurstRender,
    painter: androidx.compose.ui.graphics.painter.Painter,
    scaleX: Float,
    scaleY: Float,
    performanceProfile: ReverseBeatPerformanceProfile
) {
    val density = LocalDensity.current
    val sizePx = (190f * performanceProfile.burstSizeMultiplier * burst.scale * ((scaleX + scaleY) * 0.5f)).coerceAtLeast(42f)
    val sizeDp = with(density) { sizePx.toDp() }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (burst.x * scaleX - (sizePx / 2f)).roundToInt(),
                    y = (burst.y * scaleY - (sizePx / 2f)).roundToInt()
                )
            }
            .size(sizeDp)
            .scale(burst.scale),
        contentScale = ContentScale.Fit,
        alpha = burst.alpha
    )
}

@Composable
private fun ReverseBeatHitWord(
    word: ReverseBeatHitWordRender,
    scaleX: Float,
    scaleY: Float
) {
    val accent = lerp(Color(0xFF67E8F9), Color(0xFFF9A8D4), word.accent.coerceIn(0f, 1f))
    Text(
        text = word.text,
        color = accent.copy(alpha = word.alpha),
        fontSize = 26.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (word.x * scaleX).roundToInt(),
                    y = (word.y * scaleY).roundToInt()
                )
            }
            .graphicsLayer(
                alpha = word.alpha,
                rotationZ = word.rotationDeg,
                scaleX = word.scale,
                scaleY = word.scale
            )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReverseBeatHud(
    theme: VisualizerPalette,
    uiState: com.lyrictica.game.reversebeat.ReverseBeatUiState,
    onPauseRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.phase != ReverseBeatPhase.PLAYING) return

    val comboDance = rememberInfiniteTransition(label = "comboDance")
    val comboBob by comboDance.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "comboBob"
    )
    val comboScale by comboDance.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820),
            repeatMode = RepeatMode.Reverse
        ),
        label = "comboScale"
    )
    val activeAirDanceCount = uiState.airDanceActiveCount
    val comboAccent = if (uiState.perfectActive) Color(0xFFFBBF24) else theme.lyricsActive
    val bottomHue = if (activeAirDanceCount >= 2) {
        lerp(Color(0xFF22D3EE), Color(0xFFA855F7), ((activeAirDanceCount - 2).coerceAtMost(6) / 6f))
    } else {
        Color.Transparent
    }

    Box(modifier = modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        if (activeAirDanceCount >= 2) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                bottomHue.copy(alpha = 0.18f),
                                bottomHue.copy(alpha = 0.36f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "AIR DANCE",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = "${activeAirDanceCount}x",
                    color = bottomHue,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onPauseRun,
                        modifier = Modifier.size(38.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.30f),
                            contentColor = Color.White.copy(alpha = 0.88f)
                        )
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(18.dp))
                    }
                    ReverseBeatStatChip(label = "Score", value = uiState.score.toString(), accent = theme.sliderActiveTrack)
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Black.copy(alpha = if (uiState.perfectActive) 0.42f else 0.28f),
                    border = BorderStroke(1.dp, comboAccent.copy(alpha = 0.34f)),
                    modifier = Modifier.graphicsLayer(
                        rotationZ = if (uiState.combo > 0) comboBob else 0f,
                        scaleX = if (uiState.combo > 0) comboScale else 1f,
                        scaleY = if (uiState.combo > 0) comboScale else 1f
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (uiState.perfectActive) "ON-BEAT PERFECT" else "COMBO",
                            color = comboAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.1.sp
                        )
                        Text(
                            text = if (uiState.combo > 0) uiState.combo.toString() else "0",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (uiState.perfectStreak > 0) {
                            Text(
                                text = "${"%.2f".format(uiState.perfectMultiplier)}x",
                                color = comboAccent.copy(alpha = 0.92f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.airDanceSummaryAlpha > 0f && uiState.airDanceSummaryCount > 0 -> {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.32f * uiState.airDanceSummaryAlpha),
                            border = BorderStroke(1.dp, bottomHue.copy(alpha = 0.42f * uiState.airDanceSummaryAlpha))
                        ) {
                            Text(
                                text = "AIR DANCE x${uiState.airDanceSummaryCount}",
                                color = Color.White.copy(alpha = uiState.airDanceSummaryAlpha),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                    uiState.specialCalloutAlpha > 0f && uiState.specialCalloutText != null -> {
                        val calloutColor = reverseBeatCalloutColor(uiState.specialCalloutTone, theme)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.30f * uiState.specialCalloutAlpha),
                            border = BorderStroke(1.dp, calloutColor.copy(alpha = 0.42f * uiState.specialCalloutAlpha))
                        ) {
                            Text(
                                text = uiState.specialCalloutText,
                                color = calloutColor.copy(alpha = uiState.specialCalloutAlpha),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.doubleScoreActive) {
                    ReverseBeatStatChip(
                        label = "×2",
                        value = "${((uiState.doubleScoreRemainingMs + 999L) / 1000L).coerceAtLeast(1L)}s",
                        accent = Color(0xFFF59E0B)
                    )
                }
                if (uiState.lyricBloomCharges > 0) {
                    ReverseBeatStatChip(
                        label = "✿",
                        value = uiState.lyricBloomCharges.toString(),
                        accent = Color(0xFFF472B6)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { uiState.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = theme.sliderActiveTrack.copy(alpha = 0.75f),
                trackColor = Color.White.copy(alpha = 0.10f)
            )
        }
    }
}

@Composable
private fun ReverseBeatCenterCard(
    theme: VisualizerPalette,
    chartState: ReverseBeatChartUiState,
    uiState: com.lyrictica.game.reversebeat.ReverseBeatUiState,
    trackTitle: String,
    onStart: () -> Unit,
    onExitGameMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ready = chartState.phase == ReverseBeatChartPhase.READY && uiState.chartReady
    Box(
        modifier = modifier
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0F172A).copy(alpha = 0.75f),
                        Color(0xFF020617).copy(alpha = 0.90f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f))
                ),
                shape = RoundedCornerShape(30.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(theme.sliderActiveTrack.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TrackChanges,
                    contentDescription = null,
                    tint = theme.sliderActiveTrack,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "REVERSE BEAT",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = trackTitle,
                    color = theme.mutedText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = when (chartState.phase) {
                    ReverseBeatChartPhase.IDLE,
                    ReverseBeatChartPhase.LOADING,
                    ReverseBeatChartPhase.ERROR -> chartState.message
                    ReverseBeatChartPhase.READY -> uiState.chartSummary
                },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )

            if (chartState.phase == ReverseBeatChartPhase.LOADING) {
                CircularProgressIndicator(color = theme.sliderActiveTrack, strokeWidth = 2.5.dp)
            }

            FilledTonalButton(
                onClick = onStart,
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (ready) "Start fullscreen run" else "Preparing chart")
            }

            FilledTonalButton(
                onClick = onExitGameMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exit reverse beat")
            }
        }
    }
}

@Composable
private fun ReverseBeatPauseCard(
    theme: VisualizerPalette,
    phase: ReverseBeatPhase,
    trackTitle: String,
    artistText: String?,
    chartMode: ReverseBeatChartMode?,
    score: Int,
    bestCombo: Int,
    catches: Int,
    misses: Int,
    progress: Float,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(32.dp)
    val accent = when (chartMode) {
        ReverseBeatChartMode.LYRIC -> theme.lyricsActive
        ReverseBeatChartMode.BEAT -> theme.ambientGlow
        null -> theme.controlText
    }
    val progressValue = progress.coerceIn(0f, 1f)
    val progressPercent = (progressValue * 100f).roundToInt()
    val chartLabel = when (chartMode) {
        ReverseBeatChartMode.LYRIC -> "Lyric chart"
        ReverseBeatChartMode.BEAT -> "Beat chart"
        null -> "Chart loading"
    }

    Box(
        modifier = modifier
            .padding(horizontal = 26.dp)
            .shadow(
                elevation = 26.dp,
                shape = cardShape,
                ambientColor = theme.ambientGlow.copy(alpha = 0.38f),
                spotColor = theme.ambientGlow.copy(alpha = 0.30f)
            )
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        theme.backgroundTop.copy(alpha = 0.90f),
                        theme.backgroundBottom.copy(alpha = 0.97f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.75f),
                        theme.controlText.copy(alpha = 0.12f)
                    )
                ),
                shape = cardShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (phase == ReverseBeatPhase.FINISHED) "Run summary" else "Paused run",
                    color = theme.controlText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = trackTitle,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artistText?.takeIf { it.isNotBlank() }
                        ?: if (phase == ReverseBeatPhase.FINISHED) "Run complete" else "Take a breath and jump back in.",
                    color = theme.mutedText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = accent,
                trackColor = theme.sliderInactiveTrack.copy(alpha = 0.60f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReverseBeatPauseTag(
                    label = chartLabel,
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                ReverseBeatPauseTag(
                    label = "$progressPercent% cleared",
                    accent = theme.ambientGlow,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Score",
                    color = theme.mutedText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = score.toString(),
                        color = theme.controlText,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Icon(
                        imageVector = Icons.Default.TrackChanges,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReverseBeatSummaryMetric(
                    label = "BEST COMBO",
                    value = bestCombo.toString(),
                    accent = theme.controlText,
                    modifier = Modifier.weight(1f)
                )
                ReverseBeatSummaryMetric(
                    label = "SLICED",
                    value = catches.toString(),
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                ReverseBeatSummaryMetric(
                    label = "MISSES",
                    value = misses.toString(),
                    accent = if (misses > 0) Color(0xFFFF8A80) else theme.mutedText,
                    modifier = Modifier.weight(1f)
                )
            }

            if (phase == ReverseBeatPhase.PAUSED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = theme.controlText
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resume")
                    }
                    FilledTonalButton(
                        onClick = onRestart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = theme.ambientGlow.copy(alpha = 0.24f),
                            contentColor = theme.controlText
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restart")
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = theme.ambientGlow.copy(alpha = 0.24f),
                        contentColor = theme.controlText
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Replay")
                }
            }

            TextButton(
                onClick = onMainMenu,
                colors = ButtonDefaults.textButtonColors(contentColor = theme.controlText.copy(alpha = 0.88f))
            ) {
                Text("Main menu")
            }
        }
    }
}

@Composable
private fun ReverseBeatPauseTag(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.24f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReverseBeatSummaryMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Text(
            text = value,
            color = accent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ReverseBeatStatChip(
    label: String,
    value: String,
    accent: Color
) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = "$label $value",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = accent.copy(alpha = 0.16f),
            labelColor = accent
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp)
    )
}

private fun targetAccentColor(target: ReverseBeatTargetRender): Color {
    return when (target.kind) {
        ReverseBeatTargetKind.BOMB -> lerp(Color(0xFFEF4444), Color(0xFFF59E0B), target.accent.coerceIn(0f, 1f))
        ReverseBeatTargetKind.POWER_DOUBLE_SCORE -> lerp(Color(0xFFF59E0B), Color(0xFFA855F7), target.accent.coerceIn(0f, 1f))
        ReverseBeatTargetKind.POWER_LINE_CLEAR -> lerp(Color(0xFFFFFFFF), Color(0xFF22D3EE), target.accent.coerceIn(0f, 1f))
        ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> lerp(Color(0xFFF472B6), Color(0xFF67E8F9), target.accent.coerceIn(0f, 1f))
        ReverseBeatTargetKind.BALL -> when (target.mode) {
            ReverseBeatChartMode.LYRIC -> lerp(Color(0xFF67E8F9), Color(0xFFF9A8D4), target.accent.coerceIn(0f, 1f))
            ReverseBeatChartMode.BEAT -> lerp(Color(0xFFA855F7), Color(0xFFF59E0B), target.accent.coerceIn(0f, 1f))
        }
    }
}

private fun reverseBeatCalloutColor(
    tone: ReverseBeatCalloutTone,
    theme: VisualizerPalette
): Color {
    return when (tone) {
        ReverseBeatCalloutTone.INFO -> theme.controlText
        ReverseBeatCalloutTone.GOLD -> Color(0xFFFBBF24)
        ReverseBeatCalloutTone.CYAN -> Color(0xFF67E8F9)
        ReverseBeatCalloutTone.TEAL -> Color(0xFF2DD4BF)
        ReverseBeatCalloutTone.PINK -> Color(0xFFF472B6)
        ReverseBeatCalloutTone.AMBER -> Color(0xFFF59E0B)
    }
}
