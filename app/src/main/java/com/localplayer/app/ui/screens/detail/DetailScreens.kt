package com.localplayer.app.ui.screens.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.app.R
import com.localplayer.app.data.model.Track
import com.localplayer.app.ui.actions.SelectableTrackRow
import com.localplayer.app.ui.actions.SelectionTopBar
import com.localplayer.app.ui.actions.TrackSelection
import com.localplayer.app.ui.components.AlbumCard
import com.localplayer.app.ui.components.CollectionHeader
import com.localplayer.app.ui.components.DetailTopBar
import com.localplayer.app.ui.components.EmptyState
import com.localplayer.app.ui.components.LocalPlaybackActions
import com.localplayer.app.ui.screens.library.artistSubtitle
import com.localplayer.app.ui.screens.library.folderContent
import com.localplayer.app.util.formatDuration

/** Общая обвязка: панель (или панель выбора), загрузка, «не найдено», список. */
@Composable
private fun <T> DetailScaffold(
    title: String,
    state: DetailState<T>,
    onBack: () -> Unit,
    selection: TrackSelection,
    allTracks: (T) -> List<Track>,
    content: LazyListScope.(T) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val item = state.item
        if (selection.isActive && item != null) {
            SelectionTopBar(selection, allTracks(item))
        } else {
            DetailTopBar(title = title, onBack = onBack)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            item == null -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.not_found_title),
                hint = stringResource(R.string.item_not_found_hint),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) { content(item) }
        }
    }
}

/** Альбом: треки по номеру. */
@Composable
fun AlbumScreen(onBack: () -> Unit, viewModel: AlbumViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback = LocalPlaybackActions.current
    val selection = remember { TrackSelection() }
    DetailScaffold(
        title = state.item?.title.orEmpty(),
        state = state,
        onBack = onBack,
        selection = selection,
        allTracks = { it.tracks },
    ) { album ->
        item(key = "header") {
            val info = listOfNotNull(
                album.year.takeIf { it > 0 }?.toString(),
                pluralStringResource(R.plurals.tracks_count, album.tracks.size, album.tracks.size),
                formatDuration(album.tracks.sumOf { it.durationMs }),
            ).joinToString(" · ")
            CollectionHeader(
                title = album.title,
                subtitle = album.artist + "\n" + info,
                coverTrackId = album.coverTrackId,
                onPlay = { playback.play(album.tracks) },
                onShuffle = { playback.play(album.tracks, shuffle = true) },
            )
        }
        trackItems(album.tracks, selection, numbered = true)
    }
}

/** Исполнитель: альбомы лентой и все треки. */
@Composable
fun ArtistScreen(onBack: () -> Unit, onAlbum: (Long) -> Unit, viewModel: ArtistViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback = LocalPlaybackActions.current
    val selection = remember { TrackSelection() }
    DetailScaffold(
        title = state.item?.artist?.name.orEmpty(),
        state = state,
        onBack = onBack,
        selection = selection,
        allTracks = { it.artist.tracks },
    ) { detail ->
        val tracks = detail.artist.tracks
        item(key = "header") {
            CollectionHeader(
                title = detail.artist.name,
                subtitle = artistSubtitle(detail.artist),
                coverTrackId = detail.artist.coverTrackId,
                circleCover = true,
                onPlay = { playback.play(tracks) },
                onShuffle = { playback.play(tracks, shuffle = true) },
            )
        }
        if (detail.albums.isNotEmpty()) {
            item(key = "albums") {
                SectionTitle(stringResource(R.string.tab_albums))
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(detail.albums, key = { it.id }) { album ->
                        AlbumCard(album, onClick = { onAlbum(album.id) }, modifier = Modifier.width(160.dp))
                    }
                }
            }
            item(key = "tracks_title") { SectionTitle(stringResource(R.string.tab_tracks)) }
        }
        trackItems(tracks, selection)
    }
}

/** Папка: вложенные папки, затем треки; «Играть» — вместе с вложенными. */
@Composable
fun FolderScreen(onBack: () -> Unit, onFolder: (String) -> Unit, viewModel: FolderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback = LocalPlaybackActions.current
    val selection = remember { TrackSelection() }
    DetailScaffold(
        title = state.item?.name.orEmpty(),
        state = state,
        onBack = onBack,
        selection = selection,
        allTracks = { it.tracks },
    ) { folder ->
        item(key = "header") {
            val all = folder.allTracks()
            CollectionHeader(
                title = folder.name,
                subtitle = folder.path + "\n" +
                    pluralStringResource(R.plurals.tracks_count, folder.totalTracks, folder.totalTracks),
                coverTrackId = null,
                showCover = false,
                onPlay = { playback.play(all) },
                onShuffle = { playback.play(all, shuffle = true) },
            )
        }
        folderContent(folder, onFolder, selection)
    }
}

fun LazyListScope.trackItems(
    tracks: List<Track>,
    selection: TrackSelection?,
    numbered: Boolean = false,
) {
    itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
        if (numbered) {
            SelectableTrackRow(track, tracks, i, selection, number = track.trackNumber, subtitle = formatDuration(track.durationMs))
        } else {
            SelectableTrackRow(track, tracks, i, selection)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
