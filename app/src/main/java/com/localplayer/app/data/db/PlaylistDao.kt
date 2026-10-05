package com.localplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Простые запросы; многошаговые операции собираются в транзакции в PlaylistRepository. */
@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name,
               (SELECT COUNT(*) FROM playlist_tracks t WHERE t.playlist_id = p.id) AS track_count,
               (SELECT t.track_id FROM playlist_tracks t WHERE t.playlist_id = p.id ORDER BY t.position LIMIT 1) AS first_track_id
        FROM playlists p
        ORDER BY p.created_at
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT track_id FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    fun observeTrackIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT track_id FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun getTrackIds(playlistId: Long): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId)")
    fun observeContains(playlistId: Long, trackId: Long): Flow<Boolean>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE track_id IN (:trackIds)")
    suspend fun removeTracksEverywhere(trackIds: List<Long>)

    @Query("SELECT * FROM play_counts WHERE track_id = :trackId")
    suspend fun getPlayCount(trackId: Long): PlayCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayCount(entity: PlayCountEntity)

    @Query("SELECT track_id FROM play_counts WHERE count > 0 ORDER BY count DESC, last_played DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<Long>>
}
