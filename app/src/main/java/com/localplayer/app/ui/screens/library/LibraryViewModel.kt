package com.localplayer.app.ui.screens.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.model.Album
import com.localplayer.app.data.model.Artist
import com.localplayer.app.data.model.FolderNode
import com.localplayer.app.data.model.Track
import com.localplayer.app.data.model.TrackSort
import com.localplayer.app.data.model.TrackSortField
import com.localplayer.app.data.prefs.SettingsRepository
import com.localplayer.app.di.DefaultDispatcher
import com.localplayer.app.ui.components.IndexEntry
import com.localplayer.app.ui.components.buildIndex
import com.localplayer.app.util.indexLetter
import com.localplayer.app.util.normalized
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LibraryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val trackIndex: List<IndexEntry> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val artistIndex: List<IndexEntry> = emptyList(),
    val folderRoot: FolderNode? = null,
    val sort: TrackSort = TrackSort(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MediaStoreRepository,
    private val settingsRepository: SettingsRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val sortFlow = settingsRepository.settings.map { it.trackSort }.distinctUntilChanged()

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.library,
        sortFlow,
        repository.isRefreshing,
    ) { library, sort, refreshing ->
        if (library == null) {
            LibraryUiState(loading = true, refreshing = refreshing, sort = sort)
        } else {
            val sorted = sortTracks(library.tracks, sort)
            LibraryUiState(
                loading = false,
                refreshing = refreshing,
                tracks = sorted,
                trackIndex = trackIndex(sorted, sort),
                albums = library.albums,
                artists = library.artists,
                artistIndex = buildIndex(library.artists) { indexLetter(it.name) },
                folderRoot = library.folderRoot,
                sort = sort,
            )
        }
    }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        repository.ensureLoaded()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun setSort(sort: TrackSort) {
        viewModelScope.launch { settingsRepository.setTrackSort(sort) }
    }

    companion object {
        /** F5: сортировка треков; для строк — без учёта регистра и ё/е. */
        fun sortTracks(tracks: List<Track>, sort: TrackSort): List<Track> {
            val comparator: Comparator<Track> = when (sort.field) {
                TrackSortField.TITLE -> compareBy { it.title.normalized() }
                TrackSortField.ARTIST -> compareBy<Track>({ it.artist.normalized() }, { it.title.normalized() })
                TrackSortField.ALBUM -> compareBy<Track>({ it.album.normalized() }, { it.discNumber }, { it.trackNumber })
                TrackSortField.DATE_ADDED -> compareBy<Track>({ it.dateAdded }, { it.title.normalized() })
                TrackSortField.DURATION -> compareBy<Track>({ it.durationMs }, { it.title.normalized() })
            }
            return tracks.sortedWith(if (sort.ascending) comparator else comparator.reversed())
        }

        /** Алфавитный индекс имеет смысл только для сортировки по строковым полям. */
        fun trackIndex(sorted: List<Track>, sort: TrackSort): List<IndexEntry> = when (sort.field) {
            TrackSortField.TITLE -> buildIndex(sorted) { indexLetter(it.title) }
            TrackSortField.ARTIST -> buildIndex(sorted) { indexLetter(it.artist) }
            TrackSortField.ALBUM -> buildIndex(sorted) { indexLetter(it.album) }
            else -> emptyList()
        }
    }
}
