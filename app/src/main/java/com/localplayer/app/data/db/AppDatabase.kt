package com.localplayer.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlaylistEntity::class, PlaylistTrackEntity::class, PlayCountEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    /** «Избранное» — системный плейлист с фиксированным id, создаётся вместе с базой. */
    object Callback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO playlists (id, name, created_at) VALUES ($FAVORITES_ID, '', 0)",
            )
        }
    }

    companion object {
        const val NAME = "localplayer.db"
        const val FAVORITES_ID = 1L
    }
}
