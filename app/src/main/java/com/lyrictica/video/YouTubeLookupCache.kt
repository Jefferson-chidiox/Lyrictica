package com.lyrictica.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class YouTubeLookupCache private constructor(
    private val storageFile: File?,
    private val clock: () -> Long,
    private val softTtlMs: Long,
    private val hardTtlMs: Long,
    private val scope: CoroutineScope
) : Closeable {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, CachedLookup>()
    private val inFlight = mutableMapOf<String, Deferred<List<YouTubeVideoCandidate>>>()
    private var loaded = false

    suspend fun lookup(
        query: String,
        maxResults: Int,
        fetcher: suspend () -> List<YouTubeVideoCandidate>
    ): List<YouTubeVideoCandidate> {
        val key = buildCacheKey(query, maxResults)
        ensureLoaded()

        val snapshot = mutex.withLock { entries[key] }
        val now = clock()
        if (snapshot != null) {
            val ageMs = (now - snapshot.lookupTimestampMs).coerceAtLeast(0L)
            if (ageMs < softTtlMs) {
                return snapshot.candidates
            }

            if (ageMs < hardTtlMs) {
                refreshInBackground(key, fetcher)
                return snapshot.candidates
            }
        }

        return fetchWithDedup(
            key = key,
            staleSnapshot = snapshot,
            fetcher = fetcher
        )
    }

    override fun close() {
        scope.cancel()
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val persisted = loadPersistedEntries()
        mutex.withLock {
            if (loaded) return
            entries.clear()
            entries.putAll(persisted)
            loaded = true
        }
    }

    private suspend fun loadPersistedEntries(): Map<String, CachedLookup> {
        val file = storageFile ?: return emptyMap()
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext emptyMap()

            runCatching {
                val payload = json.decodeFromString<PersistedLookupCache>(file.readText())
                val now = clock()
                payload.entries
                    .asSequence()
                    .filter { it.key.isNotBlank() }
                    .mapNotNull { record ->
                        val lookup = record.toCachedLookup() ?: return@mapNotNull null
                        val ageMs = (now - lookup.lookupTimestampMs).coerceAtLeast(0L)
                        if (ageMs >= hardTtlMs) {
                            null
                        } else {
                            record.key to lookup
                        }
                    }
                    .fold(LinkedHashMap<String, CachedLookup>()) { acc, (key, lookup) ->
                        acc[key] = lookup
                        acc
                    }
            }.getOrElse { emptyMap() }
        }
    }

    private suspend fun persistSnapshot(snapshot: Map<String, CachedLookup>) {
        val file = storageFile ?: return
        val payload = PersistedLookupCache(
            entries = snapshot.entries
                .sortedByDescending { it.value.lookupTimestampMs }
                .map { (key, value) ->
                    PersistedLookupCacheEntry(
                        key = key,
                        lookupTimestampMs = value.lookupTimestampMs,
                        candidates = value.candidates.map { candidate ->
                            PersistedLookupCacheCandidate(
                                videoId = candidate.videoId,
                                title = candidate.title,
                                channelTitle = candidate.channelTitle,
                                thumbnailUrl = candidate.thumbnailUrl,
                                description = candidate.description
                            )
                        }
                    )
                }
        )

        kotlinx.coroutines.withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val jsonString = json.encodeToString(payload)
            val tempFile = File(file.parentFile ?: file.absoluteFile.parentFile, "${file.name}.tmp")
            runCatching {
                tempFile.writeText(jsonString)
                if (file.exists() && !file.delete()) {
                    throw IllegalStateException("Could not replace YouTube lookup cache file")
                }
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            }.onFailure {
                tempFile.delete()
            }
        }
    }

    private suspend fun refreshInBackground(
        key: String,
        fetcher: suspend () -> List<YouTubeVideoCandidate>
    ) {
        val shouldLaunch = mutex.withLock { !inFlight.containsKey(key) }
        if (!shouldLaunch) return

        scope.launch {
            fetchWithDedup(
                key = key,
                staleSnapshot = null,
                fetcher = fetcher
            )
        }
    }

    private suspend fun fetchWithDedup(
        key: String,
        staleSnapshot: CachedLookup?,
        fetcher: suspend () -> List<YouTubeVideoCandidate>
    ): List<YouTubeVideoCandidate> {
        val deferred = mutex.withLock {
            inFlight[key] ?: scope.async { fetcher() }.also { inFlight[key] = it }
        }

        return try {
            val freshCandidates = deferred.await()
            storeSuccess(key, freshCandidates)
            freshCandidates
        } catch (_: Throwable) {
            staleSnapshot?.takeIf { lookup ->
                (clock() - lookup.lookupTimestampMs).coerceAtLeast(0L) < hardTtlMs
            }?.candidates.orEmpty()
        } finally {
            mutex.withLock {
                if (inFlight[key] === deferred) {
                    inFlight.remove(key)
                }
            }
        }
    }

    private suspend fun storeSuccess(
        key: String,
        candidates: List<YouTubeVideoCandidate>
    ) {
        val snapshot = mutex.withLock {
            val now = clock()
            entries[key] = CachedLookup(
                lookupTimestampMs = now,
                candidates = candidates.toList()
            )
            pruneExpiredLocked(now)
            entries.toMap()
        }

        persistSnapshot(snapshot)
    }

    private fun pruneExpiredLocked(nowMs: Long) {
        val expiredKeys = entries.entries
            .filter { (_, value) -> (nowMs - value.lookupTimestampMs).coerceAtLeast(0L) >= hardTtlMs }
            .map { it.key }

        expiredKeys.forEach(entries::remove)

        if (entries.size <= MAX_CACHE_ENTRIES) return

        val overflow = entries.size - MAX_CACHE_ENTRIES
        entries.entries
            .sortedBy { it.value.lookupTimestampMs }
            .take(overflow)
            .map { it.key }
            .forEach(entries::remove)
    }

    private fun buildCacheKey(query: String, maxResults: Int): String {
        val normalizedQuery = query
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

        return "$maxResults|$normalizedQuery"
    }

    companion object {
        private const val MEMORY_CACHE_KEY = "__memory__"
        private const val STORAGE_FILE_NAME = "youtube_lookup_cache.json"
        private const val MAX_CACHE_ENTRIES = 100
        private val sharedCaches = ConcurrentHashMap<String, YouTubeLookupCache>()

        fun shared(
            storageDirectory: File?,
            clock: () -> Long = System::currentTimeMillis
        ): YouTubeLookupCache {
            val storageFile = storageDirectory?.let { File(it, STORAGE_FILE_NAME) }
            val key = storageFile?.absolutePath ?: MEMORY_CACHE_KEY
            return sharedCaches.getOrPut(key) {
                create(
                    storageDirectory = storageDirectory,
                    clock = clock
                )
            }
        }

        fun create(
            storageDirectory: File? = null,
            clock: () -> Long = System::currentTimeMillis,
            softTtlMs: Long = TimeUnit.HOURS.toMillis(12),
            hardTtlMs: Long = TimeUnit.DAYS.toMillis(30)
        ): YouTubeLookupCache {
            val storageFile = storageDirectory?.let {
                it.mkdirs()
                File(it, STORAGE_FILE_NAME)
            }

            return YouTubeLookupCache(
                storageFile = storageFile,
                clock = clock,
                softTtlMs = softTtlMs,
                hardTtlMs = hardTtlMs,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
        }
    }
}

@Serializable
private data class PersistedLookupCache(
    val entries: List<PersistedLookupCacheEntry> = emptyList()
)

@Serializable
private data class PersistedLookupCacheEntry(
    val key: String,
    val lookupTimestampMs: Long,
    val candidates: List<PersistedLookupCacheCandidate> = emptyList()
) {
    fun toCachedLookup(): CachedLookup? {
        val converted = candidates.mapNotNull { candidate -> candidate.toCandidate() }
        return CachedLookup(
            lookupTimestampMs = lookupTimestampMs,
            candidates = converted
        )
    }
}

@Serializable
private data class PersistedLookupCacheCandidate(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String? = null,
    val description: String
) {
    fun toCandidate(): YouTubeVideoCandidate? {
        val normalizedVideoId = videoId.trim()
        if (normalizedVideoId.isBlank()) return null
        return YouTubeVideoCandidate(
            videoId = normalizedVideoId,
            title = title.trim(),
            channelTitle = channelTitle.trim(),
            thumbnailUrl = thumbnailUrl?.trim()?.takeIf { it.isNotBlank() },
            description = description.trim()
        )
    }
}

internal data class CachedLookup(
    val lookupTimestampMs: Long,
    val candidates: List<YouTubeVideoCandidate>
)
