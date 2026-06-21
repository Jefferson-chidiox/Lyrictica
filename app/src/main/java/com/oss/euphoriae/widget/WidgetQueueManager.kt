package com.oss.euphoriae.widget

import android.content.Context
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.queueDataStore by preferencesDataStore(name = "widget_queue")

/**
 * Simplified song info for queue storage
 */
data class QueueSongInfo(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: String?,
    val duration: Long,
    val data: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("albumArtUri", albumArtUri ?: "")
            put("duration", duration)
            put("data", data)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): QueueSongInfo {
            return QueueSongInfo(
                id = json.getLong("id"),
                title = json.getString("title"),
                artist = json.getString("artist"),
                album = json.optString("album", ""),
                albumArtUri = json.optString("albumArtUri").takeIf { it.isNotEmpty() },
                duration = json.optLong("duration", 0L),
                data = json.optString("data", "")
            )
        }
    }
}

/**
 * Manages the playback queue for widget navigation
 */
object WidgetQueueManager {
    
    private val QUEUE_KEY = stringPreferencesKey("queue_json")
    private val CURRENT_INDEX_KEY = intPreferencesKey("current_index")
    
    /**
     * Save current queue and index
     */
    suspend fun saveQueue(context: Context, queue: List<QueueSongInfo>, currentIndex: Int) {
        val jsonArray = JSONArray()
        queue.forEach { song ->
            jsonArray.put(song.toJson())
        }
        
        context.queueDataStore.edit { prefs ->
            prefs[QUEUE_KEY] = jsonArray.toString()
            prefs[CURRENT_INDEX_KEY] = currentIndex
        }
    }
    
    /**
     * Get the current queue
     */
    suspend fun getQueue(context: Context): List<QueueSongInfo> {
        return context.queueDataStore.data.map { prefs ->
            val queueJson = prefs[QUEUE_KEY] ?: return@map emptyList()
            try {
                val jsonArray = JSONArray(queueJson)
                (0 until jsonArray.length()).map { i ->
                    QueueSongInfo.fromJson(jsonArray.getJSONObject(i))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }.first()
    }
    
    /**
     * Get current index
     */
    suspend fun getCurrentIndex(context: Context): Int {
        return context.queueDataStore.data.map { prefs ->
            prefs[CURRENT_INDEX_KEY] ?: -1
        }.first()
    }
    
    /**
     * Update current index only
     */
    suspend fun updateCurrentIndex(context: Context, index: Int) {
        context.queueDataStore.edit { prefs ->
            prefs[CURRENT_INDEX_KEY] = index
        }
    }
    
    /**
     * Get next song info
     */
    suspend fun getNextSong(context: Context): Pair<QueueSongInfo, Int>? {
        val queue = getQueue(context)
        if (queue.isEmpty()) return null
        
        val currentIndex = getCurrentIndex(context)
        val nextIndex = (currentIndex + 1) % queue.size
        return queue[nextIndex] to nextIndex
    }
    
    /**
     * Get previous song info
     */
    suspend fun getPreviousSong(context: Context): Pair<QueueSongInfo, Int>? {
        val queue = getQueue(context)
        if (queue.isEmpty()) return null
        
        val currentIndex = getCurrentIndex(context)
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else queue.size - 1
        return queue[prevIndex] to prevIndex
    }
    
    /**
     * Get media URI for a song ID or stream URL
     */
    fun getMediaUri(songId: Long, data: String): Uri {
        return if (data.startsWith("http://") || data.startsWith("https://") || data.startsWith("content://")) {
            Uri.parse(data)
        } else {
            ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                songId
            )
        }
    }
}
