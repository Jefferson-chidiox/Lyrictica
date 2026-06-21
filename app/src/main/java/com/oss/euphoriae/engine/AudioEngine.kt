package com.oss.euphoriae.engine

import android.util.Log

/**
 * Pure Kotlin fallback for Euphoriae's audio engine API.
 *
 * The original project uses a native DSP engine, but this repo does not ship
 * the Android NDK toolchain, so we keep the API and state here and let audio
 * pass through unchanged.
 */
class AudioEngine private constructor() {

    companion object {
        private const val TAG = "AudioEngine"

        @Volatile
        private var INSTANCE: AudioEngine? = null

        fun getInstance(): AudioEngine = INSTANCE ?: synchronized(this) {
            INSTANCE ?: AudioEngine().also {
                INSTANCE = it
                Log.i(TAG, "AudioEngine singleton created (Kotlin fallback)")
            }
        }
    }

    private var isCreated = false

    private var volume = 1f
    private var bassBoost = 0f
    private var virtualizer = 0f
    private val equalizerBands = FloatArray(10)

    private var compressorStrength = 0f
    private var limiterCeiling = 0.95f
    private var surround3D = 0f
    private var roomSize = 0.5f
    private var surroundLevel = 0.5f
    private var surroundMode = 0
    private var headphoneSurround = false
    private var headphoneType = 0
    private var clarity = 0f
    private var tubeWarmth = 0f
    private var spectrumExtension = 0f
    private var trebleBoost = 0f
    private var volumeLeveler = 0f
    private var stereoBalance = 0f
    private var channelSeparation = 0.5f
    private var dynamicRange = 1f
    private var loudnessGain = 0f
    private var reverbPreset = 0
    private var reverbWetMix = 0f
    private var tempo = 1f
    private var pitchSemitones = 0f

    fun create() {
        if (!isCreated) {
            isCreated = true
            Log.i(TAG, "Audio engine created (fallback/no-op)")
        }
    }

    fun destroy() {
        if (isCreated) {
            isCreated = false
            Log.i(TAG, "Audio engine destroyed (fallback/no-op)")
        }
    }

    fun processAudio(buffer: FloatArray, numFrames: Int, channelCount: Int) {
        if (!isCreated || buffer.isEmpty() || numFrames <= 0 || channelCount <= 0) return
        // No-op fallback: keep the API shape, but don't alter audio samples.
    }

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 2f)
    }

    fun getVolume(): Float = volume

    fun setBassBoost(strength: Float) {
        bassBoost = strength.coerceIn(0f, 1f)
    }

    fun getBassBoost(): Float = bassBoost

    fun setVirtualizer(strength: Float) {
        virtualizer = strength.coerceIn(0f, 1f)
    }

    fun getVirtualizer(): Float = virtualizer

    fun setEqualizerBand(band: Int, gainDb: Float) {
        if (band in equalizerBands.indices) {
            equalizerBands[band] = gainDb.coerceIn(-12f, 12f)
        }
    }

    fun setCompressor(strength: Float) {
        compressorStrength = strength.coerceIn(0f, 1f)
    }

    fun getCompressor(): Float = compressorStrength

    fun setLimiter(ceiling: Float) {
        limiterCeiling = ceiling.coerceIn(0.5f, 1f)
    }

    fun setVolumeLeveler(level: Float) {
        volumeLeveler = level.coerceIn(0f, 1f)
    }

    fun setDynamicRange(range: Float) {
        dynamicRange = range.coerceIn(0f, 1f)
    }

    fun setLoudnessGain(gain: Float) {
        loudnessGain = gain.coerceIn(0f, 1f)
    }

    fun setSurround3D(depth: Float) {
        surround3D = depth.coerceIn(0f, 1f)
    }

    fun setRoomSize(size: Float) {
        roomSize = size.coerceIn(0f, 1f)
    }

    fun setSurroundLevel(level: Float) {
        surroundLevel = level.coerceIn(0f, 1f)
    }

    fun setSurroundMode(mode: Int) {
        surroundMode = mode.coerceIn(0, 4)
    }

    fun setHeadphoneSurround(enabled: Boolean) {
        headphoneSurround = enabled
    }

    fun setHeadphoneType(type: Int) {
        headphoneType = type.coerceIn(0, 4)
    }

    fun setClarity(level: Float) {
        clarity = level.coerceIn(0f, 1f)
    }

    fun getClarity(): Float = clarity

    fun setTubeWarmth(warmth: Float) {
        tubeWarmth = warmth.coerceIn(0f, 1f)
    }

    fun getTubeWarmth(): Float = tubeWarmth

    fun setSpectrumExtension(level: Float) {
        spectrumExtension = level.coerceIn(0f, 1f)
    }

    fun setTrebleBoost(level: Float) {
        trebleBoost = level.coerceIn(0f, 1f)
    }

    fun setStereoBalance(balance: Float) {
        stereoBalance = balance.coerceIn(-1f, 1f)
    }

    fun setChannelSeparation(separation: Float) {
        channelSeparation = separation.coerceIn(0f, 1f)
    }

    fun setReverb(preset: Int, wetMix: Float = 0.5f) {
        reverbPreset = preset.coerceIn(0, 6)
        reverbWetMix = wetMix.coerceIn(0f, 1f)
    }

    fun getReverbPreset(): Int = reverbPreset

    fun setTempo(tempo: Float) {
        this.tempo = tempo.coerceIn(0.5f, 2.0f)
    }

    fun getTempo(): Float = tempo

    fun setPitch(semitones: Float) {
        pitchSemitones = semitones.coerceIn(-12f, 12f)
    }

    fun getPitch(): Float = pitchSemitones
}
