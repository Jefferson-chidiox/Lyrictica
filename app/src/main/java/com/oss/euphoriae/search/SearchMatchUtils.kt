package com.oss.euphoriae.search

import com.lyrictica.lyrics.LyricsQueryNormalizer

internal fun normalizeSearchText(value: String?): String {
    return LyricsQueryNormalizer.searchTitle(value.orEmpty())
        .lowercase()
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

private fun tokenSet(value: String?): Set<String> = normalizeSearchText(value)
    .split(' ')
    .mapNotNull { token -> token.takeIf { it.isNotBlank() } }
    .toSet()

internal fun scoreSearchMatch(
    query: String,
    title: String,
    artist: String? = null,
    album: String? = null
): Int {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank()) return 0

    val normalizedTitle = normalizeSearchText(title)
    val normalizedArtist = normalizeSearchText(artist)
    val normalizedAlbum = normalizeSearchText(album)
    val combined = listOf(normalizedTitle, normalizedArtist, normalizedAlbum)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    var score = 0
    when {
        normalizedTitle == normalizedQuery -> score += 140
        normalizedTitle.startsWith(normalizedQuery) -> score += 110
        normalizedTitle.contains(normalizedQuery) -> score += 90
    }

    when {
        normalizedArtist == normalizedQuery -> score += 80
        normalizedArtist.contains(normalizedQuery) -> score += 55
    }

    if (normalizedAlbum.contains(normalizedQuery)) {
        score += 35
    }

    if (combined.contains(normalizedQuery)) {
        score += 24
    }

    val queryTokens = tokenSet(normalizedQuery)
    val titleTokens = tokenSet(normalizedTitle)
    val artistTokens = tokenSet(normalizedArtist)
    val albumTokens = tokenSet(normalizedAlbum)

    score += queryTokens.intersect(titleTokens).size * 16
    score += queryTokens.intersect(artistTokens).size * 10
    score += queryTokens.intersect(albumTokens).size * 6

    if (score == 0) {
        val fallbackHits = queryTokens.count { token ->
            token.length > 2 && combined.contains(token)
        }
        score += fallbackHits * 8
    }

    return score
}

internal fun scoreTrackIdentity(
    titleA: String,
    artistA: String?,
    titleB: String,
    artistB: String?
): Int {
    val normalizedTitleA = normalizeSearchText(titleA)
    val normalizedTitleB = normalizeSearchText(titleB)
    val normalizedArtistA = normalizeSearchText(artistA)
    val normalizedArtistB = normalizeSearchText(artistB)

    if (normalizedTitleA.isBlank() || normalizedTitleB.isBlank()) return 0

    var score = 0
    when {
        normalizedTitleA == normalizedTitleB -> score += 120
        normalizedTitleA.contains(normalizedTitleB) || normalizedTitleB.contains(normalizedTitleA) -> score += 82
        else -> score += tokenSet(normalizedTitleA).intersect(tokenSet(normalizedTitleB)).size * 18
    }

    when {
        normalizedArtistA.isNotBlank() && normalizedArtistA == normalizedArtistB -> score += 80
        normalizedArtistA.isNotBlank() && normalizedArtistB.isNotBlank() &&
            (normalizedArtistA.contains(normalizedArtistB) || normalizedArtistB.contains(normalizedArtistA)) -> score += 52
        else -> score += tokenSet(normalizedArtistA).intersect(tokenSet(normalizedArtistB)).size * 12
    }

    return score
}

internal fun isStrongTrackMatch(score: Int): Boolean = score >= 120
