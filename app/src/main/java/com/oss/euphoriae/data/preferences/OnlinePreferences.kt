package com.oss.euphoriae.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class OnlinePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "euphoriae_online_prefs"
        private const val KEY_ONLINE_TRACKS_ENABLED = "online_tracks_enabled"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val onlineTracksEnabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ONLINE_TRACKS_ENABLED) {
                trySend(areOnlineTracksEnabled())
            }
        }
        trySend(areOnlineTracksEnabled())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun areOnlineTracksEnabled(): Boolean =
        prefs.getBoolean(KEY_ONLINE_TRACKS_ENABLED, false)

    fun setOnlineTracksEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_TRACKS_ENABLED, enabled).apply()
    }
}
