package com.oss.euphoriae.data.remote

import com.oss.euphoriae.data.model.AudiusPlaylist
import com.oss.euphoriae.data.model.AudiusPlaylistResponse
import com.oss.euphoriae.data.model.AudiusTrack
import com.oss.euphoriae.data.model.AudiusTrackResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Ktor HTTP client for Lyrictica's online music providers.
 *
 * Audius REST endpoints remain available for the legacy code path.
 * Spinamp-backed replacements use the official GraphQL endpoint exposed at
 * https://api.spinamp.xyz/v3/graphql.
 */
class AudiusService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Serializable
    data class SpinampGraphQLRequest(
        val query: String,
        val variables: JsonObject? = null
    )

    @Serializable
    data class SpinampGraphQLResponse(
        val data: SpinampData? = null,
        val errors: List<SpinampGraphQLError> = emptyList()
    )

    @Serializable
    data class SpinampGraphQLError(val message: String = "")

    @Serializable
    data class SpinampData(
        val allProcessedTracks: SpinampTracksConnection? = null,
        val allTrendingTracks: SpinampTrendingTracksConnection? = null
    )

    @Serializable
    data class SpinampTracksConnection(val nodes: List<SpinampTrackNode> = emptyList())

    @Serializable
    data class SpinampTrendingTracksConnection(val nodes: List<SpinampTrendingTrackNode> = emptyList())

    @Serializable
    data class SpinampTrendingTrackNode(val processedTrackByTrackId: SpinampTrackNode? = null)

    @Serializable
    data class SpinampTrackNode(
        val id: String,
        val title: String,
        val lossyAudioUrl: String? = null,
        val lossyArtworkUrl: String? = null,
        val duration: String? = null,
        val artistByArtistId: SpinampArtist? = null,
        val tagsByEntityId: SpinampTagConnection? = null
    )

    @Serializable
    data class SpinampArtist(val name: String)

    @Serializable
    data class SpinampTagConnection(val nodes: List<SpinampTagNode> = emptyList())

    @Serializable
    data class SpinampTagNode(
        val value: String? = null,
        val type: String? = null
    )

    /**
     * Fetch recent Spinamp tracks from the official GraphQL API.
     */
    suspend fun getSpinampTracks(limit: Int = 15, offset: Int = 0): List<SpinampTrackNode> {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)

        return try {
            val response = fetchSpinampResponse(
                query = """
                    query RecentSpinampTracks(${'$'}first: Int!, ${'$'}offset: Int!) {
                      allProcessedTracks(
                        first: ${'$'}first
                        offset: ${'$'}offset
                        orderBy: [CREATED_AT_TIME_DESC]
                        filter: { lossyAudioUrl: { isNull: false } }
                      ) {
                        nodes {
                          $SPINAMP_TRACK_FIELDS
                        }
                      }
                    }
                """.trimIndent(),
                variables = buildJsonObject {
                    put("first", JsonPrimitive(safeLimit))
                    put("offset", JsonPrimitive(safeOffset))
                }
            )
            response.data?.allProcessedTracks?.nodes.orEmpty().distinctBy { it.id }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch Spinamp tracks", e)
            emptyList()
        }
    }

    /**
     * Fetch Spinamp's official trending list.
     */
    suspend fun getSpinampTrendingTracks(limit: Int = 15): List<SpinampTrackNode> {
        val safeLimit = limit.coerceIn(1, 100)

        return try {
            val response = fetchSpinampResponse(
                query = """
                    query TrendingSpinampTracks(${'$'}first: Int!) {
                      allTrendingTracks(first: ${'$'}first) {
                        nodes {
                          processedTrackByTrackId {
                            $SPINAMP_TRACK_FIELDS
                          }
                        }
                      }
                    }
                """.trimIndent(),
                variables = buildJsonObject {
                    put("first", JsonPrimitive(safeLimit))
                }
            )
            response.data?.allTrendingTracks?.nodes.orEmpty()
                .mapNotNull { it.processedTrackByTrackId }
                .distinctBy { it.id }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch Spinamp trending tracks", e)
            emptyList()
        }
    }

    /**
     * Fetch Spinamp tracks tagged with the requested genre label.
     */
    suspend fun getSpinampTracksByGenre(genre: String, limit: Int = 15): List<SpinampTrackNode> {
        val trimmedGenre = genre.trim()
        if (trimmedGenre.isBlank()) return emptyList()

        val safeLimit = limit.coerceIn(1, 30)
        val genreTerms = buildSpinampGenreTerms(trimmedGenre)
        val merged = LinkedHashMap<String, SpinampTrackNode>()

        genreTerms.forEach { genreTerm ->
            if (merged.size >= safeLimit) return@forEach
            try {
                val response = fetchSpinampResponse(
                    query = """
                        query GenreSpinampTracks(${'$'}first: Int!, ${'$'}genre: String!) {
                          allProcessedTracks(
                            first: ${'$'}first
                            orderBy: [CREATED_AT_TIME_DESC]
                            filter: {
                              lossyAudioUrl: { isNull: false }
                              tagsByEntityId: { some: { value: { includesInsensitive: ${'$'}genre } } }
                            }
                          ) {
                            nodes {
                              $SPINAMP_TRACK_FIELDS
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = buildJsonObject {
                        put("first", JsonPrimitive(safeLimit))
                        put("genre", JsonPrimitive(genreTerm))
                    }
                )
                response.data?.allProcessedTracks?.nodes.orEmpty().forEach { track ->
                    merged.putIfAbsent(track.id, track)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to fetch Spinamp genre tracks for $genreTerm", e)
            }
        }

        return merged.values.take(safeLimit)
    }

    /**
     * Search Spinamp tracks by title or artist using the official GraphQL API.
     */
    suspend fun searchSpinampTracks(query: String, limit: Int = 15): List<SpinampTrackNode> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()

        val safeLimit = limit.coerceIn(1, 30)

        return try {
            val response = fetchSpinampResponse(
                query = """
                    query SearchSpinampTracks(${'$'}first: Int!, ${'$'}term: String!) {
                      allProcessedTracks(
                        first: ${'$'}first
                        orderBy: [CREATED_AT_TIME_DESC]
                        filter: {
                          lossyAudioUrl: { isNull: false }
                          or: [
                            { title: { includesInsensitive: ${'$'}term } }
                            { artistByArtistId: { name: { includesInsensitive: ${'$'}term } } }
                            { supportingArtist: { includesInsensitive: ${'$'}term } }
                          ]
                        }
                      ) {
                        nodes {
                          $SPINAMP_TRACK_FIELDS
                        }
                      }
                    }
                """.trimIndent(),
                variables = buildJsonObject {
                    put("first", JsonPrimitive(safeLimit))
                    put("term", JsonPrimitive(trimmedQuery))
                }
            )
            response.data?.allProcessedTracks?.nodes.orEmpty().distinctBy { it.id }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to search Spinamp tracks for query=$trimmedQuery", e)
            emptyList()
        }
    }

    /**
     * Fetch trending tracks.
     * @param time Time range: "week", "month", "allTime"
     * @param genre Optional genre filter (e.g. "Electronic", "Hip-Hop/Rap")
     */
    suspend fun getTrendingTracks(
        time: String = "week",
        genre: String? = null,
        limit: Int = 15
    ): List<AudiusTrack> = withContext(Dispatchers.IO) {
        try {
            val response: AudiusTrackResponse = client.get("$BASE_URL/tracks/trending") {
                parameter("app_name", APP_NAME)
                parameter("time", time)
                parameter("limit", limit)
                if (genre != null) {
                    parameter("genre", genre)
                }
            }.body()
            response.data
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch trending tracks (genre=$genre)", e)
            emptyList()
        }
    }

    /**
     * Fetch trending underground tracks — surfaces tracks/artists that haven't
     * broken into the mainstream trending chart.
     */
    suspend fun getUndergroundTrending(
        limit: Int = 15
    ): List<AudiusTrack> = withContext(Dispatchers.IO) {
        try {
            val response: AudiusTrackResponse = client.get("$BASE_URL/tracks/trending/underground") {
                parameter("app_name", APP_NAME)
                parameter("limit", limit)
            }.body()
            response.data
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch underground trending", e)
            emptyList()
        }
    }

    /**
     * Fetch best new releases.
     * @param window Time window: "week", "month", "year"
     */
    suspend fun getBestNewReleases(
        window: String = "week",
        limit: Int = 15
    ): List<AudiusTrack> = withContext(Dispatchers.IO) {
        try {
            val response: AudiusTrackResponse = client.get("$BASE_URL/tracks/best_new_releases") {
                parameter("app_name", APP_NAME)
                parameter("window", window)
                parameter("limit", limit)
            }.body()
            response.data
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch best new releases", e)
            emptyList()
        }
    }

    /**
     * Search Audius tracks by free-text query.
     */
    suspend fun searchTracks(
        query: String,
        limit: Int = 15
    ): List<AudiusTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val response: AudiusTrackResponse = client.get("$BASE_URL/tracks/search") {
                parameter("app_name", APP_NAME)
                parameter("query", query)
                parameter("limit", limit)
                parameter("sort_method", "relevant")
            }.body()
            response.data
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to search Audius tracks for query=$query", e)
            emptyList()
        }
    }

    /**
     * Fetch trending playlists.
     */
    suspend fun getTrendingPlaylists(
        limit: Int = 10
    ): List<AudiusPlaylist> = withContext(Dispatchers.IO) {
        try {
            val response: AudiusPlaylistResponse = client.get("$BASE_URL/playlists/trending") {
                parameter("app_name", APP_NAME)
                parameter("limit", limit)
            }.body()
            response.data
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch trending playlists", e)
            emptyList()
        }
    }

    /**
     * Fetch tracks of a specific playlist.
     */
    suspend fun getPlaylistTracks(
        playlistId: String
    ): List<AudiusTrack> = withContext(Dispatchers.IO) {
        try {
            val response: AudiusTrackResponse = client.get("$BASE_URL/playlists/$playlistId/tracks") {
                parameter("app_name", APP_NAME)
            }.body()
            response.data
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch playlist tracks (id=$playlistId)", e)
            emptyList()
        }
    }

    private suspend fun fetchSpinampResponse(
        query: String,
        variables: JsonObject
    ): SpinampGraphQLResponse = withContext(Dispatchers.IO) {
        val response: SpinampGraphQLResponse = client.post(SPINAMP_GRAPHQL_URL) {
            contentType(ContentType.Application.Json)
            setBody(SpinampGraphQLRequest(query = query, variables = variables))
        }.body()

        if (response.errors.isNotEmpty()) {
            android.util.Log.w(TAG, "Spinamp GraphQL returned ${response.errors.size} error(s): ${response.errors.joinToString { it.message }}")
        }

        response
    }

    private fun buildSpinampGenreTerms(genre: String): List<String> = buildList {
        add(genre)
        genre.split('/', '&', ',').map { it.trim() }
            .filter { it.length >= 3 }
            .forEach(::add)
    }.distinct()

    companion object {
        private const val TAG = "AudiusService"
        private const val BASE_URL = "https://api.audius.co/v1"
        private const val APP_NAME = "Lyrictica"
        private const val SPINAMP_GRAPHQL_URL = "https://api.spinamp.xyz/v3/graphql"
        private val SPINAMP_TRACK_FIELDS = """
            id
            title
            lossyAudioUrl
            lossyArtworkUrl
            duration
            artistByArtistId {
              name
            }
            tagsByEntityId(first: 4) {
              nodes {
                value
                type
              }
            }
        """.trimIndent()
    }
}
