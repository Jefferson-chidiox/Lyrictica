package com.oss.euphoriae.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oss.euphoriae.data.model.CachedLyrics
import com.oss.euphoriae.data.model.CachedAudiusShelf
import com.oss.euphoriae.data.model.FavoriteSong
import com.oss.euphoriae.data.model.GameScoreRecord
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.PlaylistSong
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.data.model.SongPlayEvent
import kotlinx.coroutines.flow.Flow

private const val HOME_SECTION_LIMIT = 8

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC, id DESC LIMIT :limit")
    fun getRecentlyAddedSongs(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT songId, COUNT(*) AS playCount, MAX(playedAt) AS lastPlayedAt
            FROM song_play_events
            WHERE playedAt >= :sinceMs
            GROUP BY songId
        ) stats ON stats.songId = s.id
        ORDER BY stats.playCount DESC, stats.lastPlayedAt DESC, s.dateAdded DESC
        LIMIT :limit
        """
    )
    fun getMostPlayedSince(sinceMs: Long, limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT songId, COUNT(*) AS playCount, MAX(playedAt) AS lastPlayedAt
            FROM song_play_events
            GROUP BY songId
        ) stats ON stats.songId = s.id
        ORDER BY stats.playCount DESC, stats.lastPlayedAt DESC, s.dateAdded DESC
        LIMIT :limit
        """
    )
    fun getMostPlayedAllTime(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN favorite_songs f ON f.songId = s.id
        ORDER BY f.addedAt DESC, s.dateAdded DESC
        LIMIT :limit
        """
    )
    fun getFavoriteSongs(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>>

    @Query(
        """
        SELECT s.* FROM songs s
        LEFT JOIN (
            SELECT DISTINCT songId FROM song_play_events
        ) played ON played.songId = s.id
        WHERE played.songId IS NULL
        ORDER BY s.dateAdded DESC, s.id DESC
        LIMIT :limit
        """
    )
    fun getNotPlayedSongs(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY title ASC")
    suspend fun getSongsByAlbumId(albumId: Long): List<Song>

    @Query("SELECT * FROM songs WHERE album = :albumName ORDER BY title ASC")
    suspend fun getSongsByAlbumName(albumName: String): List<Song>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: Long): Song?

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("DELETE FROM songs WHERE album != 'Audius' AND data NOT LIKE 'http://%' AND data NOT LIKE 'https://%'")
    suspend fun deleteAllSongs()

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(playlistSong: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    suspend fun removeSongFromAllPlaylists(songId: Long)

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.addedAt DESC")
    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("SELECT ps.playlistId, s.albumArtUri FROM playlist_songs ps JOIN songs s ON ps.songId = s.id")
    fun getAllPlaylistItems(): Flow<List<PlaylistItem>>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON ps.songId = s.id
        GROUP BY s.id
        ORDER BY MAX(ps.addedAt) DESC, s.dateAdded DESC
        LIMIT :limit
        """
    )
    fun getSongsFromPlaylists(limit: Int = HOME_SECTION_LIMIT): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteSong(favoriteSong: FavoriteSong)

    @Query("DELETE FROM favorite_songs WHERE songId = :songId")
    suspend fun deleteFavoriteSong(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Insert
    suspend fun insertSongPlayEvent(event: SongPlayEvent)

    @Query("DELETE FROM song_play_events WHERE songId = :songId")
    suspend fun deleteSongPlayEvents(songId: Long)

    @Query("SELECT * FROM game_scores ORDER BY score DESC, achievedAt DESC LIMIT :limit")
    fun getTopGameScores(limit: Int = 3): Flow<List<GameScoreRecord>>

    @Insert
    suspend fun insertGameScore(score: GameScoreRecord)

    // Cached Lyrics
    @Query("SELECT * FROM cached_lyrics WHERE songId = :songId")
    suspend fun getCachedLyrics(songId: Long): CachedLyrics?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedLyrics(lyrics: CachedLyrics)

    @Query("DELETE FROM cached_lyrics WHERE songId = :songId")
    suspend fun deleteCachedLyrics(songId: Long)

    // ── Audius Shelf Cache ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedShelf(shelf: CachedAudiusShelf)

    @Query("SELECT * FROM cached_audius_shelves WHERE shelfId = :shelfId")
    suspend fun getCachedShelf(shelfId: String): CachedAudiusShelf?

    @Query("DELETE FROM cached_audius_shelves")
    suspend fun deleteAllCachedShelves()

    // ── Genre Analysis ───────────────────────────────────────────────────────

    @Query(
        """
        SELECT genre FROM songs
        WHERE genre != ''
        GROUP BY genre
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """
    )
    suspend fun getTopGenres(limit: Int = 3): List<String>
}

data class PlaylistItem(
    val playlistId: Long,
    val albumArtUri: String?
)
