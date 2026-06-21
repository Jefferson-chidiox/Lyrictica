package com.oss.euphoriae.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// ================== Enums ==================

enum class ReverbPreset(val displayName: String, val value: Short) {
    NONE("None", 0),
    SMALL_ROOM("Small Room", 1),
    MEDIUM_ROOM("Medium Room", 2),
    LARGE_ROOM("Large Room", 3),
    MEDIUM_HALL("Medium Hall", 4),
    LARGE_HALL("Large Hall", 5),
    PLATE("Plate", 6)
}

enum class SurroundMode(val displayName: String) {
    OFF("Off"),
    MUSIC("Music"),
    MOVIE("Movie"),
    GAME("Game"),
    PODCAST("Podcast")
}

enum class HeadphoneType(val displayName: String, val description: String) {
    GENERIC("Generic", "Standard headphones"),
    IN_EAR("In-Ear", "IEMs with bass boost"),
    OVER_EAR("Over-Ear", "Full-size headphones"),
    OPEN_BACK("Open-Back", "Natural soundstage"),
    STUDIO("Studio", "Flat monitoring")
}

enum class EffectProfile(val displayName: String, val description: String) {
    CUSTOM("Custom", "Your custom settings"),
    MUSIC("Music", "Optimized for music"),
    MOVIE("Movie", "Surround for movies"),
    GAME("Game", "Low latency gaming"),
    PODCAST("Podcast", "Voice clarity"),
    HIFI("Hi-Fi", "Audiophile flat response")
}

