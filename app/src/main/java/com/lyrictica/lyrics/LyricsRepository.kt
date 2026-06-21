package com.lyrictica.lyrics

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LyricsRepository(
    private val context: Context,
    private val client: MusixmatchClient = MusixmatchClient(userAgent = "Lyrictica/1.0 (https://github.com)")
) {
    suspend fun fetchLyrics(
        uri: Uri,
        fallbackDisplayName: String?,
        artist: String? = null,
        album: String? = null,
        durationSec: Int? = null
    ): ParsedLyrics? = withContext(Dispatchers.IO) {
        val meta = TrackMetadataExtractor.extract(context, uri, fallbackDisplayName)

        val finalTitle = fallbackDisplayName?.takeIf { it.isNotBlank() } ?: meta.title
        val finalArtist = TrackMetadataExtractor.cleanMetadataValue(artist) ?: meta.artist
        val finalAlbum = TrackMetadataExtractor.cleanMetadataValue(album) ?: meta.album
        val finalDurationSec = durationSec?.takeIf { it > 0 } ?: meta.durationSec

        fetchLyrics(
            trackName = finalTitle,
            artist = finalArtist,
            album = finalAlbum,
            durationSec = finalDurationSec
        )
    }

    suspend fun fetchLyrics(
        trackName: String,
        artist: String? = null,
        album: String? = null,
        durationSec: Int? = null
    ): ParsedLyrics? = withContext(Dispatchers.IO) {
        val normalizedTitle = LyricsQueryNormalizer.searchTitle(trackName)
        if (normalizedTitle.isBlank()) return@withContext null

        client.findLyrics(
            trackName = normalizedTitle,
            artistName = TrackMetadataExtractor.cleanMetadataValue(artist),
            albumName = TrackMetadataExtractor.cleanMetadataValue(album),
            durationSec = durationSec?.takeIf { it > 0 }
        )?.let { toParsed(it) }
    }

    private fun toParsed(lyrics: MusixmatchLyrics): ParsedLyrics? {
        val richSync = lyrics.richSyncBody
        val synced = lyrics.syncedLyricsLrc
        val plain = lyrics.plainLyrics

        return when {
            !richSync.isNullOrBlank() -> MusixmatchLyricsParsers.parseRichsyncBody(richSync)
            !synced.isNullOrBlank() -> LrcParser.parse(synced)
            !plain.isNullOrBlank() -> {
                val lines = plain.lines()
                    .mapNotNull { rawLine ->
                        LyricsQueryNormalizer.text(rawLine)?.let { text -> LyricLine(timeMs = null, text = text) }
                    }
                if (lines.isEmpty()) null else ParsedLyrics(lines = lines, isSynced = false)
            }
            else -> null
        }
    }
}
