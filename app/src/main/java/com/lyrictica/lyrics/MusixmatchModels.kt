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

internal data class MusixmatchTrackRecord(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val instrumental: Boolean,
    val hasLyrics: Boolean,
    val hasSubtitles: Boolean,
    val hasRichsync: Boolean,
    val trackRating: Int
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
    val imageUrl: String?
)
