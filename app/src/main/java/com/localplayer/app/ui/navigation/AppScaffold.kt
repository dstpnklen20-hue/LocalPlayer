package com.localplayer.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.localplayer.app.R
import com.localplayer.app.ui.components.LocalCurrentTrackId
import com.localplayer.app.ui.components.LocalPlaybackActions
import com.localplayer.app.ui.components.MiniPlayer
import com.localplayer.app.ui.screens.player.FullPlayer
import com.localplayer.app.ui.screens.player.PlayerExtras
import com.localplayer.app.ui.screens.player.PlayerViewModel
import com.localplayer.app.ui.screens.queue.QueueSheet

/** Ширина ≥ 600 dp (планшет, альбомная ориентация) — NavigationRail вместо нижней панели. */
@Composable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** Общий Snackbar приложения. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun AppScaffold(playerViewModel: PlayerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val wide = isWideLayout()
    val context = LocalContext.current

    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    // Позиция читается только внутри лямбд — каждые 500 мс перерисовываются лишь прогресс-бары
    val position by playerViewModel.position.collectAsStateWithLifecycle(0L)
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var queueVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isFavorite by playerViewModel.isFavorite.collectAsStateWithLifecycle()

    LaunchedEffect(playerViewModel) {
        playerViewModel.errors.collect { event ->
            val message = if (event.title.isNotEmpty()) context.getString(R.string.player_error, event.title)
            else context.getString(R.string.player_error_untitled)
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = playerExpanded) { playerExpanded = false }

    val miniPlayer: @Composable () -> Unit = {
        if (playerState.hasMedia) {
            MiniPlayer(
                state = playerState,
                positionMs = { position },
                onExpand = { playerExpanded = true },
                onTogglePlay = playerViewModel::togglePlayPause,
                onNext = playerViewModel::next,
            )
        }
    }

    CompositionLocalProvider(
        LocalPlaybackActions provides playerViewModel,
        LocalCurrentTrackId provides playerState.currentTrackId,
        LocalSnackbarHostState provides snackbarHostState,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail {
                            TopLevelDestination.entries.forEach { dest ->
                                val selected = currentDestination.isTopLevel(dest)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navController.navigateTopLevel(dest) },
                                    icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                                    label = { Text(stringResource(dest.label)) },
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.weight(1f)) { AppNavHost(navController) }
                            Box(Modifier.navigationBarsPadding()) { miniPlayer() }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { AppNavHost(navController) }
                        miniPlayer()
                        NavigationBar(Modifier.fillMaxWidth()) {
                            TopLevelDestination.entries.forEach { dest ->
                                val selected = currentDestination.isTopLevel(dest)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navController.navigateTopLevel(dest) },
                                    icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                                    label = { Text(stringResource(dest.label)) },
                                )
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    // Над мини-плеером и нижней панелью
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            bottom = (if (wide) 0.dp else NAV_BAR_HEIGHT) +
                                (if (playerState.hasMedia) MINI_PLAYER_HEIGHT else 0.dp),
                        ),
                )

                AnimatedVisibility(
                    visible = playerExpanded && playerState.hasMedia,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    FullPlayer(
                        state = playerState,
                        positionMs = { position },
                        onCollapse = { playerExpanded = false },
                        onTogglePlay = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::next,
                        onPrevious = playerViewModel::previous,
                        onSeek = playerViewModel::seekTo,
                        onToggleShuffle = playerViewModel::toggleShuffle,
                        onCycleRepeat = playerViewModel::cycleRepeat,
                        onOpenQueue = { queueVisible = true },
                        onArtistClick = {
                            playerExpanded = false
                            navController.navigate(ArtistRoute(playerState.artist))
                        },
                        extras = PlayerExtras(
                            isFavorite = isFavorite,
                            onToggleFavorite = playerViewModel::toggleFavorite,
                        ),
                    )
                }
            }
        }

        if (queueVisible && playerState.hasMedia) {
            QueueSheet(
                state = playerState,
                onDismiss = { queueVisible = false },
                onSkipTo = playerViewModel::skipTo,
                onRemove = playerViewModel::removeFromQueue,
                onMove = playerViewModel::moveInQueue,
            )
        }
    }
}

private val NAV_BAR_HEIGHT = 80.dp
private val MINI_PLAYER_HEIGHT = 62.dp

private fun NavDestination?.isTopLevel(dest: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(dest.route::class) } == true

private fun NavHostController.navigateTopLevel(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
