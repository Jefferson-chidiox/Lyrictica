package com.oss.euphoriae.utils

import com.oss.euphoriae.data.model.LyricLine
import com.oss.euphoriae.data.model.Lyrics
import java.io.File

object LrcParser {
    
    // Regex matches [mm:ss.xx] or [mm:ss.xxx]
    private val TIME_REGEX = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]")

    fun parse(file: File): Lyrics? {
        // Try simple read first, handle exception
        return try {
            if (!file.exists()) return null
            file.inputStream().use { parse(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parse(inputStream: java.io.InputStream): Lyrics? {
        val lyricLines = mutableListOf<LyricLine>()
        
        try {
            inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val matches = TIME_REGEX.findAll(line).toList()
                    if (matches.isNotEmpty()) {
                        val text = line.replace(TIME_REGEX, "").trim()
                        matches.forEach { matchResult ->
                            val (min, sec, msStr) = matchResult.destructured
                            // If 2 digits, it's centiseconds (x10 ms). If 3 digits, it's milliseconds.
                            val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                            val timestamp = (min.toLong() * 60 * 1000) + (sec.toLong() * 1000) + ms
                            
                            // We include empty lines if they have timestamps, as they might indicate instrumental breaks
                            lyricLines.add(LyricLine(text, timestamp))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        
        if (lyricLines.isEmpty()) return null
        
        return Lyrics(lyricLines.sortedBy { it.timestamp })
    }
    
    /**
     * Parse LRC format string (from Musixmatch synced lyrics)
     */
    fun parseString(lrcContent: String): Lyrics? {
        val lyricLines = mutableListOf<LyricLine>()
        
        try {
            lrcContent.lines().forEach { line ->
                val matches = TIME_REGEX.findAll(line).toList()
                if (matches.isNotEmpty()) {
                    val text = line.replace(TIME_REGEX, "").trim()
                    matches.forEach { matchResult ->
                        val (min, sec, msStr) = matchResult.destructured
                        val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                        val timestamp = (min.toLong() * 60 * 1000) + (sec.toLong() * 1000) + ms
                        lyricLines.add(LyricLine(text, timestamp))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        
        if (lyricLines.isEmpty()) return null
        
        return Lyrics(lyricLines.sortedBy { it.timestamp })
    }
    
    /**
     * Parse plain lyrics (non-synced) - creates lyrics with isSynced = false
     */
    fun parsePlainLyrics(plainLyrics: String): Lyrics? {
        val lines = plainLyrics.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { index, text ->
                // Assign fake timestamps (just for ordering)
                LyricLine(text.trim(), index * 5000L)
            }
        
        if (lines.isEmpty()) return null
        
        return Lyrics(lines, isSynced = false)
    }
    
    fun findLrcFile(songPath: String): File? {
        // Assume songPath is like /storage/emulated/0/Music/Song.mp3
        // We look for /storage/emulated/0/Music/Song.lrc
        val songFile = File(songPath)
        val lrcPath = songFile.extension.let { ext ->
            if (songPath.endsWith(ext, ignoreCase = true)) {
                songPath.substring(0, songPath.length - ext.length) + "lrc"
            } else {
                "$songPath.lrc" // Fallback
            }
        }
        
        val lrcFile = File(lrcPath)
        return if (lrcFile.exists()) lrcFile else null
    }
}

