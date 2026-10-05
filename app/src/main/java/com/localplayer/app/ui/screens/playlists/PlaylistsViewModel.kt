package com.localplayer.app.ui.screens.playlists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.localplayer.app.data.PlaylistRepository
import com.localplayer.app.data.db.PlaylistSummary
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.model.Library
import com.localplayer.app.data.model.Track
import com.localplayer.app.ui.navigation.PlaylistRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Автоплейлисты (F17) — вычисляются, в базе не хранятся. Id отрицательные, чтобы не пересекаться с Room. */
object AutoPlaylists {
    const val RECENT_ID = -1L
    const val MOST_PLAYED_ID = -2L
    const val RECENT_DAYS = 30

    fun recent(library: Library, nowMs: Long): List<Track> {
        val since = nowMs / 1000 - RECENT_DAYS * 24L * 3600
        return library.tracks.filter { it.dateAdded >= since }.sortedByDescending { it.dateAdded }
    }

    fun mostPlayed(library: Library, ids: List<Long>): List<Track> = ids.mapNotNull { library.tracksById[it] }
}

@Immutable
data class PlaylistsUiState(
    val favoritesCount: Int = 0,
    val favoritesCoverId: Long? = null,
    val recentCount: Int = 0,
    val recentCoverId: Long? = null,
    val mostPlayedCount: Int = 0,
    val mostPlayedCoverId: Long? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    mediaStoreRepository: MediaStoreRepository,
) : ViewModel() {

    val state: StateFlow<PlaylistsUiState> = combine(
        mediaStoreRepository.library.filterNotNull(),
        playlistRepository.favoritesSummary,
        playlistRepository.observeMostPlayed(),
        playlistRepository.playlists,
    ) { library, favorites, mostPlayedIds, playlists ->
        val recent = AutoPlaylists.recent(library, System.currentTimeMillis())
        val mostPlayed = AutoPlaylists.mostPlayed(library, mostPlayedIds)
        PlaylistsUiState(
            favoritesCount = favorites?.trackCount ?: 0,
            favoritesCoverId = favorites?.firstTrackId,
            recentCount = recent.size,
            recentCoverId = recent.firstOrNull()?.id,
            mostPlayedCount = mostPlayed.size,
            mostPlayedCoverId = mostPlayed.firstOrNull()?.id,
            playlists = playlists,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistsUiState())

    init {
        mediaStoreRepository.ensureLoaded()
    }

    fun create(name: String) = viewModelScope.launch { playlistRepository.create(name) }
    fun rename(id: Long, name: String) = viewModelScope.launch { playlistRepository.rename(id, name) }
    fun delete(id: Long) = viewModelScope.launch { playlistRepository.delete(id) }
}

/** Элемент плейлиста; [track] = null — файл удалён или перемещён (§7 ТЗ). */
@Immutable
data class PlaylistItem(val trackId: Long, val track: Track?)

@Immutable
data class PlaylistDetailState(
    val loading: Boolean = true,
    val exists: Boolean = true,
    val name: String = "",
    /** Можно ли менять состав и порядок (не автоплейлист). */
    val editable: Boolean = false,
    val kind: Kind = Kind.USER,
    val items: List<PlaylistItem> = emptyList(),
) {
    enum class Kind { USER, FAVORITES, RECENT, MOST_PLAYED }

    val playableTracks: List<Track> get() = items.mapNotNull { it.track }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    mediaStoreRepository: MediaStoreRepository,
) : ViewModel() {

    val playlistId: Long = savedStateHandle.toRoute<PlaylistRoute>().playlistId

    val state: StateFlow<PlaylistDetailState> = when (playlistId) {
        AutoPlaylists.RECENT_ID -> mediaStoreRepository.library.filterNotNull().map { lib ->
            autoState(PlaylistDetailState.Kind.RECENT, AutoPlaylists.recent(lib, System.currentTimeMillis()))
        }
        AutoPlaylists.MOST_PLAYED_ID -> combine(
            mediaStoreRepository.library.filterNotNull(),
            playlistRepository.observeMostPlayed(),
        ) { lib, ids -> autoState(PlaylistDetailState.Kind.MOST_PLAYED, AutoPlaylists.mostPlayed(lib, ids)) }
        else -> userPlaylist(mediaStoreRepository)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailState())

    init {
        mediaStoreRepository.ensureLoaded()
    }

    private fun autoState(kind: PlaylistDetailState.Kind, tracks: List<Track>) = PlaylistDetailState(
        loading = false,
        kind = kind,
        items = tracks.map { PlaylistItem(it.id, it) },
    )

    private fun userPlaylist(mediaStore: MediaStoreRepository): Flow<PlaylistDetailState> = combine(
        playlistRepository.observePlaylist(playlistId),
        playlistRepository.observeTrackIds(playlistId),
        // Ищем по всем трекам без фильтров: короткий трек в плейлисте не должен считаться удалённым
        mediaStore.allTracks.filterNotNull().map { all -> all.associateBy { it.id } },
    ) { playlist, ids, byId ->
        if (playlist == null) {
            PlaylistDetailState(loading = false, exists = false)
        } else {
            val favorites = playlist.id == PlaylistRepository.FAVORITES_ID
            PlaylistDetailState(
                loading = false,
                name = playlist.name,
                editable = true,
                kind = if (favorites) PlaylistDetailState.Kind.FAVORITES else PlaylistDetailState.Kind.USER,
                items = ids.map { PlaylistItem(it, byId[it]) },
            )
        }
    }

    fun remove(trackId: Long) = viewModelScope.launch { playlistRepository.removeTrack(playlistId, trackId) }

    fun reorder(orderedIds: List<Long>) = viewModelScope.launch { playlistRepository.reorder(playlistId, orderedIds) }

    fun rename(name: String) = viewModelScope.launch { playlistRepository.rename(playlistId, name) }

    fun delete() = viewModelScope.launch { playlistRepository.delete(playlistId) }

}
