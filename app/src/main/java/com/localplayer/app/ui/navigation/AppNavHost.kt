package com.localplayer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.localplayer.app.ui.actions.TrackActionsHost
import com.localplayer.app.ui.screens.detail.AlbumScreen
import com.localplayer.app.ui.screens.detail.ArtistScreen
import com.localplayer.app.ui.screens.detail.FolderScreen
import com.localplayer.app.ui.screens.library.LibraryScreen
import com.localplayer.app.ui.screens.playlists.PlaylistDetailScreen
import com.localplayer.app.ui.screens.playlists.PlaylistsScreen
import com.localplayer.app.ui.screens.search.SearchScreen
import com.localplayer.app.ui.screens.settings.AboutScreen
import com.localplayer.app.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    val onAlbum: (Long) -> Unit = { navController.navigate(AlbumRoute(it)) }
    val onArtist: (String) -> Unit = { navController.navigate(ArtistRoute(it)) }
    val onFolder: (String) -> Unit = { navController.navigate(FolderRoute(it)) }
    val onBack: () -> Unit = { navController.popBackStack() }

    TrackActionsHost(onNavigateToAlbum = onAlbum, onNavigateToArtist = onArtist) {
    NavHost(navController = navController, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryScreen(
                onSearch = { navController.navigate(SearchRoute) { launchSingleTop = true } },
                onSettings = { navController.navigate(SettingsRoute) },
                onAlbum = onAlbum,
                onArtist = onArtist,
                onFolder = onFolder,
            )
        }
        composable<PlaylistsRoute> { PlaylistsScreen(onPlaylist = { navController.navigate(PlaylistRoute(it)) }) }
        composable<PlaylistRoute> { PlaylistDetailScreen(onBack = onBack) }
        composable<SearchRoute> { SearchScreen(onAlbum = onAlbum, onArtist = onArtist) }
        composable<SettingsRoute> { SettingsScreen(onBack = onBack, onAbout = { navController.navigate(AboutRoute) }) }
        composable<AboutRoute> { AboutScreen(onBack = onBack) }
        composable<AlbumRoute> { AlbumScreen(onBack = onBack) }
        composable<ArtistRoute> { ArtistScreen(onBack = onBack, onAlbum = onAlbum) }
        composable<FolderRoute> { FolderScreen(onBack = onBack, onFolder = onFolder) }
    }
    }
}
