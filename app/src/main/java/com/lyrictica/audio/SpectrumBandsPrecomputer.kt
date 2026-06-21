package com.lyrictica.audio

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Offline precompute of four non-overlapping band envelopes.
 */
internal object SpectrumBandsPrecomputer {

    private const val TAG = "SpectrumBands"
    private const val CACHE_VERSION = 2

    data class PrecomputedBands(
        val durationMs: Long,
        val fps: Int,
        val bass: FloatArray,
        val mid: FloatArray,
        val presence: FloatArray,
        val treble: FloatArray
    )

    /**
     * Computes bands and caches them to [cacheFile] (overwritten). Returns the computed data.
     */
    fun computeAndCache(
        audioFile: File,
        fps: Int,
        cacheFile: File
    ): PrecomputedBands {
        val computed = compute(audioFile = audioFile, fps = fps)
        val parentDir = cacheFile.parentFile ?: error("Missing parent dir for ${cacheFile.absolutePath}")
        val tempFile = File(parentDir, "${cacheFile.name}.tmp")

        runCatching { tempFile.delete() }
        writeCache(tempFile, computed)

        runCatching { cacheFile.delete() }
        if (!tempFile.renameTo(cacheFile)) {
            tempFile.copyTo(cacheFile, overwrite = true)
            runCatching { tempFile.delete() }
        }

        return computed
    }

    /** Reads cached bands if file exists and is parseable; returns null otherwise. */
    fun readCache(cacheFile: File): PrecomputedBands? {
        if (!cacheFile.exists() || cacheFile.length() < 32) return null
        return runCatching { readCacheInternal(cacheFile) }.getOrNull()
    }

    fun cacheKeyFor(file: File, fps: Int): String {
        return sha1("${file.absolutePath}|${file.length()}|${file.lastModified()}|fps=$fps")
    }

    // ---------------------- core compute ----------------------

    private fun compute(audioFile: File, fps: Int): PrecomputedBands {
        var sampleRate = 44100

        // Band edges (Hz): tuned for clear separation and a dedicated presence lane.
        val bassCutHz = 180f
        val midCutHz = 750f
        val presenceCutHz = 3000f

        lateinit var bassLp: BiquadFilter
        lateinit var midHp: BiquadFilter
        lateinit var midLp: BiquadFilter
        lateinit var presenceHp: BiquadFilter
        lateinit var presenceLp: BiquadFilter
        lateinit var trebleHp: BiquadFilter

        // Accumulators
        var frameSamplesTarget = 0
        var frameSamplesCount = 0

        var sumSqBass = 0.0
        var sumSqMid = 0.0
        var sumSqPresence = 0.0
        var sumSqTreble = 0.0

        val bassFrames = ArrayList<Float>(4096)
        val midFrames = ArrayList<Float>(4096)
        val presenceFrames = ArrayList<Float>(4096)
        val trebleFrames = ArrayList<Float>(4096)

        var totalSamplesDecoded: Long = 0L

        PcmDecoder.decodeToMonoFloat(
            file = audioFile,
            onFormat = { fmt ->
                sampleRate = fmt.sampleRateHz
                frameSamplesTarget = max(1, sampleRate / fps)

                bassLp = BiquadFilter.lowPass(sampleRateHz = sampleRate, cutoffHz = bassCutHz)
                midHp = BiquadFilter.highPass(sampleRateHz = sampleRate, cutoffHz = bassCutHz)
                midLp = BiquadFilter.lowPass(sampleRateHz = sampleRate, cutoffHz = midCutHz)
                presenceHp = BiquadFilter.highPass(sampleRateHz = sampleRate, cutoffHz = midCutHz)
                presenceLp = BiquadFilter.lowPass(sampleRateHz = sampleRate, cutoffHz = presenceCutHz)
                trebleHp = BiquadFilter.highPass(sampleRateHz = sampleRate, cutoffHz = presenceCutHz)

                bassLp.reset()
                midHp.reset()
                midLp.reset()
                presenceHp.reset()
                presenceLp.reset()
                trebleHp.reset()

                Log.d(TAG, "Decode format: rate=${fmt.sampleRateHz}Hz ch=${fmt.channelCount} -> frameSamples=$frameSamplesTarget @ ${fps}fps")
            },
            onPcmChunk = { chunk ->
                for (x in chunk) {
                    totalSamplesDecoded++

                    val bass = bassLp.process(x)
                    val mid = midLp.process(midHp.process(x))
                    val presence = presenceLp.process(presenceHp.process(x))
                    val treble = trebleHp.process(x)

                    sumSqBass += (bass * bass).toDouble()
                    sumSqMid += (mid * mid).toDouble()
                    sumSqPresence += (presence * presence).toDouble()
                    sumSqTreble += (treble * treble).toDouble()

                    frameSamplesCount++
                    if (frameSamplesCount >= frameSamplesTarget) {
                        val invN = 1.0 / frameSamplesCount.toDouble().coerceAtLeast(1.0)

                        bassFrames.add(sqrt(sumSqBass * invN).toFloat())
                        midFrames.add(sqrt(sumSqMid * invN).toFloat())
                        presenceFrames.add(sqrt(sumSqPresence * invN).toFloat())
                        trebleFrames.add(sqrt(sumSqTreble * invN).toFloat())

                        // reset
                        frameSamplesCount = 0
                        sumSqBass = 0.0
                        sumSqMid = 0.0
                        sumSqPresence = 0.0
                        sumSqTreble = 0.0
                    }
                }
            }
        )

        // Flush last partial frame.
        if (frameSamplesCount > 0) {
            val invN = 1.0 / frameSamplesCount.toDouble().coerceAtLeast(1.0)
            bassFrames.add(sqrt(sumSqBass * invN).toFloat())
            midFrames.add(sqrt(sumSqMid * invN).toFloat())
            presenceFrames.add(sqrt(sumSqPresence * invN).toFloat())
            trebleFrames.add(sqrt(sumSqTreble * invN).toFloat())
        }

        val durationMs = if (sampleRate > 0) (totalSamplesDecoded * 1000L / sampleRate.toLong()) else 0L

        // Normalization: map to a stable 0..1 range with a soft knee.
        fun norm(v: Float): Float {
            val x = max(0f, v)
            val soft = x / (x + 0.18f)
            return soft.coerceIn(0f, 1f)
        }

        val bassArr = FloatArray(bassFrames.size) { i -> norm(bassFrames[i]) }
        val midArr = FloatArray(midFrames.size) { i -> norm(midFrames[i]) }
        val presenceArr = FloatArray(presenceFrames.size) { i -> norm(presenceFrames[i]) }
        val trebleArr = FloatArray(trebleFrames.size) { i -> norm(trebleFrames[i]) }

        // Ensure arrays are the same length.
        val n = minOf(bassArr.size, midArr.size, presenceArr.size, trebleArr.size)
        return PrecomputedBands(
            durationMs = durationMs,
            fps = fps,
            bass = bassArr.copyOf(n),
            mid = midArr.copyOf(n),
            presence = presenceArr.copyOf(n),
            treble = trebleArr.copyOf(n)
        )
    }

