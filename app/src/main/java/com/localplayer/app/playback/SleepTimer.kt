package com.localplayer.app.playback

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class SleepTimerState(
    val active: Boolean = false,
    /** Режим «до конца трека». */
    val endOfTrack: Boolean = false,
    /** Сколько осталось (для режима с минутами), мс. */
    val remainingMs: Long = 0L,
)

/**
 * F13: таймер сна. Работает с плеером внутри PlaybackService (а не через UI),
 * чтобы сработать и при выключенном экране. За 10 с до остановки — плавное затухание.
 */
@Singleton
class SleepTimer @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var job: Job? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private val endListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Плеер сам встал на паузу в конце трека — таймер «до конца трека» отработал
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _state.value.endOfTrack
            ) {
                cancel()
            }
        }
    }

    /** Вызывается сервисом при создании плеера. */
    fun attach(player: ExoPlayer) {
        this.player = player
        player.addListener(endListener)
    }

    fun detach() {
        cancel()
        player?.removeListener(endListener)
        player = null
    }

    fun start(minutes: Int) {
        val p = player ?: return
        stopJob(p)
        val endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        _state.value = SleepTimerState(active = true, remainingMs = minutes * 60_000L)
        job = scope.launch {
            while (isActive) {
                val remaining = endAt - SystemClock.elapsedRealtime()
                _state.value = SleepTimerState(active = true, remainingMs = remaining.coerceAtLeast(0))
                if (remaining <= 0) {
                    p.pause()
                    cancel()
                    break
                }
                p.volume = fadeVolume(remaining)
                delay(TICK_MS)
            }
        }
    }

    fun startEndOfTrack() {
        val p = player ?: return
        stopJob(p)
        p.pauseAtEndOfMediaItems = true
        _state.value = SleepTimerState(active = true, endOfTrack = true)
        job = scope.launch {
            while (isActive) {
                val duration = p.duration
                if (duration > 0 && p.isPlaying) {
                    p.volume = fadeVolume(duration - p.currentPosition)
                }
                delay(TICK_MS)
            }
        }
    }

    fun cancel() {
        player?.let { stopJob(it) }
        _state.value = SleepTimerState()
    }

    private fun stopJob(p: ExoPlayer) {
        job?.cancel()
        job = null
        p.pauseAtEndOfMediaItems = false
        p.volume = 1f
    }

    companion object {
        const val FADE_MS = 10_000L
        private const val TICK_MS = 250L

        /** Громкость при затухании: 1 до последних 10 с, затем линейно до 0. */
        fun fadeVolume(remainingMs: Long): Float =
            if (remainingMs >= FADE_MS) 1f else (remainingMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }
}
