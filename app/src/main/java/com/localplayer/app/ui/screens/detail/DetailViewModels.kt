package com.localplayer.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.model.Album
import com.localplayer.app.data.model.Artist
import com.localplayer.app.data.model.FolderNode
import com.localplayer.app.data.model.Library
import com.localplayer.app.ui.navigation.AlbumRoute
import com.localplayer.app.ui.navigation.ArtistRoute
import com.localplayer.app.ui.navigation.FolderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Состояние экрана-коллекции: загрузка или найденный объект (null — не найден, например удалён). */
data class DetailState<T>(val loading: Boolean = true, val item: T? = null)

private fun <T : Any> ViewModel.detailState(
    repository: MediaStoreRepository,
    select: (Library) -> T?,
): StateFlow<DetailState<T>> {
    repository.ensureLoaded()
    return repository.library
        .map { lib -> if (lib == null) DetailState() else DetailState(loading = false, item = select(lib)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MediaStoreRepository,
) : ViewModel() {
    private val albumId = savedStateHandle.toRoute<AlbumRoute>().albumId
    val state: StateFlow<DetailState<Album>> = detailState(repository) { it.albumsById[albumId] }
}

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MediaStoreRepository,
) : ViewModel() {
    private val name = savedStateHandle.toRoute<ArtistRoute>().name

    /** Исполнитель и его альбомы. */
    data class ArtistDetail(val artist: Artist, val albums: List<Album>)

    val state: StateFlow<DetailState<ArtistDetail>> = detailState(repository) { lib ->
        lib.artistsByName[name]?.let { artist ->
            val albumIds = artist.tracks.map { it.albumId }.toSet()
            ArtistDetail(artist, lib.albums.filter { it.id in albumIds })
        }
    }
}

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MediaStoreRepository,
) : ViewModel() {
    private val path = savedStateHandle.toRoute<FolderRoute>().path
    val state: StateFlow<DetailState<FolderNode>> = detailState(repository) { it.foldersByPath[path] }
}
