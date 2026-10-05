package com.localplayer.app.data.mediastore

import com.localplayer.app.data.model.Track
import kotlinx.coroutines.flow.Flow

/** Источник треков. В приложении — MediaStore, в тестах — фейк. */
interface MediaStoreSource {
    /** Все треки с IS_MUSIC = 1. Бросает SecurityException без разрешения. */
    suspend fun queryTracks(): List<Track>

    /** Сигнал «в MediaStore что-то изменилось». */
    fun changes(): Flow<Unit>
}
