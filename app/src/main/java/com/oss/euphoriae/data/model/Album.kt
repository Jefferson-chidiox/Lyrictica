package com.oss.euphoriae.data.model

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val coverUri: String? = null,
    val covers: List<String> = emptyList(),
    val songCount: Int = 0
)
