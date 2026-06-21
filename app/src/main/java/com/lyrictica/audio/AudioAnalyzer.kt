package com.lyrictica.audio

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Band-based audio analysis.
 *
 * The visualizer only needs stable frequency-band envelopes, so we precompute
 * a small fixed set of bands and sample them during playback.
 */
class AudioAnalyzer(
    private val analysisStore: AudioAnalysisStore
) {

    private val _features = MutableStateFlow(AudioFeatures())
    val features: StateFlow<AudioFeatures> = _features.asStateFlow()

    private val _status = MutableStateFlow(AnalysisStatus())
    val status: StateFlow<AnalysisStatus> = _status.asStateFlow()

    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var loadJob: Job? = null

    private var spectrumBands: SpectrumBandsPrecomputer.PrecomputedBands? = null

    companion object {
        private const val TAG = "AudioAnalyzer"

        // Small compensation for output buffering. Keep this tiny to avoid visible lag.
        private const val LATENCY_COMPENSATION_MS = 40L
    }

    /**
     * Load/prepare analysis data for the given track.
     *
     * This is async and safe to call repeatedly; previous work is cancelled.
     */
    @Synchronized
    fun load(uri: Uri?) {
        loadJob?.cancel()
        spectrumBands = null
        _features.value = AudioFeatures()

        if (uri == null) {
            _status.value = AnalysisStatus(stage = AnalysisStatus.Stage.IDLE)
            return
        }

        _status.value = AnalysisStatus(
            stage = AnalysisStatus.Stage.STAGING,
            message = "Preparing durable audio analysis"
        )

        loadJob = scope.launch {
            _status.value = AnalysisStatus(
                stage = AnalysisStatus.Stage.SPECTRUM,
                message = "Computing band spectrum"
            )

            val bands = runCatching {
                analysisStore.loadOrComputeBands(uri, AUDIO_ANALYSIS_FPS)
            }

            bands.onSuccess { data ->
                spectrumBands = data
                Log.d(TAG, "Band spectrum ready: frames=${data.bass.size}, fps=${data.fps}, durationMs=${data.durationMs}")
                _status.value = AnalysisStatus(
                    stage = AnalysisStatus.Stage.READY,
                    spectrumReady = true,
                    message = "Band analysis ready"
                )
            }.onFailure { e ->
                val msg = "Band spectrum precompute failed"
                Log.w(TAG, "$msg: ${e.localizedMessage}", e)
                spectrumBands = null
                _status.value = AnalysisStatus(
                    stage = AnalysisStatus.Stage.ERROR,
                    message = msg,
                    error = e.localizedMessage ?: msg
                )
            }
        }
    }

    /** Reset analyzer output to idle (does not discard the loaded band cache). */
    @Synchronized
    fun reset() {
        _features.value = AudioFeatures()
    }

    /**
     * Update analysis output for the current playback position.
     *
     * Call this from your playback progress loop (ideally every frame or ~16ms).
     */
    @Synchronized
    fun onPlaybackPosition(positionMs: Long, isPlaying: Boolean) {
        if (!isPlaying) {
            _features.value = AudioFeatures()
            return
        }

        val bands = spectrumBands
        if (bands == null || bands.bass.isEmpty() || bands.mid.isEmpty() || bands.presence.isEmpty() || bands.treble.isEmpty() || bands.durationMs <= 0L) {
            _features.value = AudioFeatures()
            return
        }

        // Shift by a tiny compensation to better match the audible output,
        // then interpolate between frames to avoid staircase motion.
        val visualPosMs = (positionMs - LATENCY_COMPENSATION_MS).coerceAtLeast(0L)
        val framePos = (visualPosMs.toDouble() * bands.fps.toDouble()) / 1000.0
        val idx0 = framePos.toInt().coerceIn(0, bands.bass.lastIndex)
        val idx1 = (idx0 + 1).coerceIn(0, bands.bass.lastIndex)
        val frac = (framePos - idx0.toDouble()).toFloat().coerceIn(0f, 1f)

        fun sample(arr: FloatArray): Float {
            if (arr.isEmpty()) return 0f
            if (arr.size == 1 || idx0 == idx1) return arr[idx0]
            return arr[idx0] + (arr[idx1] - arr[idx0]) * frac
        }

        _features.value = AudioFeatures(
            bass = sample(bands.bass),
            mid = sample(bands.mid),
            presence = sample(bands.presence),
            treble = sample(bands.treble)
        )
    }

    fun release() {
        loadJob?.cancel()
        scope.cancel()
    }
}
