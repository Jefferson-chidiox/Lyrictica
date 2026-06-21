package com.lyrictica.game.reversebeat

internal enum class ReverseBeatChartMode {
    LYRIC,
    BEAT
}

internal enum class ReverseBeatTargetKind {
    BALL,
    BOMB,
    POWER_DOUBLE_SCORE,
    POWER_LINE_CLEAR,
    POWER_LYRIC_BLOOM;

    val isPowerUp: Boolean
        get() = this == POWER_DOUBLE_SCORE || this == POWER_LINE_CLEAR || this == POWER_LYRIC_BLOOM

    val isStandardBall: Boolean
        get() = this == BALL
}

internal data class ReverseBeatChart(
    val mode: ReverseBeatChartMode,
    val entries: List<ReverseBeatChartEntry>,
    val durationMs: Long,
    val summary: String
) {
    val entryCount: Int
        get() = entries.size

    val playableTargetCount: Int
        get() = entries.count { it.kind.isStandardBall }

    val bombCount: Int
        get() = entries.count { it.kind == ReverseBeatTargetKind.BOMB }

    val powerUpCount: Int
        get() = entries.count { it.kind.isPowerUp }

    val lastExpiryMs: Long
        get() = entries.maxOfOrNull { it.expiryTimeMs } ?: durationMs
}

internal data class ReverseBeatChartEntry(
    val id: Long,
    val kind: ReverseBeatTargetKind,
    val hitTimeMs: Long,
    val flightDurationMs: Long,
    val startXFraction: Float,
    val apexXFraction: Float,
    val endXFraction: Float,
    val apexYFraction: Float,
    val radiusPx: Float,
    val emphasis: Float,
    val label: String?
) {
    val spawnTimeMs: Long
        get() = hitTimeMs - (flightDurationMs / 2L)

    val expiryTimeMs: Long
        get() = hitTimeMs + (flightDurationMs / 2L)
}

internal data class ReverseBeatBeatMoment(
    val timeMs: Long,
    val strength: Float
)
