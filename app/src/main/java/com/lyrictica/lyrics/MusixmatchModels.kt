package com.lyrictica.lyrics

internal data class MusixmatchLyrics(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyricsLrc: String?,
    val richSyncBody: String? = null
)

data class MusixmatchTrackRecord(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val instrumental: Boolean,
    val hasLyrics: Boolean,
    val hasSubtitles: Boolean,
    val hasRichsync: Boolean,
    val trackRating: Int,
    val numFavourite: Int = 0,
    val updatedTime: String = "",
    val genres: List<String> = emptyList(),
    val explicit: Boolean = false,
    val albumCoverUrl: String? = null
)

internal data class TrackSignature(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int
)

internal data class MusixmatchArtistMetadata(
    val name: String,
    val country: String,
    val twitterUrl: String,
    val rating: Int,
    val imageUrl: String?,
    val description: String? = null
)
