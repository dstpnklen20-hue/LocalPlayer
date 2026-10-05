package com.localplayer.app.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.app.R
import com.localplayer.app.ui.components.CollectionRow
import com.localplayer.app.ui.components.EmptyState
import com.localplayer.app.ui.components.LocalCurrentTrackId
import com.localplayer.app.ui.components.LocalPlaybackActions
import com.localplayer.app.ui.components.TrackRow
import com.localplayer.app.ui.screens.detail.SectionTitle
import com.localplayer.app.ui.actions.TrackMenuButton
import com.localplayer.app.ui.screens.library.artistSubtitle

@Composable
fun SearchScreen(
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val playback = LocalPlaybackActions.current
    val currentId = LocalCurrentTrackId.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Автофокус на поле ввода при открытии экрана
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )

        when {
            query.isBlank() -> EmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.search_start_title),
                hint = stringResource(R.string.search_start_hint),
            )
            results.query.isNotEmpty() && results.isEmpty -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.not_found_title),
                hint = stringResource(R.string.search_not_found_hint),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (results.artists.isNotEmpty()) {
                    item(key = "h_artists") { SectionTitle(stringResource(R.string.tab_artists)) }
                    items(results.artists.take(MAX_GROUP), key = { "ar:" + it.name }) { artist ->
                        CollectionRow(
                            title = artist.name,
                            subtitle = artistSubtitle(artist),
                            coverTrackId = artist.coverTrackId,
                            circleCover = true,
                            onClick = { onArtist(artist.name) },
                        )
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item(key = "h_albums") { SectionTitle(stringResource(R.string.tab_albums)) }
                    items(results.albums.take(MAX_GROUP), key = { "al:" + it.id }) { album ->
                        CollectionRow(
                            title = album.title,
                            subtitle = album.artist + " · " +
                                pluralStringResource(R.plurals.tracks_count, album.tracks.size, album.tracks.size),
                            coverTrackId = album.coverTrackId,
                            onClick = { onAlbum(album.id) },
                        )
                    }
                }
                if (results.tracks.isNotEmpty()) {
                    item(key = "h_tracks") { SectionTitle(stringResource(R.string.tab_tracks)) }
                    itemsIndexed(results.tracks, key = { _, t -> "tr:" + t.id }) { i, track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.id == currentId,
                            onClick = { playback.play(results.tracks, i) },
                            trailing = { TrackMenuButton(track) },
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_GROUP = 20
