package com.localplayer.app.ui.components.cover

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Модель для Coil: обложка трека из MediaStore (встроенная картинка файла). */
data class AudioCover(val trackId: Long)

/** Ключ кэша памяти Coil; размер Coil добавляет к ключу сам. */
class AudioCoverKeyer : Keyer<AudioCover> {
    override fun key(data: AudioCover, options: Options): String = "cover:${data.trackId}"
}

class AudioCoverFetcher(
    private val data: AudioCover,
    private val options: Options,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val size = maxOf(
            options.size.width.pxOrElse { DEFAULT_SIZE },
            options.size.height.pxOrElse { DEFAULT_SIZE },
        ).coerceIn(1, MAX_SIZE)
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, data.trackId)
        val bitmap = CoverLoader.load(context, uri, size)
            ?: throw NoCoverException(data.trackId)
        ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<AudioCover> {
        override fun create(data: AudioCover, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioCoverFetcher(data, options, context)
    }

    private class NoCoverException(id: Long) : Exception("No cover for track $id")

    private companion object {
        const val DEFAULT_SIZE = 512
        const val MAX_SIZE = 1024
    }
}

/** Загрузка обложки аудиофайла с даунскейлом до нужного размера (раздел 7 ТЗ). */
object CoverLoader {

    fun load(context: Context, trackUri: Uri, sizePx: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                context.contentResolver.loadThumbnail(trackUri, Size(sizePx, sizePx), null)
            } catch (_: Exception) {
                null
            }
        } else {
            loadEmbedded(context, trackUri, sizePx)
        }

    private fun loadEmbedded(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
