package com.localplayer.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.model.AppSettings
import com.localplayer.app.data.model.ThemeMode
import com.localplayer.app.data.prefs.SettingsRepository
import com.localplayer.app.playback.PlayerConnection
import com.localplayer.app.ui.navigation.AppScaffold
import com.localplayer.app.ui.screens.permission.AudioPermission
import com.localplayer.app.ui.screens.permission.PermissionScreen
import com.localplayer.app.ui.theme.LocalPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerConnection: PlayerConnection
    @Inject lateinit var mediaStoreRepository: MediaStoreRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private var hasPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hasPermission = AudioPermission.isGranted(this)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Иконки статус-бара и навигации — под выбранную в приложении тему, а не системную
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            LocalPlayerTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                if (hasPermission) {
                    AppScaffold()
                } else {
                    PermissionScreen(onGranted = ::onPermissionGranted)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Разрешение могли отозвать в настройках — тогда снова экран запроса (раздел 7 ТЗ)
        hasPermission = AudioPermission.isGranted(this)
        if (hasPermission) playerConnection.connect()
    }

    override fun onStop() {
        // UI не держит контроллер в фоне: музыкой владеет сервис
        playerConnection.disconnect()
        super.onStop()
    }

    private fun onPermissionGranted() {
        hasPermission = true
        // Сканирование могло пройти без разрешения и вернуть пустой список
        lifecycleScope.launch { mediaStoreRepository.refresh() }
        playerConnection.connect()
    }
}
