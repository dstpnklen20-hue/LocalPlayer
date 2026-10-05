package com.localplayer.app.ui.screens.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.model.Album
import com.localplayer.app.data.model.Artist
import com.localplayer.app.data.model.Library
import com.localplayer.app.data.model.Track
import com.localplayer.app.di.DefaultDispatcher
import com.localplayer.app.util.normalized
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class SearchResults(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repository: MediaStoreRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    val results: StateFlow<SearchResults> = combine(
        query
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MS },
        repository.library.filterNotNull(),
    ) { q, library -> search(library, q) }
        .onStart { repository.ensureLoaded() }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    companion object {
        private const val KEY_QUERY = "query"
        const val DEBOUNCE_MS = 300L

        /** F6: поиск по названию, исполнителю и альбому без учёта регистра и ё/е. */
        fun search(library: Library, rawQuery: String): SearchResults {
            val q = rawQuery.trim().normalized()
            if (q.isEmpty()) return SearchResults()
            return SearchResults(
                query = rawQuery,
                tracks = library.tracks.filter {
                    it.title.normalized().contains(q) ||
                        it.artist.normalized().contains(q) ||
                        it.album.normalized().contains(q)
                }.sortedBy { it.title.normalized() },
                albums = library.albums.filter {
                    it.title.normalized().contains(q) || it.artist.normalized().contains(q)
                },
                artists = library.artists.filter { it.name.normalized().contains(q) },
            )
        }
    }
}