    // ---------------------- cache format ----------------------

    // Simple binary format:
    // magic(4)='SBND' + version(1)=2 + fps(int32) + durationMs(int64) + n(int32)
    // then 4 float arrays (bass, mid, presence, treble), each n float32 LE.

    private fun writeCache(file: File, data: PrecomputedBands) {
        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(4 + 1 + 4 + 8 + 4)
                .order(ByteOrder.LITTLE_ENDIAN)
            header.put(byteArrayOf('S'.code.toByte(), 'B'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte()))
            header.put(CACHE_VERSION.toByte())
            header.putInt(data.fps)
            header.putLong(data.durationMs)
            header.putInt(data.bass.size)
            fos.write(header.array())

            fun writeFloats(arr: FloatArray) {
                val buf = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                for (v in arr) buf.putFloat(v)
                fos.write(buf.array())
            }

            writeFloats(data.bass)
            writeFloats(data.mid)
            writeFloats(data.presence)
            writeFloats(data.treble)
        }
    }

    private fun readCacheInternal(file: File): PrecomputedBands {
        FileInputStream(file).use { fis ->
            val headerBytes = fis.readNBytes(4 + 1 + 4 + 8 + 4)
            val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

            val magic = ByteArray(4)
            header.get(magic)
            require(String(magic, Charsets.US_ASCII) == "SBND") { "Bad magic" }

            val version = header.get().toInt() and 0xFF
            require(version == CACHE_VERSION) { "Unsupported version=$version" }

            val fps = header.int
            val durationMs = header.long
            val n = header.int
            require(n >= 0) { "Bad n" }

            fun readFloats(): FloatArray {
                val bytes = fis.readNBytes(n * 4)
                require(bytes.size == n * 4) { "Truncated cache" }
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                return FloatArray(n) { bb.float }
            }

            val bass = readFloats()
            val mid = readFloats()
            val presence = readFloats()
            val treble = readFloats()

            return PrecomputedBands(
                durationMs = durationMs,
                fps = fps,
                bass = bass,
                mid = mid,
                presence = presence,
                treble = treble
            )
        }
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
