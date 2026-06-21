package com.lyrictica.visualizer

import android.content.Context
import android.content.SharedPreferences

class VisualizerGesturePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "lyrictica_visualizer_gestures"
        private const val KEY_TUTORIAL_SEEN = "gesture_tutorial_seen"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeenGestureTutorial(): Boolean = prefs.getBoolean(KEY_TUTORIAL_SEEN, false)

    fun markGestureTutorialSeen() {
        prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, true).apply()
    }
}
