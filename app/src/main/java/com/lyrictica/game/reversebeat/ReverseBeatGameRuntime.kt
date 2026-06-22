package com.lyrictica.game.reversebeat

import com.lyrictica.audio.AudioFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal enum class ReverseBeatPhase {
    READY,
    PLAYING,
    PAUSED,
    FINISHED
}

internal enum class ReverseBeatCalloutTone {
    INFO,
    GOLD,
    CYAN,
    TEAL,
    PINK,
    AMBER
}

internal data class ReverseBeatUiState(
    val phase: ReverseBeatPhase = ReverseBeatPhase.READY,
    val chartReady: Boolean = false,
    val chartMode: ReverseBeatChartMode? = null,
    val chartSummary: String = "Preparing reverse beat chart.",
    val score: Int = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val catches: Int = 0,
    val misses: Int = 0,
    val totalTargets: Int = 0,
    val clearedTargets: Int = 0,
    val progress: Float = 0f,
    val message: String = "Preparing reverse beat chart.",
    val perfectStreak: Int = 0,
    val perfectActive: Boolean = false,
    val perfectMultiplier: Float = 1f,
    val doubleScoreActive: Boolean = false,
    val doubleScoreRemainingMs: Long = 0L,
    val lyricBloomCharges: Int = 0,
    val airDanceActiveCount: Int = 0,
    val airDanceSummaryCount: Int = 0,
    val airDanceSummaryAlpha: Float = 0f,
    val specialCalloutText: String? = null,
    val specialCalloutTone: ReverseBeatCalloutTone = ReverseBeatCalloutTone.INFO,
    val specialCalloutAlpha: Float = 0f,
    val bombRevealCueCount: Int = 0,
    val bombHitCueCount: Int = 0
)

internal data class ReverseBeatTargetRender(
    val id: Long,
    val kind: ReverseBeatTargetKind,
    val x: Double,
    val y: Double,
    val radius: Double,
    val accent: Float,
    val alpha: Float,
    val label: String?,
    val mode: ReverseBeatChartMode
)

internal data class ReverseBeatBurstRender(
    val id: Long,
    val x: Double,
    val y: Double,
    val scale: Float,
    val alpha: Float
)

internal data class ReverseBeatHitWordRender(
    val id: Long,
    val text: String,
    val x: Double,
    val y: Double,
    val scale: Float,
    val alpha: Float,
    val rotationDeg: Float,
    val accent: Float
)

internal data class ReverseBeatTrailPointRender(
    val x: Double,
    val y: Double,
    val alpha: Float
)

internal data class ReverseBeatRenderState(
    val phase: ReverseBeatPhase,
    val beatPulse: Float,
    val swipePulse: Float,
    val targets: List<ReverseBeatTargetRender>,
    val bursts: List<ReverseBeatBurstRender>,
    val hitWords: List<ReverseBeatHitWordRender>,
    val trail: List<ReverseBeatTrailPointRender>
)

