package com.lyrictica.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Offline decoder: compressed audio file -> mono PCM float stream.
 *
 * Notes:
 * - This uses MediaExtractor/MediaCodec, so supported formats match what the device decoder stack
 *   can decode (same class of codecs ExoPlayer typically plays).
 * - Output PCM is expected to be 16-bit signed little-endian on most devices.
 */
internal object PcmDecoder {

    private const val TAG = "PcmDecoder"

    data class OutputFormat(
        val sampleRateHz: Int,
        val channelCount: Int
    )

    /**
     * Decodes [file] and calls [onPcmChunk] with interleaved mono samples in [-1, 1].
     *
     * [onPcmChunk] may be called many times; keep it fast.
     */
    fun decodeToMonoFloat(
        file: File,
        onFormat: (OutputFormat) -> Unit,
        onPcmChunk: (FloatArray) -> Unit
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        var audioTrackIndex = -1
        var trackFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                trackFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || trackFormat == null) {
            extractor.release()
            throw IllegalStateException("No audio track found in file=${file.absolutePath}")
        }

        extractor.selectTrack(audioTrackIndex)

        val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("Missing mime for audio track")

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(trackFormat, null, null, 0)
        codec.start()

        val sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        onFormat(OutputFormat(sampleRateHz = sampleRate, channelCount = channels))

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        // Reuse buffers.
        val scratchShorts = ShortArray(8192)

        try {
            while (!sawOutputEos) {
                // Feed input.
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            val ptsUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, ptsUs, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain output.
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)

                            val floats = pcm16ToMonoFloats(
                                pcmBytes = outBuf,
                                channels = channels,
                                scratchShorts = scratchShorts
                            )
                            if (floats.isNotEmpty()) onPcmChunk(floats)
                        }

                        codec.releaseOutputBuffer(outIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }

                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        val newRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val newCh = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        Log.d(TAG, "Output format changed: rate=$newRate, ch=$newCh")
                        onFormat(OutputFormat(sampleRateHz = newRate, channelCount = newCh))
                    }

                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet.
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }
    }

    private fun pcm16ToMonoFloats(
        pcmBytes: ByteBuffer,
        channels: Int,
        scratchShorts: ShortArray
    ): FloatArray {
        // Most Android decoders output 16-bit PCM in native order.
        pcmBytes.order(ByteOrder.LITTLE_ENDIAN)

        val bytes = pcmBytes.remaining()
        if (bytes < 2) return FloatArray(0)

        val shortsCount = bytes / 2
        val shorts = if (shortsCount <= scratchShorts.size) scratchShorts else ShortArray(shortsCount)

        // Bulk read.
        var i = 0
        while (i < shortsCount) {
            shorts[i] = pcmBytes.short
            i++
        }

        val frames = shortsCount / channels
        if (frames <= 0) return FloatArray(0)

        val out = FloatArray(frames)
        var s = 0
        for (f in 0 until frames) {
            var acc = 0f
            for (ch in 0 until channels) {
                val v = shorts[s++].toInt() / 32768f
                acc += v
            }
            out[f] = acc / channels.toFloat()
        }
        return out
    }
}
