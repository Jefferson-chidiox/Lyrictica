package com.lyrictica.lyrics

import java.util.concurrent.ConcurrentHashMap

internal class LyricsCache {

    private val memory = ConcurrentHashMap<String, MusixmatchLyrics>()

    fun read(key: String): MusixmatchLyrics? = memory[key]

    fun write(key: String, lyrics: MusixmatchLyrics) {
        memory[key] = lyrics
    }

    fun keyFor(signature: TrackSignature): String {
        val raw = listOf(
            signature.trackName,
            signature.artistName,
            signature.albumName,
            signature.durationSec.toString()
        ).joinToString("|") { it.trim().lowercase() }
        return raw.hashCode().toString(16)
    }

    fun keyForLoose(title: String, durationSec: Int?): String {
        val raw = title.trim().lowercase() + "|" + (durationSec?.toString() ?: "")
        return raw.hashCode().toString(16)
    }
}
