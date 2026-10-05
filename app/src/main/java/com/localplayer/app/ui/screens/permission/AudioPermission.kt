package com.localplayer.app.ui.screens.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Разрешения зависят от версии Android: просим только нужное для текущей (раздел 6 ТЗ). */
object AudioPermission {

    val audio: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    /** Всё, что запрашиваем на экране онбординга; уведомления — необязательные. */
    val toRequest: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(audio, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(audio)
        }

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audio) == PackageManager.PERMISSION_GRANTED
}
