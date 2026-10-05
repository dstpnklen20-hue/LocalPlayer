package com.localplayer.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.localplayer.app.data.model.Track

/** Команды воспроизведения, доступные любому экрану. */
interface PlaybackActions {
    /** F8: запустить трек, очередью становится весь переданный список. */
    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false)
    fun playNext(tracks: List<Track>)
    fun addToQueue(tracks: List<Track>)
}

private object NoOpPlaybackActions : PlaybackActions {
    override fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean) = Unit
    override fun playNext(tracks: List<Track>) = Unit
    override fun addToQueue(tracks: List<Track>) = Unit
}

val LocalPlaybackActions = staticCompositionLocalOf<PlaybackActions> { NoOpPlaybackActions }

/** id играющего трека — для подсветки в списках. */
val LocalCurrentTrackId = compositionLocalOf<Long?> { null }