class AudioPreferences(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "euphoriae_audio_prefs"
        
        // Equalizer keys
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_SELECTED_PRESET = "selected_preset"
        private const val KEY_BAND_PREFIX = "band_"
        private const val KEY_BASS_BOOST = "bass_boost"
        private const val KEY_VIRTUALIZER = "virtualizer"
        
        // DAC keys
        private const val KEY_REVERB_PRESET = "reverb_preset"
        private const val KEY_LOUDNESS_GAIN = "loudness_gain"
        private const val KEY_STEREO_BALANCE = "stereo_balance"
        private const val KEY_CHANNEL_SEPARATION = "channel_separation"
        
        // Surround Sound keys
        private const val KEY_SURROUND_MODE = "surround_mode"
        private const val KEY_SURROUND_LEVEL = "surround_level"
        private const val KEY_ROOM_SIZE = "room_size"
        private const val KEY_3D_EFFECT = "3d_effect"
        
        // Headphone keys
        private const val KEY_HEADPHONE_TYPE = "headphone_type"
        private const val KEY_HEADPHONE_SURROUND = "headphone_surround"
        
        // Dynamic processing keys
        private const val KEY_COMPRESSOR = "compressor"
        private const val KEY_VOLUME_LEVELER = "volume_leveler"
        private const val KEY_LIMITER = "limiter"
        private const val KEY_DYNAMIC_RANGE = "dynamic_range"
        
        // Enhancement keys
        private const val KEY_CLARITY = "clarity"
        private const val KEY_SPECTRUM_EXTENSION = "spectrum_extension"
        private const val KEY_TUBE_AMP = "tube_amp"
        private const val KEY_TREBLE_BOOST = "treble_boost"
        
        // Profile key
        private const val KEY_EFFECT_PROFILE = "effect_profile"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // ================== Equalizer Settings ==================
    
    fun isEqEnabled(): Boolean = prefs.getBoolean(KEY_EQ_ENABLED, true)
    fun setEqEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
    
    fun getSelectedPreset(): String = prefs.getString(KEY_SELECTED_PRESET, "Flat") ?: "Flat"
    fun setSelectedPreset(preset: String) = prefs.edit().putString(KEY_SELECTED_PRESET, preset).apply()
    
    fun getBandLevel(band: Int): Float = prefs.getFloat("${KEY_BAND_PREFIX}$band", 0f)
    fun setBandLevel(band: Int, level: Float) = prefs.edit().putFloat("${KEY_BAND_PREFIX}$band", level).apply()
    
    fun getBassBoost(): Float = prefs.getFloat(KEY_BASS_BOOST, 0f)
    fun setBassBoost(level: Float) = prefs.edit().putFloat(KEY_BASS_BOOST, level).apply()
    
    fun getVirtualizer(): Float = prefs.getFloat(KEY_VIRTUALIZER, 0f)
    fun setVirtualizer(level: Float) = prefs.edit().putFloat(KEY_VIRTUALIZER, level).apply()
    
    // ================== DSP Settings ==================
    
    fun getReverbPreset(): ReverbPreset {
        val presetOrdinal = prefs.getInt(KEY_REVERB_PRESET, 0)
        return ReverbPreset.entries.getOrElse(presetOrdinal) { ReverbPreset.NONE }
    }
    
    fun setReverbPreset(preset: ReverbPreset) = 
        prefs.edit().putInt(KEY_REVERB_PRESET, preset.ordinal).apply()
    
    val reverbPresetFlow: Flow<ReverbPreset> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_REVERB_PRESET) {
                trySend(getReverbPreset())
            }
        }
        trySend(getReverbPreset())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    fun getLoudnessGain(): Float = prefs.getFloat(KEY_LOUDNESS_GAIN, 0f)
    fun setLoudnessGain(gain: Float) = prefs.edit().putFloat(KEY_LOUDNESS_GAIN, gain).apply()
    
    fun getStereoBalance(): Float = prefs.getFloat(KEY_STEREO_BALANCE, 0f)
    fun setStereoBalance(balance: Float) = prefs.edit().putFloat(KEY_STEREO_BALANCE, balance).apply()
    
    fun getChannelSeparation(): Float = prefs.getFloat(KEY_CHANNEL_SEPARATION, 0.5f)
    fun setChannelSeparation(separation: Float) = prefs.edit().putFloat(KEY_CHANNEL_SEPARATION, separation).apply()
    
    // ================== Surround Sound ==================
    
    fun getSurroundMode(): SurroundMode {
        val ordinal = prefs.getInt(KEY_SURROUND_MODE, 0)
        return SurroundMode.entries.getOrElse(ordinal) { SurroundMode.OFF }
    }
    fun setSurroundMode(mode: SurroundMode) = prefs.edit().putInt(KEY_SURROUND_MODE, mode.ordinal).apply()
    
    fun getSurroundLevel(): Float = prefs.getFloat(KEY_SURROUND_LEVEL, 0.5f)
    fun setSurroundLevel(level: Float) = prefs.edit().putFloat(KEY_SURROUND_LEVEL, level).apply()
    
    fun getRoomSize(): Float = prefs.getFloat(KEY_ROOM_SIZE, 0.5f)
    fun setRoomSize(size: Float) = prefs.edit().putFloat(KEY_ROOM_SIZE, size).apply()
    
    fun get3DEffect(): Float = prefs.getFloat(KEY_3D_EFFECT, 0f)
    fun set3DEffect(effect: Float) = prefs.edit().putFloat(KEY_3D_EFFECT, effect).apply()
    
    // ================== Headphone Optimization ==================
    
    fun getHeadphoneType(): HeadphoneType {
        val ordinal = prefs.getInt(KEY_HEADPHONE_TYPE, 0)
        return HeadphoneType.entries.getOrElse(ordinal) { HeadphoneType.GENERIC }
    }
    fun setHeadphoneType(type: HeadphoneType) = prefs.edit().putInt(KEY_HEADPHONE_TYPE, type.ordinal).apply()
    
    fun getHeadphoneSurround(): Boolean = prefs.getBoolean(KEY_HEADPHONE_SURROUND, false)
    fun setHeadphoneSurround(enabled: Boolean) = prefs.edit().putBoolean(KEY_HEADPHONE_SURROUND, enabled).apply()
    
    // ================== Dynamic Processing ==================
    
    fun getCompressor(): Float = prefs.getFloat(KEY_COMPRESSOR, 0f)
    fun setCompressor(level: Float) = prefs.edit().putFloat(KEY_COMPRESSOR, level).apply()
    
    fun getVolumeLeveler(): Float = prefs.getFloat(KEY_VOLUME_LEVELER, 0f)
    fun setVolumeLeveler(level: Float) = prefs.edit().putFloat(KEY_VOLUME_LEVELER, level).apply()
    
    fun getLimiter(): Float = prefs.getFloat(KEY_LIMITER, 0f)
    fun setLimiter(level: Float) = prefs.edit().putFloat(KEY_LIMITER, level).apply()
    
    fun getDynamicRange(): Float = prefs.getFloat(KEY_DYNAMIC_RANGE, 1f)
    fun setDynamicRange(range: Float) = prefs.edit().putFloat(KEY_DYNAMIC_RANGE, range).apply()
    
    // ================== Audio Enhancement ==================
    
    fun getClarity(): Float = prefs.getFloat(KEY_CLARITY, 0f)
    fun setClarity(level: Float) = prefs.edit().putFloat(KEY_CLARITY, level).apply()
    
    fun getSpectrumExtension(): Float = prefs.getFloat(KEY_SPECTRUM_EXTENSION, 0f)
    fun setSpectrumExtension(level: Float) = prefs.edit().putFloat(KEY_SPECTRUM_EXTENSION, level).apply()
    
    fun getTubeAmp(): Float = prefs.getFloat(KEY_TUBE_AMP, 0f)
    fun setTubeAmp(level: Float) = prefs.edit().putFloat(KEY_TUBE_AMP, level).apply()
    
    fun getTrebleBoost(): Float = prefs.getFloat(KEY_TREBLE_BOOST, 0f)
    fun setTrebleBoost(level: Float) = prefs.edit().putFloat(KEY_TREBLE_BOOST, level).apply()
    
    // ================== Effect Profile ==================
    
    fun getEffectProfile(): EffectProfile {
        val ordinal = prefs.getInt(KEY_EFFECT_PROFILE, 0)
        return EffectProfile.entries.getOrElse(ordinal) { EffectProfile.CUSTOM }
    }
    fun setEffectProfile(profile: EffectProfile) = prefs.edit().putInt(KEY_EFFECT_PROFILE, profile.ordinal).apply()
    
    // ================== Reset Functions ==================
    
    fun resetDacSettings() {
        prefs.edit()
            .putInt(KEY_REVERB_PRESET, ReverbPreset.NONE.ordinal)
            .putFloat(KEY_LOUDNESS_GAIN, 0f)
            .putFloat(KEY_STEREO_BALANCE, 0f)
            .putFloat(KEY_CHANNEL_SEPARATION, 0.5f)
            .apply()
    }
    
    fun resetSurroundSettings() {
        prefs.edit()
            .putInt(KEY_SURROUND_MODE, SurroundMode.OFF.ordinal)
            .putFloat(KEY_SURROUND_LEVEL, 0.5f)
            .putFloat(KEY_ROOM_SIZE, 0.5f)
            .putFloat(KEY_3D_EFFECT, 0f)
            .apply()
    }
    
    fun resetDynamicSettings() {
        prefs.edit()
            .putFloat(KEY_COMPRESSOR, 0f)
            .putFloat(KEY_VOLUME_LEVELER, 0f)
            .putFloat(KEY_LIMITER, 0f)
            .putFloat(KEY_DYNAMIC_RANGE, 1f)
            .apply()
    }
    
    fun resetEnhancementSettings() {
        prefs.edit()
            .putFloat(KEY_CLARITY, 0f)
            .putFloat(KEY_SPECTRUM_EXTENSION, 0f)
            .putFloat(KEY_TUBE_AMP, 0f)
            .putFloat(KEY_TREBLE_BOOST, 0f)
            .apply()
    }
    
    fun resetAll() {
        prefs.edit()
            .putBoolean(KEY_EQ_ENABLED, true)
            .putString(KEY_SELECTED_PRESET, "Flat")
            .putFloat(KEY_BASS_BOOST, 0f)
            .putFloat(KEY_VIRTUALIZER, 0f)
            .putInt(KEY_EFFECT_PROFILE, EffectProfile.CUSTOM.ordinal)
            .putInt(KEY_HEADPHONE_TYPE, HeadphoneType.GENERIC.ordinal)
            .putBoolean(KEY_HEADPHONE_SURROUND, false)
            .apply()
        
        // Reset band levels
        for (i in 0..9) {
            prefs.edit().putFloat("${KEY_BAND_PREFIX}$i", 0f).apply()
        }
        
        resetDacSettings()
        resetSurroundSettings()
        resetDynamicSettings()
        resetEnhancementSettings()
    }
}
