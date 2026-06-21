package com.lyrictica.karaoke

import android.net.Uri
import com.lyrictica.audio.AudioAnalysisStore
import com.lyrictica.lyrics.ParsedLyrics
import kotlinx.coroutines.delay
import java.io.File

internal class KaraokeAssetsRepository(
    private val stemStore: KaraokeStemStore,
    private val analysisStore: AudioAnalysisStore,
    private val provider: StemSeparationProvider
) {
    val isProviderConfigured: Boolean
        get() = provider.isConfigured

    suspend fun pruneMissingSources() {
        stemStore.pruneMissingSources()
    }

    suspend fun loadCachedAssets(
        sourceUri: Uri,
        lyrics: ParsedLyrics?,
        onProgress: (KaraokePreparationProgress) -> Unit = {}
    ): KaraokeAssetsResult {
        pruneMissingSources()
        val cached = stemStore.inspect(sourceUri)
            ?: return KaraokeAssetsResult(
                assets = KaraokePreparedAssets(stemProviderAvailable = provider.isConfigured),
                melodyReference = null
            )

        if (!cached.stemsReady) {
            return KaraokeAssetsResult(
                assets = cached.toPreparedAssets(provider.isConfigured),
                melodyReference = null
            )
        }

        val lyricsFingerprint = lyrics?.takeIf { it.hasWordSync }?.let(KaraokeMelodyReference::lyricsFingerprint)
        var bandCacheReady = cached.manifest?.bandCacheReady == true
        var failureReason = cached.manifest?.failureReason
        var encounteredFailure = false

        cached.instrumentalFile?.let { instrumental ->
            if (!bandCacheReady) {
                onProgress(
                    KaraokePreparationProgress(
                        stage = KaraokePreparationStage.BUILDING_BANDS,
                        message = "Refreshing instrumental band cache"
                    )
                )
            }
            bandCacheReady = runCatching {
                analysisStore.loadOrComputeBands(Uri.fromFile(instrumental))
                true
            }.getOrElse { error ->
                encounteredFailure = true
                failureReason = error.localizedMessage ?: "Instrumental analysis failed"
                false
            }
        }

        val melodyReference = if (lyrics != null && lyrics.hasWordSync && cached.vocalsFile != null) {
            if (cached.manifest?.melodyReady != true || cached.manifest?.lyricsFingerprint != lyricsFingerprint) {
                onProgress(
                    KaraokePreparationProgress(
                        stage = KaraokePreparationStage.BUILDING_MELODY,
                        message = "Updating pitch chart from cached LALAL.AI vocals"
                    )
                )
            }
            runCatching {
                analysisStore.loadOrComputeKaraokeMelody(Uri.fromFile(cached.vocalsFile), lyrics)
            }.getOrElse { error ->
                encounteredFailure = true
                failureReason = error.localizedMessage ?: "Pitch chart generation failed"
                null
            }?.takeIf { it.hasAnyReference }
        } else {
            null
        }

        if (!encounteredFailure) {
            failureReason = null
        }

        val updated = stemStore.updatePreparedState(
            cached = cached,
            lyricsFingerprint = lyricsFingerprint,
            melodyReady = melodyReference != null,
            bandCacheReady = bandCacheReady,
            failureReason = failureReason
        )

        return KaraokeAssetsResult(
            assets = updated.toPreparedAssets(
                stemProviderAvailable = provider.isConfigured,
                melodyReadyOverride = melodyReference != null,
                bandCacheReadyOverride = bandCacheReady,
                failureReasonOverride = failureReason
            ),
            melodyReference = melodyReference
        )
    }

    suspend fun prepare(
        sourceUri: Uri,
        lyrics: ParsedLyrics,
        onProgress: (KaraokePreparationProgress) -> Unit = {}
    ): KaraokeAssetsResult {
        pruneMissingSources()
        val cached = stemStore.inspect(sourceUri)
            ?: return KaraokeAssetsResult(
                assets = KaraokePreparedAssets(
                    stemProviderAvailable = provider.isConfigured,
                    failureReason = "The source audio is no longer available on this device."
                ),
                melodyReference = null
            )

        if (cached.stemsReady) {
            return loadCachedAssets(sourceUri, lyrics, onProgress)
        }

        if (!provider.isConfigured) {
            return KaraokeAssetsResult(
                assets = cached.toPreparedAssets(
                    stemProviderAvailable = false,
                    failureReasonOverride = "LALAL.AI karaoke prep is not configured on this build."
                ),
                melodyReference = null
            )
        }

        var remoteSourceId: String? = null
        var stagedSourceFile: File? = null
        var downloadTarget: File? = null

        try {
            onProgress(KaraokePreparationProgress(KaraokePreparationStage.UPLOADING, "Uploading track to LALAL.AI"))
            stagedSourceFile = stemStore.copySourceToUploadFile(cached.descriptor)
            val job = provider.submit(stagedSourceFile)
            remoteSourceId = job.sourceId

            var status: StemJobStatus
            while (true) {
                status = provider.poll(job.jobId)
                remoteSourceId = status.sourceId ?: remoteSourceId
                when (status.phase) {
                    StemJobPhase.QUEUED,
                    StemJobPhase.PROCESSING -> {
                        onProgress(
                            KaraokePreparationProgress(
                                stage = KaraokePreparationStage.WAITING,
                                message = status.statusMessage ?: "LALAL.AI is separating vocals and backing",
                                progressPercent = status.progressPercent
                            )
                        )
                        delay(POLL_INTERVAL_MS)
                    }

                    StemJobPhase.SUCCEEDED -> break
                    StemJobPhase.FAILED -> {
                        throw IllegalStateException(status.failureReason ?: "Stem separation failed")
                    }
                }
            }

            onProgress(KaraokePreparationProgress(KaraokePreparationStage.DOWNLOADING, "Downloading LALAL.AI karaoke stems"))
            downloadTarget = stemStore.createDownloadDirectory(cached.descriptor.trackKey)
            val bundle = provider.download(job.jobId, downloadTarget)
            remoteSourceId = bundle.sourceId ?: remoteSourceId

            onProgress(KaraokePreparationProgress(KaraokePreparationStage.CACHING, "Saving karaoke stems on this device"))
            stemStore.persistDownloadedBundle(
                descriptor = cached.descriptor,
                providerId = provider.providerId,
                bundle = bundle
            )

            val refreshed = loadCachedAssets(sourceUri, lyrics, onProgress)
            return refreshed.copy(
                assets = refreshed.assets.copy(
                    failureReason = null
                )
            )
        } catch (error: Exception) {
            val failureReason = error.localizedMessage ?: "LALAL.AI karaoke prep failed"
            val failed = stemStore.recordFailure(
                descriptor = cached.descriptor,
                providerId = provider.providerId,
                failureReason = failureReason
            )
            return KaraokeAssetsResult(
                assets = failed.toPreparedAssets(
                    stemProviderAvailable = provider.isConfigured,
                    failureReasonOverride = failureReason
                ),
                melodyReference = null
            )
        } finally {
            runCatching { stagedSourceFile?.delete() }
            runCatching { downloadTarget?.deleteRecursively() }
            runCatching { provider.delete(remoteSourceId) }
        }
    }

    private fun CachedKaraokeStems.toPreparedAssets(
        stemProviderAvailable: Boolean,
        melodyReadyOverride: Boolean? = null,
        bandCacheReadyOverride: Boolean? = null,
        failureReasonOverride: String? = manifest?.failureReason
    ): KaraokePreparedAssets {
        return KaraokePreparedAssets(
            stemProviderAvailable = stemProviderAvailable,
            cachedStems = stemsReady,
            backingTrackReady = instrumentalFile?.isFile == true,
            melodyReady = melodyReadyOverride ?: manifest?.melodyReady == true,
            bandCacheReady = bandCacheReadyOverride ?: manifest?.bandCacheReady == true,
            instrumentalFile = instrumentalFile,
            vocalsFile = vocalsFile,
            failureReason = failureReasonOverride
        )
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1_500L
    }
}

internal data class KaraokePreparedAssets(
    val stemProviderAvailable: Boolean,
    val cachedStems: Boolean = false,
    val backingTrackReady: Boolean = false,
    val melodyReady: Boolean = false,
    val bandCacheReady: Boolean = false,
    val instrumentalFile: File? = null,
    val vocalsFile: File? = null,
    val failureReason: String? = null
)

internal data class KaraokeAssetsResult(
    val assets: KaraokePreparedAssets,
    val melodyReference: KaraokeMelodyReference?
)

internal enum class KaraokePreparationStage {
    UPLOADING,
    WAITING,
    DOWNLOADING,
    CACHING,
    BUILDING_MELODY,
    BUILDING_BANDS
}

internal data class KaraokePreparationProgress(
    val stage: KaraokePreparationStage,
    val message: String,
    val progressPercent: Int? = null
)
