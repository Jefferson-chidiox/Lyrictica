package com.oss.euphoriae.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "game_scores",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["score"]),
        Index(value = ["achievedAt"])
    ]
)
data class GameScoreRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: Long,
    val songTitle: String,
    val songArtist: String,
    val songAlbum: String,
    val songArtUri: String? = null,
    val mode: String,
    val score: Int,
    val achievedAt: Long = System.currentTimeMillis()
) {
    val modeLabel: String
        get() = when (mode) {
            "KARAOKE" -> "Karaoke"
            "REVERSE_BEAT" -> "Reverse Beat"
            "ECHO_DROP" -> "Echo Drop"
            else -> mode.replace('_', ' ').lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
}

enum class GameRecommendationReason(val label: String) {
    MOST_PLAYED("Most played"),
    RECENTLY_ADDED("Recently added"),
    FROM_PLAYLISTS("Playlist pick")
}

data class GameSongRecommendation(
    val song: Song,
    val reasons: Set<GameRecommendationReason>
)
