package com.localplayer.app.ui.screens.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.app.R
import com.localplayer.app.ui.actions.ConfirmDialog
import com.localplayer.app.ui.actions.MenuItem
import com.localplayer.app.ui.actions.NameDialog
import com.localplayer.app.ui.actions.SelectionTopBar
import com.localplayer.app.ui.actions.TrackMenuButton
import com.localplayer.app.ui.actions.TrackSelection
import com.localplayer.app.ui.components.CollectionHeader
import com.localplayer.app.ui.components.CoverPlaceholderBox
import com.localplayer.app.ui.components.DetailTopBar
import com.localplayer.app.ui.components.EmptyState
import com.localplayer.app.ui.components.LocalCurrentTrackId
import com.localplayer.app.ui.components.LocalPlaybackActions
import com.localplayer.app.ui.components.TrackRow
import com.localplayer.app.util.formatDuration
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback = LocalPlaybackActions.current
    val currentId = LocalCurrentTrackId.current
    val selection = remember { TrackSelection() }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    val title = when (state.kind) {
        PlaylistDetailState.Kind.FAVORITES -> stringResource(R.string.playlist_favorites)
        PlaylistDetailState.Kind.RECENT -> stringResource(R.string.playlist_recent)
        PlaylistDetailState.Kind.MOST_PLAYED -> stringResource(R.string.playlist_most_played)
        PlaylistDetailState.Kind.USER -> state.name
    }

    // Локальная копия для плавного перетаскивания (F15); в БД пишем по окончании жеста
    var items by remember { mutableStateOf(state.items) }
    LaunchedEffect(state.items) { items = state.items }

    Column(Modifier.fillMaxSize()) {
        if (selection.isActive) {
            SelectionTopBar(selection, state.playableTracks)
        } else {
            DetailTopBar(title = title, onBack = onBack) {
                if (state.kind == PlaylistDetailState.Kind.USER && state.exists) {
                    PlaylistMenu(onRename = { renaming = true }, onDelete = { deleting = true })
                }
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            !state.exists -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.not_found_title),
                hint = null,
            )
            else -> {
                val listState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                    // Сдвиг на 1 — первым элементом идёт шапка
                    if (from.index < 1 || to.index < 1) return@rememberReorderableLazyListState
                    val list = items.toMutableList()
                    list.add(to.index - 1, list.removeAt(from.index - 1))
                    items = list
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "header") {
                        val tracks = state.playableTracks
                        CollectionHeader(
                            title = title,
                            subtitle = pluralStringResource(R.plurals.tracks_count, items.size, items.size) +
                                " · " + formatDuration(tracks.sumOf { it.durationMs }),
                            coverTrackId = tracks.firstOrNull()?.id,
                            onPlay = { playback.play(tracks) },
                            onShuffle = { playback.play(tracks, shuffle = true) },
                            playEnabled = tracks.isNotEmpty(),
                        )
                    }
                    if (items.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                title = stringResource(R.string.playlist_empty_title),
                                hint = stringResource(
                                    when (state.kind) {
                                        PlaylistDetailState.Kind.FAVORITES -> R.string.favorites_empty_hint
                                        PlaylistDetailState.Kind.RECENT -> R.string.recent_empty_hint
                                        PlaylistDetailState.Kind.MOST_PLAYED -> R.string.most_played_empty_hint
                                        PlaylistDetailState.Kind.USER -> R.string.playlist_empty_hint
                                    },
                                ),
                                modifier = Modifier.heightIn(min = 240.dp),
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, item -> item.trackId }) { _, item ->
                        ReorderableItem(reorderState, key = item.trackId, enabled = state.editable) { isDragging ->
                            Surface(
                                color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.background,
                                shadowElevation = if (isDragging) 4.dp else 0.dp,
                            ) {
                                val track = item.track
                                val handle: @Composable () -> Unit = {
                                    if (state.editable && !selection.isActive) {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier.draggableHandle(
                                                onDragStopped = { viewModel.reorder(items.map { it.trackId }) },
                                            ),
                                        ) {
                                            Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.queue_drag))
                                        }
                                    }
                                }
                                if (track == null) {
                                    UnavailableRow(
                                        onRemove = if (state.editable) ({ viewModel.remove(item.trackId) }) else null,
                                        handle = handle,
                                    )
                                } else {
                                    val playable = state.playableTracks
                                    TrackRow(
                                        track = track,
                                        isCurrent = track.id == currentId,
                                        onClick = {
                                            if (selection.isActive) selection.toggle(track)
                                            else playback.play(playable, playable.indexOf(track).coerceAtLeast(0))
                                        },
                                        onLongClick = { selection.toggle(track) },
                                        selectionMode = selection.isActive,
                                        selected = selection.isSelected(track.id),
                                        trailing = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TrackMenuButton(track) { dismiss ->
                                                    if (state.editable) {
                                                        MenuItem(R.string.action_remove_from_playlist, Icons.Rounded.PlaylistRemove) {
                                                            dismiss()
                                                            viewModel.remove(track.id)
                                                        }
                                                    }
                                                }
                                                handle()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = stringResource(R.string.playlist_rename),
            initial = state.name,
            onDismiss = { renaming = false },
            onConfirm = { viewModel.rename(it); renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete),
            text = stringResource(R.string.playlist_delete_confirm, state.name),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                viewModel.delete()
                onBack()
            },
        )
    }
}

/** Трек из плейлиста, файла которого больше нет: серая строка (§7 ТЗ). */
@Composable
private fun UnavailableRow(onRemove: (() -> Unit)?, handle: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).alpha(0.4f)) { CoverPlaceholderBox(Icons.Rounded.MusicOff) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).alpha(0.5f)) {
            Text(stringResource(R.string.track_unavailable), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.track_unavailable_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove_from_playlist))
            }
        }
        handle()
    }
}
