package com.lyrictica.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

internal object TrackMetadataExtractor {

    data class Metadata(
        val title: String,
        val artist: String?,
        val album: String?,
        val durationSec: Int?
    )

    fun extract(context: Context, uri: Uri, fallbackDisplayName: String? = null): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val title = cleanMetadataValue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
            val artist = cleanMetadataValue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                ?: cleanMetadataValue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
            val album = cleanMetadataValue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

            val fallbackTitle = fallbackDisplayName
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: queryDisplayName(context, uri)
                    ?.substringBeforeLast('.')
                    ?.takeIf { it.isNotBlank() }
                ?: "Unknown"

            Metadata(
                title = title?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                artist = artist?.trim()?.takeIf { it.isNotBlank() },
                album = album?.trim()?.takeIf { it.isNotBlank() },
                durationSec = durationMs?.let { (it / 1000L).toInt() }?.takeIf { it > 0 }
            )
        } catch (_: Exception) {
            // Fallback if metadata reading fails.
            val name = fallbackDisplayName
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: queryDisplayName(context, uri)?.substringBeforeLast('.')
                ?: "Unknown"

            Metadata(
                title = name,
                artist = null,
                album = null,
                durationSec = null
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    internal fun cleanMetadataValue(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        return when (trimmed.lowercase()) {
            "unknown",
            "unknown artist",
            "unknown album",
            "unknown title",
            "<unknown>",
            "(unknown)",
            "null",
            "audius" -> null
            else -> trimmed
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) cursor.getString(nameIndex) else null
                } else null
            }
        } else {
            uri.path?.substringAfterLast('/')
        }
    }
}
