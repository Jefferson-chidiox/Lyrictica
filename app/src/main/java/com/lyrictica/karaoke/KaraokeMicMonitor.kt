package com.lyrictica.karaoke

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

internal data class MicrophonePitchSample(
    val rms: Float,
    val pitchHz: Float?,
    val confidence: Float,
    val voiced: Boolean
)

internal class KaraokeMicMonitor(
    private val onSample: (MicrophonePitchSample) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var readJob: Job? = null
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (readJob != null) return true

        val sampleRate = 22_050
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false

        val bufferSize = maxOf(minBuffer, 4_096)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        audioRecord = record
        record.startRecording()

        readJob = scope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ShortArray(bufferSize / 2)

            while (isActive) {
                val readCount = record.read(buffer, 0, buffer.size)
                if (readCount <= 0) continue

                val rms = computeRms(buffer, readCount)
                val (pitchHz, confidence) = detectPitch(buffer, readCount, sampleRate)
                val voiced = rms >= 0.020f && pitchHz != null && confidence >= 0.38f

                onSample(
                    MicrophonePitchSample(
                        rms = rms,
                        pitchHz = pitchHz,
                        confidence = confidence,
                        voiced = voiced
                    )
                )
            }
        }

        return true
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun computeRms(buffer: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (index in 0 until count) {
            val sample = buffer[index] / 32768.0
            sum += sample * sample
        }
        return sqrt(sum / count).toFloat()
    }

    private fun detectPitch(buffer: ShortArray, count: Int, sampleRate: Int): Pair<Float?, Float> {
        if (count < 512) return null to 0f

        val normalized = FloatArray(count) { index -> buffer[index] / 32768f }
        val minLag = (sampleRate / 880f).toInt().coerceAtLeast(1)
        val maxLag = (sampleRate / 80f).toInt().coerceAtMost(count - 1)

        var zeroLagEnergy = 0f
        for (sample in normalized) {
            zeroLagEnergy += sample * sample
        }
        if (zeroLagEnergy <= 1e-6f) return null to 0f

        var bestLag = -1
        var bestScore = 0f

        for (lag in minLag..maxLag) {
            var correlation = 0f
            var lhsEnergy = 0f
            var rhsEnergy = 0f
            val limit = count - lag
            for (index in 0 until limit) {
                val lhs = normalized[index]
                val rhs = normalized[index + lag]
                correlation += lhs * rhs
                lhsEnergy += lhs * lhs
                rhsEnergy += rhs * rhs
            }

            if (lhsEnergy <= 1e-6f || rhsEnergy <= 1e-6f) continue
            val score = correlation / sqrt(lhsEnergy * rhsEnergy)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }

        if (bestLag <= 0 || bestScore < 0.30f) return null to bestScore
        val pitchHz = sampleRate.toFloat() / bestLag.toFloat()
        return pitchHz to bestScore
    }
}
