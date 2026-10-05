package com.localplayer.app.playback

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.localplayer.app.data.model.Track
import com.localplayer.app.data.model.uri
import java.util.concurrent.atomic.AtomicLong

private val uidCounter = AtomicLong(System.currentTimeMillis())

/** Ключ элемента очереди: один трек может стоять в очереди несколько раз. */
const val EXTRA_QUEUE_UID = "queue_uid"

/**
 * mediaId = id трека в MediaStore. Обложка берётся из самого файла: artworkUri = URI трека,
 * его понимает [CoverBitmapLoader] в сервисе.
 */
fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(uri)
                .setTrackNumber(trackNumber.takeIf { it > 0 })
                .setDurationMs(durationMs)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(Bundle().apply { putLong(EXTRA_QUEUE_UID, uidCounter.incrementAndGet()) })
                .build(),
        )
        .build()

val MediaItem.trackId: Long? get() = mediaId.toLongOrNull()

val MediaItem.queueUid: Long get() = mediaMetadata.extras?.getLong(EXTRA_QUEUE_UID) ?: mediaId.hashCode().toLong()

/**
 * Элементы от MediaController могут прийти без URI (он не передаётся между процессами),
 * поэтому восстанавливаем его по mediaId.
 */
fun MediaItem.withResolvedUri(): MediaItem {
    if (localConfiguration != null) return this
    val id = trackId ?: return this
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    return buildUpon().setUri(uri).build()
}
