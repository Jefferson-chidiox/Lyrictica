package com.lyrictica.audio

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.MediaStore
import com.oss.euphoriae.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "lyrictica_playback_session"
private const val KEY_SESSION = "session_json"

@Serializable
data class PlaybackTrackSnapshot(
    val uri: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUri: String? = null,
    val durationMs: Long = 0L,
    val songId: Long? = null
) {
    fun toSong(): Song = Song(
        id = songId ?: 0L,
        title = title,
        artist = artist ?: "Unknown Artist",
        album = album ?: "Unknown Album",
        duration = durationMs,
        data = uri,
        albumArtUri = albumArtUri
    )
}

@Serializable
data class PlaybackSession(
    val current: PlaybackTrackSnapshot,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val queue: List<PlaybackTrackSnapshot> = emptyList(),
    val currentQueueIndex: Int = -1,
    val queueSourceKey: String? = null,
    val queueSourceLabel: String? = null,
    val queueSourceCategory: String? = null,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = 0
)

class PlaybackSessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun save(session: PlaybackSession) {
        prefs.edit().putString(KEY_SESSION, json.encodeToString(PlaybackSession.serializer(), session)).apply()
    }

    fun load(): PlaybackSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { json.decodeFromString(PlaybackSession.serializer(), raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }
}

fun Song.toPlaybackSnapshot(): PlaybackTrackSnapshot {
    val uri = if (data.startsWith("http://") || data.startsWith("https://")) {
        data
    } else {
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
    }
    return PlaybackTrackSnapshot(
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        albumArtUri = albumArtUri,
        durationMs = duration,
        songId = id
    )
}

fun Uri.toPlaybackSnapshot(
    title: String,
    artist: String? = null,
    album: String? = null,
    albumArtUri: String? = null,
    durationMs: Long = 0L
): PlaybackTrackSnapshot {
    return PlaybackTrackSnapshot(
        uri = toString(),
        title = title,
        artist = artist,
        album = album,
        albumArtUri = albumArtUri,
        durationMs = durationMs
    )
}
