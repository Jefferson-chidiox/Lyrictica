package com.oss.euphoriae.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Service for fetching contributors from GitHub API
 * Uses public API
 */
class GitHubService {
    
    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    companion object {
        private const val TAG = "GitHubService"
        private const val BASE_URL = "https://api.github.com"
        private const val OWNER = "ellenoireQ"
        private const val REPO = "Euphoriae"
    }
    
    /**
     * Fetch contributors for the repository
     * Returns empty list if error occurs
     */
    suspend fun getContributors(): List<GitHubContributor> {
        return try {
            android.util.Log.d(TAG, "Fetching contributors for $OWNER/$REPO")
            
            val response = client.get("$BASE_URL/repos/$OWNER/$REPO/contributors") {
                header("Accept", "application/vnd.github.v3+json")
            }
            
            android.util.Log.d(TAG, "Response status: ${response.status}")
            
            if (response.status == HttpStatusCode.OK) {
                val result = response.body<List<GitHubContributor>>()
                android.util.Log.d(TAG, "Got ${result.size} contributors")
                result
            } else {
                android.util.Log.w(TAG, "Non-OK response: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch contributors", e)
            emptyList()
        }
    }
    
    fun close() {
        client.close()
    }
}
