package com.localplayer.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.localplayer.app.R
import kotlinx.serialization.Serializable

// Типобезопасные маршруты Navigation Compose

@Serializable
data object LibraryRoute

@Serializable
data object PlaylistsRoute

@Serializable
data object SearchRoute

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

@Serializable
data class AlbumRoute(val albumId: Long)

@Serializable
data class ArtistRoute(val name: String)

@Serializable
data class FolderRoute(val path: String)

@Serializable
data class PlaylistRoute(val playlistId: Long)

/** Пункты нижней панели / NavigationRail. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    LIBRARY(LibraryRoute, R.string.nav_library, Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    PLAYLISTS(PlaylistsRoute, R.string.nav_playlists, Icons.AutoMirrored.Rounded.QueueMusic, Icons.AutoMirrored.Outlined.QueueMusic),
    SEARCH(SearchRoute, R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search),
}
