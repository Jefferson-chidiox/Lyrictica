package com.lyrictica.game.reversebeat

import android.app.ActivityManager
import android.content.Context

private const val REVERSE_BEAT_LOW_END_TOTAL_RAM_BYTES = 3L * 1024L * 1024L * 1024L
private const val REVERSE_BEAT_LOW_END_MEMORY_CLASS_MB = 192

internal enum class ReverseBeatPerformanceTier {
    STANDARD,
    LOW_END
}

internal data class ReverseBeatPerformanceProfile(
    val tier: ReverseBeatPerformanceTier,
    val maxTrailPoints: Int,
    val maxBursts: Int,
    val maxHitWords: Int,
    val showSecondaryTargetGlow: Boolean,
    val burstSizeMultiplier: Float
) {
    companion object {
        val STANDARD = ReverseBeatPerformanceProfile(
            tier = ReverseBeatPerformanceTier.STANDARD,
            maxTrailPoints = 18,
            maxBursts = 8,
            maxHitWords = 6,
            showSecondaryTargetGlow = true,
            burstSizeMultiplier = 1f
        )

        val LOW_END = ReverseBeatPerformanceProfile(
            tier = ReverseBeatPerformanceTier.LOW_END,
            maxTrailPoints = 12,
            maxBursts = 4,
            maxHitWords = 3,
            showSecondaryTargetGlow = false,
            burstSizeMultiplier = 0.84f
        )
    }
}

internal fun resolveReverseBeatPerformanceProfile(context: Context): ReverseBeatPerformanceProfile {
    val activityManager = context.getSystemService(ActivityManager::class.java)
        ?: return ReverseBeatPerformanceProfile.STANDARD
    val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val lowEndDevice = activityManager.isLowRamDevice ||
        activityManager.memoryClass <= REVERSE_BEAT_LOW_END_MEMORY_CLASS_MB ||
        memoryInfo.totalMem in 1 until REVERSE_BEAT_LOW_END_TOTAL_RAM_BYTES
    return if (lowEndDevice) {
        ReverseBeatPerformanceProfile.LOW_END
    } else {
        ReverseBeatPerformanceProfile.STANDARD
    }
}
