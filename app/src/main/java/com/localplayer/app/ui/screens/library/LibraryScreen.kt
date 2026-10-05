package com.localplayer.app.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.app.R
import com.localplayer.app.data.model.Album
import com.localplayer.app.data.model.Artist
import com.localplayer.app.data.model.FolderNode
import com.localplayer.app.data.model.Track
import com.localplayer.app.ui.components.AlbumCard
import com.localplayer.app.ui.components.AlphabetScroller
import com.localplayer.app.ui.components.CollectionRow
import com.localplayer.app.ui.components.EmptyState
import com.localplayer.app.ui.components.IndexEntry
import com.localplayer.app.ui.components.LocalCurrentTrackId
import com.localplayer.app.ui.components.LocalPlaybackActions
import com.localplayer.app.ui.components.TrackRow
import kotlinx.coroutines.launch

private enum class LibraryTab(val title: Int) {
    TRACKS(R.string.tab_tracks),
    ALBUMS(R.string.tab_albums),
    ARTISTS(R.string.tab_artists),
    FOLDERS(R.string.tab_folders),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onFolder: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { LibraryTab.entries.size }
    val scope = rememberCoroutineScope()
    var showSort by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_library)) },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.nav_search))
                }
                if (pagerState.currentPage == LibraryTab.TRACKS.ordinal) {
                    IconButton(onClick = { showSort = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.action_sort))
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                }
            },
        )
        PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = pagerState.currentPage == tab.ordinal,
                    onClick = { scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                    text = { Text(stringResource(tab.title), maxLines = 1) },
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing && !state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.tracks.isEmpty() -> EmptyLibrary()
                else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (LibraryTab.entries[page]) {
                        LibraryTab.TRACKS -> TracksTab(state.tracks, state.trackIndex)
                        LibraryTab.ALBUMS -> AlbumsTab(state.albums, onAlbum)
                        LibraryTab.ARTISTS -> ArtistsTab(state.artists, state.artistIndex, onArtist)
                        LibraryTab.FOLDERS -> FoldersTab(state.folderRoot, onFolder)
                    }
                }
            }
        }
    }

    if (showSort) {
        SortDialog(
            current = state.sort,
            onSelect = viewModel::setSort,
            onDismiss = { showSort = false },
        )
    }
}

@Composable
private fun EmptyLibrary() {
    // Обёртка в LazyColumn — чтобы pull-to-refresh работал и на пустом экране
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            EmptyState(
                icon = Icons.Rounded.MusicOff,
                title = stringResource(R.string.empty_library_title),
                hint = stringResource(R.string.empty_library_hint),
                modifier = Modifier.fillParentMaxSize(),
            )
        }
    }
}

@Composable
private fun TracksTab(tracks: List<Track>, index: List<IndexEntry>) {
    val listState = rememberLazyListState()
    val playback = LocalPlaybackActions.current
    val currentId = LocalCurrentTrackId.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == currentId,
                    onClick = { playback.play(tracks, i) },
                    trailing = { TrackMenuSlot(track) },
                )
            }
        }
        AlphabetScroller(
            entries = index,
            onScrollTo = { listState.scrollToItem(it) },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun AlbumsTab(albums: List<Album>, onAlbum: (Long) -> Unit) {
    val gridState = rememberLazyGridState()
    // Adaptive(160 dp): на телефоне 2 колонки, на планшете 4+
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album = album, onClick = { onAlbum(album.id) })
        }
    }
}

@Composable
private fun ArtistsTab(artists: List<Artist>, index: List<IndexEntry>, onArtist: (String) -> Unit) {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(artists, key = { it.name }) { artist ->
                CollectionRow(
                    title = artist.name,
                    subtitle = artistSubtitle(artist),
                    coverTrackId = artist.coverTrackId,
                    circleCover = true,
                    onClick = { onArtist(artist.name) },
                )
            }
        }
        AlphabetScroller(
            entries = index,
            onScrollTo = { listState.scrollToItem(it) },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
fun artistSubtitle(artist: Artist): String =
    pluralStringResource(R.plurals.albums_count, artist.albumCount, artist.albumCount) + " · " +
        pluralStringResource(R.plurals.tracks_count, artist.tracks.size, artist.tracks.size)

@Composable
private fun FoldersTab(root: FolderNode?, onFolder: (String) -> Unit) {
    if (root == null) return
    val playback = LocalPlaybackActions.current
    val currentId = LocalCurrentTrackId.current
    LazyColumn(Modifier.fillMaxSize()) {
        folderContent(root, onFolder, currentId, onPlay = { tracks, i -> playback.play(tracks, i) })
    }
}

/** Содержимое папки: сначала вложенные папки, затем треки. Используется и на экране папки. */
fun androidx.compose.foundation.lazy.LazyListScope.folderContent(
    folder: FolderNode,
    onFolder: (String) -> Unit,
    currentTrackId: Long?,
    onPlay: (List<Track>, Int) -> Unit,
) {
    items(folder.subfolders, key = { "dir:" + it.path }) { sub ->
        CollectionRow(
            title = sub.name,
            subtitle = folderSubtitle(sub),
            coverTrackId = null,
            onClick = { onFolder(sub.path) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            },
        )
    }
    itemsIndexed(folder.tracks, key = { _, t -> t.id }) { i, track ->
        TrackRow(
            track = track,
            isCurrent = track.id == currentTrackId,
            onClick = { onPlay(folder.tracks, i) },
            trailing = { TrackMenuSlot(track) },
        )
    }
}

@Composable
private fun folderSubtitle(folder: FolderNode): String =
    pluralStringResource(R.plurals.tracks_count, folder.totalTracks, folder.totalTracks)

/** Место для меню «⋮» трека; наполняется на этапе 4. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TrackMenuSlot(track: Track) = Unit
