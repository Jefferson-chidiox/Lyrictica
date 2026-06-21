package com.oss.euphoriae.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oss.euphoriae.data.model.CachedAudiusShelf
import com.oss.euphoriae.data.model.CachedLyrics
import com.oss.euphoriae.data.model.FavoriteSong
import com.oss.euphoriae.data.model.GameScoreRecord
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.PlaylistSong
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.data.model.SongPlayEvent

@Database(
    entities = [Song::class, Playlist::class, PlaylistSong::class, CachedLyrics::class, FavoriteSong::class, SongPlayEvent::class, CachedAudiusShelf::class, GameScoreRecord::class],
    version = 7,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN mimeType TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_lyrics (
                        songId INTEGER PRIMARY KEY NOT NULL,
                        trackName TEXT NOT NULL,
                        artistName TEXT NOT NULL,
                        syncedLyrics TEXT,
                        plainLyrics TEXT,
                        fetchedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_songs (
                        songId INTEGER NOT NULL PRIMARY KEY,
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_play_events (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        songId INTEGER NOT NULL,
                        playedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_song_play_events_songId ON song_play_events(songId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_song_play_events_playedAt ON song_play_events(playedAt)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN dateModified INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_audius_shelves (
                        shelfId TEXT NOT NULL PRIMARY KEY,
                        shelfJson TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS game_scores (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        songId INTEGER NOT NULL,
                        songTitle TEXT NOT NULL,
                        songArtist TEXT NOT NULL,
                        songAlbum TEXT NOT NULL,
                        songArtUri TEXT,
                        mode TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        achievedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_game_scores_songId ON game_scores(songId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_game_scores_score ON game_scores(score)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_game_scores_achievedAt ON game_scores(achievedAt)")
            }
        }

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "euphoriae_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

