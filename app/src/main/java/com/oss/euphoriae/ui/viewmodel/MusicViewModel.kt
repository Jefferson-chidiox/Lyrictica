package com.oss.euphoriae.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.oss.euphoriae.EuphoriaeApp
import com.oss.euphoriae.data.`class`.AudioEffectsManager
import com.oss.euphoriae.data.model.GameRecommendationReason
import com.oss.euphoriae.data.model.GameScoreRecord
import com.oss.euphoriae.data.model.GameSongRecommendation
import com.oss.euphoriae.data.model.HomeFeedItem
import com.oss.euphoriae.data.model.Lyrics
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.data.model.toSong
import com.oss.euphoriae.data.preferences.OnlinePreferences
import com.oss.euphoriae.engine.AudioEngine
import com.oss.euphoriae.explore.ExploreCatalog
import com.oss.euphoriae.explore.ExploreCategory
import com.oss.euphoriae.search.HomeSearchRepository
import com.oss.euphoriae.search.HomeSearchUiState
import com.oss.euphoriae.service.MusicPlaybackService
import com.oss.euphoriae.widget.QueueSongInfo
import com.oss.euphoriae.widget.WidgetQueueManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MusicUiState(
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val playlistSongs: List<Song> = emptyList(),
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = -1,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val progress: Float = 0f,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = 0,
    val tempo: Float = 1.0f,
    val pitch: Float = 0.0f,
    val albums: List<com.oss.euphoriae.data.model.Album> = emptyList(),
    val recentlyAddedSongs: List<Song> = emptyList(),
    val mostPlayedThisWeek: List<Song> = emptyList(),
    val mostPlayedThisMonth: List<Song> = emptyList(),
    val mostPlayedAllTime: List<Song> = emptyList(),
    val favoriteSongs: List<Song> = emptyList(),
    val notPlayedSongs: List<Song> = emptyList(),
    val gameTopScores: List<GameScoreRecord> = emptyList(),
    val gameRecommendations: List<GameSongRecommendation> = emptyList(),
    val lyrics: Lyrics? = null,
    val currentLyricIndex: Int = -1,
    val onlineShelves: List<HomeFeedItem> = emptyList(),
    val isOnlineFeedLoading: Boolean = false,
    val isOnlineTracksEnabled: Boolean = false,
    val homeSearch: HomeSearchUiState = HomeSearchUiState()
)

data class HomeCollections(
    val recentlyAdded: List<Song> = emptyList(),
    val mostPlayedWeek: List<Song> = emptyList(),
    val mostPlayedMonth: List<Song> = emptyList(),
    val mostPlayedAllTime: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val notPlayed: List<Song> = emptyList()
)

