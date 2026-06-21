package com.lyrictica.karaoke

import java.io.File

internal interface StemSeparationProvider {
    val providerId: String
    val isConfigured: Boolean

    suspend fun submit(source: File): StemJob

    suspend fun poll(jobId: String): StemJobStatus

    suspend fun download(jobId: String, targetDir: File): StemBundle

    suspend fun delete(sourceId: String?) {
        // Optional remote cleanup.
    }
}

internal data class StemJob(
    val jobId: String,
    val sourceId: String? = null,
    val acceptedMessage: String? = null
)

internal enum class StemJobPhase {
    QUEUED,
    PROCESSING,
    SUCCEEDED,
    FAILED
}

internal data class StemJobStatus(
    val jobId: String,
    val phase: StemJobPhase,
    val sourceId: String? = null,
    val progressPercent: Int? = null,
    val statusMessage: String? = null,
    val failureReason: String? = null,
    val vocalsUrl: String? = null,
    val instrumentalUrl: String? = null,
    val durationMs: Long? = null
)

internal data class StemBundle(
    val vocalsFile: File,
    val instrumentalFile: File,
    val sourceId: String? = null,
    val durationMs: Long? = null,
    val formatHint: String? = null
)
