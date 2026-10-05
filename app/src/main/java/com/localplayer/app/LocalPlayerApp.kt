package com.localplayer.app

import android.app.Application
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.localplayer.app.ui.components.cover.AudioCoverFetcher
import com.localplayer.app.ui.components.cover.AudioCoverKeyer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LocalPlayerApp : Application(), SingletonImageLoader.Factory {

    // Coil: обложки грузим своим фетчером из MediaStore, сеть не используется
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AudioCoverKeyer())
                add(AudioCoverFetcher.Factory(context))
            }
            .crossfade(true)
            .build()

    override fun onCreate() {
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate()
    }

    // В debug ловим операции с диском и сетью на главном потоке (раздел 7 ТЗ)
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}
