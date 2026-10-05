package com.localplayer.app.data.mediastore

import com.localplayer.app.data.model.Library
import com.localplayer.app.data.model.Track
import com.localplayer.app.data.prefs.SettingsRepository
import com.localplayer.app.di.ApplicationScope
import com.localplayer.app.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая точка доступа к библиотеке. Хранит «сырые» треки из MediaStore и
 * отдаёт отфильтрованную по настройкам (F2, F21) и сгруппированную [library].
 */
@OptIn(FlowPreview::class)
@Singleton
class MediaStoreRepository @Inject constructor(
    private val source: MediaStoreSource,
    settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /** null — ещё ни разу не сканировали. */
    private val rawTracks = MutableStateFlow<List<Track>?>(null)
    private val refreshMutex = Mutex()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Все треки без фильтров — нужны, например, для восстановления очереди по id. */
    val allTracks: StateFlow<List<Track>?> = rawTracks.asStateFlow()

    /** Отфильтрованная библиотека; null — идёт первое сканирование. */
    val library: StateFlow<Library?> = combine(
        rawTracks.filterNotNull(),
        settingsRepository.settings
            .map { it.minDurationSec to it.excludedFolders }
            .distinctUntilChanged(),
    ) { tracks, (minDuration, excluded) ->
        LibraryBuilder.build(LibraryBuilder.filter(tracks, minDuration, excluded))
    }
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // F3: автообновление при изменениях MediaStore (debounce — изменения приходят пачками)
        scope.launch {
            source.changes().debounce(CHANGE_DEBOUNCE_MS).collect {
                if (rawTracks.value != null) refresh()
            }
        }
    }

    /** Пересканировать MediaStore. Без разрешения библиотека просто пустая. */
    suspend fun refresh() {
        refreshMutex.withLock {
            _isRefreshing.value = true
            try {
                rawTracks.value = try {
                    source.queryTracks()
                } catch (_: SecurityException) {
                    emptyList()
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Первое сканирование, если его ещё не было. */
    fun ensureLoaded() {
        if (rawTracks.value == null) scope.launch { refresh() }
    }

    suspend fun awaitLibrary(): Library {
        ensureLoaded()
        return library.filterNotNull().first()
    }

    suspend fun awaitAllTracks(): List<Track> {
        ensureLoaded()
        return rawTracks.filterNotNull().first()
    }

    companion object {
        private const val CHANGE_DEBOUNCE_MS = 1000L
    }
}
