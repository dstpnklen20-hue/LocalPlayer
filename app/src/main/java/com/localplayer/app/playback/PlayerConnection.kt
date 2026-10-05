package com.localplayer.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.localplayer.app.data.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class QueueEntry(
    val uid: Long,
    /** Индекс в плейлисте ExoPlayer (порядок окон, не порядок перемешивания). */
    val windowIndex: Int,
    val trackId: Long,
    val title: String,
    val artist: String,
)

@Immutable
data class PlayerState(
    val connected: Boolean = false,
    val currentTrackId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Очередь в порядке воспроизведения (с учётом перемешивания). */
    val queue: List<QueueEntry> = emptyList(),
    val currentWindowIndex: Int = C.INDEX_UNSET,
) {
    val hasMedia: Boolean get() = currentTrackId != null
}

/** Ошибка воспроизведения для Snackbar. */
data class PlaybackErrorEvent(val title: String)

/**
 * Обёртка над MediaController для UI. ExoPlayer здесь нет — только команды в PlaybackService.
 * Подключается, пока Activity видима ([connect]/[disconnect]).
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerFlow = MutableStateFlow<MediaController?>(null)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<PlaybackErrorEvent>(extraBufferCapacity = 4)
    val errors: SharedFlow<PlaybackErrorEvent> = _errors.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player, timelineChanged = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            ))
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = controllerFlow.value?.mediaMetadata?.title?.toString().orEmpty()
            _errors.tryEmit(PlaybackErrorEvent(title))
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            if (controllerFuture !== future) {
                controller.release()
                return@addListener
            }
            controller.addListener(listener)
            controllerFlow.value = controller
            updateState(controller, timelineChanged = true)
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        controllerFlow.value?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controllerFlow.value = null
        _state.value = _state.value.copy(connected = false)
    }

    /** Текущая позиция: опрашивается, пока на неё кто-то подписан. */
    fun positionFlow(intervalMs: Long = 500L): Flow<Long> = flow {
        while (true) {
            emit(controllerFlow.value?.currentPosition ?: 0L)
            delay(intervalMs)
        }
    }

    private fun updateState(player: Player, timelineChanged: Boolean) {
        val item = player.currentMediaItem
        val old = _state.value
        val queue = if (timelineChanged || !old.connected) buildQueue(player) else old.queue
        _state.value = PlayerState(
            connected = true,
            currentTrackId = item?.trackId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            album = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }
                ?: item?.mediaMetadata?.durationMs ?: 0L,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = queue,
            currentWindowIndex = player.currentMediaItemIndex,
        )
    }

    /** Очередь в порядке воспроизведения: при перемешивании идём по shuffle-порядку таймлайна. */
    private fun buildQueue(player: Player): List<QueueEntry> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val shuffle = player.shuffleModeEnabled
        val result = ArrayList<QueueEntry>(timeline.windowCount)
        val window = Timeline.Window()
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET && result.size < timeline.windowCount) {
            val mediaItem: MediaItem = timeline.getWindow(index, window).mediaItem
            result += QueueEntry(
                uid = mediaItem.queueUid,
                windowIndex = index,
                trackId = mediaItem.trackId ?: -1L,
                title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            )
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        return result
    }

    /** Выполнить команду, когда контроллер подключится (обычно мгновенно). */
    private fun withController(block: (MediaController) -> Unit) {
        val controller = controllerFlow.value
        if (controller != null) {
            block(controller)
        } else {
            connect()
            scope.launch { block(controllerFlow.filterNotNull().first()) }
        }
    }

    // region Команды

    fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean) = withController { c ->
        if (tracks.isEmpty()) return@withController
        val start = if (shuffle) tracks.indices.random() else startIndex.coerceIn(0, tracks.lastIndex)
        c.setMediaItems(tracks.map { it.toMediaItem() }, start, 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    fun playNext(tracks: List<Track>) = withController { c ->
        if (tracks.isEmpty()) return@withController
        val items = tracks.map { it.toMediaItem() }
        if (c.mediaItemCount == 0) {
            c.setMediaItems(items)
            c.prepare()
        } else {
            // TODO: при перемешивании новые элементы встают в случайное место shuffle-порядка
            c.addMediaItems(c.currentMediaItemIndex + 1, items)
        }
    }

    fun addToQueue(tracks: List<Track>) = withController { c ->
        if (tracks.isEmpty()) return@withController
        val items = tracks.map { it.toMediaItem() }
        if (c.mediaItemCount == 0) {
            c.setMediaItems(items)
            c.prepare()
        } else {
            c.addMediaItems(items)
        }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }

    /** F9: если прошло больше 3 с — в начало трека (порог maxSeekToPreviousPosition = 3 с по умолчанию). */
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    /** F10: выкл → вся очередь → один трек → выкл. */
    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun skipToQueueItem(windowIndex: Int) = withController { c ->
        c.seekToDefaultPosition(windowIndex)
        c.prepare()
        c.play()
    }

    fun removeQueueItem(windowIndex: Int) = withController { it.removeMediaItem(windowIndex) }

    fun moveQueueItem(fromWindowIndex: Int, toWindowIndex: Int) =
        withController { it.moveMediaItem(fromWindowIndex, toWindowIndex) }

    /** Плавное затухание для таймера сна. */
    fun setVolume(volume: Float) = withController { it.volume = volume }

    fun pause() = withController { it.pause() }

    // endregion
}
