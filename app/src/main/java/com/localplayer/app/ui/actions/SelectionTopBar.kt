package com.localplayer.app.ui.actions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.localplayer.app.R
import com.localplayer.app.data.model.Track

/** Панель множественного выбора: заменяет обычную шапку, пока что-то выбрано. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(selection: TrackSelection, allTracks: List<Track>) {
    val actions = LocalTrackActions.current ?: return
    BackHandler(enabled = selection.isActive) { selection.clear() }
    var menu by remember { mutableStateOf(false) }

    fun apply(block: (List<Track>) -> Unit) {
        block(selection.tracks)
        selection.clear()
    }

    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selection.count)) },
        navigationIcon = {
            IconButton(onClick = selection::clear) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.selection_cancel))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        actions = {
            IconButton(onClick = { apply(actions::play) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play))
            }
            IconButton(onClick = { apply(actions::playNext) }) {
                Icon(Icons.Rounded.QueuePlayNext, contentDescription = stringResource(R.string.action_play_next))
            }
            IconButton(onClick = { apply(actions::addToPlaylist) }) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.action_add_to_playlist))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(R.string.action_add_to_queue, Icons.AutoMirrored.Rounded.QueueMusic) { menu = false; apply(actions::addToQueue) }
                    MenuItem(R.string.action_select_all, Icons.Rounded.SelectAll) { menu = false; selection.selectAll(allTracks) }
                    MenuItem(R.string.action_share, Icons.Rounded.Share) { menu = false; apply(actions::share) }
                    if (actions.canDelete) {
                        MenuItem(R.string.action_delete_file, Icons.Rounded.Delete) { menu = false; apply(actions::delete) }
                    }
                }
            }
        },
    )
}
