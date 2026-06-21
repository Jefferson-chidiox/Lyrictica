package com.oss.euphoriae.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_play_events",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["playedAt"])
    ]
)
data class SongPlayEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis()
)
