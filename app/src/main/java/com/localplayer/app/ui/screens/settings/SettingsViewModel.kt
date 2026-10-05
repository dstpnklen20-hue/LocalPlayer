package com.localplayer.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.model.AppSettings
import com.localplayer.app.data.model.ThemeMode
import com.localplayer.app.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    mediaStoreRepository: MediaStoreRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Папки с музыкой (без фильтров), которые ещё можно исключить — для диалога добавления. */
    val folderCandidates: StateFlow<List<String>> = combine(
        mediaStoreRepository.allTracks.filterNotNull(),
        settingsRepository.settings,
    ) { tracks, settings ->
        tracks.map { it.folderPath }.distinct().filter { it !in settings.excludedFolders }.sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setThemeMode(mode: ThemeMode) = launch { setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { setDynamicColor(enabled) }
    fun setMinDuration(seconds: Int) = launch { setMinDurationSec(seconds) }
    fun addExcludedFolder(path: String) = launch { if (path.isNotBlank()) addExcludedFolder(path) }
    fun removeExcludedFolder(path: String) = launch { removeExcludedFolder(path) }
    fun setPauseOnDisconnect(enabled: Boolean) = launch { setPauseOnDisconnect(enabled) }
    fun setResumeOnBluetooth(enabled: Boolean) = launch { setResumeOnBluetooth(enabled) }

    private fun launch(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }
}
