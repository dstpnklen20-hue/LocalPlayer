package com.localplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.localplayer.app.MainActivity
import com.localplayer.app.data.PlaylistRepository
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.prefs.SessionRepository
import com.localplayer.app.data.prefs.SettingsRepository
import com.localplayer.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Единственный владелец ExoPlayer (раздел 6 ТЗ). Музыка переживает закрытие экрана и поворот;
 * уведомление, экран блокировки и кнопки гарнитуры Media3 обслуживает сам.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var mediaStoreRepository: MediaStoreRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var sleepTimer: SleepTimer

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    private var consecutiveErrors = 0
    private var queueDirty = true
    /** Прослушивание текущего трека уже засчитано (сбрасывается при смене/повторе трека). */
    private var playCounted = false
    private var restoreJob: Job? = null
    private var resumeOnBluetooth = false
    private var audioDeviceCallback: AudioDeviceCallback? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true, // пауза при звонке, приглушение при навигаторе
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PlayerListener())
        sleepTimer.attach(player)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(CacheBitmapLoader(CoverBitmapLoader(this)))
            .setCallback(SessionCallback())
            .build()

        observeSettings()
        registerBluetoothCallback()
        restoreJob = scope.launch { restoreSession() }
        startPeriodicSave()
        startPlayCounter()
    }

    /** F17: прослушивание засчитывается после 50% длительности. */
    private fun startPlayCounter() {
        scope.launch {
            while (isActive) {
                delay(PLAY_COUNT_CHECK_MS)
                val duration = player.duration
                if (!playCounted && player.isPlaying && duration > 0 && player.currentPosition * 2 >= duration) {
                    playCounted = true
                    val trackId = player.currentMediaItem?.trackId ?: continue
                    appScope.launch { playlistRepository.recordPlay(trackId) }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Свайп из недавних: на паузе — останавливаемся, при игре — продолжаем. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            saveSession()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveSession()
        sleepTimer.detach()
        audioDeviceCallback?.let { getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(it) }
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    // region Сессия (F12)

    private suspend fun restoreSession() {
        val saved = sessionRepository.load() ?: return
        val byId = mediaStoreRepository.awaitAllTracks().associateBy { it.id }
        // Пользователь успел что-то запустить, пока мы читали диск — не перетираем
        if (player.mediaItemCount > 0) return
        val currentId = saved.queueIds.getOrNull(saved.currentIndex)
        // Удалённые файлы просто выпадают из очереди
        val tracks = saved.queueIds.mapNotNull { byId[it] }
        if (tracks.isEmpty()) return
        val index = tracks.indexOfFirst { it.id == currentId }
        val startIndex = if (index >= 0) index else 0
        val position = if (index >= 0) saved.positionMs else 0L
        player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, position)
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = saved.repeatMode
        player.playWhenReady = false // без автозапуска
        player.prepare()
    }

    /** Позиция и очередь сохраняются каждые 5 с во время игры (а также на паузе и при смене трека). */
    private fun startPeriodicSave() {
        scope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                if (player.isPlaying) saveSession()
            }
        }
    }

    private fun saveSession() {
        if (restoreJob?.isActive == true) return // не затираем сохранённое до окончания восстановления
        val count = player.mediaItemCount
        val ids = if (queueDirty) List(count) { player.getMediaItemAt(it).trackId ?: -1L } else null
        queueDirty = false
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val shuffle = player.shuffleModeEnabled
        val repeat = player.repeatMode
        // appScope — чтобы запись завершилась даже при уничтожении сервиса
        appScope.launch {
            if (ids != null) sessionRepository.saveQueue(ids)
            sessionRepository.savePosition(index, position, shuffle, repeat)
        }
    }

    // endregion

    private fun observeSettings() {
        scope.launch {
            settingsRepository.settings
                .map { it.pauseOnDisconnect to it.resumeOnBluetooth }
                .distinctUntilChanged()
                .collect { (pauseOnDisconnect, resumeBt) ->
                    player.setHandleAudioBecomingNoisy(pauseOnDisconnect) // F22
                    resumeOnBluetooth = resumeBt
                }
        }
    }

    /** F22: возобновление при подключении Bluetooth-гарнитуры (если включено в настройках). */
    private fun registerBluetoothCallback() {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (!resumeOnBluetooth) return
                val bluetooth = addedDevices.any { it.isSink && it.isBluetoothOutput() }
                if (bluetooth && !player.isPlaying && player.mediaItemCount > 0) {
                    player.prepare()
                    player.play()
                }
            }
        }
        // Колбэк сразу сообщает об уже подключённых устройствах — игнорируем первый вызов
        var initial = true
        val wrapper = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (initial) {
                    initial = false
                    return
                }
                callback.onAudioDevicesAdded(addedDevices)
            }
        }
        getSystemService(AudioManager::class.java).registerAudioDeviceCallback(wrapper, null)
        audioDeviceCallback = wrapper
    }

    private fun AudioDeviceInfo.isBluetoothOutput(): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    private inner class PlayerListener : Player.Listener {

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                queueDirty = true
                saveSession()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            playCounted = false
            saveSession()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveSession()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) consecutiveErrors = 0
        }

        /** Битый или удалённый файл: переходим к следующему, но не более 3 ошибок подряд. */
        override fun onPlayerError(error: PlaybackException) {
            consecutiveErrors++
            if (consecutiveErrors < MAX_CONSECUTIVE_ERRORS && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                consecutiveErrors = 0
                player.pause()
                player.prepare() // выводим плеер из состояния ошибки, чтобы можно было выбрать другой трек
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withResolvedUri() }.toMutableList())

        /** Возобновление из системы (Bluetooth, карточка медиа на Android 13+) — по сохранённой сессии. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val saved = sessionRepository.load()
                ?: throw UnsupportedOperationException("No saved session")
            val byId = mediaStoreRepository.awaitAllTracks().associateBy { it.id }
            val tracks = saved.queueIds.mapNotNull { byId[it] }
            if (tracks.isEmpty()) throw UnsupportedOperationException("Saved queue is empty")
            val currentId = saved.queueIds.getOrNull(saved.currentIndex)
            val index = tracks.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            MediaSession.MediaItemsWithStartPosition(
                tracks.map { it.toMediaItem() },
                index,
                if (tracks[index].id == currentId) saved.positionMs else 0L,
            )
        }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 5_000L
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val PLAY_COUNT_CHECK_MS = 2_000L
    }
}
