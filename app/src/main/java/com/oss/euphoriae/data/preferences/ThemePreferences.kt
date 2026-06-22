package com.oss.euphoriae.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class ThemeColorOption(val displayName: String) {
    DYNAMIC("Dynamic"),
    PURPLE("Purple"),
    BLUE("Blue"),
    GREEN("Green"),
    ORANGE("Orange"),
    PINK("Pink"),
    RED("Red")
}

enum class DarkModeOption(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

class ThemePreferences(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "euphoriae_theme_prefs"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_DARK_MODE = "dark_mode"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    val themeColor: Flow<ThemeColorOption> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME_COLOR) {
                trySend(getCurrentThemeColor())
            }
        }
        trySend(getCurrentThemeColor())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    val darkMode: Flow<DarkModeOption> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DARK_MODE) {
                trySend(getCurrentDarkMode())
            }
        }
        trySend(getCurrentDarkMode())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    fun getCurrentThemeColor(): ThemeColorOption {
        val colorName = prefs.getString(KEY_THEME_COLOR, ThemeColorOption.DYNAMIC.name)
        return try {
            ThemeColorOption.valueOf(colorName ?: ThemeColorOption.DYNAMIC.name)
        } catch (e: IllegalArgumentException) {
            ThemeColorOption.DYNAMIC
        }
    }
    
    fun getCurrentDarkMode(): DarkModeOption {
        val modeName = prefs.getString(KEY_DARK_MODE, DarkModeOption.DARK.name)
        return try {
            DarkModeOption.valueOf(modeName ?: DarkModeOption.DARK.name)
        } catch (e: IllegalArgumentException) {
            DarkModeOption.DARK
        }
    }
    
    fun setThemeColor(option: ThemeColorOption) {
        prefs.edit().putString(KEY_THEME_COLOR, option.name).apply()
    }
    
    fun setDarkMode(option: DarkModeOption) {
        prefs.edit().putString(KEY_DARK_MODE, option.name).apply()
    }
}
