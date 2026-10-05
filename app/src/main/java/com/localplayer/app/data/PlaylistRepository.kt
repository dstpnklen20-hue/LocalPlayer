package com.localplayer.app.data

import com.localplayer.app.data.db.AppDatabase
import com.localplayer.app.data.db.PlayCountEntity
import com.localplayer.app.data.db.PlaylistDao
import com.localplayer.app.data.db.PlaylistEntity
import com.localplayer.app.data.db.PlaylistSummary
import com.localplayer.app.data.db.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Выполняет блок в транзакции БД; в тестах — просто выполняет блок. */
interface TransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R
}

/** Плейлисты, избранное и счётчики прослушиваний (F14–F17). */
@Singleton
class PlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
    private val transaction: TransactionRunner,
) {
    /** Пользовательские плейлисты (без «Избранного»). */
    val playlists: Flow<List<PlaylistSummary>> =
        dao.observeSummaries().map { list -> list.filter { it.id != FAVORITES_ID } }

    val favoritesSummary: Flow<PlaylistSummary?> =
        dao.observeSummaries().map { list -> list.firstOrNull { it.id == FAVORITES_ID } }

    fun observePlaylist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)

    fun observeTrackIds(playlistId: Long): Flow<List<Long>> = dao.observeTrackIds(playlistId)

    fun observeIsFavorite(trackId: Long): Flow<Boolean> = dao.observeContains(FAVORITES_ID, trackId)

    fun observeMostPlayed(limit: Int = MOST_PLAYED_LIMIT): Flow<List<Long>> = dao.observeMostPlayed(limit)

    suspend fun create(name: String, now: Long = System.currentTimeMillis()): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now))

    suspend fun rename(id: Long, name: String) {
        if (id != FAVORITES_ID) dao.rename(id, name.trim())
    }

    suspend fun delete(id: Long) {
        if (id != FAVORITES_ID) dao.deletePlaylist(id)
    }

    /** Добавляет треки в конец; уже имеющиеся пропускаются. */
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>) = transaction {
        val existing = dao.getTrackIds(playlistId)
        val known = existing.toHashSet()
        val toAdd = trackIds.distinct().filter { it !in known }
        dao.insertTracks(toAdd.mapIndexed { i, id -> PlaylistTrackEntity(playlistId, id, existing.size + i) })
    }

    suspend fun removeTrack(playlistId: Long, trackId: Long) = transaction {
        val ids = dao.getTrackIds(playlistId).filter { it != trackId }
        writeOrder(playlistId, ids)
    }

    /** Новый порядок после перетаскивания (F15). */
    suspend fun reorder(playlistId: Long, orderedIds: List<Long>) = transaction {
        writeOrder(playlistId, orderedIds.distinct())
    }

    private suspend fun writeOrder(playlistId: Long, ids: List<Long>) {
        dao.clearTracks(playlistId)
        dao.insertTracks(ids.mapIndexed { i, id -> PlaylistTrackEntity(playlistId, id, i) })
    }

    suspend fun toggleFavorite(trackId: Long) = transaction {
        val ids = dao.getTrackIds(FAVORITES_ID)
        if (trackId in ids) {
            writeOrder(FAVORITES_ID, ids - trackId)
        } else {
            dao.insertTracks(listOf(PlaylistTrackEntity(FAVORITES_ID, trackId, ids.size)))
        }
    }

    /** После удаления файлов с устройства убираем их из всех плейлистов. */
    suspend fun removeTracksEverywhere(trackIds: List<Long>) {
        if (trackIds.isNotEmpty()) dao.removeTracksEverywhere(trackIds)
    }

    /** F17: прослушивание засчитывается после 50% длительности (решает вызывающий код). */
    suspend fun recordPlay(trackId: Long, now: Long = System.currentTimeMillis()) = transaction {
        val current = dao.getPlayCount(trackId)
        dao.upsertPlayCount(PlayCountEntity(trackId, (current?.count ?: 0) + 1, now))
    }

    companion object {
        const val FAVORITES_ID = AppDatabase.FAVORITES_ID
        const val MOST_PLAYED_LIMIT = 50
    }
}
