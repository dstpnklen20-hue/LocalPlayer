package com.localplayer.app.ui.screens.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.app.R
import com.localplayer.app.data.PlaylistRepository
import com.localplayer.app.data.db.PlaylistSummary
import com.localplayer.app.ui.actions.ConfirmDialog
import com.localplayer.app.ui.actions.MenuItem
import com.localplayer.app.ui.actions.NameDialog
import com.localplayer.app.ui.components.CollectionRow
import com.localplayer.app.ui.components.EmptyState
import com.localplayer.app.ui.screens.detail.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylist: (Long) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(stringResource(R.string.nav_playlists)) })
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                item(key = "auto_title") { SectionTitle(stringResource(R.string.playlists_auto)) }
                item(key = "favorites") {
                    AutoRow(
                        title = stringResource(R.string.playlist_favorites),
                        count = state.favoritesCount,
                        icon = Icons.Rounded.Favorite,
                        onClick = { onPlaylist(PlaylistRepository.FAVORITES_ID) },
                    )
                }
                item(key = "recent") {
                    AutoRow(
                        title = stringResource(R.string.playlist_recent),
                        count = state.recentCount,
                        icon = Icons.Rounded.NewReleases,
                        onClick = { onPlaylist(AutoPlaylists.RECENT_ID) },
                    )
                }
                item(key = "most_played") {
                    AutoRow(
                        title = stringResource(R.string.playlist_most_played),
                        count = state.mostPlayedCount,
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        onClick = { onPlaylist(AutoPlaylists.MOST_PLAYED_ID) },
                    )
                }
                item(key = "mine_title") { SectionTitle(stringResource(R.string.playlists_mine)) }
                if (state.playlists.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            title = stringResource(R.string.playlists_empty_title),
                            hint = stringResource(R.string.playlists_empty_hint),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                items(state.playlists, key = { it.id }) { playlist ->
                    CollectionRow(
                        title = playlist.name,
                        subtitle = pluralStringResource(R.plurals.tracks_count, playlist.trackCount, playlist.trackCount),
                        coverTrackId = playlist.firstTrackId,
                        onClick = { onPlaylist(playlist.id) },
                        trailing = {
                            PlaylistMenu(
                                onRename = { renaming = playlist },
                                onDelete = { deleting = playlist },
                            )
                        },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.playlist_new))
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { viewModel.create(it); creating = false },
        )
    }
    renaming?.let { playlist ->
        NameDialog(
            title = stringResource(R.string.playlist_rename),
            initial = playlist.name,
            onDismiss = { renaming = null },
            onConfirm = { viewModel.rename(playlist.id, it); renaming = null },
        )
    }
    deleting?.let { playlist ->
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete),
            text = stringResource(R.string.playlist_delete_confirm, playlist.name),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = { viewModel.delete(playlist.id); deleting = null },
        )
    }
}

@Composable
private fun AutoRow(title: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    CollectionRow(
        title = title,
        subtitle = pluralStringResource(R.plurals.tracks_count, count, count),
        coverTrackId = null,
        onClick = onClick,
        leadingIcon = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
    )
}

@Composable
fun PlaylistMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem(R.string.playlist_rename, Icons.Rounded.Edit) { expanded = false; onRename() }
            MenuItem(R.string.playlist_delete, Icons.Rounded.Delete) { expanded = false; onDelete() }
        }
    }
}
