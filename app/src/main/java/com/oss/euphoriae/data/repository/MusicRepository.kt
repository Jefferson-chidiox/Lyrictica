package com.oss.euphoriae.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.oss.euphoriae.data.local.MusicDao
import com.oss.euphoriae.data.model.FavoriteSong
import com.oss.euphoriae.data.model.GameScoreRecord
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.PlaylistSong
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.data.model.SongPlayEvent
import com.oss.euphoriae.data.remote.MusixmatchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val HOME_SECTION_LIMIT = 8

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {

    private val musixmatchService = MusixmatchService()

    fun getAllSongs(): Flow<List<Song>> = musicDao.getAllSongs()

    fun getRecentlyAddedSongs(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>> =
        musicDao.getRecentlyAddedSongs(limit)

    fun getMostPlayedThisWeek(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>> =
        musicDao.getMostPlayedSince(nowMinusDays(7), limit)

    fun getMostPlayedThisMonth(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>> =
        musicDao.getMostPlayedSince(nowMinusDays(30), limit)

    fun getMostPlayedAllTime(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>> =
        musicDao.getMostPlayedAllTime(limit)

    fun getTopGameScores(limit: Int = 3): Flow<List<GameScoreRecord>> =
        musicDao.getTopGameScores(limit)

    fun getFavoriteSongs(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>> =
        musicDao.getFavoriteSongs(limit)

    fun getNotPlayedSongs(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>> =
        musicDao.getNotPlayedSongs(limit)

    fun getSongsFromPlaylists(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>> =
        musicDao.getSongsFromPlaylists(limit)

    suspend fun getTopGenres(limit: Int = 3): List<String> = withContext(Dispatchers.IO) {
        musicDao.getTopGenres(limit)
    }

    fun searchSongs(query: String): Flow<List<Song>> = musicDao.searchSongs(query)

    suspend fun getSongById(songId: Long): Song? = musicDao.getSongById(songId)

    suspend fun insertSong(song: Song): Long = musicDao.insertSong(song)

    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        if (song.id <= 0L) return@withContext

        runCatching {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.id
            )
            context.contentResolver.delete(uri, null, null)
        }

        musicDao.deleteFavoriteSong(song.id)
        musicDao.deleteSongPlayEvents(song.id)
        musicDao.removeSongFromAllPlaylists(song.id)
        musicDao.deleteSong(song)
    }

    fun getAllPlaylists(): Flow<List<Playlist>> {
        return combine(
            musicDao.getAllPlaylists(),
            musicDao.getAllPlaylistItems()
        ) { playlists, items ->
            playlists.map { playlist ->
                val playlistItems = items.filter { it.playlistId == playlist.id }
                val songCount = playlistItems.size
                val covers = playlistItems
                    .mapNotNull { it.albumArtUri }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(4)

                Playlist(
                    id = playlist.id,
                    name = playlist.name,
                    coverUri = covers.firstOrNull(),
                    createdAt = playlist.createdAt,
                    songCount = songCount,
                    covers = covers
                )
            }
        }
    }

    suspend fun getPlaylistById(playlistId: Long): Playlist? = musicDao.getPlaylistById(playlistId)

    suspend fun createPlaylist(name: String): Long {
        val playlist = Playlist(name = name)
        return musicDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: Playlist) = musicDao.deletePlaylist(playlist)

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val playlistSong = PlaylistSong(playlistId = playlistId, songId = songId)
        musicDao.addSongToPlaylist(playlistSong)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        musicDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> = musicDao.getSongsInPlaylist(playlistId)

    fun getAlbums(): Flow<List<com.oss.euphoriae.data.model.Album>> {
        return musicDao.getAllSongs().map { songs ->
            songs.groupBy { it.album }
                .map { (albumName, albumSongs) ->
                    val distinctCovers = albumSongs
                        .mapNotNull { it.albumArtUri }
                        .filter { it.isNotEmpty() }
                        .distinct()

                    val representativeSong = albumSongs.first()
                    val artists = albumSongs.map { it.artist }.distinct()
                    val primaryArtist = if (artists.size > 1) "Various Artists" else representativeSong.artist

                    com.oss.euphoriae.data.model.Album(
                        id = representativeSong.albumId,
                        name = albumName,
                        artist = primaryArtist,
                        coverUri = distinctCovers.firstOrNull(),
                        covers = distinctCovers.take(4),
                        songCount = albumSongs.size
                    )
                }
                .sortedBy { it.name }
        }
    }

    suspend fun getSongsByAlbumId(albumId: Long): List<Song> = musicDao.getSongsByAlbumId(albumId)

    suspend fun getSongsByAlbumName(albumName: String): List<Song> = musicDao.getSongsByAlbumName(albumName)

    suspend fun setFavorite(songId: Long, favorite: Boolean) {
        if (songId <= 0L) return
        if (favorite) {
            musicDao.insertFavoriteSong(FavoriteSong(songId = songId))
        } else {
            musicDao.deleteFavoriteSong(songId)
        }
    }

    suspend fun recordSongPlay(songId: Long) {
        if (songId <= 0L) return
        musicDao.insertSongPlayEvent(SongPlayEvent(songId = songId))
    }

    suspend fun recordGameScore(song: Song, mode: String, score: Int) {
        if (score <= 0) return
        musicDao.insertGameScore(
            GameScoreRecord(
                songId = song.id,
                songTitle = song.title,
                songArtist = song.artist,
                songAlbum = song.album,
                songArtUri = song.albumArtUri,
                mode = mode,
                score = score
            )
        )
    }

    suspend fun isFavorite(songId: Long): Boolean = musicDao.isFavorite(songId)

    suspend fun scanAndImportMusic(): Int = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        // Build a genre lookup map: audioId -> genre name
        val genreMap = buildGenreMap()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)
                val data = cursor.getString(dataColumn) ?: ""
                val mimeType = cursor.getString(mimeTypeColumn)
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000L
                val dateModified = cursor.getLong(dateModifiedColumn) * 1000L
                val genre = genreMap[id] ?: ""

                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                val song = Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = albumId,
                    duration = duration,
                    data = data,
                    albumArtUri = albumArtUri,
                    mimeType = mimeType,
                    dateAdded = dateAdded,
                    dateModified = dateModified,
                    genre = genre
                )
                songs.add(song)
            }
        }

        if (songs.isNotEmpty()) {
            musicDao.deleteAllSongs()
            musicDao.insertSongs(songs)
        }

        songs.size
    }

    suspend fun refreshLibrary(): Int = scanAndImportMusic()

    suspend fun scanAndImportMusicFromFolder(folderUri: Uri): Int = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        
        try {
            context.contentResolver.takePersistableUriPermission(
                folderUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            android.util.Log.w("MusicRepository", "Failed to take persistable permission", e)
        }

        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
        if (tree == null || !tree.canRead()) return@withContext 0

        val retriever = android.media.MediaMetadataRetriever()

        // Try to resolve the real file path from a SAF document URI.
        // This allows us to look up the song in MediaStore to get its real ID.
        fun resolveFilePath(docUri: Uri): String? {
            // SAF document URIs from external storage use the "document" authority
            // and encode the path as e.g. "primary:Music/song.mp3"
            if (docUri.authority == "com.android.externalstorage.documents") {
                val docId = try {
                    android.provider.DocumentsContract.getDocumentId(docUri)
                } catch (e: Exception) { null }
                if (docId != null) {
                    val split = docId.split(":", limit = 2)
                    if (split.size == 2) {
                        val storageType = split[0]
                        val relativePath = split[1]
                        if (storageType.equals("primary", ignoreCase = true)) {
                            return android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
                        }
                        // Try SD card or other volumes
                        val externalDirs = context.getExternalFilesDirs(null)
                        for (dir in externalDirs) {
                            if (dir == null) continue
                            val path = dir.absolutePath
                            // path is like /storage/<volume-id>/Android/data/<pkg>/files
                            val volumeRoot = path.substringBefore("/Android/")
                            if (volumeRoot.contains(storageType, ignoreCase = true)) {
                                return "$volumeRoot/$relativePath"
                            }
                        }
                    }
                }
            }
            return null
        }

        // Look up a file path in MediaStore to get its real MediaStore ID and album art URI
        fun lookupMediaStoreId(filePath: String): Pair<Long, String?>? {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()
                    return id to albumArtUri
                }
            }
            return null
        }
        
        fun traverse(dir: androidx.documentfile.provider.DocumentFile) {
            val files = dir.listFiles()
            for (file in files) {
                if (file.isDirectory) {
                    traverse(file)
                } else {
                    val mimeType = file.type
                    if (mimeType != null && mimeType.startsWith("audio/")) {
                        try {
                            retriever.setDataSource(context, file.uri)
                            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name ?: "Unknown"
                            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            val duration = durationStr?.toLongOrNull() ?: 0L
                            
                            // Try to resolve the real file path and look up MediaStore ID
                            val filePath = resolveFilePath(file.uri)
                            val mediaStoreResult = filePath?.let { lookupMediaStoreId(it) }
                            
                            val songId = mediaStoreResult?.first ?: 0L
                            val songData = filePath ?: file.uri.toString()
                            
                            val albumIdFromHash = (album + artist).hashCode().toLong()
                            
                            var albumArtUriStr: String? = mediaStoreResult?.second
                            if (albumArtUriStr == null) {
                                val picture = retriever.embeddedPicture
                                if (picture != null) {
                                    try {
                                        val cacheDir = java.io.File(context.cacheDir, "album_art")
                                        if (!cacheDir.exists()) cacheDir.mkdirs()
                                        val artFile = java.io.File(cacheDir, "art_$albumIdFromHash.jpg")
                                        if (!artFile.exists()) {
                                            java.io.FileOutputStream(artFile).use { fos ->
                                                fos.write(picture)
                                            }
                                        }
                                        albumArtUriStr = Uri.fromFile(artFile).toString()
                                    } catch (e: Exception) {
                                        android.util.Log.w("MusicRepository", "Failed to save album art", e)
                                    }
                                }
                            }
                            
                            val song = Song(
                                id = songId,
                                title = title,
                                artist = artist,
                                album = album,
                                albumId = albumIdFromHash,
                                duration = duration,
                                data = songData,
                                albumArtUri = albumArtUriStr,
                                mimeType = mimeType,
                                dateAdded = file.lastModified(),
                                dateModified = file.lastModified(),
                                genre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
                            )
                            songs.add(song)
                        } catch (e: Exception) {
                            android.util.Log.w("MusicRepository", "Failed to extract metadata for ${file.uri}", e)
                        }
                    }
                }
            }
        }
        
        try {
            traverse(tree)
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "Error traversing directory", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (songs.isNotEmpty()) {
            musicDao.deleteAllSongs()
            musicDao.insertSongs(songs)
        }

        songs.size
    }

    /**
     * Get lyrics for a song with fallback chain:
     * 1. Local LRC file (same directory as song)
     * 2. MediaStore LRC file
     * 3. Fetch from Musixmatch API
     */
    fun getLyrics(song: Song): Flow<com.oss.euphoriae.data.model.Lyrics?> = kotlinx.coroutines.flow.flow {
        val lyrics = withContext(Dispatchers.IO) {
            val lrcFile = com.oss.euphoriae.utils.LrcParser.findLrcFile(song.data)
            val lyricsFromFile = lrcFile?.let { com.oss.euphoriae.utils.LrcParser.parse(it) }

            if (lyricsFromFile != null) {
                android.util.Log.d("MusicRepository", "Lyrics found from local file")
                return@withContext lyricsFromFile
            }

            try {
                val songFileName = song.data.substringAfterLast('/')
                val lrcFileName = songFileName.substringBeforeLast('.') + ".lrc"

                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(lrcFileName)

                val queryUri = MediaStore.Files.getContentUri("external")

                context.contentResolver.query(
                    queryUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val id = cursor.getLong(idColumn)
                        val contentUri = ContentUris.withAppendedId(queryUri, id)

                        val mediaStoreLyrics = context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                            com.oss.euphoriae.utils.LrcParser.parse(inputStream)
                        }

                        if (mediaStoreLyrics != null) {
                            android.util.Log.d("MusicRepository", "Lyrics found from MediaStore")
                            return@withContext mediaStoreLyrics
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicRepository", "Failed to load lyrics from MediaStore", e)
            }

            try {
                android.util.Log.d("MusicRepository", "Fetching lyrics from Musixmatch: ${song.title} - ${song.artist}")

                val response = musixmatchService.searchLyrics(
                    trackName = song.title,
                    artistName = song.artist,
                    albumName = song.album,
                    duration = song.duration
                )

                if (response != null) {
                    android.util.Log.d("MusicRepository", "Lyrics fetched from Musixmatch")

                    response.syncedLyrics?.let { synced ->
                        com.oss.euphoriae.utils.LrcParser.parseString(synced)
                    }?.let { return@withContext it }

                    response.plainLyrics?.let { plain ->
                        com.oss.euphoriae.utils.LrcParser.parsePlainLyrics(plain)
                    }?.let { return@withContext it }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicRepository", "Failed to fetch lyrics from Musixmatch", e)
            }

            null
        }
        emit(lyrics)
    }

    /**
     * Build a map of audioId -> genre by iterating over all genres
     * in MediaStore.Audio.Genres.
     */
    private fun buildGenreMap(): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        try {
            val genreProjection = arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME
            )
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                genreProjection,
                null, null, null
            )?.use { genreCursor ->
                val genreIdCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val genreNameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genreCursor.moveToNext()) {
                    val genreId = genreCursor.getLong(genreIdCol)
                    val genreName = genreCursor.getString(genreNameCol)?.trim().orEmpty()
                    if (genreName.isBlank()) continue

                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    context.contentResolver.query(
                        membersUri,
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null
                    )?.use { membersCursor ->
                        val audioIdCol = membersCursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Genres.Members.AUDIO_ID
                        )
                        while (membersCursor.moveToNext()) {
                            val audioId = membersCursor.getLong(audioIdCol)
                            // First genre found wins (don't overwrite)
                            if (!map.containsKey(audioId)) {
                                map[audioId] = genreName
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicRepository", "Failed to build genre map", e)
        }
        return map
    }

    suspend fun getRelatedSongs(song: Song): List<Song> = withContext(Dispatchers.IO) {
        try {
            val allSongs = musicDao.getAllSongs().first()
            val otherSongs = allSongs.filter { it.id != song.id }

            val scoredSongs = otherSongs.map { other ->
                var score = 0
                if (song.artist.isNotBlank() && !song.artist.equals("Unknown Artist", ignoreCase = true) &&
                    other.artist.equals(song.artist, ignoreCase = true)) {
                    score += 3
                }
                if (song.genre.isNotBlank() && other.genre.isNotBlank() &&
                    other.genre.equals(song.genre, ignoreCase = true)) {
                    score += 2
                }
                if (song.album.isNotBlank() && !song.album.equals("Unknown Album", ignoreCase = true) &&
                    other.album.equals(song.album, ignoreCase = true)) {
                    score += 1
                }
                other to score
            }

            scoredSongs
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(20)
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "Failed to get related songs", e)
            emptyList()
        }
    }

    private fun nowMinusDays(days: Long): Long {
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
    }
}
