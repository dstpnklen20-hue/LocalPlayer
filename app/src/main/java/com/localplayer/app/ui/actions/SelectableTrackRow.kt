package com.localplayer.app.ui.actions

import androidx.compose.runtime.Composable
import com.localplayer.app.data.model.Track
import com.localplayer.app.ui.components.LocalCurrentTrackId
import com.localplayer.app.ui.components.LocalPlaybackActions
import com.localplayer.app.ui.components.TrackRow
import com.localplayer.app.util.formatDuration

/**
 * Строка трека в списке: тап — играть (F8, очередь = [tracks]), в режиме выбора — отметить;
 * долгое нажатие — множественный выбор; «⋮» — меню действий.
 */
@Composable
fun SelectableTrackRow(
    track: Track,
    tracks: List<Track>,
    index: Int,
    selection: TrackSelection?,
    number: Int? = null,
    subtitle: String = "${track.artist} · ${formatDuration(track.durationMs)}",
) {
    val playback = LocalPlaybackActions.current
    val selecting = selection?.isActive == true
    TrackRow(
        track = track,
        isCurrent = track.id == LocalCurrentTrackId.current,
        onClick = { if (selecting) selection.toggle(track) else playback.play(tracks, index) },
        onLongClick = selection?.let { { it.toggle(track) } },
        selectionMode = selecting,
        selected = selection?.isSelected(track.id) == true,
        number = number,
        subtitle = subtitle,
        trailing = { TrackMenuButton(track) },
    )
}
