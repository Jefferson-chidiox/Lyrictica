package com.oss.euphoriae.data.`class`

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import com.oss.euphoriae.data.preferences.ReverbPreset
import com.oss.euphoriae.engine.AudioEngine

class AudioEffectsManager {
    
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    
    private var audioSessionId: Int = 0
    private var isEnabled: Boolean = true
    
    private var bandLevels: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    
    private var bassBoostLevel: Float = 0f
    private var virtualizerLevel: Float = 0f
    
    // DSP settings
    private var reverbPreset: ReverbPreset = ReverbPreset.NONE
    private var loudnessGain: Float = 0f  // 0 to 1
    private var stereoBalance: Float = 0f  // -1 (left) to 1 (right)
    private var channelSeparation: Float = 0.5f  // 0 to 1
    
    // Native audio engine reference
    private var nativeEngine: AudioEngine? = null
    private var useNativeEffects: Boolean = false
    
    fun initialize(audioSessionId: Int) {
        if (audioSessionId == 0) {
            Log.w(TAG, "Invalid audio session ID: 0")
            return
        }
        
        this.audioSessionId = audioSessionId
        
        try {
            release()
            
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled
            }
            
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = isEnabled
                if (getStrengthSupported()) {
                    setStrength((bassBoostLevel * 1000).toInt().toShort())
                }
            }
            
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = isEnabled
                if (getStrengthSupported()) {
                    setStrength((virtualizerLevel * 1000).toInt().toShort())
                }
            }
            
            // Initialize PresetReverb
            try {
                presetReverb = PresetReverb(0, audioSessionId).apply {
                    enabled = isEnabled && reverbPreset != ReverbPreset.NONE
                    preset = reverbPreset.value
                }
                Log.d(TAG, "PresetReverb initialized")
            } catch (e: Exception) {
                Log.w(TAG, "PresetReverb not supported", e)
            }
            
            // Initialize LoudnessEnhancer (API 19+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                        enabled = isEnabled && loudnessGain > 0
                        setTargetGain((loudnessGain * 1000).toInt())  // mB (millibels)
                    }
                    Log.d(TAG, "LoudnessEnhancer initialized")
                } catch (e: Exception) {
                    Log.w(TAG, "LoudnessEnhancer not supported", e)
                }
            }
            
            applyBandLevels()
            
            Log.d(TAG, "Audio effects initialized for session: $audioSessionId")
            Log.d(TAG, "Equalizer bands: ${equalizer?.numberOfBands}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects", e)
        }
    }
    
    /**
     * Attach native audio engine for high-performance effects
     */
    fun attachNativeEngine(engine: AudioEngine?) {
        nativeEngine = engine
        if (engine != null) {
            Log.d(TAG, "Native AudioEngine attached")
            // Sync current settings to native engine
            syncToNativeEngine()
        }
    }
    
    /**
     * Enable or disable native effects processing
     */
    fun setUseNativeEffects(enabled: Boolean) {
        useNativeEffects = enabled
        Log.d(TAG, "Native effects: $enabled")
    }
    
    private fun syncToNativeEngine() {
        nativeEngine?.apply {
            setBassBoost(bassBoostLevel)
            setVirtualizer(virtualizerLevel)
            bandLevels.forEachIndexed { index, level ->
                setEqualizerBand(index, level * 12f) // Convert to dB range
            }
        }
    }
    
    fun getNumberOfBands(): Int {
        return equalizer?.numberOfBands?.toInt() ?: 5
    }
    
    fun getBandFrequencyRange(band: Int): IntArray {
        return try {
            equalizer?.getBandFreqRange(band.toShort()) ?: intArrayOf(0, 0)
        } catch (e: Exception) {
            intArrayOf(0, 0)
        }
    }
    
    fun getCenterFrequency(band: Int): Int {
        return try {
            equalizer?.getCenterFreq(band.toShort()) ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    fun getBandLevelRange(): ShortArray {
        return equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
    }
    
    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
    }
    
    fun setBandLevel(band: Int, level: Float) {
        if (band >= 0 && band < bandLevels.size) {
            bandLevels[band] = level.coerceIn(-1f, 1f)
            
            try {
                val range = getBandLevelRange()
                val minLevel = range[0]
                val maxLevel = range[1]
                
                val mBLevel = if (level >= 0) {
                    (level * maxLevel).toInt().toShort()
                } else {
                    (level * -minLevel).toInt().toShort()
                }
                
                equalizer?.setBandLevel(band.toShort(), mBLevel)
                
                // Also apply to native engine
                if (useNativeEffects) {
                    nativeEngine?.setEqualizerBand(band, level * 12f)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set band level", e)
            }
        }
    }
    
    fun setAllBandLevels(levels: List<Float>) {
        levels.forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
    }
    
    fun getBandLevel(band: Int): Float {
        return if (band >= 0 && band < bandLevels.size) {
            bandLevels[band]
        } else {
            0f
        }
    }
    
    fun getAllBandLevels(): List<Float> {
        return bandLevels.toList()
    }
    
    fun setBassBoostLevel(level: Float) {
        bassBoostLevel = level.coerceIn(0f, 1f)
        try {
            bassBoost?.setStrength((bassBoostLevel * 1000).toInt().toShort())
            
            // Also apply to native engine
            if (useNativeEffects) {
                nativeEngine?.setBassBoost(bassBoostLevel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set bass boost", e)
        }
    }
    
    fun getBassBoostLevel(): Float = bassBoostLevel
    
    fun setVirtualizerLevel(level: Float) {
        virtualizerLevel = level.coerceIn(0f, 1f)
        try {
            virtualizer?.setStrength((virtualizerLevel * 1000).toInt().toShort())
            
            // Also apply to native engine
            if (useNativeEffects) {
                nativeEngine?.setVirtualizer(virtualizerLevel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set virtualizer", e)
        }
    }
    
    fun getVirtualizerLevel(): Float = virtualizerLevel
    
    // ================== DSP Settings ==================
    
    fun setReverbPreset(preset: ReverbPreset) {
        reverbPreset = preset
        try {
            presetReverb?.apply {
                this.preset = preset.value
                enabled = isEnabled && preset != ReverbPreset.NONE
            }
            Log.d(TAG, "Reverb preset set to: ${preset.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set reverb preset", e)
        }
    }
    
    fun getReverbPreset(): ReverbPreset = reverbPreset
    
    fun setLoudnessGain(gain: Float) {
        loudnessGain = gain.coerceIn(0f, 1f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer?.apply {
                    setTargetGain((loudnessGain * 1000).toInt())  // Convert to millibels
                    enabled = isEnabled && loudnessGain > 0
                }
            }
            Log.d(TAG, "Loudness gain set to: ${(loudnessGain * 100).toInt()}%")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set loudness gain", e)
        }
    }
    
    fun getLoudnessGain(): Float = loudnessGain
    
    fun setStereoBalance(balance: Float) {
        stereoBalance = balance.coerceIn(-1f, 1f)
        // Stereo balance is applied through native engine or virtualizer manipulation
        // For now, we store the value and can apply it in the audio processor
        Log.d(TAG, "Stereo balance set to: $stereoBalance")
    }
    
    fun getStereoBalance(): Float = stereoBalance
    
    fun setChannelSeparation(separation: Float) {
        channelSeparation = separation.coerceIn(0f, 1f)
        // Channel separation affects stereo width
        // Can be applied via virtualizer or native engine
        Log.d(TAG, "Channel separation set to: ${(channelSeparation * 100).toInt()}%")
    }
    
    fun getChannelSeparation(): Float = channelSeparation
    
    fun getAudioSessionId(): Int = audioSessionId
    
    fun isReverbSupported(): Boolean = presetReverb != null
    
    fun isLoudnessEnhancerSupported(): Boolean = loudnessEnhancer != null
    
    // ================== Preset & Reset ==================
    
    fun applyPreset(presetName: String): Boolean {
        val presetLevels = PRESETS[presetName] ?: return false
        setAllBandLevels(presetLevels)
        return true
    }
    
    fun getPresets(): List<String> = PRESETS.keys.toList()
    
    fun reset() {
        setAllBandLevels(listOf(0f, 0f, 0f, 0f, 0f))
        setBassBoostLevel(0f)
        setVirtualizerLevel(0f)
    }
    
    fun resetDacSettings() {
        setReverbPreset(ReverbPreset.NONE)
        setLoudnessGain(0f)
        setStereoBalance(0f)
        setChannelSeparation(0.5f)
    }
    
    fun resetAll() {
        reset()
        resetDacSettings()
    }
    
    private fun applyBandLevels() {
        bandLevels.forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
    }
    
    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            presetReverb?.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio effects", e)
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        presetReverb = null
        loudnessEnhancer = null
        nativeEngine = null
    }
    
    companion object {
        private const val TAG = "AudioEffectsManager"
        
        val PRESETS = mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
            "Bass Boost" to listOf(0.8f, 0.5f, 0f, 0f, 0f),
            "Treble Boost" to listOf(0f, 0f, 0f, 0.5f, 0.8f),
            "Rock" to listOf(0.6f, 0.3f, -0.2f, 0.4f, 0.6f),
            "Pop" to listOf(-0.2f, 0.3f, 0.5f, 0.3f, -0.2f),
            "Jazz" to listOf(0.4f, 0.2f, -0.3f, 0.2f, 0.4f),
            "Classical" to listOf(0.3f, 0.1f, 0f, 0.2f, 0.5f),
            "Hip Hop" to listOf(0.7f, 0.4f, 0f, 0.2f, 0.4f),
            "Electronic" to listOf(0.6f, 0.2f, 0f, 0.3f, 0.7f)
        )
    }
}

