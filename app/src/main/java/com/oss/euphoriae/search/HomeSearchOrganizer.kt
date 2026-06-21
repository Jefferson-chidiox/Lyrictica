package com.oss.euphoriae.search

import com.lyrictica.lyrics.MusixmatchTrackRecord
import com.oss.euphoriae.data.model.OnlineTrack
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.data.model.toSong

internal object HomeSearchOrganizer {

    fun rankLocalSongs(
        query: String,
        songs: List<Song>,
        limit: Int
    ): List<Song> {
        return songs
            .asSequence()
            .map { song -> song to scoreSearchMatch(query, song.title, song.artist, song.album) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<Song, Int>> { it.second }
                    .thenBy { it.first.title.lowercase() }
                    .thenBy { it.first.artist.lowercase() }
            )
            .map { it.first }
            .distinctBy { song -> song.data.ifBlank { "${song.title}|${song.artist}|${song.album}" } }
            .take(limit)
            .toList()
    }

    fun rankOnlineTracks(
        query: String,
        tracks: List<OnlineTrack>,
        limit: Int
    ): List<OnlineTrack> {
        return tracks
            .asSequence()
            .filter { it.isPlayable }
            .map { track -> track to scoreSearchMatch(query, track.title, track.artist, track.genre) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<OnlineTrack, Int>> { it.second }
                    .thenBy { it.first.title.lowercase() }
                    .thenBy { it.first.displayArtist.lowercase() }
            )
            .map { it.first }
            .distinctBy { track -> track.streamUrl.ifBlank { "${track.provider}:${track.id}:${track.title}" } }
            .take(limit)
            .toList()
    }

    fun buildMusixmatchResults(
        query: String,
        rawResults: List<MusixmatchTrackRecord>,
        localSongs: List<Song>,
        audiusTracks: List<OnlineTrack>,
        ncsTracks: List<OnlineTrack>,
        limit: Int
    ): List<MusixmatchSearchResult> {
        return rawResults
            .asSequence()
            .filter { record -> !record.instrumental && (record.hasLyrics || record.hasSubtitles || record.hasRichsync) }
            .map { record -> record to scoreSearchMatch(query, record.trackName, record.artistName, record.albumName) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<MusixmatchTrackRecord, Int>> { it.second }
                    .thenByDescending { it.first.hasRichsync }
                    .thenByDescending { it.first.hasSubtitles }
                    .thenByDescending { it.first.trackRating }
            )
            .map { it.first }
            .distinctBy { record -> "${normalizeSearchText(record.trackName)}|${normalizeSearchText(record.artistName)}" }
            .take(limit)
            .map { record ->
                val localMatch = bestSongMatch(record.trackName, record.artistName, localSongs)
                val sources = buildList {
                    bestTrackMatch(record.trackName, record.artistName, audiusTracks)?.let { track ->
                        add(SearchAvailability(platform = SearchAvailabilityPlatform.AUDIUS, song = track.toSong()))
                    }
                    bestTrackMatch(record.trackName, record.artistName, ncsTracks)?.let { track ->
                        add(SearchAvailability(platform = SearchAvailabilityPlatform.NCS, song = track.toSong()))
                    }
                }
                MusixmatchSearchResult(
                    trackId = record.trackId,
                    title = record.trackName,
                    artist = record.artistName,
                    album = record.albumName,
                    durationMs = record.durationSec.coerceAtLeast(0).toLong() * 1000L,
                    hasSyncedLyrics = record.hasRichsync || record.hasSubtitles,
                    localMatch = localMatch,
                    availableSources = sources,
                    artworkUri = localMatch?.albumArtUri ?: sources.firstOrNull()?.song?.albumArtUri
                )
            }
            .toList()
    }

    private fun bestSongMatch(
        title: String,
        artist: String,
        songs: List<Song>
    ): Song? {
        val match = songs
            .map { song -> song to scoreTrackIdentity(title, artist, song.title, song.artist) }
            .maxByOrNull { it.second }
            ?: return null

        return match.first.takeIf {
            isStrongTrackMatch(match.second) && hasArtistAgreement(artist, it.artist)
        }
    }

    private fun bestTrackMatch(
        title: String,
        artist: String,
        tracks: List<OnlineTrack>
    ): OnlineTrack? {
        val match = tracks
            .map { track -> track to scoreTrackIdentity(title, artist, track.title, track.artist) }
            .maxByOrNull { it.second }
            ?: return null

        return match.first.takeIf {
            isStrongTrackMatch(match.second) && hasArtistAgreement(artist, it.artist)
        }
    }

    private fun hasArtistAgreement(expectedArtist: String?, candidateArtist: String?): Boolean {
        val normalizedExpected = normalizeSearchText(expectedArtist)
        val normalizedCandidate = normalizeSearchText(candidateArtist)
        if (normalizedExpected.isBlank() || normalizedCandidate.isBlank()) return true
        if (normalizedExpected == normalizedCandidate) return true
        if (normalizedExpected.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedExpected)) return true
        return normalizedExpected.split(' ').toSet().intersect(normalizedCandidate.split(' ').toSet()).isNotEmpty()
    }
}
