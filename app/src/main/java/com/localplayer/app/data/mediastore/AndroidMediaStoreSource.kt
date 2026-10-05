package com.localplayer.app.data.mediastore

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.localplayer.app.R
import com.localplayer.app.data.model.Track
import com.localplayer.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMediaStoreSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MediaStoreSource {

    private val collection: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // VOLUME_EXTERNAL — все внешние тома, включая SD-карту
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    @Suppress("DEPRECATION") // DATA устарел, но всё ещё заполняется и нужен для пути к папке
    private val projection = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.YEAR)
        add(MediaStore.Audio.Media.DATA)
        add(MediaStore.Audio.Media.DISPLAY_NAME)
        add(MediaStore.Audio.Media.DATE_ADDED)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.MIME_TYPE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.RELATIVE_PATH)
    }.toTypedArray()

    override suspend fun queryTracks(): List<Track> = withContext(io) {
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownAlbum = context.getString(R.string.unknown_album)
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        )?.use { cursor -> readTracks(cursor, unknownArtist, unknownAlbum) } ?: emptyList()
    }

    @Suppress("DEPRECATION")
    private fun readTracks(cursor: Cursor, unknownArtist: String, unknownAlbum: String): List<Track> {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val relPathCol = cursor.getColumnIndex("relative_path")
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath

        val result = ArrayList<Track>(cursor.count)
        while (cursor.moveToNext()) {
            val data = cursor.getString(dataCol)
            val displayName = cursor.getString(nameCol) ?: data?.substringAfterLast('/') ?: continue
            val folder = when {
                !data.isNullOrEmpty() -> data.substringBeforeLast('/', "")
                relPathCol >= 0 -> "$storageRoot/" + (cursor.getString(relPathCol) ?: "").trimEnd('/')
                else -> ""
            }
            // MediaStore хранит TRACK как disc * 1000 + номер трека
            val rawTrack = cursor.getInt(trackCol)
            // TODO: перекодировка тегов старых MP3 в cp1251 — пока доверяем MediaStore (раздел 7 ТЗ)
            result += Track(
                id = cursor.getLong(idCol),
                title = cursor.getString(titleCol).orUnknown() ?: displayName.substringBeforeLast('.'),
                artist = cursor.getString(artistCol).orUnknown() ?: unknownArtist,
                album = cursor.getString(albumCol).orUnknown() ?: unknownAlbum,
                albumId = cursor.getLong(albumIdCol),
                durationMs = cursor.getLong(durationCol),
                trackNumber = rawTrack % 1000,
                discNumber = rawTrack / 1000,
                year = cursor.getInt(yearCol),
                folderPath = folder,
                fileName = displayName,
                dateAdded = cursor.getLong(dateCol),
                sizeBytes = cursor.getLong(sizeCol),
                mimeType = cursor.getString(mimeCol) ?: "",
            )
        }
        return result
    }

    /** Пустые значения и «<unknown>» превращаем в null, чтобы подставить замену. */
    private fun String?.orUnknown(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }

    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(collection, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
