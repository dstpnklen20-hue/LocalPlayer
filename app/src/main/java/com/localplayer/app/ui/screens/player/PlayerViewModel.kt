package com.localplayer.app.ui.screens.player

import androidx.lifecycle.ViewModel
import com.localplayer.app.data.model.Track
import com.localplayer.app.playback.PlaybackErrorEvent
import com.localplayer.app.playback.PlayerConnection
import com.localplayer.app.playback.PlayerState
import com.localplayer.app.ui.components.PlaybackActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** ViewModel мини-плеера, полного плеера и очереди. Плеером управляет только через MediaController. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val connection: PlayerConnection,
) : ViewModel(), PlaybackActions {

    val state: StateFlow<PlayerState> = connection.state
    val errors: SharedFlow<PlaybackErrorEvent> = connection.errors
    val position: Flow<Long> = connection.positionFlow()

    override fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean) =
        connection.play(tracks, startIndex, shuffle)

    override fun playNext(tracks: List<Track>) = connection.playNext(tracks)

    override fun addToQueue(tracks: List<Track>) = connection.addToQueue(tracks)

    fun togglePlayPause() = connection.togglePlayPause()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun toggleShuffle() = connection.setShuffle(!state.value.shuffle)
    fun cycleRepeat() = connection.cycleRepeat()
    fun skipTo(windowIndex: Int) = connection.skipToQueueItem(windowIndex)
    fun removeFromQueue(windowIndex: Int) = connection.removeQueueItem(windowIndex)
    fun moveInQueue(from: Int, to: Int) = connection.moveQueueItem(from, to)
}
