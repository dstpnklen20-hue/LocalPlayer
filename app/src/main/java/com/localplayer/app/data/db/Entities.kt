package com.localplayer.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Плейлист. Треки хранятся по id из MediaStore — метаданные не дублируются. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("track_id")],
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "track_id") val trackId: Long,
    val position: Int,
)

/** Счётчик прослушиваний (F17). */
@Entity(tableName = "play_counts")
data class PlayCountEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    val count: Int,
    @ColumnInfo(name = "last_played") val lastPlayed: Long,
)

/** Плейлист со сводкой для списка. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    /** Первый трек — для обложки. */
    @ColumnInfo(name = "first_track_id") val firstTrackId: Long?,
)