@OptIn(UnstableApi::class)
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as EuphoriaeApp).musicRepository
    private val audiusRepository = (application as EuphoriaeApp).audiusRepository
    private val ncsRepository = (application as EuphoriaeApp).ncsRepository
    private val onlinePreferences = OnlinePreferences(application)
    private val homeSearchRepository = HomeSearchRepository(repository, audiusRepository, ncsRepository)
    
    val audioEffectsManager = AudioEffectsManager()
    private var audioEffectsInitialized = false
    
    // Native audio engine - create our own instance for UI control
    private var _audioEngine: AudioEngine? = null
    val audioEngine: AudioEngine?
        get() = _audioEngine
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")

    private var progressJob: Job? = null
    private var songsSearchJob: Job? = null
    private var homeSearchJob: Job? = null
    private var onlineFeedJob: Job? = null
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val controller = mediaController ?: return
                    _uiState.update { 
                        it.copy(duration = controller.duration)
                    }
                    if (!audioEffectsInitialized && controller.audioSessionId != 0) {
                        audioEffectsManager.initialize(controller.audioSessionId)
                        audioEffectsInitialized = true
                    }
                }
                Player.STATE_ENDED -> {
                    handleSongEnded()
                }
            }
        }
        
        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int
        ) {
            syncCurrentSongFromMediaItem(mediaItem)
            updateCurrentPosition()
        }
    }
    
    init {
        initializeAudioEngine()
        loadSongs()
        loadPlaylists()
        loadAlbums()
        loadHomeCollections()
        loadGamesHub()
        observeOnlineTrackPreference()
        connectToService()
        observeCurrentSong()
    }

    private fun observeOnlineTrackPreference() {
        viewModelScope.launch {
            onlinePreferences.onlineTracksEnabled
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        _uiState.update { it.copy(isOnlineTracksEnabled = true) }
                        loadOnlineFeed()
                    } else {
                        onlineFeedJob?.cancel()
                        homeSearchJob?.cancel()
                        _uiState.update {
                            it.copy(
                                isOnlineTracksEnabled = false,
                                onlineShelves = emptyList(),
                                isOnlineFeedLoading = false,
                                homeSearch = it.homeSearch.withOnlineTracksDisabled()
                            )
                        }
                    }
                }
        }
    }

    fun setOnlineTracksEnabled(enabled: Boolean) {
        onlinePreferences.setOnlineTracksEnabled(enabled)
    }
    
    private fun observeCurrentSong() {
        viewModelScope.launch {
            uiState.map { it.currentSong }
                .distinctUntilChanged()
                .collect { song ->
                    if (song != null) {
                        loadLyrics(song)
                    } else {
                        _uiState.update { it.copy(lyrics = null, currentLyricIndex = -1) }
                    }
                }
        }
    }
    
    private fun loadLyrics(song: Song) {
        viewModelScope.launch {
            // Reset lyrics first
            _uiState.update { it.copy(lyrics = null, currentLyricIndex = -1) }
            repository.getLyrics(song).collect { lyrics ->
                _uiState.update { it.copy(lyrics = lyrics) }
            }
        }
    }
    
    private fun initializeAudioEngine() {
        try {
            _audioEngine = AudioEngine.getInstance().apply {
                create()
            }
            android.util.Log.i("MusicViewModel", "AudioEngine singleton obtained for effects control")
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to get AudioEngine", e)
        }
    }
    
    private fun connectToService() {
        startService()
        
        val app = getApplication<Application>()
        val sessionToken = SessionToken(
            app,
            ComponentName(app, MusicPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.let { controller ->
                    controller.addListener(playerListener)
                    controller.repeatMode = when (_uiState.value.repeatMode) {
                        2 -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
                // Sync current playback state from service
                syncWithController()
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to connect to service", e)
            }
        }, MoreExecutors.directExecutor())
    }
    
    private fun startService() {
        val app = getApplication<Application>()
        val intent = Intent(app, MusicPlaybackService::class.java)
        app.startService(intent)
    }
    
    /**
     * Sync UI state with the current playback state from MediaController.
     * This is called when the controller connects to restore the playback state
     * when opening the app from widget or notification.
     */
    private fun syncWithController() {
        val controller = mediaController ?: return
        
        viewModelScope.launch {
            try {
                val currentMediaItem = controller.currentMediaItem
                if (currentMediaItem != null) {
                    if (_uiState.value.songs.isEmpty()) {
                        delay(500) // Wait for songs to load
                    }

                    syncCurrentSongFromMediaItem(currentMediaItem)

                    val currentSong = _uiState.value.currentSong
                    if (currentSong != null) {
                        android.util.Log.i("MusicViewModel", "Synced with controller: ${currentSong.title}, playing: ${controller.isPlaying}")
                        
                        // Start progress updates if playing
                        if (controller.isPlaying) {
                            startProgressUpdates()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to sync with controller", e)
            }
        }
    }
    
    private fun syncCurrentSongFromMediaItem(mediaItem: MediaItem?) {
        val controller = mediaController ?: return
        val songs = _uiState.value.queue.ifEmpty { _uiState.value.songs }
        if (songs.isEmpty() || mediaItem == null) return

        val resolvedSong = run {
            mediaItem.mediaId.takeIf { it.isNotBlank() }?.toLongOrNull()?.let { songId ->
                songs.firstOrNull { it.id == songId }
            }
                ?: songs.firstOrNull { it.data == mediaItem.mediaId }
                ?: mediaItem.localConfiguration?.uri?.takeIf { it.scheme == "content" }?.let { uri ->
                    runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { songId ->
                        songs.firstOrNull { it.id == songId }
                    }
                }
                ?: run {
                    val title = mediaItem.mediaMetadata.title?.toString()?.trim().orEmpty()
                    val artist = mediaItem.mediaMetadata.artist?.toString()?.trim().orEmpty()
                    if (title.isBlank()) {
                        null
                    } else {
                        songs.firstOrNull {
                            it.title.equals(title, ignoreCase = true) &&
                                (artist.isBlank() || it.artist.equals(artist, ignoreCase = true))
                        } ?: songs.firstOrNull { it.title.equals(title, ignoreCase = true) }
                    }
                }
        } ?: return

        val currentIndex = songs.indexOfFirst { it.id == resolvedSong.id }
            .takeIf { it >= 0 }
            ?: _uiState.value.currentQueueIndex

        val currentPosition = controller.currentPosition
        val duration = controller.duration.takeIf { it > 0 } ?: resolvedSong.duration
        val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

        _uiState.update {
            it.copy(
                currentSong = resolvedSong,
                currentQueueIndex = currentIndex,
                isPlaying = controller.isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                progress = progress.coerceIn(0f, 1f)
            )
        }
    }

    private fun loadSongs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getAllSongs().collect { songs ->
                val localSongs = songs.filter { it.album != "Audius" && !it.data.startsWith("http://") && !it.data.startsWith("https://") }
                _uiState.update { 
                    it.copy(
                        songs = localSongs,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    private fun loadPlaylists() {
        viewModelScope.launch {
            repository.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    private fun loadHomeCollections() {
        viewModelScope.launch {
            combine(
                repository.getRecentlyAddedSongs(),
                repository.getMostPlayedThisWeek(),
                repository.getMostPlayedThisMonth(),
                repository.getMostPlayedAllTime(),
                repository.getFavoriteSongs(),
                repository.getNotPlayedSongs()
            ) { values: Array<List<Song>> ->
                HomeCollections(
                    recentlyAdded = values[0],
                    mostPlayedWeek = values[1],
                    mostPlayedMonth = values[2],
                    mostPlayedAllTime = values[3],
                    favorites = values[4],
                    notPlayed = values[5]
                )
            }.collect { collections ->
                _uiState.update {
                    it.copy(
                        recentlyAddedSongs = collections.recentlyAdded,
                        mostPlayedThisWeek = collections.mostPlayedWeek,
                        mostPlayedThisMonth = collections.mostPlayedMonth,
                        mostPlayedAllTime = collections.mostPlayedAllTime,
                        favoriteSongs = collections.favorites,
                        notPlayedSongs = collections.notPlayed
                    )
                }
            }
        }
    }

    private fun loadGamesHub() {
        viewModelScope.launch {
            combine(
                repository.getTopGameScores(),
                repository.getMostPlayedAllTime(limit = 10),
                repository.getRecentlyAddedSongs(limit = 10),
                repository.getSongsFromPlaylists(limit = 10)
            ) { topScores, mostPlayed, recentlyAdded, playlistSongs ->
                topScores to buildGameRecommendations(
                    mostPlayed = mostPlayed,
                    recentlyAdded = recentlyAdded,
                    playlistSongs = playlistSongs
                )
            }.collect { (topScores, recommendations) ->
                _uiState.update {
                    it.copy(
                        gameTopScores = topScores,
                        gameRecommendations = recommendations
                    )
                }
            }
        }
    }

    private fun buildGameRecommendations(
        mostPlayed: List<Song>,
        recentlyAdded: List<Song>,
        playlistSongs: List<Song>
    ): List<GameSongRecommendation> {
        val recommendationReasons = linkedMapOf<String, Pair<Song, MutableSet<GameRecommendationReason>>>()

        fun collectSongs(songs: List<Song>, reason: GameRecommendationReason) {
            songs.forEach { song ->
                val key = songRecommendationKey(song)
                val existing = recommendationReasons[key]
                if (existing == null) {
                    recommendationReasons[key] = song to linkedSetOf(reason)
                } else {
                    existing.second += reason
                }
            }
        }

        collectSongs(mostPlayed, GameRecommendationReason.MOST_PLAYED)
        collectSongs(recentlyAdded, GameRecommendationReason.RECENTLY_ADDED)
        collectSongs(playlistSongs, GameRecommendationReason.FROM_PLAYLISTS)

        return recommendationReasons.values
            .map { (song, reasons) -> GameSongRecommendation(song = song, reasons = reasons.toSet()) }
            .sortedWith(
                compareByDescending<GameSongRecommendation> { it.reasons.size }
                    .thenByDescending { it.reasons.contains(GameRecommendationReason.MOST_PLAYED) }
                    .thenByDescending { it.reasons.contains(GameRecommendationReason.RECENTLY_ADDED) }
                    .thenByDescending { it.reasons.contains(GameRecommendationReason.FROM_PLAYLISTS) }
                    .thenByDescending { it.song.dateAdded }
                    .thenBy { it.song.title.lowercase() }
            )
        }

    private fun songRecommendationKey(song: Song): String {
        return when {
            song.id > 0L -> "id:${song.id}"
            song.data.isNotBlank() -> "data:${song.data}"
            else -> "${song.title}|${song.artist}|${song.album}|${song.duration}"
        }
    }
    
    fun scanMusic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                val count = repository.scanAndImportMusic()
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = if (count == 0) "No music found on device" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = "Failed to scan music: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun refreshLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                val count = repository.refreshLibrary()
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = if (count == 0) "No music found on device" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = "Failed to refresh library: ${e.message}"
                    )
                }
            }
        }
    }

    fun scanFolder(folderUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                val count = repository.scanAndImportMusicFromFolder(folderUri)
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = if (count == 0) "No music found in the selected folder" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = "Failed to scan folder: ${e.message}"
                    )
                }
            }
        }
    }

    fun recordSongPlay(song: Song) {
        if (song.id <= 0L) return
        viewModelScope.launch {
            runCatching {
                repository.recordSongPlay(song.id)
            }.onFailure { error ->
                android.util.Log.e("MusicViewModel", "Failed to record play for ${song.id}", error)
            }
        }
    }

    fun toggleFavorite(song: Song) {
        if (song.id <= 0L) return
        viewModelScope.launch {
            runCatching {
                val isFavorite = repository.isFavorite(song.id)
                repository.setFavorite(song.id, !isFavorite)
            }.onFailure { error ->
                android.util.Log.e("MusicViewModel", "Failed to toggle favorite for ${song.id}", error)
                _uiState.update { it.copy(error = "Failed to update favorites: ${error.message}") }
            }
        }
    }

    fun addToFavorites(song: Song) {
        if (song.id <= 0L) return
        viewModelScope.launch {
            runCatching {
                repository.setFavorite(song.id, true)
            }.onFailure { error ->
                android.util.Log.e("MusicViewModel", "Failed to add favorite for ${song.id}", error)
                _uiState.update { it.copy(error = "Failed to update favorites: ${error.message}") }
            }
        }
    }

    fun deleteSong(song: Song) {
        if (song.id <= 0L) return
        viewModelScope.launch {
            runCatching {
                repository.deleteSong(song)
            }.onFailure { error ->
                android.util.Log.e("MusicViewModel", "Failed to delete song ${song.id}", error)
                _uiState.update { it.copy(error = "Failed to delete song: ${error.message}") }
            }
        }
    }
    
    fun searchSongs(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
        songsSearchJob?.cancel()

        if (query.isEmpty()) {
            loadSongs()
            return
        }

        songsSearchJob = viewModelScope.launch {
            repository.searchSongs(query).collect { songs ->
                _uiState.update { it.copy(songs = songs) }
            }
        }
    }

    fun submitHomeSearch(query: String) {
        val trimmedQuery = query.trim()
        homeSearchJob?.cancel()

        if (trimmedQuery.isBlank()) {
            clearHomeSearch()
            return
        }

        val includeOnlineProviders = _uiState.value.isOnlineTracksEnabled
        val previousResults = if (includeOnlineProviders) {
            _uiState.value.homeSearch.results
        } else {
            _uiState.value.homeSearch.results.withOnlineTracksDisabled()
        }
        _uiState.update {
            it.copy(
                homeSearch = HomeSearchUiState(
                    activeQuery = trimmedQuery,
                    isLoading = true,
                    results = previousResults
                )
            )
        }

        homeSearchJob = viewModelScope.launch {
            val results = runCatching {
                homeSearchRepository.search(
                    query = trimmedQuery,
                    includeOnlineProviders = includeOnlineProviders
                )
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        homeSearch = HomeSearchUiState(
                            activeQuery = trimmedQuery,
                            isLoading = false,
                            error = error.message ?: "Search failed."
                        )
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    homeSearch = HomeSearchUiState(
                        activeQuery = trimmedQuery,
                        isLoading = false,
                        results = results
                    )
                )
            }
        }
    }

    fun clearHomeSearch() {
        homeSearchJob?.cancel()
        _uiState.update { it.copy(homeSearch = HomeSearchUiState()) }
    }
    
    private fun songsToMediaItems(songs: List<Song>): List<MediaItem> {
        return songs.map { song ->
            val mediaUri = getMediaUri(song)
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(mediaUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }
    }

    fun playSong(song: Song) {
        playSongFromList(song, _uiState.value.queue)
    }
    
    fun playSongFromList(song: Song, songList: List<Song>) {
        val controller = mediaController ?: return
        
        val currentQueue = _uiState.value.queue
        val matchedIndex = currentQueue.indexOfFirst {
            (song.id > 0L && it.id == song.id) ||
            (song.data.isNotEmpty() && it.data == song.data) ||
            (it.title.equals(song.title, ignoreCase = true) && it.artist.equals(song.artist, ignoreCase = true))
        }

        if (matchedIndex >= 0) {
            val matchedSong = currentQueue[matchedIndex]
            _uiState.update { 
                it.copy(
                    currentQueueIndex = matchedIndex,
                    currentSong = matchedSong,
                    isPlaying = true
                )
            }
            controller.seekTo(matchedIndex, 0L)
            controller.play()
            recordSongPlay(matchedSong)
            saveQueueForWidget(currentQueue, matchedIndex)
            return
        }
        
        // Selected from outside: play song immediately in a single-song queue to prevent any lag
        val tempQueue = listOf(song)
        _uiState.update { 
            it.copy(
                queue = tempQueue,
                currentQueueIndex = 0,
                currentSong = song,
                isPlaying = true,
                progress = 0f,
                currentPosition = 0L,
                duration = song.duration
            )
        }
        
        val mediaItem = songsToMediaItems(tempQueue).first()
        controller.setMediaItem(mediaItem)
        controller.repeatMode = when (_uiState.value.repeatMode) {
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.prepare()
        controller.play()
        recordSongPlay(song)
        saveQueueForWidget(tempQueue, 0)

        // Fetch related songs in background
        viewModelScope.launch {
            try {
                val related = when {
                    song.album == "NCS" -> {
                        ncsRepository.getRelatedTracks(genre = song.genre, limit = 20)
                            .filterNot { it.title.equals(song.title, ignoreCase = true) }
                            .map { it.toSong() }
                    }

                    song.data.startsWith("http://") || song.data.startsWith("https://") || song.album == "Audius" -> {
                        audiusRepository.getRelatedTracks(genre = song.genre, limit = 20)
                            .filterNot { it.title.equals(song.title, ignoreCase = true) }
                            .map { it.toSong() }
                    }

                    else -> repository.getRelatedSongs(song)
                }

                if (related.isNotEmpty()) {
                    val fullRelatedSongs = listOf(song) + related
                    
                    if (_uiState.value.currentSong?.id == song.id) {
                        _uiState.update {
                            it.copy(
                                queue = fullRelatedSongs,
                                currentQueueIndex = 0
                            )
                        }
                        val mediaItems = songsToMediaItems(fullRelatedSongs)
                        val currentPosition = controller.currentPosition
                        val isPlaying = controller.isPlaying
                        
                        controller.setMediaItems(mediaItems, 0, currentPosition)
                        controller.prepare()
                        if (isPlaying) {
                            controller.play()
                        } else {
                            controller.pause()
                        }
                        saveQueueForWidget(fullRelatedSongs, 0)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to build related queue", e)
            }
        }
    }
    
    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun setPlaybackParameters(tempo: Float, pitch: Float) {
        try {
            mediaController?.let { controller ->
                // Pitch in ExoPlayer is a factor (1.0 = normal), but our UI sends semitones (-12 to +12).
                // Convert semitones to factor: factor = 2^(semitones/12)
                val pitchFactor = java.lang.Math.pow(2.0, pitch.toDouble() / 12.0).toFloat()
                
                // Ensure tempo and pitch are positive to avoid IllegalArgumentException
                val safeTempo = tempo.coerceAtLeast(0.1f)
                val safePitch = pitchFactor.coerceAtLeast(0.1f)
                
                val params = androidx.media3.common.PlaybackParameters(safeTempo, safePitch)
                controller.playbackParameters = params
                
                // Update UI state with the original semitone value for sliders
                _uiState.update { it.copy(tempo = tempo, pitch = pitch) }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to set playback parameters", e)
        }
    }
    
    fun playNext() {
        val controller = mediaController ?: return
        val currentState = _uiState.value
        val queue = currentState.queue.ifEmpty { currentState.songs }
        
        if (queue.isEmpty()) return
        
        val currentIndex = currentState.currentQueueIndex
        val nextIndex = if (currentState.isShuffleOn) {
            (queue.indices).random()
        } else {
            (currentIndex + 1) % queue.size
        }
        
        val nextSong = queue[nextIndex]
        _uiState.update { 
            it.copy(
                currentQueueIndex = nextIndex,
                currentSong = nextSong,
                progress = 0f,
                currentPosition = 0L,
                duration = nextSong.duration
            )
        }
        
        val mediaUri = getMediaUri(nextSong)
        
        val mediaItem = MediaItem.Builder()
            .setMediaId(nextSong.id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(nextSong.title)
                    .setArtist(nextSong.artist)
                    .setAlbumTitle(nextSong.album)
                    .setArtworkUri(nextSong.albumArtUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
        recordSongPlay(nextSong)
    }
    
    fun playPrevious() {
        val controller = mediaController ?: return
        val currentState = _uiState.value
        val queue = currentState.queue.ifEmpty { currentState.songs }
        
        if (queue.isEmpty()) return
        
        if (controller.currentPosition > 3000) {
            controller.seekTo(0)
            return
        }
        
        val currentIndex = currentState.currentQueueIndex
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else queue.size - 1
        
        val prevSong = queue[prevIndex]
        _uiState.update { 
            it.copy(
                currentQueueIndex = prevIndex,
                currentSong = prevSong,
                progress = 0f,
                currentPosition = 0L,
                duration = prevSong.duration
            )
        }
        
        val mediaUri = getMediaUri(prevSong)
        
        val mediaItem = MediaItem.Builder()
            .setMediaId(prevSong.id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(prevSong.title)
                    .setArtist(prevSong.artist)
                    .setAlbumTitle(prevSong.album)
                    .setArtworkUri(prevSong.albumArtUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
        recordSongPlay(prevSong)
    }
    
    fun seekTo(progress: Float) {
        val controller = mediaController ?: return
        val duration = _uiState.value.duration
        val position = (progress * duration).toLong()
        controller.seekTo(position)
        _uiState.update { 
            it.copy(
                progress = progress,
                currentPosition = position
            )
        }
    }
    
    fun seekToPosition(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        val duration = _uiState.value.duration
        val progress = if (duration > 0) positionMs.toFloat() / duration else 0f
        _uiState.update { 
            it.copy(
                currentPosition = positionMs,
                progress = progress
            )
        }
    }
    
    private fun getMediaUri(song: Song): Uri {
        return if (song.data.startsWith("http://") || song.data.startsWith("https://") || song.data.startsWith("content://")) {
            Uri.parse(song.data)
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        }
    }
    
    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffleState = !_uiState.value.isShuffleOn
        _uiState.update { it.copy(isShuffleOn = newShuffleState) }
        controller.shuffleModeEnabled = newShuffleState
    }
    
    fun toggleRepeat() {
        val controller = mediaController ?: return
        val newRepeatMode = (_uiState.value.repeatMode + 1) % 3
        _uiState.update { it.copy(repeatMode = newRepeatMode) }
        
        controller.repeatMode = when (newRepeatMode) {
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    
    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = viewModelScope.launch {
            while (isActive) {
                updateCurrentPosition()
                delay(500L)
            }
        }
    }
    
    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }
    
    private fun updateCurrentPosition() {
        val controller = mediaController ?: return
        val currentPosition = controller.currentPosition
        val duration = controller.duration.takeIf { it > 0 } ?: _uiState.value.duration
        val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
        
        val lyrics = _uiState.value.lyrics
        val currentLyricIndex = if (lyrics != null && lyrics.isSynced) {
            lyrics.lines.indexOfLast { it.timestamp <= currentPosition }
        } else {
            -1
        }
        
        _uiState.update {
            it.copy(
                currentPosition = currentPosition,
                duration = duration,
                progress = progress.coerceIn(0f, 1f),
                currentLyricIndex = currentLyricIndex
            )
        }
    }
    
    private fun handleSongEnded() {
        val currentState = _uiState.value
        val queue = currentState.queue.ifEmpty { currentState.songs }
        
        when (currentState.repeatMode) {
            2 -> {
                mediaController?.seekTo(0)
                mediaController?.play()
            }
            1 -> {
                playNext()
            }
            else -> {
                val currentIndex = currentState.currentQueueIndex
                if (currentIndex < queue.size - 1) {
                    playNext()
                } else {
                    _uiState.update { it.copy(isPlaying = false, progress = 0f) }
                }
            }
        }
    }
    
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun createPlaylistFromSongs(name: String, songs: List<Song>) {
        viewModelScope.launch {
            try {
                val trimmedName = name.trim()
                val uniqueSongs = songs.distinctBy { it.id }
                if (trimmedName.isBlank() || uniqueSongs.isEmpty()) return@launch

                val playlistId = repository.createPlaylist(trimmedName)
                uniqueSongs.forEach { song ->
                    val dbSong = repository.getSongById(song.id)
                    if (dbSong == null) {
                        repository.insertSong(song)
                    }
                    repository.addSongToPlaylist(playlistId, song.id)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to create playlist from songs: ${e.message}")
                }
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            try {
                val song = _uiState.value.songs.firstOrNull { it.id == songId }
                    ?: _uiState.value.currentSong?.takeIf { it.id == songId }
                    ?: _uiState.value.queue.firstOrNull { it.id == songId }

                if (song != null) {
                    val dbSong = repository.getSongById(songId)
                    if (dbSong == null) {
                        repository.insertSong(song)
                    }
                }
                repository.addSongToPlaylist(playlistId, songId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to add song to playlist: ${e.message}")
                }
            }
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch {
            try {
                songs.distinctBy { it.id }.forEach { song ->
                    val dbSong = repository.getSongById(song.id)
                    if (dbSong == null) {
                        repository.insertSong(song)
                    }
                    repository.addSongToPlaylist(playlistId, song.id)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to add songs to playlist: ${e.message}")
                }
            }
        }
    }
    
    fun loadPlaylistSongs(playlistId: Long) {
        viewModelScope.launch {
            repository.getSongsInPlaylist(playlistId).collect { songs ->
                _uiState.update { it.copy(playlistSongs = songs) }
            }
        }
    }
    
    fun createPlaylistFromAlbum(album: com.oss.euphoriae.data.model.Album) {
        viewModelScope.launch {
            try {
                // 1. Create playlist with album name (Artist - Album)
                val playlistName = "${album.artist} - ${album.name}"
                val playlistId = repository.createPlaylist(name = playlistName)
                
                // 2. Get all songs from this album (by name, to catch merged albums)
                val albumSongs = repository.getSongsByAlbumName(album.name)
                
                // 3. Add all songs to the new playlist
                albumSongs.forEach { song ->
                    repository.addSongToPlaylist(playlistId, song.id)
                }
                
                // 4. Force refresh of playlists to show the new one immediately
                // The repository.getAllPlaylists() flow should handle this automatically, 
                // but sometimes it needs a nudge if we just inserted.
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "Failed to create playlist from album: ${e.message}")
                }
            }
        }
    }
    
    private fun loadAlbums() {
        viewModelScope.launch {
            repository.getAlbums().collect { albums ->
                _uiState.update { it.copy(albums = albums) }
            }
        }
    }
    
    private fun saveQueueForWidget(songs: List<Song>, currentIndex: Int) {
        viewModelScope.launch {
            val queueInfo = songs.map { song ->
                QueueSongInfo(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    albumArtUri = song.albumArtUri,
                    duration = song.duration,
                    data = song.data
                )
            }
            WidgetQueueManager.saveQueue(getApplication(), queueInfo, currentIndex)
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Online Feed (Audius + NCS) ────────────────────────────────────────────

    private fun loadOnlineFeed() {
        onlineFeedJob?.cancel()
        onlineFeedJob = viewModelScope.launch {
            if (!_uiState.value.isOnlineTracksEnabled) {
                _uiState.update {
                    it.copy(
                        onlineShelves = emptyList(),
                        isOnlineFeedLoading = false
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isOnlineFeedLoading = true) }
            try {
                val genres = repository.getTopGenres()
                onlineFeedFlow(genres, refresh = false).collect { shelves ->
                    _uiState.update {
                        it.copy(
                            onlineShelves = shelves,
                            isOnlineFeedLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to load online feed", e)
                _uiState.update { it.copy(isOnlineFeedLoading = false) }
            }
        }
    }

    fun refreshOnlineFeed() {
        if (!_uiState.value.isOnlineTracksEnabled) {
            _uiState.update {
                it.copy(
                    onlineShelves = emptyList(),
                    isOnlineFeedLoading = false
                )
            }
            return
        }

        onlineFeedJob?.cancel()
        onlineFeedJob = viewModelScope.launch {
            _uiState.update { it.copy(isOnlineFeedLoading = true) }
            try {
                val genres = repository.getTopGenres()
                onlineFeedFlow(genres, refresh = true).collect { shelves ->
                    _uiState.update {
                        it.copy(
                            onlineShelves = shelves,
                            isOnlineFeedLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to refresh online feed", e)
                _uiState.update { it.copy(isOnlineFeedLoading = false) }
            }
        }
    }

    private fun onlineFeedFlow(
        genres: List<String>,
        refresh: Boolean
    ): Flow<List<HomeFeedItem>> {
        val audiusFlow = if (refresh) {
            audiusRepository.refreshShelves(genres)
        } else {
            audiusRepository.getOnlineShelves(genres)
        }
        val ncsFlow = if (refresh) {
            ncsRepository.refreshShelves(genres)
        } else {
            ncsRepository.getOnlineShelves(genres)
        }

        return combine(audiusFlow, ncsFlow) { audiusShelves, ncsShelves ->
            mergeOnlineShelves(audiusShelves, ncsShelves)
        }
    }

    private fun mergeOnlineShelves(
        audiusShelves: List<HomeFeedItem>,
        ncsShelves: List<HomeFeedItem>
    ): List<HomeFeedItem> {
        val audiusTrackShelves = audiusShelves.filterIsInstance<HomeFeedItem.OnlineTrackShelf>()
        val audiusPlaylistShelves = audiusShelves.filterIsInstance<HomeFeedItem.OnlinePlaylistShelf>()
        val ncsTrackShelves = ncsShelves.filterIsInstance<HomeFeedItem.OnlineTrackShelf>()

        val primaryAudius = audiusTrackShelves.firstOrNull { it.type == com.oss.euphoriae.data.model.ShelfType.NEW_RELEASES }
            ?: audiusTrackShelves.firstOrNull()
        val primaryNcs = ncsTrackShelves.firstOrNull()

        val mergedShelves = buildList {
            primaryAudius?.let(::add)
            primaryNcs?.let(::add)

            val remainingAudius = audiusTrackShelves.filterNot { it.stableId == primaryAudius?.stableId }
            val remainingNcs = ncsTrackShelves.filterNot { it.stableId == primaryNcs?.stableId }
            val maxCount = maxOf(remainingAudius.size, remainingNcs.size)
            for (index in 0 until maxCount) {
                remainingAudius.getOrNull(index)?.let(::add)
                remainingNcs.getOrNull(index)?.let(::add)
            }

            addAll(audiusPlaylistShelves)
        }

        return if (mergedShelves.isEmpty()) {
            emptyList()
        } else {
            listOf(HomeFeedItem.OnlineHeader()) + mergedShelves
        }
    }

    fun getExploreTracks(sectionId: String, optionId: String): Flow<List<OnlineTrack>> {
        if (!_uiState.value.isOnlineTracksEnabled) return flowOf(emptyList())

        val option = ExploreCatalog.findOption(sectionId, optionId) ?: return flowOf(emptyList())
        return when (option.category) {
            ExploreCategory.GENRES,
            ExploreCategory.MOODS -> fetchBlendedGenreTracksFlow(
                audiusGenres = option.audiusGenres,
                ncsGenres = option.ncsGenres
            )

            ExploreCategory.CHARTS -> option.chartKey?.let { fetchChartsTracksFlow(it) } ?: flowOf(emptyList())
        }
    }

    fun getOnlinePlaylistTracks(playlistId: String): Flow<List<OnlineTrack>> = flow {
        val tracks = audiusRepository.getPlaylistTracks(playlistId)
        emit(tracks)
    }

    private fun fetchBlendedGenreTracksFlow(
        audiusGenres: List<String>,
        ncsGenres: List<String>,
        limitPerProviderGenre: Int = 15,
        resultLimit: Int = 30
    ): Flow<List<OnlineTrack>> = channelFlow {
        val allAudiusGenres = audiusGenres.distinct()
        val allNcsGenres = ncsGenres.distinct()

        val mutex = Mutex()
        var audiusTracks = emptyList<OnlineTrack>()
        var ncsTracks = emptyList<OnlineTrack>()

        val jobs = mutableListOf<Job>()

        allAudiusGenres.forEach { genre ->
            jobs += launch {
                try {
                    val tracks = audiusRepository.getTracksByGenre(genre, limit = limitPerProviderGenre)
                    mutex.withLock {
                        audiusTracks = (audiusTracks + tracks).distinctBy { it.id }
                        val merged = interleaveOnlineTracks(primary = audiusTracks, secondary = ncsTracks, limit = resultLimit)
                        if (merged.isNotEmpty()) {
                            send(merged)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Failed to fetch Audius genre $genre", e)
                }
            }
        }

        allNcsGenres.forEach { genre ->
            jobs += launch {
                try {
                    val tracks = ncsRepository.getTracksByGenre(genre, limit = limitPerProviderGenre)
                    mutex.withLock {
                        ncsTracks = (ncsTracks + tracks).distinctBy { it.id }
                        val merged = interleaveOnlineTracks(primary = audiusTracks, secondary = ncsTracks, limit = resultLimit)
                        if (merged.isNotEmpty()) {
                            send(merged)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Failed to fetch NCS genre $genre", e)
                }
            }
        }

        jobs.forEach { it.join() }
    }

    private fun fetchChartsTracksFlow(
        chartKey: String,
        resultLimit: Int = 30
    ): Flow<List<OnlineTrack>> = channelFlow {
        val mutex = Mutex()
        var audiusTracks = emptyList<OnlineTrack>()
        var ncsTracks = emptyList<OnlineTrack>()

        val primaryJob = launch {
            try {
                val tracks = when (chartKey) {
                    "momentum" -> audiusRepository.getTrendingTracks(limit = 15)
                    "fresh" -> audiusRepository.getNewReleaseTracks(limit = 15)
                    "indie" -> audiusRepository.getUndergroundTracks(limit = 15)
                    "creator_safe" -> audiusRepository.getTracksByGenre("Electronic", limit = 15)
                    else -> emptyList()
                }
                mutex.withLock {
                    audiusTracks = tracks
                    val merged = if (chartKey == "creator_safe") {
                        interleaveOnlineTracks(primary = ncsTracks, secondary = audiusTracks, limit = resultLimit)
                    } else {
                        interleaveOnlineTracks(primary = audiusTracks, secondary = ncsTracks, limit = resultLimit)
                    }
                    if (merged.isNotEmpty()) {
                        send(merged)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to fetch Audius chart $chartKey", e)
            }
        }

        val secondaryJob = launch {
            try {
                val tracks = when (chartKey) {
                    "momentum" -> ncsRepository.getPopularTracks(limit = 15)
                    "fresh" -> ncsRepository.getFreshTracks(limit = 15)
                    "indie" -> ncsRepository.getRandomTracks(limit = 15)
                    "creator_safe" -> ncsRepository.getRandomTracks(limit = 15)
                    else -> emptyList()
                }
                mutex.withLock {
                    ncsTracks = tracks
                    val merged = if (chartKey == "creator_safe") {
                        interleaveOnlineTracks(primary = ncsTracks, secondary = audiusTracks, limit = resultLimit)
                    } else {
                        interleaveOnlineTracks(primary = audiusTracks, secondary = ncsTracks, limit = resultLimit)
                    }
                    if (merged.isNotEmpty()) {
                        send(merged)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to fetch NCS chart $chartKey", e)
            }
        }

        primaryJob.join()
        secondaryJob.join()
    }

    private fun interleaveOnlineTracks(
        primary: List<OnlineTrack>,
        secondary: List<OnlineTrack>,
        limit: Int
    ): List<OnlineTrack> {
        val merged = ArrayList<OnlineTrack>(limit)
        val seen = LinkedHashSet<String>()
        val left = primary.iterator()
        val right = secondary.iterator()

        fun accept(track: OnlineTrack) {
            val key = track.streamUrl.ifBlank { "${track.provider}:${track.id}:${track.title}" }
            if (seen.add(key)) {
                merged += track
            }
        }

        while (merged.size < limit && (left.hasNext() || right.hasNext())) {
            if (left.hasNext()) accept(left.next())
            if (merged.size >= limit) break
            if (right.hasNext()) accept(right.next())
        }

        return merged.take(limit)
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        songsSearchJob?.cancel()
        homeSearchJob?.cancel()
        onlineFeedJob?.cancel()
        mediaController?.removeListener(playerListener)
        audioEffectsManager.release()
        // Note: Don't destroy audioEngine here - it's a singleton managed by the service
        _audioEngine = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
