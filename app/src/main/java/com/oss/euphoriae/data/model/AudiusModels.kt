package com.oss.euphoriae.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Audius Track API Response ────────────────────────────────────────────────

@Serializable
data class AudiusTrackResponse(
    val data: List<AudiusTrack> = emptyList()
)

@Serializable
data class AudiusTrack(
    val id: String,
    val title: String,
    val genre: String = "",
    val duration: Int = 0,
    @SerialName("play_count")
    val playCount: Int = 0,
    @SerialName("favorite_count")
    val favoriteCount: Int = 0,
    @SerialName("repost_count")
    val repostCount: Int = 0,
    val mood: String? = null,
    val tags: String? = null,
    val permalink: String? = null,
    val artwork: AudiusArtwork? = null,
    val user: AudiusUser? = null,
    val stream: AudiusStream? = null,
    val description: String? = null
)

@Serializable
data class AudiusArtwork(
    @SerialName("150x150")
    val small: String? = null,
    @SerialName("480x480")
    val medium: String? = null,
    @SerialName("1000x1000")
    val large: String? = null
)

@Serializable
data class AudiusUser(
    val id: String = "",
    val name: String = "",
    val handle: String = "",
    @SerialName("follower_count")
    val followerCount: Int = 0,
    @SerialName("is_verified")
    val isVerified: Boolean = false,
    @SerialName("profile_picture")
    val profilePicture: AudiusArtwork? = null
)

@Serializable
data class AudiusStream(
    val url: String? = null,
    val mirrors: List<String> = emptyList()
)

// ── Audius Playlist API Response ─────────────────────────────────────────────

@Serializable
data class AudiusPlaylistResponse(
    val data: List<AudiusPlaylist> = emptyList()
)

@Serializable
data class AudiusPlaylist(
    val id: String,
    @SerialName("playlist_name")
    val playlistName: String = "",
    val description: String? = null,
    @SerialName("repost_count")
    val repostCount: Int = 0,
    @SerialName("favorite_count")
    val favoriteCount: Int = 0,
    @SerialName("total_play_count")
    val totalPlayCount: Int = 0,
    val artwork: AudiusArtwork? = null,
    val user: AudiusUser? = null,
    val permalink: String? = null,
    @SerialName("track_count")
    val trackCount: Int = 0
)

// ── Room Cache Entity ────────────────────────────────────────────────────────

@Entity(tableName = "cached_audius_shelves")
data class CachedAudiusShelf(
    @PrimaryKey
    val shelfId: String,
    val shelfJson: String,
    val fetchedAt: Long = System.currentTimeMillis()
)