internal class ReverseBeatGameRuntime(
    private val songSeed: Int,
    private val performanceProfile: ReverseBeatPerformanceProfile = ReverseBeatPerformanceProfile.STANDARD
) {
    companion object {
        const val WORLD_WIDTH = 1080.0
        const val WORLD_HEIGHT = 1920.0

        private const val MAX_MISSES = 12
        private const val TRAIL_LIFE_SECONDS = 0.22f
        private const val WORD_LIFE_SECONDS = 0.96f
        private const val BURST_LIFE_SECONDS = 0.46f
        private const val PERFECT_WINDOW_MS = 44L
        private const val PERFECT_ACTIVATION_STREAK = 3
        private const val AIR_DANCE_WINDOW_MS = 1_500L
        private const val AIR_DANCE_SUMMARY_LIFE_SECONDS = 1.55f
        private const val SPECIAL_CALLOUT_LIFE_SECONDS = 1.10f
        private const val DOUBLE_SCORE_DURATION_MS = 8_000L
        private const val LYRIC_BLOOM_PICKUP_CHARGES = 4
        private const val BOMB_DODGE_SCORE_BONUS = 45
        private const val BOMB_NEAR_DODGE_RADIUS_MULTIPLIER = 1.82
        private const val BOMB_HIT_RADIUS_MULTIPLIER = 1.06
        private const val BLOOM_CATCH_RADIUS = 250.0
        private const val BLOOM_MAX_TIME_DELTA_MS = 620L
    }

    private enum class SliceSide {
        LEFT,
        RIGHT
    }

    private data class ActiveTarget(
        val entry: ReverseBeatChartEntry,
        var nearDodgeRegistered: Boolean = false
    )

    private data class Burst(
        val id: Long,
        val x: Double,
        val y: Double,
        var life: Float,
        val maxLife: Float
    )

    private data class HitWord(
        val id: Long,
        val text: String,
        val x: Double,
        val y: Double,
        var life: Float,
        val maxLife: Float,
        val accent: Float,
        val rotationDirection: Float
    )

    private data class TrailPoint(
        val x: Double,
        val y: Double,
        var life: Float,
        val maxLife: Float
    )

    private data class Resolution(
        var score: Int,
        var combo: Int,
        var catches: Int,
        var misses: Int,
        var bestCombo: Int,
        var clearedTargets: Int,
        var hitAny: Boolean = false,
        var triggeredBomb: Boolean = false,
        var triggeredLineClear: Boolean = false,
        var triggeredDoubleScore: Boolean = false,
        var triggeredLyricBloomPickup: Boolean = false,
        var awardedBombDodge: Boolean = false
    )

    private var chart: ReverseBeatChart? = null
    private val ui = MutableStateFlow(ReverseBeatUiState())
    val uiState: StateFlow<ReverseBeatUiState> = ui.asStateFlow()

    private val activeTargets = mutableListOf<ActiveTarget>()
    private val bursts = mutableListOf<Burst>()
    private val hitWords = mutableListOf<HitWord>()
    private val trailPoints = mutableListOf<TrailPoint>()

    private var nextBurstId = 1L
    private var nextWordId = 1L
    private var pendingIndex = 0
    private var lastPlaybackMs = 0L

    @Volatile
    internal var beatPulse = 0f
        private set

    @Volatile
    internal var swipePulse = 0f
        private set

    @Volatile
    internal var themeTopColor: Int = 0x050814

    @Volatile
    internal var themeCenterColor: Int = 0x050814

    @Volatile
    internal var themeBottomColor: Int = 0x050814

    @Volatile
    internal var errorTintPulse: Float = 0f

    private var lastFeatures = AudioFeatures()
    private var lastSwipePoint: Pair<Double, Double>? = null

    private var perfectStreak = 0
    private var doubleScoreUntilMs = 0L
    private var lyricBloomCharges = 0
    private var airDanceActiveCount = 0
    private var airDanceLastSide: SliceSide? = null
    private var airDanceLastHitMs: Long? = null
    private var airDanceSummaryCount = 0
    private var airDanceSummaryLife = 0f
    private var specialCalloutText: String? = null
    private var specialCalloutTone: ReverseBeatCalloutTone = ReverseBeatCalloutTone.INFO
    private var specialCalloutLife = 0f
    private var lineClearInProgress = false

    @Synchronized
    fun loadChart(chart: ReverseBeatChart?) {
        this.chart = chart
        clearTransientState()
        val base = if (chart == null || chart.entries.isEmpty()) {
            ReverseBeatUiState(
                chartReady = false,
                chartSummary = "No playable lyric or beat chart is ready yet.",
                message = "No playable lyric or beat chart is ready yet."
            )
        } else {
            ReverseBeatUiState(
                chartReady = true,
                chartMode = chart.mode,
                chartSummary = chart.summary,
                totalTargets = chart.playableTargetCount,
                message = readyMessage(chart)
            )
        }
        ui.value = syncSpecialUi(base)
    }

    @Synchronized
    fun startRun() {
        val loadedChart = chart ?: return
        if (loadedChart.entries.isEmpty()) return

        clearTransientState()
        pendingIndex = 0
        lastPlaybackMs = 0L
        lastFeatures = AudioFeatures()
        val base = ReverseBeatUiState(
            phase = ReverseBeatPhase.PLAYING,
            chartReady = true,
            chartMode = loadedChart.mode,
            chartSummary = loadedChart.summary,
            totalTargets = loadedChart.playableTargetCount,
            message = playingMessage(loadedChart.mode)
        )
        ui.value = syncSpecialUi(base)
    }

    @Synchronized
    fun pauseRun() {
        val state = ui.value
        if (state.phase != ReverseBeatPhase.PLAYING) return
        ui.value = syncSpecialUi(
            state.copy(
                phase = ReverseBeatPhase.PAUSED,
                message = "Paused — slice again when you are ready."
            )
        )
    }

    @Synchronized
    fun resumeRun() {
        val state = ui.value
        if (state.phase != ReverseBeatPhase.PAUSED) return
        ui.value = syncSpecialUi(
            state.copy(
                phase = ReverseBeatPhase.PLAYING,
                message = playingMessage(state.chartMode ?: chart?.mode ?: ReverseBeatChartMode.BEAT)
            )
        )
    }

    @Synchronized
    fun abandonRun() {
        val loadedChart = chart
        clearTransientState()
        val base = if (loadedChart == null || loadedChart.entries.isEmpty()) {
            ReverseBeatUiState(
                chartReady = false,
                chartSummary = "No playable lyric or beat chart is ready yet.",
                message = "No playable lyric or beat chart is ready yet."
            )
        } else {
            ReverseBeatUiState(
                chartReady = true,
                chartMode = loadedChart.mode,
                chartSummary = loadedChart.summary,
                totalTargets = loadedChart.playableTargetCount,
                message = readyMessage(loadedChart)
            )
        }
        ui.value = syncSpecialUi(base)
    }

    @Synchronized
    fun onPlaybackSample(
        positionMs: Long,
        isPlaying: Boolean,
        features: AudioFeatures,
        ended: Boolean
    ) {
        updateBeatPulse(features)
        lastFeatures = features
        lastPlaybackMs = positionMs

        val state = ui.value
        if (!state.chartReady) {
            refreshTransientUiState()
            return
        }
        if (ended && state.phase != ReverseBeatPhase.READY) {
            finishRun("Track complete — ${ui.value.catches} sliced.")
            return
        }
        if (state.phase != ReverseBeatPhase.PLAYING || !isPlaying) {
            refreshTransientUiState()
            return
        }

        val loadedChart = chart ?: return
        spawnReadyTargets(positionMs, loadedChart)
        missExpiredTargets(positionMs, loadedChart)

        if (ui.value.misses >= MAX_MISSES) {
            finishRun("Too many misses — restart to sharpen the rhythm.")
            return
        }

        refreshTransientUiState()
    }

    @Synchronized
    fun beginSwipe(x: Double, y: Double) {
        if (ui.value.phase != ReverseBeatPhase.PLAYING) return
        lastSwipePoint = x to y
        pushTrailPoint(x, y)
        swipePulse = max(swipePulse, 0.24f)
    }

    @Synchronized
    fun extendSwipe(x: Double, y: Double) {
        val start = lastSwipePoint ?: run {
            beginSwipe(x, y)
            return
        }
        if (ui.value.phase != ReverseBeatPhase.PLAYING) return

        val segmentDistance = hypot(x - start.first, y - start.second)
        if (segmentDistance < 6.0) return

        registerNearBombPass(start.first, start.second, x, y)
        sliceTargets(start.first, start.second, x, y)
        lastSwipePoint = x to y
        pushTrailPoint(x, y)
        swipePulse = max(swipePulse, (segmentDistance / 160.0).toFloat().coerceIn(0.18f, 1f))
    }

    @Synchronized
    fun endSwipe() {
        lastSwipePoint = null
    }

    @Synchronized
    fun advance(deltaSeconds: Double) {
        if (deltaSeconds <= 0.0) return
        beatPulse = (beatPulse - (deltaSeconds * 1.8)).coerceAtLeast(0.0).toFloat()
        swipePulse = (swipePulse - (deltaSeconds * 3.4)).coerceAtLeast(0.0).toFloat()
        errorTintPulse = (errorTintPulse - (deltaSeconds * 1.5)).coerceAtLeast(0.0).toFloat()

        var transientChanged = false
        if (airDanceSummaryLife > 0f) {
            airDanceSummaryLife = (airDanceSummaryLife - deltaSeconds.toFloat()).coerceAtLeast(0f)
            transientChanged = true
        }
        if (specialCalloutLife > 0f) {
            specialCalloutLife = (specialCalloutLife - deltaSeconds.toFloat()).coerceAtLeast(0f)
            if (specialCalloutLife == 0f) {
                specialCalloutText = null
            }
            transientChanged = true
        }

        val burstIterator = bursts.iterator()
        while (burstIterator.hasNext()) {
            val burst = burstIterator.next()
            burst.life -= deltaSeconds.toFloat()
            if (burst.life <= 0f) {
                burstIterator.remove()
            }
        }

        val wordIterator = hitWords.iterator()
        while (wordIterator.hasNext()) {
            val word = wordIterator.next()
            word.life -= deltaSeconds.toFloat()
            if (word.life <= 0f) {
                wordIterator.remove()
            }
        }

        val trailIterator = trailPoints.iterator()
        while (trailIterator.hasNext()) {
            val point = trailIterator.next()
            point.life -= deltaSeconds.toFloat()
            if (point.life <= 0f) {
                trailIterator.remove()
            }
        }

        if (transientChanged) {
            ui.value = syncSpecialUi(ui.value)
        }
    }

    @Synchronized
    fun snapshot(): ReverseBeatRenderState {
        val loadedChart = chart
        val mode = loadedChart?.mode ?: ReverseBeatChartMode.BEAT
        val targets = if (loadedChart == null || activeTargets.isEmpty()) {
            emptyList()
        } else {
            buildList(activeTargets.size) {
                activeTargets.forEach { active ->
                    renderTarget(active.entry, mode, lastPlaybackMs)?.let(::add)
                }
            }
        }
        val burstFrames = if (bursts.isEmpty()) {
            emptyList()
        } else {
            buildList(bursts.size) {
                bursts.forEach { burst ->
                    val progress = 1f - (burst.life / burst.maxLife).coerceIn(0f, 1f)
                    add(
                        ReverseBeatBurstRender(
                            id = burst.id,
                            x = burst.x,
                            y = burst.y,
                            scale = 0.72f + (progress * 1.42f),
                            alpha = (1f - progress).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }
        val hitWordFrames = if (hitWords.isEmpty()) {
            emptyList()
        } else {
            buildList(hitWords.size) {
                hitWords.forEach { word ->
                    val progress = 1f - (word.life / word.maxLife).coerceIn(0f, 1f)
                    add(
                        ReverseBeatHitWordRender(
                            id = word.id,
                            text = word.text,
                            x = word.x,
                            y = word.y - (progress * 190.0),
                            scale = 0.88f + (progress * 0.74f),
                            alpha = (1f - progress).coerceIn(0f, 1f),
                            rotationDeg = word.rotationDirection * (6f + (progress * 18f)),
                            accent = word.accent
                        )
                    )
                }
            }
        }
        val trailFrames = if (trailPoints.isEmpty()) {
            emptyList()
        } else {
            buildList(trailPoints.size) {
                trailPoints.forEach { point ->
                    add(
                        ReverseBeatTrailPointRender(
                            x = point.x,
                            y = point.y,
                            alpha = (point.life / point.maxLife).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }

        return ReverseBeatRenderState(
            phase = ui.value.phase,
            beatPulse = beatPulse,
            swipePulse = swipePulse,
            targets = targets,
            bursts = burstFrames,
            hitWords = hitWordFrames,
            trail = trailFrames
        )
    }

    private fun renderTarget(
        entry: ReverseBeatChartEntry,
        mode: ReverseBeatChartMode,
        positionMs: Long
    ): ReverseBeatTargetRender? {
        val flightProgress = flightProgress(entry, positionMs) ?: return null
        val startX = WORLD_WIDTH * entry.startXFraction
        val apexX = WORLD_WIDTH * entry.apexXFraction
        val endX = WORLD_WIDTH * entry.endXFraction
        val floorY = WORLD_HEIGHT + (entry.radiusPx * 1.3f)
        val apexY = WORLD_HEIGHT * entry.apexYFraction
        val controlX = apexAnchoredControlX(startX = startX, apexX = apexX, endX = endX)
        val x = ReverseBeatSliceMath.quadraticBezier(startX, controlX, endX, flightProgress)
        val y = ReverseBeatSliceMath.quadraticBezier(floorY, apexY, floorY, flightProgress)
        val fadeIn = (flightProgress / 0.12).coerceIn(0.36, 1.0)
        val fadeOut = ((1.0 - flightProgress) / 0.18).coerceIn(0.30, 1.0)
        val cueCloseness = 1.0 - (abs(positionMs - entry.hitTimeMs).toDouble() / (entry.flightDurationMs * 0.5).toDouble()).coerceIn(0.0, 1.0)
        val alpha = min(fadeIn, fadeOut) * (0.70 + (cueCloseness * 0.30))
        return ReverseBeatTargetRender(
            id = entry.id,
            kind = entry.kind,
            x = x,
            y = y,
            radius = entry.radiusPx.toDouble(),
            accent = entry.emphasis,
            alpha = alpha.toFloat().coerceIn(0f, 1f),
            label = entry.label,
            mode = mode
        )
    }

    private fun flightProgress(entry: ReverseBeatChartEntry, positionMs: Long): Double? {
        if (positionMs < entry.spawnTimeMs || positionMs > entry.expiryTimeMs) return null
        val elapsed = (positionMs - entry.spawnTimeMs).toDouble()
        return (elapsed / entry.flightDurationMs.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun apexAnchoredControlX(startX: Double, apexX: Double, endX: Double): Double {
        return (2.0 * apexX) - ((startX + endX) * 0.5)
    }

    private fun updateBeatPulse(features: AudioFeatures) {
        val bassRise = max(0f, features.bass - lastFeatures.bass)
        val midRise = max(0f, features.mid - lastFeatures.mid)
        val presenceRise = max(0f, features.presence - lastFeatures.presence)
        val trebleRise = max(0f, features.treble - lastFeatures.treble)
        val onset = (bassRise * 1.12f) + (midRise * 0.64f) + (presenceRise * 0.86f) + (trebleRise * 0.48f)
        beatPulse = max(beatPulse, onset.coerceIn(0f, 1f))
    }

    private fun spawnReadyTargets(positionMs: Long, chart: ReverseBeatChart) {
        var revealedBombs = 0
        while (pendingIndex < chart.entries.size && chart.entries[pendingIndex].spawnTimeMs <= positionMs) {
            val entry = chart.entries[pendingIndex]
            activeTargets += ActiveTarget(entry)
            if (entry.kind == ReverseBeatTargetKind.BOMB) {
                revealedBombs += 1
            }
            pendingIndex += 1
        }
        if (revealedBombs > 0) {
            ui.value = syncSpecialUi(
                ui.value.copy(
                    bombRevealCueCount = ui.value.bombRevealCueCount + revealedBombs
                )
            )
        }
    }

    private fun missExpiredTargets(positionMs: Long, chart: ReverseBeatChart) {
        if (activeTargets.isEmpty()) return
        val resolution = Resolution(
            score = ui.value.score,
            combo = ui.value.combo,
            catches = ui.value.catches,
            misses = ui.value.misses,
            bestCombo = ui.value.bestCombo,
            clearedTargets = ui.value.clearedTargets
        )
        var changed = false

        val iterator = activeTargets.iterator()
        while (iterator.hasNext()) {
            val active = iterator.next()
            if (positionMs <= active.entry.expiryTimeMs) continue
            iterator.remove()
            when {
                active.entry.kind.isStandardBall -> {
                    resolution.combo = 0
                    resolution.misses += 1
                    errorTintPulse = 1f
                    onComboInterrupted()
                    changed = true
                }
                active.entry.kind == ReverseBeatTargetKind.BOMB && active.nearDodgeRegistered -> {
                    resolution.score += applyScoreMultiplier(BOMB_DODGE_SCORE_BONUS)
                    resolution.awardedBombDodge = true
                    registerCallout("DODGE +$BOMB_DODGE_SCORE_BONUS", ReverseBeatCalloutTone.CYAN)
                    changed = true
                }
            }
        }

        if (changed) {
            ui.value = syncSpecialUi(
                ui.value.copy(
                    score = resolution.score,
                    combo = resolution.combo,
                    bestCombo = resolution.bestCombo,
                    catches = resolution.catches,
                    misses = resolution.misses,
                    clearedTargets = resolution.clearedTargets,
                    progress = progress(chart, resolution.catches),
                    message = when {
                        resolution.misses >= MAX_MISSES -> "Too many misses — restart to sharpen the rhythm."
                        resolution.awardedBombDodge -> "Bomb dodge bonus — style kept alive."
                        else -> playingMessage(chart.mode)
                    }
                )
            )
        }
    }

    private fun sliceTargets(startX: Double, startY: Double, endX: Double, endY: Double) {
        if (activeTargets.isEmpty()) return
        val loadedChart = chart ?: return
        val resolution = Resolution(
            score = ui.value.score,
            combo = ui.value.combo,
            catches = ui.value.catches,
            misses = ui.value.misses,
            bestCombo = ui.value.bestCombo,
            clearedTargets = ui.value.clearedTargets
        )

        val iterator = activeTargets.iterator()
        while (iterator.hasNext()) {
            val active = iterator.next()
            val render = renderTarget(active.entry, loadedChart.mode, lastPlaybackMs) ?: continue
            if (!ReverseBeatSliceMath.segmentHitsCircle(
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY,
                    circleX = render.x,
                    circleY = render.y,
                    radius = render.radius * BOMB_HIT_RADIUS_MULTIPLIER
                )
            ) {
                continue
            }

            iterator.remove()
            pushBurst(render.x, render.y)
            when {
                active.entry.kind == ReverseBeatTargetKind.BOMB -> {
                    resolution.combo = 0
                    resolution.misses += 2
                    resolution.score = max(0, resolution.score - 140)
                    beatPulse = max(beatPulse, 0.98f)
                    swipePulse = max(swipePulse, 0.86f)
                    errorTintPulse = 1f
                    onComboInterrupted()
                    resolution.triggeredBomb = true
                    resolution.hitAny = true
                }
                active.entry.kind.isPowerUp -> {
                    activatePowerUp(
                        kind = active.entry.kind,
                        render = render,
                        resolution = resolution,
                        chart = loadedChart
                    )
                    resolution.hitAny = true
                }
                else -> {
                    handleStandardBallHit(
                        entry = active.entry,
                        render = render,
                        resolution = resolution,
                        chart = loadedChart,
                        timingErrorMs = abs(lastPlaybackMs - active.entry.hitTimeMs),
                        allowPerfectAndAirDance = true,
                        allowBloom = true,
                        emitHitWord = true
                    )
                    resolution.hitAny = true
                }
            }
        }

        if (resolution.triggeredLineClear) {
            triggerLineClear(resolution, loadedChart)
        }

        if (resolution.hitAny || resolution.triggeredLineClear) {
            if (resolution.misses >= MAX_MISSES) {
                finishRun("Bombed out — restart to sharpen the rhythm.")
                return
            }
            ui.value = syncSpecialUi(
                ui.value.copy(
                    score = resolution.score,
                    combo = resolution.combo,
                    bestCombo = resolution.bestCombo,
                    catches = resolution.catches,
                    misses = resolution.misses,
                    clearedTargets = resolution.clearedTargets,
                    progress = progress(loadedChart, resolution.catches),
                    bombHitCueCount = ui.value.bombHitCueCount + if (resolution.triggeredBomb) 1 else 0,
                    message = when {
                        resolution.triggeredBomb -> "Bomb hit — keep the blade off hazards."
                        resolution.triggeredLineClear -> "Line Clear — the lane just exploded open."
                        resolution.triggeredDoubleScore -> "Double Score active."
                        resolution.triggeredLyricBloomPickup -> "Lyric Bloom primed."
                        else -> playingMessage(loadedChart.mode)
                    }
                )
            )
        }
    }

    private fun handleStandardBallHit(
        entry: ReverseBeatChartEntry,
        render: ReverseBeatTargetRender,
        resolution: Resolution,
        chart: ReverseBeatChart,
        timingErrorMs: Long,
        allowPerfectAndAirDance: Boolean,
        allowBloom: Boolean,
        emitHitWord: Boolean
    ) {
        val perfectHit = allowPerfectAndAirDance && timingErrorMs <= PERFECT_WINDOW_MS
        if (allowPerfectAndAirDance) {
            updatePerfectStreak(perfectHit)
            updateAirDanceState(render.x)
        }

        resolution.combo += 1
        resolution.catches += 1
        resolution.clearedTargets += 1
        resolution.bestCombo = max(resolution.bestCombo, resolution.combo)

        val accuracyBonus = if (allowPerfectAndAirDance) {
            max(0, 90 - (timingErrorMs / 6L).toInt())
        } else {
            36
        }
        val comboBonus = 110 + (resolution.combo * 16) + accuracyBonus
        val airDanceBonus = if (allowPerfectAndAirDance && airDanceActiveCount >= 2) {
            airDanceActiveCount * 8
        } else {
            0
        }
        val awarded = applyScoreMultiplier(comboBonus + airDanceBonus)
        resolution.score += awarded

        if (emitHitWord) {
            entry.label?.let { label ->
                pushHitWord(
                    text = label,
                    x = render.x,
                    y = render.y,
                    accent = entry.emphasis
                )
            }
        }

        beatPulse = max(beatPulse, if (perfectHit) 1f else 0.92f)
        swipePulse = max(swipePulse, if (perfectHit) 0.84f else 0.68f)

        if (allowPerfectAndAirDance && perfectStreak == PERFECT_ACTIVATION_STREAK) {
            registerCallout("ON-BEAT PERFECT", ReverseBeatCalloutTone.GOLD)
        }

        if (allowBloom && lyricBloomCharges > 0 && chart.mode == ReverseBeatChartMode.LYRIC) {
            lyricBloomCharges = (lyricBloomCharges - 1).coerceAtLeast(0)
            triggerLyricBloomCatch(originX = render.x, originY = render.y, resolution = resolution, chart = chart)
        }
    }

    private fun activatePowerUp(
        kind: ReverseBeatTargetKind,
        render: ReverseBeatTargetRender,
        resolution: Resolution,
        chart: ReverseBeatChart
    ) {
        when (kind) {
            ReverseBeatTargetKind.POWER_DOUBLE_SCORE -> {
                doubleScoreUntilMs = max(lastPlaybackMs, doubleScoreUntilMs) + DOUBLE_SCORE_DURATION_MS
                registerCallout("×2 ACTIVE", ReverseBeatCalloutTone.AMBER)
                resolution.triggeredDoubleScore = true
                beatPulse = max(beatPulse, 0.96f)
            }
            ReverseBeatTargetKind.POWER_LINE_CLEAR -> {
                registerCallout("LINE CLEAR", ReverseBeatCalloutTone.TEAL)
                resolution.triggeredLineClear = true
                beatPulse = max(beatPulse, 1f)
            }
            ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> {
                lyricBloomCharges = (lyricBloomCharges + LYRIC_BLOOM_PICKUP_CHARGES).coerceAtMost(8)
                registerCallout("LYRIC BLOOM", ReverseBeatCalloutTone.PINK)
                resolution.triggeredLyricBloomPickup = true
                beatPulse = max(beatPulse, 0.96f)
            }
            else -> Unit
        }
        pushBurst(render.x, render.y)
    }

    private fun triggerLineClear(
        resolution: Resolution,
        chart: ReverseBeatChart
    ) {
        if (lineClearInProgress) return
        lineClearInProgress = true
        try {
            val clearable = activeTargets.filter { it.entry.kind != ReverseBeatTargetKind.BOMB }.toList()
            clearable.forEach { target ->
                if (!activeTargets.remove(target)) return@forEach
                val render = renderTarget(target.entry, chart.mode, lastPlaybackMs) ?: return@forEach
                pushBurst(render.x, render.y)
                when {
                    target.entry.kind.isStandardBall -> {
                        handleStandardBallHit(
                            entry = target.entry,
                            render = render,
                            resolution = resolution,
                            chart = chart,
                            timingErrorMs = PERFECT_WINDOW_MS * 2,
                            allowPerfectAndAirDance = false,
                            allowBloom = false,
                            emitHitWord = false
                        )
                    }
                    target.entry.kind == ReverseBeatTargetKind.POWER_DOUBLE_SCORE -> {
                        doubleScoreUntilMs = max(lastPlaybackMs, doubleScoreUntilMs) + DOUBLE_SCORE_DURATION_MS
                    }
                    target.entry.kind == ReverseBeatTargetKind.POWER_LYRIC_BLOOM -> {
                        lyricBloomCharges = (lyricBloomCharges + LYRIC_BLOOM_PICKUP_CHARGES).coerceAtMost(8)
                    }
                    else -> Unit
                }
            }
        } finally {
            lineClearInProgress = false
        }
    }

    private fun triggerLyricBloomCatch(
        originX: Double,
        originY: Double,
        resolution: Resolution,
        chart: ReverseBeatChart
    ) {
        val candidate = activeTargets
            .filter { it.entry.kind.isStandardBall }
            .mapNotNull { target ->
                val render = renderTarget(target.entry, chart.mode, lastPlaybackMs) ?: return@mapNotNull null
                val distance = hypot(render.x - originX, render.y - originY)
                val timeDelta = abs(target.entry.hitTimeMs - lastPlaybackMs)
                if (distance > BLOOM_CATCH_RADIUS || timeDelta > BLOOM_MAX_TIME_DELTA_MS) return@mapNotNull null
                Triple(target, render, distance)
            }
            .minByOrNull { it.third }
            ?: return

        activeTargets.remove(candidate.first)
        pushBurst(candidate.second.x, candidate.second.y)
        handleStandardBallHit(
            entry = candidate.first.entry,
            render = candidate.second,
            resolution = resolution,
            chart = chart,
            timingErrorMs = abs(lastPlaybackMs - candidate.first.entry.hitTimeMs),
            allowPerfectAndAirDance = false,
            allowBloom = false,
            emitHitWord = true
        )
    }

    private fun registerNearBombPass(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double
    ) {
        val chartMode = chart?.mode ?: return
        activeTargets.forEach { target ->
            if (target.entry.kind != ReverseBeatTargetKind.BOMB || target.nearDodgeRegistered) return@forEach
            val render = renderTarget(target.entry, chartMode, lastPlaybackMs) ?: return@forEach
            val near = ReverseBeatSliceMath.segmentHitsCircle(
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                circleX = render.x,
                circleY = render.y,
                radius = render.radius * BOMB_NEAR_DODGE_RADIUS_MULTIPLIER
            )
            val hit = ReverseBeatSliceMath.segmentHitsCircle(
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                circleX = render.x,
                circleY = render.y,
                radius = render.radius * BOMB_HIT_RADIUS_MULTIPLIER
            )
            if (near && !hit) {
                target.nearDodgeRegistered = true
            }
        }
    }

    private fun updatePerfectStreak(perfectHit: Boolean) {
        perfectStreak = if (perfectHit) {
            perfectStreak + 1
        } else {
            0
        }
    }

    private fun updateAirDanceState(hitX: Double) {
        val side = if (hitX < WORLD_WIDTH * 0.5) SliceSide.LEFT else SliceSide.RIGHT
        val lastSide = airDanceLastSide
        val lastHitMs = airDanceLastHitMs
        val withinWindow = lastHitMs != null && (lastPlaybackMs - lastHitMs) <= AIR_DANCE_WINDOW_MS

        if (lastSide == null || !withinWindow) {
            if (airDanceActiveCount >= 2) {
                triggerAirDanceSummary()
            }
            airDanceActiveCount = 1
        } else if (lastSide != side) {
            airDanceActiveCount = if (airDanceActiveCount < 2) 2 else airDanceActiveCount + 1
        } else {
            if (airDanceActiveCount >= 2) {
                triggerAirDanceSummary()
            }
            airDanceActiveCount = 1
        }

        airDanceLastSide = side
        airDanceLastHitMs = lastPlaybackMs
    }

    private fun onComboInterrupted() {
        if (airDanceActiveCount >= 2) {
            triggerAirDanceSummary()
        }
        airDanceActiveCount = 0
        airDanceLastSide = null
        airDanceLastHitMs = null
        perfectStreak = 0
    }

    private fun triggerAirDanceSummary() {
        airDanceSummaryCount = airDanceActiveCount
        airDanceSummaryLife = AIR_DANCE_SUMMARY_LIFE_SECONDS
    }

    private fun applyScoreMultiplier(baseScore: Int): Int {
        val perfectMultiplier = currentPerfectMultiplier()
        val doubleScoreMultiplier = if (doubleScoreUntilMs > lastPlaybackMs) 2f else 1f
        return (baseScore.toFloat() * perfectMultiplier * doubleScoreMultiplier).toInt()
    }

    private fun currentPerfectMultiplier(): Float {
        if (perfectStreak < PERFECT_ACTIVATION_STREAK) return 1f
        return (1f + min(0.85f, (perfectStreak - PERFECT_ACTIVATION_STREAK + 1) * 0.18f)).coerceAtMost(2.1f)
    }

    private fun registerCallout(text: String, tone: ReverseBeatCalloutTone) {
        specialCalloutText = text
        specialCalloutTone = tone
        specialCalloutLife = SPECIAL_CALLOUT_LIFE_SECONDS
    }

    private fun pushTrailPoint(x: Double, y: Double) {
        appendCapped(
            list = trailPoints,
            item = TrailPoint(
                x = x,
                y = y,
                life = TRAIL_LIFE_SECONDS,
                maxLife = TRAIL_LIFE_SECONDS
            ),
            maxSize = performanceProfile.maxTrailPoints
        )
    }

    private fun pushBurst(x: Double, y: Double) {
        appendCapped(
            list = bursts,
            item = Burst(
                id = nextBurstId++,
                x = x,
                y = y,
                life = BURST_LIFE_SECONDS,
                maxLife = BURST_LIFE_SECONDS
            ),
            maxSize = performanceProfile.maxBursts
        )
    }

    private fun pushHitWord(
        text: String,
        x: Double,
        y: Double,
        accent: Float
    ) {
        val id = nextWordId++
        appendCapped(
            list = hitWords,
            item = HitWord(
                id = id,
                text = text,
                x = x,
                y = y,
                life = WORD_LIFE_SECONDS,
                maxLife = WORD_LIFE_SECONDS,
                accent = accent,
                rotationDirection = if ((id + songSeed) % 2L == 0L) 1f else -1f
            ),
            maxSize = performanceProfile.maxHitWords
        )
    }

    private fun <T> appendCapped(
        list: MutableList<T>,
        item: T,
        maxSize: Int
    ) {
        if (maxSize <= 0) return
        if (list.size >= maxSize) {
            list.removeAt(0)
        }
        list += item
    }

    private fun progress(chart: ReverseBeatChart, catches: Int): Float {
        if (chart.playableTargetCount == 0) return 0f
        return (catches.toFloat() / chart.playableTargetCount.toFloat()).coerceIn(0f, 1f)
    }

    private fun readyMessage(chart: ReverseBeatChart): String {
        return when (chart.mode) {
            ReverseBeatChartMode.LYRIC -> "Fullscreen lyric slicing is ready. Swipe through the words, collect the symbols, and avoid the bombs."
            ReverseBeatChartMode.BEAT -> "Fullscreen beat slicing is ready. Swipe through the beat arcs, collect the symbols, and avoid the bombs."
        }
    }

    private fun playingMessage(mode: ReverseBeatChartMode): String {
        return when (mode) {
            ReverseBeatChartMode.LYRIC -> "Slice the lyric balls on the beat, collect the symbols, and avoid the bombs."
            ReverseBeatChartMode.BEAT -> "Keep the blade moving through the beat arcs, collect the symbols, and avoid the bombs."
        }
    }

    private fun finishRun(message: String) {
        if (ui.value.phase == ReverseBeatPhase.FINISHED) return
        onComboInterrupted()
        ui.value = syncSpecialUi(
            ui.value.copy(
                phase = ReverseBeatPhase.FINISHED,
                message = message
            )
        )
        lastSwipePoint = null
    }

    private fun syncSpecialUi(base: ReverseBeatUiState): ReverseBeatUiState {
        val doubleRemaining = (doubleScoreUntilMs - lastPlaybackMs).coerceAtLeast(0L)
        return base.copy(
            perfectStreak = perfectStreak,
            perfectActive = perfectStreak >= PERFECT_ACTIVATION_STREAK,
            perfectMultiplier = currentPerfectMultiplier(),
            doubleScoreActive = doubleRemaining > 0L,
            doubleScoreRemainingMs = doubleRemaining,
            lyricBloomCharges = lyricBloomCharges,
            airDanceActiveCount = if (airDanceActiveCount >= 2) airDanceActiveCount else 0,
            airDanceSummaryCount = if (airDanceSummaryLife > 0f) airDanceSummaryCount else 0,
            airDanceSummaryAlpha = (airDanceSummaryLife / AIR_DANCE_SUMMARY_LIFE_SECONDS).coerceIn(0f, 1f),
            specialCalloutText = specialCalloutText.takeIf { specialCalloutLife > 0f },
            specialCalloutTone = specialCalloutTone,
            specialCalloutAlpha = (specialCalloutLife / SPECIAL_CALLOUT_LIFE_SECONDS).coerceIn(0f, 1f)
        )
    }

    private fun refreshTransientUiState() {
        if (ui.value.phase == ReverseBeatPhase.READY && !ui.value.chartReady) return
        if (
            perfectStreak > 0 ||
            doubleScoreUntilMs > lastPlaybackMs ||
            lyricBloomCharges > 0 ||
            airDanceActiveCount > 0 ||
            airDanceSummaryLife > 0f ||
            specialCalloutLife > 0f
        ) {
            ui.value = syncSpecialUi(ui.value)
        }
    }

    private fun clearTransientState() {
        activeTargets.clear()
        bursts.clear()
        hitWords.clear()
        trailPoints.clear()
        pendingIndex = 0
        beatPulse = 0f
        swipePulse = 0f
        lastPlaybackMs = 0L
        lastFeatures = AudioFeatures()
        lastSwipePoint = null
        nextBurstId = 1L
        nextWordId = 1L
        errorTintPulse = 0f
        perfectStreak = 0
        doubleScoreUntilMs = 0L
        lyricBloomCharges = 0
        airDanceActiveCount = 0
        airDanceLastSide = null
        airDanceLastHitMs = null
        airDanceSummaryCount = 0
        airDanceSummaryLife = 0f
        specialCalloutText = null
        specialCalloutTone = ReverseBeatCalloutTone.INFO
        specialCalloutLife = 0f
        lineClearInProgress = false
    }
}
