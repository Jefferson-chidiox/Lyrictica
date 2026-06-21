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

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Custom RenderersFactory that injects NativeAudioProcessor into ExoPlayer's
 * audio rendering pipeline for native effects processing.
 */
@OptIn(UnstableApi::class)
class NativeRenderersFactory(
    context: Context,
    private val audioEngine: AudioEngine
) : DefaultRenderersFactory(context) {

    private var nativeAudioProcessor: NativeAudioProcessor? = null

    companion object {
        private const val TAG = "NativeRenderersFactory"
    }

    init {
        // Do NOT use EXTENSION_RENDERER_MODE_PREFER here.
        // Extension renderers FFmpeg can produce audio in formats or
        // at frame sizes that are incompatible with our NativeAudioProcessor
        // pipeline, introducing static / glitches regardless of EQ state.
        Log.i(TAG, "NativeRenderersFactory created")
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        Log.i(TAG, "buildAudioSink called - creating NativeAudioProcessor")
        
        // Create our native audio processor
        nativeAudioProcessor = NativeAudioProcessor(audioEngine)
        
        val audioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput) // Revert forced Float to prevent crash with Sonic/TimeStretch
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(nativeAudioProcessor!!))
            .build()
        
        Log.i(TAG, "AudioSink created with NativeAudioProcessor")
        return audioSink
    }

    fun getNativeAudioProcessor(): NativeAudioProcessor? = nativeAudioProcessor
}

