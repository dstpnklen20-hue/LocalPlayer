package com.localplayer.app.data.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TrackSortField { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

data class TrackSort(
    val field: TrackSortField = TrackSortField.TITLE,
    val ascending: Boolean = true,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val minDurationSec: Int = DEFAULT_MIN_DURATION_SEC,
    val excludedFolders: Set<String> = emptySet(),
    val pauseOnDisconnect: Boolean = true,
    val resumeOnBluetooth: Boolean = false,
    val trackSort: TrackSort = TrackSort(),
) {
    companion object {
        const val DEFAULT_MIN_DURATION_SEC = 30
    }
}
