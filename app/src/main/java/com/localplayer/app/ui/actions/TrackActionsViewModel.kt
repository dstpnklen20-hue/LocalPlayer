package com.localplayer.app.ui.actions

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.app.data.PlaylistRepository
import com.localplayer.app.data.db.PlaylistSummary
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.model.Track
import com.localplayer.app.data.model.uri
import com.localplayer.app.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Подробности о файле для диалога «Подробнее». */
data class TrackDetails(
    val track: Track,
    val bitrateKbps: Int?,
)

/** Данные и операции для меню «⋮»: плейлисты, подробности, очистка после удаления. */
@HiltViewModel
class TrackActionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val mediaStoreRepository: MediaStoreRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistSummary>> = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addToPlaylist(playlistId: Long, tracks: List<Track>) {
        viewModelScope.launch { playlistRepository.addTracks(playlistId, tracks.map { it.id }) }
    }

    fun createPlaylistWith(name: String, tracks: List<Track>) {
        viewModelScope.launch {
            val id = playlistRepository.create(name)
            playlistRepository.addTracks(id, tracks.map { it.id })
        }
    }

    /** Файлы удалены системным диалогом: чистим плейлисты и пересканируем. */
    fun onDeleted(tracks: List<Track>) {
        viewModelScope.launch {
            playlistRepository.removeTracksEverywhere(tracks.map { it.id })
            mediaStoreRepository.refresh()
        }
    }

    suspend fun loadDetails(track: Track): TrackDetails = withContext(io) {
        TrackDetails(track, readBitrate(track))
    }

    private fun readBitrate(track: Track): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.contentResolver.query(
                    track.uri,
                    arrayOf(MediaStore.Audio.Media.BITRATE),
                    null,
                    null,
                    null,
                )?.use { c -> if (c.moveToFirst() && !c.isNull(0)) return c.getInt(0) / 1000 }
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, track.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
