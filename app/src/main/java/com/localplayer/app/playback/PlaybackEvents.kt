package com.localplayer.app.playback

/** Кастомные события от PlaybackService к контроллерам (UI). */
object PlaybackEvents {
    /** Трек не удалось воспроизвести (файл удалён или повреждён). */
    const val ACTION_PLAYBACK_ERROR = "com.localplayer.app.PLAYBACK_ERROR"
    const val EXTRA_TITLE = "title"
}
