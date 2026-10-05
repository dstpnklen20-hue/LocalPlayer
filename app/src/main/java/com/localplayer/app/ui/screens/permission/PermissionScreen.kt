package com.localplayer.app.ui.screens.permission

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localplayer.app.R

@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    // true — пользователь отказал «навсегда», системный диалог больше не покажется
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[AudioPermission.audio] == true) {
            onGranted()
        } else {
            deniedOnce = true
            val activity = context as? Activity
            permanentlyDenied = activity?.shouldShowRequestPermissionRationale(AudioPermission.audio) == false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(if (deniedOnce) R.string.permission_denied else R.string.permission_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !deniedOnce) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_notifications_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            if (permanentlyDenied) {
                Button(
                    onClick = { context.startActivity(appSettingsIntent(context.packageName)) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                ) { Text(stringResource(R.string.permission_open_settings)) }
            } else {
                Button(
                    onClick = { launcher.launch(AudioPermission.toRequest) },
                    modifier = Modifier.widthIn(min = 200.dp),
                ) { Text(stringResource(R.string.permission_grant)) }
                if (deniedOnce) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { context.startActivity(appSettingsIntent(context.packageName)) },
                        modifier = Modifier.widthIn(min = 200.dp),
                    ) { Text(stringResource(R.string.permission_open_settings)) }
                }
            }
        }
    }
}

private fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
