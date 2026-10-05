package com.localplayer.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localplayer.app.BuildConfig
import com.localplayer.app.R
import com.localplayer.app.ui.components.DetailTopBar
import com.localplayer.app.ui.screens.detail.SectionTitle

/** Библиотека с открытой лицензией (F23). Названия — имена собственные, не переводятся. */
private data class OssLibrary(val name: String, val author: String)

// TODO: при добавлении зависимостей обновлять список (или подключить генерацию через плагин лицензий)
private val LIBRARIES = listOf(
    OssLibrary("AndroidX (Core, Activity, Lifecycle, Navigation, Room, DataStore, Palette)", "The Android Open Source Project"),
    OssLibrary("Jetpack Compose, Material 3", "The Android Open Source Project"),
    OssLibrary("AndroidX Media3 (ExoPlayer, Session)", "The Android Open Source Project"),
    OssLibrary("Dagger Hilt", "Google LLC"),
    OssLibrary("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains s.r.o."),
    OssLibrary("Coil", "Coil Contributors"),
    OssLibrary("Reorderable", "Calvin Liang"),
    OssLibrary("Guava", "Google LLC"),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.about), onBack = onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionTitle(stringResource(R.string.about_licenses)) }
            items(LIBRARIES) { lib ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(lib.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        lib.author + " · " + stringResource(R.string.license_apache2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
