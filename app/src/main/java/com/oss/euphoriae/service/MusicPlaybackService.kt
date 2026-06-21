package com.oss.euphoriae.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.palette.graphics.Palette
import com.lyrictica.MainActivity
import com.lyrictica.theme.NotificationThemeBridge
import com.oss.euphoriae.engine.AudioEngine
import com.oss.euphoriae.engine.NativeAudioProcessor
import com.oss.euphoriae.engine.NativeRenderersFactory
import com.oss.euphoriae.widget.WidgetQueueManager
import com.oss.euphoriae.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var audioEngine: AudioEngine? = null
    private var renderersFactory: NativeRenderersFactory? = null
    @Volatile
    private var artworkNotificationColor: Int? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val widgetPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateWidget()
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                refreshNotificationArtworkColor(player?.currentMediaItem)
            }
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateWidget()
            refreshNotificationArtworkColor(mediaItem)
        }
    }
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "euphoriae_playback_channel"
        private const val TAG = "MusicPlaybackService"
        var crossfadeDurationMs: Long = 0  // 0 = disabled, up to 12000ms
        
        // Widget action constants
        const val ACTION_PLAY_PAUSE = "com.oss.euphoriae.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.oss.euphoriae.action.NEXT"
        const val ACTION_PREVIOUS = "com.oss.euphoriae.action.PREVIOUS"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        setMediaNotificationProvider(ThemedMediaNotificationProvider(this) { resolveNotificationAccentColor() })
        initializeAudioEngine()
        
        // Create custom renderers factory with native audio processing
        renderersFactory = audioEngine?.let { NativeRenderersFactory(this, it) }
        
        player = ExoPlayer.Builder(this, renderersFactory ?: return)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        // Add widget update listener
        player?.addListener(widgetPlayerListener)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        mediaSession = MediaSession.Builder(this, createForwardingPlayer(player!!))
            .setSessionActivity(pendingIntent)
            .build()
        
        Log.d(TAG, "MusicPlaybackService created with native audio processing pipeline")
    }
    
    private fun createForwardingPlayer(exoPlayer: ExoPlayer): ForwardingPlayer {
        return object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
            
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }
            
            override fun seekToNext() {
                serviceScope.launch {
                    if (exoPlayer.hasNextMediaItem()) {
                        super.seekToNext()
                    } else {
                        playNextFromQueue()
                    }
                }
            }
            
            override fun seekToPrevious() {
                serviceScope.launch {
                    if (exoPlayer.currentPosition > 3000) {
                        exoPlayer.seekTo(0)
                    } else if (exoPlayer.hasPreviousMediaItem()) {
                        super.seekToPrevious()
                    } else {
                        playPreviousFromQueue()
                    }
                }
            }
            
            override fun seekToNextMediaItem() {
                serviceScope.launch {
                    if (exoPlayer.hasNextMediaItem()) {
                        super.seekToNextMediaItem()
                    } else {
                        playNextFromQueue()
                    }
                }
            }
            
            override fun seekToPreviousMediaItem() {
                serviceScope.launch {
                    if (exoPlayer.currentPosition > 3000) {
                        exoPlayer.seekTo(0)
                    } else if (exoPlayer.hasPreviousMediaItem()) {
                        super.seekToPreviousMediaItem()
                    } else {
                        playPreviousFromQueue()
                    }
                }
            }
            
            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
            }
            ACTION_NEXT -> {
                player?.let { currentPlayer ->
                    if (currentPlayer.hasNextMediaItem()) {
                        currentPlayer.seekToNextMediaItem()
                    } else {
                        serviceScope.launch { playNextFromQueue() }
                    }
                }
            }
            ACTION_PREVIOUS -> {
                player?.let { currentPlayer ->
                    if (currentPlayer.currentPosition > 3000) {
                        currentPlayer.seekTo(0)
                    } else if (currentPlayer.hasPreviousMediaItem()) {
                        currentPlayer.seekToPreviousMediaItem()
                    } else {
                        serviceScope.launch { playPreviousFromQueue() }
                    }
                }
            }
        }
        
        return result
    }
    
    private suspend fun playNextFromQueue() {
        val nextSong = WidgetQueueManager.getNextSong(this)
        if (nextSong != null) {
            val (songInfo, newIndex) = nextSong
            playSongFromQueue(songInfo, newIndex)
        }
    }
    
    private suspend fun playPreviousFromQueue() {
        val currentPlayer = player ?: return
        
        // If more than 3 seconds into song, restart it
        if (currentPlayer.currentPosition > 3000) {
            currentPlayer.seekTo(0)
            return
        }
        
        val prevSong = WidgetQueueManager.getPreviousSong(this)
        if (prevSong != null) {
            val (songInfo, newIndex) = prevSong
            playSongFromQueue(songInfo, newIndex)
        }
    }
    
    private suspend fun playSongFromQueue(songInfo: com.oss.euphoriae.widget.QueueSongInfo, index: Int) {
        val currentPlayer = player ?: return
        
        val contentUri = WidgetQueueManager.getMediaUri(songInfo.id, songInfo.data)
        
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(songInfo.id.toString())
            .setUri(contentUri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(songInfo.title)
                    .setArtist(songInfo.artist)
                    .setAlbumTitle(songInfo.album)
                    .setArtworkUri(songInfo.albumArtUri?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
        
        currentPlayer.setMediaItem(mediaItem)
        currentPlayer.prepare()
        currentPlayer.play()
        
        // Update the current index in queue
        WidgetQueueManager.updateCurrentIndex(this, index)
    }
    
    private fun initializeAudioEngine() {
        try {
            audioEngine = AudioEngine.getInstance().apply {
                create()
            }
            Log.d(TAG, "Native AudioEngine singleton initialized")
            
            // Apply saved audio settings from preferences
            applySavedAudioSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native AudioEngine", e)
        }
    }
    
    private fun applySavedAudioSettings() {
        val prefs = com.oss.euphoriae.data.preferences.AudioPreferences(this)
        val engine = audioEngine ?: return
        
        // Apply 10-band EQ
        for (band in 0 until 10) {
            val level = prefs.getBandLevel(band)
            engine.setEqualizerBand(band, level * 12f) // Convert -1..1 to dB (-12 to +12)
        }
        
        // Apply basic effects
        engine.setBassBoost(prefs.getBassBoost())
        engine.setVirtualizer(prefs.getVirtualizer())
        
        // Apply surround settings
        engine.setStereoBalance(prefs.getStereoBalance())
        engine.setChannelSeparation(prefs.getChannelSeparation())
        engine.setSurroundMode(prefs.getSurroundMode().ordinal)  // Apply mode preset
        engine.setSurroundLevel(prefs.getSurroundLevel())
        engine.setRoomSize(prefs.getRoomSize())
        engine.setSurround3D(prefs.get3DEffect())
        
        // Apply headphone settings
        engine.setHeadphoneType(prefs.getHeadphoneType().ordinal)
        engine.setHeadphoneSurround(prefs.getHeadphoneSurround())
        
        // Apply dynamic processing
        engine.setCompressor(prefs.getCompressor())
        engine.setVolumeLeveler(prefs.getVolumeLeveler())
        engine.setLimiter(0.99f - (prefs.getLimiter() * 0.49f))
        engine.setDynamicRange(prefs.getDynamicRange())
        engine.setLoudnessGain(prefs.getLoudnessGain())
        
        // Apply enhancement
        engine.setClarity(prefs.getClarity())
        engine.setSpectrumExtension(prefs.getSpectrumExtension())
        engine.setTubeWarmth(prefs.getTubeAmp())
        engine.setTrebleBoost(prefs.getTrebleBoost())
        
        // Apply reverb
        val reverbPreset = prefs.getReverbPreset()
        if (reverbPreset.ordinal > 0) {
            engine.setReverb(reverbPreset.ordinal, 0.5f)
        }
        
        Log.d(TAG, "Applied saved audio settings from preferences")
    }

    private fun resolveNotificationAccentColor(): Int {
        return NotificationThemeBridge.explicitColorOrNull()
            ?: artworkNotificationColor
            ?: 0xFF1F5BFF.toInt()
    }

    private fun refreshNotificationArtworkColor(mediaItem: MediaItem?) {
        val artworkUri = mediaItem?.mediaMetadata?.artworkUri
        if (artworkUri == null) {
            artworkNotificationColor = null
            return
        }

        artworkNotificationColor = null
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val isNetworkUri = artworkUri.scheme == "http" || artworkUri.scheme == "https"
                val inputStream = if (isNetworkUri) {
                    java.net.URL(artworkUri.toString()).openStream()
                } else {
                    contentResolver.openInputStream(artworkUri)
                }
                inputStream?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input) ?: return@use
                    Palette.from(bitmap).generate { palette ->
                        bitmap.recycle()
                        artworkNotificationColor = palette?.vibrantSwatch?.rgb
                            ?: palette?.dominantSwatch?.rgb
                            ?: palette?.mutedSwatch?.rgb
                            ?: palette?.lightVibrantSwatch?.rgb
                            ?: palette?.darkVibrantSwatch?.rgb
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Unable to extract notification artwork color", error)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current playing music"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady != true || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        
        // Cleanup native audio engine
        audioEngine?.destroy()
        audioEngine = null
        renderersFactory = null
        
        // Cancel coroutine scope
        serviceScope.cancel()
        
        Log.d(TAG, "MusicPlaybackService destroyed")
        super.onDestroy()
    }
    
    fun getPlayer(): ExoPlayer? = player
    
    fun getAudioSessionId(): Int = player?.audioSessionId ?: 0
    
    fun getAudioEngine(): AudioEngine? = audioEngine
    
    fun getNativeAudioProcessor(): NativeAudioProcessor? = renderersFactory?.getNativeAudioProcessor()
    
    // Native audio effects control
    fun setNativeVolume(volume: Float) {
        audioEngine?.setVolume(volume)
    }
    
    fun setNativeBassBoost(strength: Float) {
        audioEngine?.setBassBoost(strength)
    }
    
    fun setNativeVirtualizer(strength: Float) {
        audioEngine?.setVirtualizer(strength)
    }
    
    fun setNativeEqualizerBand(band: Int, gain: Float) {
        audioEngine?.setEqualizerBand(band, gain)
    }
    
    private fun updateWidget() {
        serviceScope.launch {
            val currentPlayer = player ?: return@launch
            val mediaItem = currentPlayer.currentMediaItem
            val metadata = mediaItem?.mediaMetadata
            
            val songTitle = metadata?.title?.toString() ?: "No song playing"
            val songArtist = metadata?.artist?.toString() ?: "Euphoriae"
            val albumArtUri = metadata?.artworkUri?.toString()
            val isPlaying = currentPlayer.isPlaying
            
            WidgetUpdater.updateWidgetState(
                context = this@MusicPlaybackService,
                songTitle = songTitle,
                songArtist = songArtist,
                albumArtUri = albumArtUri,
                isPlaying = isPlaying,
                songId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
            )
        }
    }
}
