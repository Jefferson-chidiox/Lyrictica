package com.oss.euphoriae.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_songs")
data class FavoriteSong(
    @PrimaryKey
    val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
