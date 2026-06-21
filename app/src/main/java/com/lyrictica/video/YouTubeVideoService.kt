package com.lyrictica.video

import com.oss.euphoriae.BuildConfig
import com.oss.euphoriae.data.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class YouTubeVideoService(
    cacheDirectory: File? = null
) : Closeable {
    private val lookupCache = YouTubeLookupCache.shared(cacheDirectory)

    val isConfigured: Boolean
        get() = BuildConfig.WORKER_BASE_URL.isNotBlank()

    suspend fun searchVideos(song: Song, maxResults: Int = 5): List<YouTubeVideoCandidate> {
        if (!isConfigured) return emptyList()

        val query = song.toYouTubeSearchQuery()
        if (query.isBlank()) return emptyList()

        val limitedResults = maxResults.coerceIn(1, 10)
        return lookupCache.lookup(
            query = query,
            maxResults = limitedResults
        ) {
            runCatching {
                fetchVideos(song = song, query = query, maxResults = limitedResults)
            }.getOrElse { error ->
                android.util.Log.e(TAG, "Failed to search YouTube videos for ${song.title}", error)
                throw error
            }
        }
    }

    override fun close() {
        // Shared client/cache live for the process; per-instance disposal is handled by Compose.
    }

    private suspend fun fetchVideos(
        song: Song,
        query: String,
        maxResults: Int
    ): List<YouTubeVideoCandidate> {
        return withContext(Dispatchers.IO) {
            val response = client.get("$BASE_URL/search") {
                parameter("part", "snippet")
                parameter("type", "video")
                parameter("videoEmbeddable", "true")
                parameter("videoSyndicated", "true")
                parameter("safeSearch", "none")
                parameter("maxResults", maxResults)
                parameter("q", query)
            }

            if (response.status.value !in OK_RANGE) {
                throw IllegalStateException("YouTube search failed with HTTP ${response.status.value}")
            }

            val payload = response.body<YouTubeSearchResponse>()
            payload.items
                .mapNotNull { it.toCandidate() }
                .sortedByDescending { youTubeScore(song, it) }
        }
    }

    companion object {
        private const val TAG = "YouTubeVideoService"
        private val BASE_URL = "${BuildConfig.WORKER_BASE_URL}/youtube"
        private val OK_RANGE = 200..299
        private val client = HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(20, TimeUnit.SECONDS)
                    writeTimeout(20, TimeUnit.SECONDS)
                }
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
}

internal data class YouTubeVideoCandidate(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String?,
    val description: String
)

@Serializable
internal data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem> = emptyList()
)

@Serializable
internal data class YouTubeSearchItem(
    val id: YouTubeSearchItemId? = null,
    val snippet: YouTubeSearchSnippet? = null
) {
    fun toCandidate(): YouTubeVideoCandidate? {
        val videoId = id?.videoId?.takeIf { it.isNotBlank() } ?: return null
        val snippet = snippet ?: return null
        return YouTubeVideoCandidate(
            videoId = videoId,
            title = snippet.title.trim(),
            channelTitle = snippet.channelTitle.trim(),
            thumbnailUrl = snippet.bestThumbnailUrl(),
            description = snippet.description.trim()
        )
    }
}

@Serializable
internal data class YouTubeSearchItemId(
    @SerialName("videoId") val videoId: String? = null
)

@Serializable
internal data class YouTubeSearchSnippet(
    val title: String = "",
    val channelTitle: String = "",
    val description: String = "",
    val thumbnails: YouTubeThumbnails = YouTubeThumbnails()
) {
    fun bestThumbnailUrl(): String? {
        return thumbnails.maxres?.url
            ?.takeIf { it.isNotBlank() }
            ?: thumbnails.high?.url?.takeIf { it.isNotBlank() }
            ?: thumbnails.medium?.url?.takeIf { it.isNotBlank() }
            ?: thumbnails.standard?.url?.takeIf { it.isNotBlank() }
            ?: thumbnails.default?.url?.takeIf { it.isNotBlank() }
    }
}

@Serializable
internal data class YouTubeThumbnails(
    val default: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null,
    val standard: YouTubeThumbnail? = null,
    val maxres: YouTubeThumbnail? = null
)

@Serializable
internal data class YouTubeThumbnail(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null
)

internal fun Song.toYouTubeSearchQuery(): String {
    val parts = buildList {
        title.trim().takeIf { it.isNotBlank() }?.let { add(it) }
        artist.usableMetadata()?.let { add(it) }
        album.usableMetadata()?.let { add(it) }
        add("official music video")
    }

    return parts
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.usableMetadata(): String? {
    val value = trim()
    if (value.isBlank()) return null
    return when (value.lowercase(Locale.ROOT)) {
        "unknown artist",
        "unknown album",
        "unknown title",
        "unknown" -> null
        else -> value
    }
}

private fun youTubeScore(song: Song, candidate: YouTubeVideoCandidate): Int {
    val title = candidate.title.normalizedYouTubeText()
    val channel = candidate.channelTitle.normalizedYouTubeText()
    val songTitle = song.title.normalizedYouTubeText()
    val songArtist = song.artist.usableMetadata()?.normalizedYouTubeText().orEmpty()

    var score = 0

    if (songTitle.isNotBlank() && title.contains(songTitle)) score += 38
    if (songTitle.isNotBlank() && title.startsWith(songTitle)) score += 12
    if (songArtist.isNotBlank() && title.contains(songArtist)) score += 24
    if (songArtist.isNotBlank() && channel.contains(songArtist)) score += 14

    when {
        title.contains("official music video") -> score += 16
        title.contains("official video") -> score += 14
        title.contains("official audio") -> score += 12
        title.contains("official") -> score += 6
    }

    when {
        channel.contains("topic") -> score += 8
        title.contains("topic") -> score += 5
    }

    if (title.contains("lyrics") || title.contains("lyric")) score -= 12
    if (title.contains("cover")) score -= 16
    if (title.contains("karaoke")) score -= 12
    if (title.contains("remix")) score -= 8
    if (title.contains("live")) score -= 6

    return score
}

private fun String.normalizedYouTubeText(): String {
    return lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
