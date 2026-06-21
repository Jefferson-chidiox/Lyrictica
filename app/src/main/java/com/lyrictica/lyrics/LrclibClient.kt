package com.lyrictica.lyrics

import android.util.Log
import com.oss.euphoriae.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal class MusixmatchClient(
    private val baseUrl: String = "${BuildConfig.WORKER_BASE_URL}/musixmatch",
    private val userAgent: String = "Lyrictica/1.0 (https://example.com)"
) {

    companion object {
        private const val TAG = "MusixmatchClient"
    }

    fun findLyrics(
        trackName: String,
        artistName: String? = null,
        albumName: String? = null,
        durationSec: Int? = null
    ): MusixmatchLyrics? {
        if (BuildConfig.WORKER_BASE_URL.isBlank()) {
            Log.w(TAG, "WORKER_BASE_URL is blank; skipping lyrics lookup")
            return null
        }

        val normalizedTitle = LyricsQueryNormalizer.searchTitle(trackName)
        val normalizedArtist = LyricsQueryNormalizer.text(artistName)?.takeUnless { it.equals("audius", ignoreCase = true) }
        val normalizedAlbum = LyricsQueryNormalizer.text(albumName)?.takeUnless { it.equals("audius", ignoreCase = true) }
        val meta = TrackMetadataExtractor.Metadata(
            title = normalizedTitle,
            artist = normalizedArtist,
            album = normalizedAlbum,
            durationSec = durationSec
        )

        var plainCandidate: MusixmatchLyrics? = null

        fun rememberPlain(candidate: MusixmatchLyrics?) {
            if (plainCandidate == null && candidate != null && !candidate.plainLyrics.isNullOrBlank()) {
                plainCandidate = candidate
            }
        }

        var subtitleCandidate: MusixmatchLyrics? = null

        if (normalizedArtist != null) {
            subtitleCandidate = matchSubtitle(normalizedTitle, normalizedArtist, durationSec)
            rememberPlain(matchLyrics(normalizedTitle, normalizedArtist))
        }

        val ranked = MusixmatchSearchRanker.rank(
            meta,
            search(normalizedTitle, normalizedArtist, normalizedAlbum)
        )

        for (track in ranked) {
            if (track.hasRichsync) {
                getRichsync(track)?.let { return it }
            }
            if (track.hasSubtitles) {
                getSubtitle(track)?.let { return it }
            }
            if (track.hasLyrics) {
                rememberPlain(getLyrics(track))
            }
        }

        return subtitleCandidate ?: plainCandidate
    }

    fun search(
        trackName: String,
        artistName: String? = null,
        albumName: String? = null
    ): List<MusixmatchTrackRecord> {
        if (BuildConfig.WORKER_BASE_URL.isBlank()) {
            Log.w(TAG, "WORKER_BASE_URL is blank; skipping track search")
            return emptyList()
        }

        val params = linkedMapOf<String, String>()
        params["q_track"] = trackName
        LyricsQueryNormalizer.text(artistName)?.let { params["q_artist"] = it }
        LyricsQueryNormalizer.text(albumName)?.let { params["q_album"] = it }
        params["page_size"] = "10"
        params["page"] = "1"
        params["s_track_rating"] = "desc"

        val body = apiBodyOrNull("track.search", params) ?: return emptyList()
        val tracks = body.optJSONArray("track_list") ?: return emptyList()

        return buildList(tracks.length()) {
            for (i in 0 until tracks.length()) {
                val track = tracks.optJSONObject(i)?.optJSONObject("track") ?: continue
                add(parseSearchRecord(track))
            }
        }
    }

    fun getArtistMetadata(artistName: String): MusixmatchArtistMetadata? {
        if (BuildConfig.WORKER_BASE_URL.isBlank()) {
            Log.w(TAG, "WORKER_BASE_URL is blank; skipping artist metadata fetch")
            return null
        }

        val normalizedArtist = LyricsQueryNormalizer.text(artistName) ?: return null

        // 1. Fetch artist details
        val artistParams = linkedMapOf(
            "q_artist" to normalizedArtist,
            "page_size" to "1"
        )
        val artistBody = apiBodyOrNull("artist.search", artistParams) ?: return null
        val artistList = artistBody.optJSONArray("artist_list")
        if (artistList == null || artistList.length() == 0) return null

        val artistObj = artistList.optJSONObject(0)?.optJSONObject("artist") ?: return null
        val artistId = artistObj.optInt("artist_id")
        val name = artistObj.optString("artist_name")
        val country = artistObj.optString("artist_country")
        val twitterUrl = artistObj.optString("artist_twitter_url")
        val rating = artistObj.optInt("artist_rating", 0)

        // 2. Fetch artist's top track to get an album cover image
        val trackParams = linkedMapOf(
            "f_artist_id" to artistId.toString(),
            "page_size" to "1",
            "s_track_rating" to "desc"
        )
        val trackBody = apiBodyOrNull("track.search", trackParams)
        var coverUrl: String? = null
        if (trackBody != null) {
            val trackList = trackBody.optJSONArray("track_list")
            if (trackList != null && trackList.length() > 0) {
                val trackObj = trackList.optJSONObject(0)?.optJSONObject("track")
                coverUrl = trackObj?.optString("album_coverart_800x800")
                    ?.takeIf { it.isNotBlank() }
                    ?: trackObj?.optString("album_coverart_500x500")
                        ?.takeIf { it.isNotBlank() }
                    ?: trackObj?.optString("album_coverart_350x350")
                        ?.takeIf { it.isNotBlank() }
                    ?: trackObj?.optString("album_coverart_100x100")
            }
        }

        return MusixmatchArtistMetadata(
            name = name.takeIf { it.isNotBlank() } ?: normalizedArtist,
            country = country,
            twitterUrl = twitterUrl,
            rating = rating,
            imageUrl = coverUrl
        )
    }

    private fun getSubtitle(track: MusixmatchTrackRecord): MusixmatchLyrics? {
        val body = apiBodyOrNull(
            "track.subtitle.get",
            mapOf(
                "track_id" to track.trackId.toString(),
                "subtitle_format" to "lrc"
            )
        ) ?: return null

        val subtitle = body.optJSONObject("subtitle") ?: return null
        val parsed = MusixmatchLyricsParsers.parseSubtitleBody(subtitle.optString("subtitle_body")) ?: return null
        return parsed.toCacheLyrics(
            trackId = track.trackId,
            trackName = track.trackName,
            artistName = track.artistName,
            albumName = track.albumName,
            durationSec = track.durationSec,
            instrumental = track.instrumental
        )
    }

    private fun getRichsync(track: MusixmatchTrackRecord): MusixmatchLyrics? {
        val body = apiBodyOrNull(
            "track.richsync.get",
            mapOf("track_id" to track.trackId.toString())
        ) ?: return null

        val richsync = body.optJSONObject("richsync") ?: return null
        val richsyncBody = richsync.optString("richsync_body")
        val parsed = MusixmatchLyricsParsers.parseRichsyncBody(richsyncBody) ?: return null
        return parsed.toCacheLyrics(
            trackId = track.trackId,
            trackName = track.trackName,
            artistName = track.artistName,
            albumName = track.albumName,
            durationSec = track.durationSec,
            instrumental = track.instrumental,
            richSyncBody = richsyncBody
        )
    }

    private fun getLyrics(track: MusixmatchTrackRecord): MusixmatchLyrics? {
        val body = apiBodyOrNull(
            "track.lyrics.get",
            mapOf("track_id" to track.trackId.toString())
        ) ?: return null

        val lyrics = body.optJSONObject("lyrics") ?: return null
        val parsed = MusixmatchLyricsParsers.parseLyricsBody(lyrics.optString("lyrics_body")) ?: return null
        return parsed.toCacheLyrics(
            trackId = track.trackId,
            trackName = track.trackName,
            artistName = track.artistName,
            albumName = track.albumName,
            durationSec = track.durationSec,
            instrumental = track.instrumental
        )
    }

    private fun matchSubtitle(
        trackName: String,
        artistName: String,
        durationSec: Int?
    ): MusixmatchLyrics? {
        val params = linkedMapOf(
            "q_track" to trackName,
            "q_artist" to artistName
        )
        durationSec?.takeIf { it > 0 }?.let {
            params["f_subtitle_length"] = it.toString()
            params["f_subtitle_length_max_deviation"] = subtitleDeviation(it).toString()
        }

        val body = apiBodyOrNull("matcher.subtitle.get", params) ?: return null
        val subtitle = body.optJSONObject("subtitle") ?: return null
        val parsed = MusixmatchLyricsParsers.parseSubtitleBody(subtitle.optString("subtitle_body")) ?: return null
        return parsed.toCacheLyrics(
            trackName = trackName,
            artistName = artistName,
            albumName = null,
            durationSec = durationSec
        )
    }

    private fun matchLyrics(trackName: String, artistName: String): MusixmatchLyrics? {
        val body = apiBodyOrNull(
            "matcher.lyrics.get",
            mapOf(
                "q_track" to trackName,
                "q_artist" to artistName
            )
        ) ?: return null

        val lyrics = body.optJSONObject("lyrics") ?: return null
        val parsed = MusixmatchLyricsParsers.parseLyricsBody(lyrics.optString("lyrics_body")) ?: return null
        return parsed.toCacheLyrics(
            trackName = trackName,
            artistName = artistName,
            albumName = null,
            durationSec = null
        )
    }

    private fun subtitleDeviation(durationSec: Int): Int {
        return when {
            durationSec < 120 -> 2
            durationSec < 240 -> 4
            durationSec < 480 -> 6
            else -> 8
        }
    }

    private fun apiBodyOrNull(method: String, params: Map<String, String>): JSONObject? {
        val url = buildUrl(method, params)
        val (httpStatus, body) = httpGet(url)
        if (httpStatus !in 200..299) {
            Log.w(TAG, "Musixmatch HTTP $httpStatus for $method")
            return null
        }

        return runCatching {
            val root = JSONObject(body)
            val message = root.optJSONObject("message") ?: return@runCatching null
            val header = message.optJSONObject("header")
            val statusCode = header?.optInt("status_code", httpStatus) ?: httpStatus
            if (statusCode != 200) {
                return@runCatching null
            }
            message.optJSONObject("body")
        }.getOrElse { error ->
            Log.w(TAG, "Musixmatch parse error for $method", error)
            null
        }
    }

    private fun buildUrl(method: String, params: Map<String, String>): String {
        val merged = linkedMapOf<String, String>(
            "format" to "json"
        )
        merged.putAll(params)

        val query = merged.entries.joinToString("&") { (key, value) ->
            "${enc(key)}=${enc(value)}"
        }
        return "$baseUrl/$method?$query"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun httpGet(urlStr: String): Pair<Int, String> {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", userAgent)
        }

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use {
                BufferedReader(InputStreamReader(it)).readText()
            } ?: ""
            code to body
        } finally {
            conn.disconnect()
        }
    }

    private fun parseSearchRecord(obj: JSONObject): MusixmatchTrackRecord {
        return MusixmatchTrackRecord(
            trackId = obj.optLong("track_id"),
            trackName = obj.optString("track_name"),
            artistName = obj.optString("artist_name"),
            albumName = obj.optString("album_name"),
            durationSec = obj.optInt("track_length"),
            instrumental = flag(obj, "instrumental"),
            hasLyrics = flag(obj, "has_lyrics"),
            hasSubtitles = flag(obj, "has_subtitles"),
            hasRichsync = flag(obj, "has_richsync"),
            trackRating = obj.optInt("track_rating")
        )
    }

    private fun flag(obj: JSONObject, key: String): Boolean {
        return when {
            !obj.has(key) -> false
            obj.optInt(key, Int.MIN_VALUE) != Int.MIN_VALUE -> obj.optInt(key) == 1
            else -> obj.optBoolean(key, false)
        }
    }
}
