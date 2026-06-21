package com.lyrictica.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.oss.euphoriae.service.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerController(private val context: Context) {

    private data class PendingPlaybackRequest(
        val snapshots: List<PlaybackTrackSnapshot>,
        val startIndex: Int,
        val replaceExisting: Boolean
    )

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var repeatMode: Int = Player.REPEAT_MODE_OFF
    private var currentMediaUri: Uri? = null

    private var pendingPlaybackRequest: PendingPlaybackRequest? = null
    private var pendingPlayWhenReady: Boolean = true
    private var pendingSeekPositionMs: Long = 0L

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null

    init {
        initializeServiceConnection()
    }

    @OptIn(UnstableApi::class)
    private fun initializeServiceConnection() {
        val appContext = context.applicationContext
        runCatching {
            appContext.startService(Intent(appContext, MusicPlaybackService::class.java))
        }

        if (controllerFuture != null) return

        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, MusicPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                controller.repeatMode = repeatMode
                controller.addListener(playerListener)
                syncFromController(controller)
                applyPendingPlaybackRequestIfNeeded()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        error = "Playback controller error: ${e.localizedMessage}"
                    )
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isLoading = true, ended = false) }
                }

                Player.STATE_READY -> {
                    val controller = mediaController
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            ended = false,
                            duration = controller?.duration?.coerceAtLeast(0L)
                                ?: it.duration.coerceAtLeast(0L)
                        )
                    }
                    controller?.let { _audioSessionId.value = it.audioSessionId }
                }

                Player.STATE_ENDED -> {
                    _uiState.update { it.copy(isLoading = false, isPlaying = false, ended = true) }
                }

                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isLoading = false, isPlaying = false, ended = false) }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentMediaUri = mediaItem?.localConfiguration?.uri
            val title = mediaItem?.mediaMetadata?.title?.toString()
                ?: currentMediaUri?.let(::getFileNameFromUri)
                ?: _uiState.value.trackName
            _uiState.update {
                it.copy(
                    trackName = title,
                    isLoading = false,
                    error = null,
                    ended = false
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = "Playback error: ${error.localizedMessage}"
                )
            }
        }
    }

    fun playUri(
        uri: Uri,
        displayName: String? = null,
        autoPlay: Boolean = true,
        startPositionMs: Long = 0L
    ) {
        val trackName = displayName ?: getFileNameFromUri(uri) ?: "Imported Audio"
        playSnapshots(
            snapshots = listOf(
                PlaybackTrackSnapshot(
                    uri = uri.toString(),
                    title = trackName,
                    durationMs = 0L
                )
            ),
            startIndex = 0,
            autoPlay = autoPlay,
            startPositionMs = startPositionMs,
            replaceExisting = true
        )
    }

    fun playSnapshots(
        snapshots: List<PlaybackTrackSnapshot>,
        startIndex: Int = 0,
        autoPlay: Boolean = true,
        startPositionMs: Long = 0L,
        replaceExisting: Boolean = true
    ) {
        if (snapshots.isEmpty()) return

        val resolved = snapshots
        val safeIndex = startIndex.coerceIn(0, resolved.lastIndex.coerceAtLeast(0))
        val startSnapshot = resolved.getOrNull(safeIndex) ?: return

        pendingPlaybackRequest = PendingPlaybackRequest(
            snapshots = resolved,
            startIndex = safeIndex,
            replaceExisting = replaceExisting
        )
        pendingPlayWhenReady = autoPlay
        pendingSeekPositionMs = startPositionMs.coerceAtLeast(0L)

        currentMediaUri = Uri.parse(startSnapshot.uri)
        _uiState.update {
            it.copy(
                trackName = startSnapshot.title.ifBlank { getFileNameFromUri(currentMediaUri) ?: "Imported Audio" },
                isLoading = true,
                error = null,
                ended = false,
                currentPosition = pendingSeekPositionMs,
                duration = startSnapshot.durationMs.coerceAtLeast(0L),
                isPlaying = false
            )
        }

        applyPendingPlaybackRequestIfNeeded()
    }

    fun play() {
        pendingPlayWhenReady = true
        mediaController?.play() ?: _uiState.update { it.copy(ended = false) }
    }

    fun pause() {
        pendingPlayWhenReady = false
        mediaController?.pause() ?: _uiState.update { it.copy(ended = false) }
    }

    fun seekTo(positionMs: Long) {
        pendingSeekPositionMs = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(pendingSeekPositionMs)
            ?: _uiState.update { it.copy(currentPosition = pendingSeekPositionMs, ended = false) }
    }

    fun clearPlayback() {
        pendingPlaybackRequest = null
        pendingPlayWhenReady = false
        pendingSeekPositionMs = 0L
        currentMediaUri = null
        mediaController?.stop()
        runCatching { mediaController?.clearMediaItems() }
        _uiState.update {
            it.copy(
                trackName = "No track selected",
                isPlaying = false,
                duration = 0L,
                currentPosition = 0L,
                isLoading = false,
                error = null,
                ended = false
            )
        }
    }

    fun getCurrentPositionMs(): Long = mediaController?.currentPosition ?: pendingSeekPositionMs

    fun getCurrentUri(): Uri? = currentMediaUri

    fun getCurrentMediaId(): String? = mediaController?.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }

    fun setRepeatMode(mode: Int) {
        repeatMode = mode
        mediaController?.repeatMode = mode
    }

    fun release() {
        stopProgressPolling()
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        currentMediaUri = null
        _audioSessionId.value = 0
        pendingPlaybackRequest = null
    }

    private fun applyPendingPlaybackRequestIfNeeded() {
        val controller = mediaController ?: return
        val request = pendingPlaybackRequest ?: return

        if (!request.replaceExisting && controller.currentMediaItem != null) {
            pendingPlaybackRequest = null
            syncFromController(controller)
            return
        }

        applyPlaybackRequest(request)
        pendingPlaybackRequest = null
    }

    private fun applyPlaybackRequest(request: PendingPlaybackRequest) {
        val controller = mediaController ?: return
        val mediaItems = request.snapshots.map { snapshotToMediaItem(it) }
        val safeIndex = request.startIndex.coerceIn(0, mediaItems.lastIndex.coerceAtLeast(0))
        val startItem = request.snapshots.getOrNull(safeIndex) ?: return

        currentMediaUri = Uri.parse(startItem.uri)
        _uiState.update {
            it.copy(
                trackName = startItem.title.ifBlank { getFileNameFromUri(currentMediaUri) ?: "Imported Audio" },
                isLoading = true,
                error = null,
                ended = false,
                currentPosition = pendingSeekPositionMs,
                duration = startItem.durationMs.coerceAtLeast(0L),
                isPlaying = false
            )
        }

        controller.setMediaItems(mediaItems, safeIndex, pendingSeekPositionMs)
        controller.repeatMode = repeatMode
        controller.prepare()
        if (pendingPlayWhenReady) {
            controller.play()
        } else {
            controller.pause()
        }
    }

    private fun syncFromController(controller: MediaController) {
        currentMediaUri = controller.currentMediaItem?.localConfiguration?.uri
        val title = controller.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: currentMediaUri?.let(::getFileNameFromUri)
            ?: _uiState.value.trackName
        _uiState.update {
            it.copy(
                trackName = title,
                isLoading = controller.playbackState == Player.STATE_BUFFERING,
                error = null,
                ended = controller.playbackState == Player.STATE_ENDED,
                isPlaying = controller.isPlaying,
                duration = controller.duration.coerceAtLeast(0L),
                currentPosition = controller.currentPosition.coerceAtLeast(0L)
            )
        }
        _audioSessionId.value = controller.audioSessionId
    }

    private fun snapshotToMediaItem(snapshot: PlaybackTrackSnapshot): MediaItem {
        val uri = Uri.parse(snapshot.uri)
        return MediaItem.Builder()
            .setMediaId(snapshot.songId?.toString() ?: snapshot.uri)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(snapshot.title)
                    .setArtist(snapshot.artist)
                    .setAlbumTitle(snapshot.album)
                    .setArtworkUri(snapshot.albumArtUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                mediaController?.let { controller ->
                    _uiState.update {
                        it.copy(
                            currentPosition = controller.currentPosition,
                            duration = controller.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun getFileNameFromUri(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}
