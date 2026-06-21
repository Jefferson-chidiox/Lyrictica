package com.lyrictica.karaoke

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal class KaraokeStemStore(context: Context) {
    private val appContext = context.applicationContext
    private val rootsDir = File(appContext.filesDir, "audio_analysis/karaoke_stems")
    private val uploadDir = File(appContext.cacheDir, "karaoke_stem_uploads")
    private val downloadDir = File(appContext.cacheDir, "karaoke_stem_downloads")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    init {
        ensureDirectories()
    }

    suspend fun inspect(sourceUri: Uri): CachedKaraokeStems? = withContext(Dispatchers.IO) {
        ensureDirectories()
        val descriptor = resolveSourceDescriptor(sourceUri) ?: return@withContext null
        val trackDir = trackDirFor(descriptor.trackKey)
        val manifest = readManifest(trackDir)
        if (manifest != null && manifest.sourceFingerprint != descriptor.fingerprint) {
            trackDir.deleteRecursively()
            return@withContext CachedKaraokeStems(descriptor = descriptor)
        }

        val vocalsFile = manifest?.vocalsFileName
            ?.let { File(trackDir, it) }
            ?.takeIf { it.isFile }
        val instrumentalFile = manifest?.instrumentalFileName
            ?.let { File(trackDir, it) }
            ?.takeIf { it.isFile }

        CachedKaraokeStems(
            descriptor = descriptor,
            manifest = manifest,
            vocalsFile = vocalsFile,
            instrumentalFile = instrumentalFile
        )
    }

    suspend fun copySourceToUploadFile(descriptor: KaraokeSourceDescriptor): File = withContext(Dispatchers.IO) {
        ensureDirectories()
        val extension = descriptor.extension.takeIf { it.isNotBlank() } ?: ""
        val outFile = File(uploadDir, "upload_${descriptor.trackKey}$extension")
        if (outFile.exists()) outFile.delete()

        appContext.contentResolver.openInputStream(descriptor.uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to stage source audio for ${descriptor.uri}")

        outFile
    }

    suspend fun createDownloadDirectory(trackKey: String): File = withContext(Dispatchers.IO) {
        ensureDirectories()
        val target = File(downloadDir, trackKey)
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        target
    }

    suspend fun persistDownloadedBundle(
        descriptor: KaraokeSourceDescriptor,
        providerId: String,
        bundle: StemBundle
    ): CachedKaraokeStems = withContext(Dispatchers.IO) {
        ensureDirectories()
        val trackDir = trackDirFor(descriptor.trackKey)
        if (trackDir.exists()) trackDir.deleteRecursively()
        trackDir.mkdirs()

        val vocalsExt = inferExtension(bundle.vocalsFile, bundle.formatHint)
        val instrumentalExt = inferExtension(bundle.instrumentalFile, bundle.formatHint)
        val vocalsFileName = "vocals$vocalsExt"
        val instrumentalFileName = "instrumental$instrumentalExt"
        val cachedVocals = File(trackDir, vocalsFileName)
        val cachedInstrumental = File(trackDir, instrumentalFileName)

        bundle.vocalsFile.copyTo(cachedVocals, overwrite = true)
        bundle.instrumentalFile.copyTo(cachedInstrumental, overwrite = true)

        val manifest = KaraokeStemManifest(
            sourceUri = descriptor.uri.toString(),
            sourceFingerprint = descriptor.fingerprint,
            provider = providerId,
            createdAtMs = System.currentTimeMillis(),
            stemFormat = bundle.formatHint,
            durationMs = bundle.durationMs ?: descriptor.durationMs,
            alignmentOffsetMs = 0L,
            vocalsFileName = vocalsFileName,
            instrumentalFileName = instrumentalFileName,
            stemsReady = true,
            melodyReady = false,
            bandCacheReady = false,
            lyricsFingerprint = null,
            failureReason = null
        )
        writeManifest(trackDir, manifest)

        CachedKaraokeStems(
            descriptor = descriptor,
            manifest = manifest,
            vocalsFile = cachedVocals,
            instrumentalFile = cachedInstrumental
        )
    }

    suspend fun updatePreparedState(
        cached: CachedKaraokeStems,
        lyricsFingerprint: String?,
        melodyReady: Boolean,
        bandCacheReady: Boolean,
        failureReason: String?
    ): CachedKaraokeStems = withContext(Dispatchers.IO) {
        val trackDir = trackDirFor(cached.descriptor.trackKey)
        trackDir.mkdirs()
        val current = cached.manifest ?: KaraokeStemManifest(
            sourceUri = cached.descriptor.uri.toString(),
            sourceFingerprint = cached.descriptor.fingerprint,
            provider = cached.manifest?.provider ?: "unknown",
            createdAtMs = System.currentTimeMillis(),
            stemFormat = null,
            durationMs = cached.descriptor.durationMs,
            alignmentOffsetMs = 0L,
            vocalsFileName = cached.vocalsFile?.name,
            instrumentalFileName = cached.instrumentalFile?.name,
            stemsReady = cached.stemsReady,
            melodyReady = false,
            bandCacheReady = false,
            lyricsFingerprint = null,
            failureReason = null
        )

        val updated = current.copy(
            sourceFingerprint = cached.descriptor.fingerprint,
            durationMs = current.durationMs ?: cached.descriptor.durationMs,
            vocalsFileName = cached.vocalsFile?.name ?: current.vocalsFileName,
            instrumentalFileName = cached.instrumentalFile?.name ?: current.instrumentalFileName,
            stemsReady = cached.stemsReady,
            melodyReady = melodyReady,
            bandCacheReady = bandCacheReady,
            lyricsFingerprint = lyricsFingerprint,
            failureReason = failureReason
        )
        writeManifest(trackDir, updated)

        cached.copy(manifest = updated)
    }

    suspend fun recordFailure(
        descriptor: KaraokeSourceDescriptor,
        providerId: String,
        failureReason: String
    ): CachedKaraokeStems = withContext(Dispatchers.IO) {
        val existing = inspect(descriptor.uri) ?: CachedKaraokeStems(descriptor = descriptor)
        val trackDir = trackDirFor(descriptor.trackKey)
        trackDir.mkdirs()
        val manifest = (existing.manifest ?: KaraokeStemManifest(
            sourceUri = descriptor.uri.toString(),
            sourceFingerprint = descriptor.fingerprint,
            provider = providerId,
            createdAtMs = System.currentTimeMillis(),
            stemFormat = null,
            durationMs = descriptor.durationMs,
            alignmentOffsetMs = 0L,
            vocalsFileName = existing.vocalsFile?.name,
            instrumentalFileName = existing.instrumentalFile?.name,
            stemsReady = existing.stemsReady,
            melodyReady = false,
            bandCacheReady = false,
            lyricsFingerprint = null,
            failureReason = failureReason
        )).copy(
            sourceFingerprint = descriptor.fingerprint,
            provider = providerId,
            failureReason = failureReason
        )
        writeManifest(trackDir, manifest)
        existing.copy(manifest = manifest)
    }

    suspend fun pruneMissingSources() = withContext(Dispatchers.IO) {
        ensureDirectories()
        rootsDir.listFiles()?.forEach { trackDir ->
            if (!trackDir.isDirectory) return@forEach
            val manifest = readManifest(trackDir) ?: return@forEach
            val sourceUri = runCatching { Uri.parse(manifest.sourceUri) }.getOrNull() ?: run {
                trackDir.deleteRecursively()
                return@forEach
            }
            if (!sourceExists(sourceUri)) {
                trackDir.deleteRecursively()
            }
        }
    }

    private fun readManifest(trackDir: File): KaraokeStemManifest? {
        val manifestFile = File(trackDir, MANIFEST_FILE_NAME)
        if (!manifestFile.isFile || manifestFile.length() <= 0L) return null
        return runCatching {
            json.decodeFromString<KaraokeStemManifest>(manifestFile.readText())
        }.getOrNull()
    }

    private fun writeManifest(trackDir: File, manifest: KaraokeStemManifest) {
        trackDir.mkdirs()
        File(trackDir, MANIFEST_FILE_NAME)
            .writeText(json.encodeToString(manifest))
    }

    private fun trackDirFor(trackKey: String): File = File(rootsDir, trackKey)

    private fun ensureDirectories() {
        rootsDir.mkdirs()
        uploadDir.mkdirs()
        downloadDir.mkdirs()
    }

    private fun resolveSourceDescriptor(sourceUri: Uri): KaraokeSourceDescriptor? {
        if (!sourceExists(sourceUri)) return null

        return when (sourceUri.scheme) {
            "file" -> {
                val sourceFile = sourceUri.path?.let(::File) ?: return null
                if (!sourceFile.isFile) return null
                descriptorFromFields(
                    uri = sourceUri,
                    displayName = sourceFile.name,
                    sizeBytes = sourceFile.length(),
                    durationMs = null,
                    lastModifiedMs = sourceFile.lastModified().takeIf { it > 0L }
                )
            }
            else -> {
                val cursor = appContext.contentResolver.query(
                    sourceUri,
                    arrayOf(
                        OpenableColumns.DISPLAY_NAME,
                        OpenableColumns.SIZE,
                        MediaStore.MediaColumns.DATE_MODIFIED,
                        MediaStore.MediaColumns.DURATION
                    ),
                    null,
                    null,
                    null
                )
                var displayName: String? = null
                var sizeBytes: Long? = null
                var lastModifiedMs: Long? = null
                var durationMs: Long? = null
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) displayName = it.getString(nameIndex)
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !it.isNull(sizeIndex)) sizeBytes = it.getLong(sizeIndex)
                        val modifiedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (modifiedIndex >= 0 && !it.isNull(modifiedIndex)) {
                            lastModifiedMs = it.getLong(modifiedIndex) * 1000L
                        }
                        val durationIndex = it.getColumnIndex(MediaStore.MediaColumns.DURATION)
                        if (durationIndex >= 0 && !it.isNull(durationIndex)) durationMs = it.getLong(durationIndex)
                    }
                }
                val afdLength = runCatching {
                    appContext.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { afd -> afd.length }
                }.getOrNull()?.takeIf { it > 0L }
                descriptorFromFields(
                    uri = sourceUri,
                    displayName = displayName ?: sourceUri.lastPathSegment ?: "track",
                    sizeBytes = sizeBytes ?: afdLength ?: 0L,
                    durationMs = durationMs,
                    lastModifiedMs = lastModifiedMs
                )
            }
        }
    }

    private fun descriptorFromFields(
        uri: Uri,
        displayName: String,
        sizeBytes: Long,
        durationMs: Long?,
        lastModifiedMs: Long?
    ): KaraokeSourceDescriptor {
        val safeName = displayName.ifBlank { "track" }
        val extension = safeName.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }
            ?.let { ".${it.lowercase()}" }
            ?: ""
        val fingerprint = sha1(
            buildString {
                append(uri.toString())
                append('|').append(safeName)
                append('|').append(sizeBytes)
                append('|').append(lastModifiedMs ?: 0L)
                append('|').append(durationMs ?: 0L)
            }
        )
        return KaraokeSourceDescriptor(
            uri = uri,
            trackKey = sha1(uri.toString()),
            fingerprint = fingerprint,
            displayName = safeName,
            extension = extension,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            lastModifiedMs = lastModifiedMs
        )
    }

    private fun sourceExists(uri: Uri): Boolean {
        return runCatching {
            when (uri.scheme) {
                "file" -> uri.path?.let(::File)?.isFile == true
                else -> appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }
        }.getOrDefault(false)
    }

    private fun inferExtension(file: File, formatHint: String?): String {
        val direct = file.extension.takeIf { it.isNotBlank() }?.lowercase()?.let { ".${it}" }
        if (direct != null) return direct
        val hinted = formatHint
            ?.substringAfterLast('/')
            ?.substringAfterLast('.')
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
        return hinted?.let { ".${it}" } ?: ".m4a"
    }

    private fun sha1(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MANIFEST_FILE_NAME = "manifest.json"
    }
}

@Serializable
internal data class KaraokeStemManifest(
    val sourceUri: String,
    val sourceFingerprint: String,
    val provider: String,
    val createdAtMs: Long,
    val stemFormat: String? = null,
    val durationMs: Long? = null,
    val alignmentOffsetMs: Long? = null,
    val vocalsFileName: String? = null,
    val instrumentalFileName: String? = null,
    val stemsReady: Boolean = false,
    val melodyReady: Boolean = false,
    val bandCacheReady: Boolean = false,
    val lyricsFingerprint: String? = null,
    val failureReason: String? = null
)

internal data class KaraokeSourceDescriptor(
    val uri: Uri,
    val trackKey: String,
    val fingerprint: String,
    val displayName: String,
    val extension: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val lastModifiedMs: Long? = null
)

internal data class CachedKaraokeStems(
    val descriptor: KaraokeSourceDescriptor,
    val manifest: KaraokeStemManifest? = null,
    val vocalsFile: File? = null,
    val instrumentalFile: File? = null
) {
    val stemsReady: Boolean
        get() = vocalsFile?.isFile == true && instrumentalFile?.isFile == true
}
