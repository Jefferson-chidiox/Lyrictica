package com.lyrictica.audio

import android.net.Uri
import android.util.Log
import com.oss.euphoriae.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Background coordinator that keeps the durable band cache warm for the library.
 */
class LibraryAudioAnalysisCoordinator(
    private val analysisStore: AudioAnalysisStore,
    private val scope: CoroutineScope
) {

    private var analysisJob: Job? = null

    fun submitSongs(songs: List<Song>) {
        analysisJob?.cancel()
        if (songs.isEmpty()) return

        analysisJob = scope.launch(Dispatchers.IO) {
            val missingUris = songs.asSequence()
                .filter { it.id > 0L }
                .map { song -> Uri.parse(song.toPlaybackSnapshot().uri) }
                .distinct()
                .filter { uri -> !analysisStore.hasCachedBands(uri) }
                .toList()

            if (missingUris.isEmpty()) return@launch

            Log.d(TAG, "Warming durable analysis cache for ${missingUris.size} songs")
            missingUris.forEach { uri ->
                ensureActive()
                runCatching {
                    analysisStore.loadOrComputeBands(uri)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to analyze ${uri.lastPathSegment ?: uri}", error)
                }
            }
            Log.d(TAG, "Durable analysis cache warm-up complete")
        }
    }

    companion object {
        private const val TAG = "LibraryAudioAnalysis"
    }
}
