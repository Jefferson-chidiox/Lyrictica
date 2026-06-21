package com.oss.euphoriae.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class OnlineSource(
    val displayName: String,
    val albumName: String
) {
    AUDIUS(displayName = "Spinamp", albumName = "Spinamp"),
    NCS(displayName = "NCS", albumName = "NCS")
}

@Serializable
data class OnlineTrack(
    val id: String = "",
    val provider: OnlineSource = OnlineSource.AUDIUS,
    val title: String = "",
    val artist: String = "",
    val streamUrl: String = "",
    val artworkUrl: String? = null,
    val genre: String = "",
    val durationMs: Long = 0L
) {
    val displayArtist: String
        get() = artist.ifBlank { provider.displayName }

    val isPlayable: Boolean
        get() = streamUrl.startsWith("http://") || streamUrl.startsWith("https://")
}

fun OnlineTrack.toSong(): Song = Song(
    id = "${provider.name}:${id.ifBlank { streamUrl }}".hashCode().toLong(),
    title = title.ifBlank { "Untitled Track" },
    artist = displayArtist,
    album = provider.albumName,
    albumId = 0L,
    duration = durationMs,
    data = streamUrl,
    albumArtUri = artworkUrl,
    genre = genre
)
