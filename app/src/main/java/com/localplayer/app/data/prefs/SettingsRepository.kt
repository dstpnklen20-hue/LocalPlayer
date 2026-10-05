package com.localplayer.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.localplayer.app.data.model.AppSettings
import com.localplayer.app.data.model.ThemeMode
import com.localplayer.app.data.model.TrackSort
import com.localplayer.app.data.model.TrackSortField
import com.localplayer.app.di.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @SettingsStore private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[DYNAMIC_COLOR] = enabled }

    suspend fun setMinDurationSec(seconds: Int) = dataStore.edit { it[MIN_DURATION] = seconds.coerceAtLeast(0) }

    suspend fun addExcludedFolder(path: String) = dataStore.edit {
        it[EXCLUDED] = (it[EXCLUDED] ?: emptySet()) + path.trim()
    }

    suspend fun removeExcludedFolder(path: String) = dataStore.edit {
        it[EXCLUDED] = (it[EXCLUDED] ?: emptySet()) - path
    }

    suspend fun setPauseOnDisconnect(enabled: Boolean) = dataStore.edit { it[PAUSE_ON_DISCONNECT] = enabled }

    suspend fun setResumeOnBluetooth(enabled: Boolean) = dataStore.edit { it[RESUME_ON_BT] = enabled }

    suspend fun setTrackSort(sort: TrackSort) = dataStore.edit {
        it[SORT_FIELD] = sort.field.name
        it[SORT_ASC] = sort.ascending
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOrDefault(this[THEME], defaults.themeMode),
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            minDurationSec = this[MIN_DURATION] ?: defaults.minDurationSec,
            excludedFolders = this[EXCLUDED] ?: defaults.excludedFolders,
            pauseOnDisconnect = this[PAUSE_ON_DISCONNECT] ?: defaults.pauseOnDisconnect,
            resumeOnBluetooth = this[RESUME_ON_BT] ?: defaults.resumeOnBluetooth,
            trackSort = TrackSort(
                field = enumOrDefault(this[SORT_FIELD], TrackSortField.TITLE),
                ascending = this[SORT_ASC] ?: true,
            ),
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val MIN_DURATION = intPreferencesKey("min_duration_sec")
        val EXCLUDED = stringSetPreferencesKey("excluded_folders")
        val PAUSE_ON_DISCONNECT = booleanPreferencesKey("pause_on_disconnect")
        val RESUME_ON_BT = booleanPreferencesKey("resume_on_bluetooth")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ASC = booleanPreferencesKey("sort_asc")
    }
}
