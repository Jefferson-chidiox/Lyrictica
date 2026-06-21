package com.oss.euphoriae

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.lyrictica.audio.AudioAnalysisStore
import com.lyrictica.audio.LibraryAudioAnalysisCoordinator
import com.lyrictica.karaoke.KaraokeAssetsRepository
import com.lyrictica.karaoke.KaraokeStemStore
import com.lyrictica.karaoke.LalalProxyClient
import com.oss.euphoriae.data.local.MusicDatabase
import com.oss.euphoriae.data.remote.AudiusService
import com.oss.euphoriae.data.remote.NcsService
import com.oss.euphoriae.data.repository.AudiusRepository
import com.oss.euphoriae.data.repository.MusicRepository
import com.oss.euphoriae.data.repository.NcsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

open class EuphoriaeApp : Application() {

    val database by lazy { MusicDatabase.getDatabase(this) }
    val audioAnalysisStore by lazy { AudioAnalysisStore(this) }
    internal val karaokeAssetsRepository by lazy {
        KaraokeAssetsRepository(
            stemStore = KaraokeStemStore(this),
            analysisStore = audioAnalysisStore,
            provider = LalalProxyClient()
        )
    }
    val musicRepository by lazy { MusicRepository(this, database.musicDao()) }
    val audiusRepository by lazy {
        AudiusRepository(AudiusService(), database.musicDao())
    }
    val ncsRepository by lazy {
        NcsRepository(NcsService())
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val libraryAnalysisCoordinator by lazy {
        LibraryAudioAnalysisCoordinator(audioAnalysisStore, applicationScope)
    }

    private var libraryRefreshJob: Job? = null
    private var mediaStoreObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        startLibraryAnalysisSync()
    }

    override fun onTerminate() {
        runCatching {
            mediaStoreObserver?.let { contentResolver.unregisterContentObserver(it) }
        }
        applicationScope.cancel()
        super.onTerminate()
    }

    private fun startLibraryAnalysisSync() {
        observeLibrarySongs()
        registerMediaStoreObserver()
        applicationScope.launch {
            karaokeAssetsRepository.pruneMissingSources()
        }
        scheduleLibraryRefresh(delayMs = 750L)
    }

    private fun observeLibrarySongs() {
        applicationScope.launch {
            musicRepository.getAllSongs()
                .distinctUntilChangedBy { songs ->
                    songs.map { song -> Triple(song.id, song.data, song.dateModified) }
                }
                .collectLatest { songs ->
                    libraryAnalysisCoordinator.submitSongs(songs)
                    karaokeAssetsRepository.pruneMissingSources()
                }
        }
    }

    private fun registerMediaStoreObserver() {
        if (mediaStoreObserver != null) return

        mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleLibraryRefresh()
            }

            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver!!
        )
    }

    private fun scheduleLibraryRefresh(delayMs: Long = 2000L) {
        libraryRefreshJob?.cancel()
        libraryRefreshJob = applicationScope.launch {
            delay(delayMs)
            runCatching { musicRepository.refreshLibrary() }
                .onFailure { error ->
                    Log.w(TAG, "Library refresh failed", error)
                }
        }
    }

    companion object {
        private const val TAG = "EuphoriaeApp"
    }
}
