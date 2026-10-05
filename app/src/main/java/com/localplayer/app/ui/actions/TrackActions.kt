package com.localplayer.app.ui.actions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.localplayer.app.R
import com.localplayer.app.data.model.Track
import com.localplayer.app.data.model.uri
import com.localplayer.app.ui.components.LocalPlaybackActions
import com.localplayer.app.ui.components.PlaybackActions
import com.localplayer.app.ui.navigation.LocalSnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Действия с треками (F18–F19), доступные из любого списка через [LocalTrackActions]. */
@Stable
class TrackActionsController internal constructor(
    private val context: Context,
    private val playback: PlaybackActions,
    private val scope: CoroutineScope,
    private val showMessage: suspend (String) -> Unit,
    private val navigateToAlbum: (Long) -> Unit,
    private val navigateToArtist: (String) -> Unit,
    private val requestDelete: (List<Track>) -> Unit,
) {
    internal var addToPlaylistTarget by mutableStateOf<List<Track>?>(null)
    internal var detailsTarget by mutableStateOf<Track?>(null)

    /** Удаление через системный диалог доступно с Android 11 (MediaStore.createDeleteRequest). */
    val canDelete: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun play(tracks: List<Track>) = playback.play(tracks)

    fun playNext(tracks: List<Track>) {
        playback.playNext(tracks)
        message(context.resources.getQuantityString(R.plurals.msg_play_next, tracks.size, tracks.size))
    }

    fun addToQueue(tracks: List<Track>) {
        playback.addToQueue(tracks)
        message(context.resources.getQuantityString(R.plurals.msg_added_to_queue, tracks.size, tracks.size))
    }

    fun addToPlaylist(tracks: List<Track>) {
        if (tracks.isNotEmpty()) addToPlaylistTarget = tracks
    }

    fun goToAlbum(track: Track) = navigateToAlbum(track.albumId)

    fun goToArtist(track: Track) = navigateToArtist(track.artist)

    fun showDetails(track: Track) {
        detailsTarget = track
    }

    /** «Поделиться» — сами файлы через системный share sheet. */
    fun share(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val intent = if (tracks.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, tracks.first().uri)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(tracks.map { it.uri }))
        }
        intent.type = tracks.map { it.mimeType }.distinct().singleOrNull()?.takeIf { it.isNotEmpty() } ?: "audio/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, context.getString(R.string.action_share))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun delete(tracks: List<Track>) {
        if (canDelete && tracks.isNotEmpty()) requestDelete(tracks)
    }

    internal fun message(text: String) {
        scope.launch { showMessage(text) }
    }
}

val LocalTrackActions = staticCompositionLocalOf<TrackActionsController?> { null }

/** Хост действий: держит диалоги и запуск системного запроса на удаление. */
@Composable
fun TrackActionsHost(
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val viewModel: TrackActionsViewModel = hiltViewModel()
    val context = LocalContext.current
    val playback = LocalPlaybackActions.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<List<Track>>(emptyList()) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val deleted = pendingDelete
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleted(deleted)
            scope.launch {
                snackbar.showSnackbar(
                    context.resources.getQuantityString(R.plurals.msg_deleted, deleted.size, deleted.size),
                )
            }
        }
        pendingDelete = emptyList()
    }

    val controller = remember(context, playback) {
        TrackActionsController(
            context = context,
            playback = playback,
            scope = scope,
            showMessage = { snackbar.showSnackbar(it) },
            navigateToAlbum = onNavigateToAlbum,
            navigateToArtist = onNavigateToArtist,
            requestDelete = { tracks ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pendingDelete = tracks
                    // F19: удаление только с подтверждением через системный диалог
                    val sender = MediaStore.createDeleteRequest(context.contentResolver, tracks.map { it.uri }).intentSender
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            },
        )
    }

    CompositionLocalProvider(LocalTrackActions provides controller) {
        content()
    }

    controller.addToPlaylistTarget?.let { tracks ->
        AddToPlaylistDialog(
            viewModel = viewModel,
            onDismiss = { controller.addToPlaylistTarget = null },
            onSelect = { playlist ->
                viewModel.addToPlaylist(playlist.id, tracks)
                controller.addToPlaylistTarget = null
                controller.message(context.getString(R.string.msg_added_to_playlist, playlist.name))
            },
            onCreate = { name ->
                viewModel.createPlaylistWith(name, tracks)
                controller.addToPlaylistTarget = null
                controller.message(context.getString(R.string.msg_added_to_playlist, name))
            },
        )
    }
    controller.detailsTarget?.let { track ->
        TrackDetailsDialog(track = track, viewModel = viewModel, onDismiss = { controller.detailsTarget = null })
    }
}

/** Кнопка «⋮» с меню действий над треком. [extraItems] — например, «Убрать из плейлиста». */
@Composable
fun TrackMenuButton(
    track: Track,
    extraItems: @Composable (dismiss: () -> Unit) -> Unit = {},
) {
    val actions = LocalTrackActions.current ?: return
    var expanded by remember { mutableStateOf(false) }
    // Box — якорь меню: без него оно открывается от края всей строки
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val dismiss = { expanded = false }
            MenuItem(R.string.action_play_next, Icons.Rounded.QueuePlayNext) { dismiss(); actions.playNext(listOf(track)) }
            MenuItem(R.string.action_add_to_queue, Icons.AutoMirrored.Rounded.QueueMusic) { dismiss(); actions.addToQueue(listOf(track)) }
            MenuItem(R.string.action_add_to_playlist, Icons.AutoMirrored.Rounded.PlaylistAdd) { dismiss(); actions.addToPlaylist(listOf(track)) }
            extraItems(dismiss)
            MenuItem(R.string.action_go_to_album, Icons.Rounded.Album) { dismiss(); actions.goToAlbum(track) }
            MenuItem(R.string.action_go_to_artist, Icons.Rounded.Person) { dismiss(); actions.goToArtist(track) }
            MenuItem(R.string.action_details, Icons.Rounded.Info) { dismiss(); actions.showDetails(track) }
            MenuItem(R.string.action_share, Icons.Rounded.Share) { dismiss(); actions.share(listOf(track)) }
            if (actions.canDelete) {
                MenuItem(R.string.action_delete_file, Icons.Rounded.Delete) { dismiss(); actions.delete(listOf(track)) }
            }
        }
    }
}

@Composable
fun MenuItem(label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** Множественный выбор треков (долгое нажатие). Порядок выбора сохраняется. */
@Stable
class TrackSelection {
    private val selected = mutableStateMapOf<Long, Track>()

    val isActive: Boolean get() = selected.isNotEmpty()
    val count: Int get() = selected.size
    val tracks: List<Track> get() = selected.values.toList()

    fun isSelected(id: Long): Boolean = selected.containsKey(id)

    fun toggle(track: Track) {
        if (selected.remove(track.id) == null) selected[track.id] = track
    }

    fun selectAll(tracks: List<Track>) = tracks.forEach { selected[it.id] = it }

    fun clear() = selected.clear()
}
