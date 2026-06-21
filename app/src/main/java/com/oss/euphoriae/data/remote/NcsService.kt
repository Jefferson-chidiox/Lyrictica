package com.oss.euphoriae.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NcsService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000L
            connectTimeoutMillis = 60000L
            socketTimeoutMillis = 60000L
        }
    }

    suspend fun searchTracks(
        genreId: String = "all",
        searchQuery: String? = null
    ): List<NcsApiTrack> = withContext(Dispatchers.IO) {
        try {
            client.get("$BASE_URL/search") {
                parameter("genre", genreId.ifBlank { "all" })
                searchQuery?.trim()?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
                parameter("_", System.nanoTime().toString())
            }.body<List<NcsApiTrack>>()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch NCS tracks for genre=$genreId search=$searchQuery", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "NcsService"
        private const val BASE_URL = "https://ncs-backend-api.onrender.com"
    }
}

@Serializable
data class NcsApiTrack(
    val title: String = "",
    val artists: String = "",
    val audioUrl: String = "",
    val coverUrl: String? = null
)
