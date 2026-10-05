package com.localplayer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.localplayer.app.R
import com.localplayer.app.ui.screens.PlaceholderScreen
import com.localplayer.app.ui.screens.detail.AlbumScreen
import com.localplayer.app.ui.screens.detail.ArtistScreen
import com.localplayer.app.ui.screens.detail.FolderScreen
import com.localplayer.app.ui.screens.library.LibraryScreen
import com.localplayer.app.ui.screens.search.SearchScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    val onAlbum: (Long) -> Unit = { navController.navigate(AlbumRoute(it)) }
    val onArtist: (String) -> Unit = { navController.navigate(ArtistRoute(it)) }
    val onFolder: (String) -> Unit = { navController.navigate(FolderRoute(it)) }
    val onBack: () -> Unit = { navController.popBackStack() }

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
        composable<PlaylistsRoute> { PlaceholderScreen(R.string.nav_playlists) }
        composable<SearchRoute> { SearchScreen(onAlbum = onAlbum, onArtist = onArtist) }
        composable<SettingsRoute> { PlaceholderScreen(R.string.settings, onBack = onBack) }
        composable<AlbumRoute> { AlbumScreen(onBack = onBack, onArtist = onArtist) }
        composable<ArtistRoute> { ArtistScreen(onBack = onBack, onAlbum = onAlbum) }
        composable<FolderRoute> { FolderScreen(onBack = onBack, onFolder = onFolder) }
    }
}
