package com.oss.euphoriae.data.remote

import com.lyrictica.lyrics.MusixmatchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MusixmatchService(
    private val client: MusixmatchClient = MusixmatchClient(userAgent = "Lyrictica/1.0 (https://github.com)")
) {

    suspend fun searchLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        duration: Long? = null
    ): MusixmatchResponse? = withContext(Dispatchers.IO) {
        val lyrics = client.findLyrics(
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationSec = duration?.let { (it / 1000L).toInt() }?.takeIf { it > 0 }
        ) ?: return@withContext null

        MusixmatchResponse(
            trackId = lyrics.trackId,
            trackName = lyrics.trackName,
            artistName = lyrics.artistName,
            albumName = lyrics.albumName,
            duration = lyrics.durationSec.toDouble(),
            instrumental = lyrics.instrumental,
            plainLyrics = lyrics.plainLyrics,
            syncedLyrics = lyrics.syncedLyricsLrc
        )
    }
}
