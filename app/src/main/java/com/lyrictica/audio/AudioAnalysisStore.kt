package com.lyrictica.audio

import android.content.Context
import android.net.Uri
import com.lyrictica.karaoke.KaraokeMelodyPrecomputer
import com.lyrictica.karaoke.KaraokeMelodyReference
import com.lyrictica.lyrics.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal const val AUDIO_ANALYSIS_FPS = 60

/**
 * Durable on-disk store for precomputed band envelopes.
 *
 * The stored analysis survives app restarts because it lives under filesDir,
 * while the temporary source copy can still be recreated from the original URI
 * if the cache ever needs to be rebuilt.
 */
class AudioAnalysisStore(context: Context) {

    private val appContext = context.applicationContext

    private val analysisRootDir = File(appContext.filesDir, "audio_analysis")
    private val stagingDir = File(appContext.cacheDir, "audio_analysis_staging")
    private val bandsDir = File(analysisRootDir, "bands")
    private val melodyDir = File(analysisRootDir, "karaoke_melody")

    private val keyMutexes = ConcurrentHashMap<String, Mutex>()

    init {
        ensureDirectories()
    }

    fun hasCachedBands(uri: Uri, fps: Int = AUDIO_ANALYSIS_FPS): Boolean {
        val cacheFile = analysisFileFor(uri, fps)
        return cacheFile.isFile && cacheFile.length() > 0L
    }

    internal suspend fun loadOrComputeBands(
        uri: Uri,
        fps: Int = AUDIO_ANALYSIS_FPS
    ): SpectrumBandsPrecomputer.PrecomputedBands = withContext(Dispatchers.IO) {
        val cacheKey = cacheKeyFor(uri, fps)
        val mutex = keyMutexes.getOrPut(cacheKey) { Mutex() }

        mutex.withLock {
            val cacheFile = analysisFileFor(uri, fps)
            SpectrumBandsPrecomputer.readCache(cacheFile)?.let { cached ->
                return@withLock cached
            }

            val sourceFile = copyToStagingFile(uri)
            try {
                SpectrumBandsPrecomputer.computeAndCache(
                    audioFile = sourceFile,
                    fps = fps,
                    cacheFile = cacheFile
                )
            } finally {
                runCatching { sourceFile.delete() }
            }
        }
    }

    internal suspend fun loadOrComputeKaraokeMelody(
        uri: Uri,
        lyrics: ParsedLyrics
    ): KaraokeMelodyReference = withContext(Dispatchers.IO) {
        val cacheKey = melodyCacheKeyFor(uri, lyrics)
        val mutex = keyMutexes.getOrPut(cacheKey) { Mutex() }

        mutex.withLock {
            val cacheFile = melodyFileFor(uri, lyrics)
            KaraokeMelodyPrecomputer.readCache(cacheFile)?.let { cached ->
                return@withLock cached
            }

            val sourceFile = copyToStagingFile(uri)
            try {
                KaraokeMelodyPrecomputer.computeAndCache(
                    audioFile = sourceFile,
                    parsedLyrics = lyrics,
                    cacheFile = cacheFile
                )
            } finally {
                runCatching { sourceFile.delete() }
            }
        }
    }

    private fun ensureDirectories() {
        analysisRootDir.mkdirs()
        stagingDir.mkdirs()
        bandsDir.mkdirs()
        melodyDir.mkdirs()
    }

    private fun analysisFileFor(uri: Uri, fps: Int): File {
        return File(bandsDir, "spectrum_bands_${cacheKeyFor(uri, fps)}.bin")
    }

    private fun melodyFileFor(uri: Uri, lyrics: ParsedLyrics): File {
        return File(melodyDir, "karaoke_melody_${melodyCacheKeyFor(uri, lyrics)}.json")
    }

    private fun copyToStagingFile(uri: Uri): File {
        ensureDirectories()

        val resolver = appContext.contentResolver
        val isNetworkUri = uri.scheme == "http" || uri.scheme == "https"
        val extension = when (uri.scheme) {
            "file" -> uri.path
                ?.substringAfterLast('/', missingDelimiterValue = "")
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() }
                ?.let { ".${it}" }
                ?: ""
            "http", "https" -> {
                val path = uri.path.orEmpty()
                val ext = path.substringAfterLast('.', missingDelimiterValue = "")
                if (ext.isNotBlank() && ext.length <= 4) {
                    ".${ext}"
                } else {
                    ".mp3"
                }
            }
            else -> resolver.getType(uri)
                ?.substringAfterLast('/', missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() }
                ?.let { ".${it}" }
                ?: ""
        }

        val name = "source_${sha1(uri.toString())}$extension"
        val outFile = File(stagingDir, name)

        if (outFile.exists() && outFile.length() > 0L) return outFile

        val inputStream = if (isNetworkUri) {
            java.net.URL(uri.toString()).openStream()
        } else {
            resolver.openInputStream(uri)
        }

        inputStream?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open input stream for uri=$uri")

        return outFile
    }

    private fun cacheKeyFor(uri: Uri, fps: Int): String {
        return "${sha1(uri.toString())}_fps=$fps"
    }

    private fun melodyCacheKeyFor(uri: Uri, lyrics: ParsedLyrics): String {
        return "${sha1(uri.toString())}_lyrics=${KaraokeMelodyReference.lyricsFingerprint(lyrics)}"
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
