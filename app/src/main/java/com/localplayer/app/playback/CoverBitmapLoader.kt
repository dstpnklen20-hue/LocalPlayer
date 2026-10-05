package com.localplayer.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.localplayer.app.ui.components.cover.CoverLoader
import java.io.IOException
import java.util.concurrent.Executors

/** Загрузчик обложек для уведомления и экрана блокировки: достаёт картинку прямо из аудиофайла. */
@UnstableApi
class CoverBitmapLoader(private val context: Context) : BitmapLoader {

    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Cannot decode bitmap")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        CoverLoader.load(context, uri, SIZE_PX) ?: throw IOException("No artwork in $uri")
    }

    private companion object {
        const val SIZE_PX = 512
    }
}
