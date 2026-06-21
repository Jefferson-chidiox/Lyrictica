package com.lyrictica.audio

import com.oss.euphoriae.data.model.Song

fun mergeOrRegisterQueue(existing: PlaybackQueue?, incoming: PlaybackQueue): PlaybackQueue {
    return when {
        existing == null -> incoming
        existing.isCustomOrder -> existing.mergePreservingOrder(incoming.songs).copy(source = incoming.source)
        else -> incoming
    }
}

fun replaceQueueSongs(queue: PlaybackQueue, songs: List<Song>): PlaybackQueue {
    return queue.copy(
        songs = songs,
        isCustomOrder = true,
        lastUsedAt = System.currentTimeMillis()
    )
}
