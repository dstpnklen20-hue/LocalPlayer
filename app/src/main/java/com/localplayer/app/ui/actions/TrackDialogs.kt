package com.localplayer.app.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.app.R
import com.localplayer.app.data.db.PlaylistSummary
import com.localplayer.app.data.model.Track
import com.localplayer.app.util.formatDuration
import com.localplayer.app.util.formatSize

/** F15: «В плейлист» — выбор существующего или создание нового. */
@Composable
fun AddToPlaylistDialog(
    viewModel: TrackActionsViewModel,
    onDismiss: () -> Unit,
    onSelect: (PlaylistSummary) -> Unit,
    onCreate: (String) -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = onCreate,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_to_playlist)) },
        text = {
            LazyColumn {
                item {
                    DialogRow(
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        title = stringResource(R.string.playlist_new),
                        onClick = { creating = true },
                    )
                }
                items(playlists, key = { it.id }) { playlist ->
                    DialogRow(
                        icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null) },
                        title = playlist.name,
                        subtitle = pluralStringResource(R.plurals.tracks_count, playlist.trackCount, playlist.trackCount),
                        onClick = { onSelect(playlist) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DialogRow(icon: @Composable () -> Unit, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.size(40.dp), verticalAlignment = Alignment.CenterVertically) { icon() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Ввод названия плейлиста (создание и переименование). */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val valid = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name.trim()) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = valid) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** F18: «Подробнее» — формат, битрейт, размер, путь. */
@Composable
fun TrackDetailsDialog(track: Track, viewModel: TrackActionsViewModel, onDismiss: () -> Unit) {
    val details by produceState<TrackDetails?>(null, track) { value = viewModel.loadDetails(track) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                DetailLine(R.string.details_artist, track.artist)
                DetailLine(R.string.details_album, track.album)
                if (track.year > 0) DetailLine(R.string.details_year, track.year.toString())
                DetailLine(R.string.details_duration, formatDuration(track.durationMs))
                DetailLine(R.string.details_format, track.mimeType.ifEmpty { "—" })
                DetailLine(
                    R.string.details_bitrate,
                    details?.bitrateKbps?.let { stringResource(R.string.details_kbps, it) } ?: "—",
                )
                DetailLine(R.string.details_size, formatSize(track.sizeBytes))
                DetailLine(R.string.details_path, track.filePath)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun DetailLine(label: Int, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Подтверждение необратимого действия (удаление плейлиста). */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
