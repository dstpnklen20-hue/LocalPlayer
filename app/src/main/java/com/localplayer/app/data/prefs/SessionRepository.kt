package com.localplayer.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.localplayer.app.di.SessionStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Сохранённая сессия воспроизведения (F12). */
data class SavedSession(
    val queueIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

@Singleton
class SessionRepository @Inject constructor(
    @SessionStore private val dataStore: DataStore<Preferences>,
) {
    suspend fun load(): SavedSession? {
        val prefs = try {
            dataStore.data.first()
        } catch (_: Exception) {
            return null
        }
        val ids = prefs[QUEUE]
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        if (ids.isEmpty()) return null
        return SavedSession(
            queueIds = ids,
            currentIndex = (prefs[INDEX] ?: 0).coerceIn(0, ids.lastIndex),
            positionMs = prefs[POSITION] ?: 0L,
            shuffle = prefs[SHUFFLE] ?: false,
            repeatMode = prefs[REPEAT] ?: 0,
        )
    }

    /** Очередь пишется только когда она изменилась — она может быть длинной. */
    suspend fun saveQueue(ids: List<Long>) {
        dataStore.edit { it[QUEUE] = ids.joinToString(",") }
    }

    suspend fun savePosition(index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        dataStore.edit {
            it[INDEX] = index
            it[POSITION] = positionMs
            it[SHUFFLE] = shuffle
            it[REPEAT] = repeatMode
        }
    }

    private companion object {
        val QUEUE = stringPreferencesKey("queue")
        val INDEX = intPreferencesKey("index")
        val POSITION = longPreferencesKey("position")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = intPreferencesKey("repeat")
    }
}
