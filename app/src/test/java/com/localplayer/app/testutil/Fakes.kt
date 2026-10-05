package com.localplayer.app.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import com.localplayer.app.data.TransactionRunner
import com.localplayer.app.data.db.PlayCountEntity
import com.localplayer.app.data.db.PlaylistDao
import com.localplayer.app.data.db.PlaylistEntity
import com.localplayer.app.data.db.PlaylistSummary
import com.localplayer.app.data.db.PlaylistTrackEntity
import com.localplayer.app.data.mediastore.MediaStoreSource
import com.localplayer.app.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

fun track(
    id: Long,
    title: String = "Track $id",
    artist: String = "Artist",
    album: String = "Album",
    albumId: Long = 1,
    durationMs: Long = 180_000,
    trackNumber: Int = id.toInt(),
    folder: String = "/storage/emulated/0/Music",
    dateAdded: Long = 1_700_000_000,
) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = 0,
    year = 2020,
    folderPath = folder,
    fileName = "$title.mp3",
    dateAdded = dateAdded,
    sizeBytes = 3_000_000,
    mimeType = "audio/mpeg",
)

/** Фейковый MediaStore: треки задаются в тесте, изменения эмитятся вручную. */
class FakeMediaStoreSource(initial: List<Track> = emptyList()) : MediaStoreSource {
    var tracks: List<Track> = initial
    var throwSecurity = false
    var queryCount = 0
        private set
    val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override suspend fun queryTracks(): List<Track> {
        queryCount++
        if (throwSecurity) throw SecurityException("no permission")
        return tracks
    }

    override fun changes(): Flow<Unit> = changeEvents
}

/**
 * DataStore в памяти. Файловый DataStore на Windows-хосте не может атомарно
 * переименовать временный файл поверх существующего, поэтому в unit-тестах — память.
 */
@Suppress("UNUSED_PARAMETER")
fun testDataStore(dir: File, scope: CoroutineScope, name: String = "test"): DataStore<Preferences> =
    InMemoryDataStore()

class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value).toPreferences()
            state.value = updated
            updated
        }
}

object DirectTransactionRunner : TransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R = block()
}

/** In-memory реализация DAO плейлистов. */
class FakePlaylistDao : PlaylistDao {
    private val playlists = MutableStateFlow<Map<Long, PlaylistEntity>>(
        mapOf(1L to PlaylistEntity(1, "", 0)), // «Избранное», как создаёт callback базы
    )
    private val tracks = MutableStateFlow<List<PlaylistTrackEntity>>(emptyList())
    private val counts = MutableStateFlow<Map<Long, PlayCountEntity>>(emptyMap())
    private var nextId = 2L

    override fun observeSummaries(): Flow<List<PlaylistSummary>> = playlists.map { map ->
        map.values.sortedBy { it.createdAt }.map { p ->
            val items = tracks.value.filter { it.playlistId == p.id }.sortedBy { it.position }
            PlaylistSummary(p.id, p.name, items.size, items.firstOrNull()?.trackId)
        }
    }

    override fun observePlaylist(id: Long): Flow<PlaylistEntity?> = playlists.map { it[id] }

    override fun observeTrackIds(playlistId: Long): Flow<List<Long>> =
        tracks.map { list -> list.filter { it.playlistId == playlistId }.sortedBy { it.position }.map { it.trackId } }

    override suspend fun getTrackIds(playlistId: Long): List<Long> =
        tracks.value.filter { it.playlistId == playlistId }.sortedBy { it.position }.map { it.trackId }

    override fun observeContains(playlistId: Long, trackId: Long): Flow<Boolean> =
        tracks.map { list -> list.any { it.playlistId == playlistId && it.trackId == trackId } }

    override suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
        val id = nextId++
        playlists.value = playlists.value + (id to playlist.copy(id = id))
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        playlists.value[id]?.let { playlists.value = playlists.value + (id to it.copy(name = name)) }
    }

    override suspend fun deletePlaylist(id: Long) {
        playlists.value = playlists.value - id
        tracks.value = tracks.value.filter { it.playlistId != id }
    }

    override suspend fun insertTracks(tracks: List<PlaylistTrackEntity>) {
        val existing = this.tracks.value
        val toAdd = tracks.filter { new -> existing.none { it.playlistId == new.playlistId && it.trackId == new.trackId } }
        this.tracks.value = existing + toAdd
    }

    override suspend fun clearTracks(playlistId: Long) {
        tracks.value = tracks.value.filter { it.playlistId != playlistId }
    }

    override suspend fun removeTracksEverywhere(trackIds: List<Long>) {
        tracks.value = tracks.value.filter { it.trackId !in trackIds }
    }

    override suspend fun getPlayCount(trackId: Long): PlayCountEntity? = counts.value[trackId]

    override suspend fun upsertPlayCount(entity: PlayCountEntity) {
        counts.value = counts.value + (entity.trackId to entity)
    }

    override fun observeMostPlayed(limit: Int): Flow<List<Long>> = counts.map { map ->
        map.values.filter { it.count > 0 }
            .sortedWith(compareByDescending<PlayCountEntity> { it.count }.thenByDescending { it.lastPlayed })
            .take(limit)
            .map { it.trackId }
    }
}
