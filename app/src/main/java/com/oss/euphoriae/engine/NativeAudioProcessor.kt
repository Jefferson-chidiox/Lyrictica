/*
 * Copyright 2025 Euphoriae
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oss.euphoriae.engine

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * NativeAudioProcessor - ExoPlayer AudioProcessor that uses native effects
 * 
 * This processor intercepts audio from ExoPlayer and applies effects
 * using the native AudioEngine.
 */
@OptIn(UnstableApi::class)
class NativeAudioProcessor(private val audioEngine: AudioEngine) : AudioProcessor {

    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var inputBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    
    private var floatBuffer: FloatArray = FloatArray(0)
    private var bufferCounter = 0

    companion object {
        private const val TAG = "NativeAudioProcessor"
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Log.i(TAG, "Configure called: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, " +
                "encoding=${inputAudioFormat.encoding}")

        // Reset both formats first so that isActive() correctly returns false
        // if we bail out early due to an unsupported encoding.  Previously
        // only the early-return path skipped the assignment, leaving a stale
        // inputAudioFormat that made isActive() return true even though the
        // processor could not handle the new format – causing garbled output.
        this.inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        this.outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET

        // We only support PCM 16-bit or Float
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && 
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            Log.w(TAG, "Unsupported encoding: ${inputAudioFormat.encoding}")
            return AudioProcessor.AudioFormat.NOT_SET
        }
        
        this.inputAudioFormat = inputAudioFormat
        
        // Output same format as input
        this.outputAudioFormat = inputAudioFormat
        
        Log.i(TAG, "Processor configured successfully, isActive=${isActive()}")
        return outputAudioFormat
    }

    override fun isActive(): Boolean {
        val active = inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET
        return active
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        
        val inputSize = inputBuffer.remaining()
        val channelCount = inputAudioFormat.channelCount
        
        // Log every 100th buffer
        bufferCounter++
        if (bufferCounter % 100 == 0) {
            Log.i(TAG, "queueInput: size=$inputSize, encoding=${inputAudioFormat.encoding}, counter=$bufferCounter")
        }
        
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                processInt16(inputBuffer, channelCount)
            }
            C.ENCODING_PCM_FLOAT -> {
                processFloat32(inputBuffer, channelCount)
            }
        }
    }

    private fun processInt16(input: ByteBuffer, channelCount: Int) {
        val sampleCount = input.remaining() / 2
        val numFrames = sampleCount / channelCount
        
        // Ensure float buffer is large enough
        if (floatBuffer.size < sampleCount) {
            floatBuffer = FloatArray(sampleCount)
        }
        
        // Convert Int16 to Float using the buffer's own byte order so we don't
        // accidentally re-interpret bytes on devices where input.order() is not
        // LITTLE_ENDIAN.
        val shortBuffer = input.asShortBuffer()
        for (i in 0 until sampleCount) {
            floatBuffer[i] = shortBuffer.get(i) / 32768f
        }
        
        // Process with native engine
        audioEngine.processAudio(floatBuffer, numFrames, channelCount)
        
        // Prepare output buffer (allocate fresh when needed)
        if (outputBuffer === AudioProcessor.EMPTY_BUFFER || outputBuffer.capacity() < sampleCount * 2) {
            outputBuffer = ByteBuffer.allocateDirect(sampleCount * 2)
                .order(input.order())
        }
        outputBuffer.clear()
        
        // Convert Float back to Int16
        val outShortBuffer = outputBuffer.asShortBuffer()
        for (i in 0 until sampleCount) {
            val sample = (floatBuffer[i] * 32767f).toInt().coerceIn(-32768, 32767)
            outShortBuffer.put(i, sample.toShort())
        }
        
        outputBuffer.position(0)
        outputBuffer.limit(sampleCount * 2)
        
        // Mark input as fully consumed
        input.position(input.limit())
    }

    private fun processFloat32(input: ByteBuffer, channelCount: Int) {
        val sampleCount = input.remaining() / 4
        val numFrames = sampleCount / channelCount
        
        // Ensure float buffer is large enough
        if (floatBuffer.size < sampleCount) {
            floatBuffer = FloatArray(sampleCount)
        }
        
        // Copy floats from input using the buffer's own byte order
        val floatInput = input.asFloatBuffer()
        floatInput.get(floatBuffer, 0, sampleCount)
        
        // Process with native engine
        audioEngine.processAudio(floatBuffer, numFrames, channelCount)
        
        // Prepare output buffer (allocate fresh when needed)
        if (outputBuffer === AudioProcessor.EMPTY_BUFFER || outputBuffer.capacity() < sampleCount * 4) {
            outputBuffer = ByteBuffer.allocateDirect(sampleCount * 4)
                .order(input.order())
        }
        outputBuffer.clear()
        
        // Copy back to output
        val floatOutput = outputBuffer.asFloatBuffer()
        floatOutput.put(floatBuffer, 0, sampleCount)
        
        outputBuffer.position(0)
        outputBuffer.limit(sampleCount * 4)
        
        // Mark input as fully consumed
        input.position(input.limit())
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        floatBuffer = FloatArray(0)
    }
}
