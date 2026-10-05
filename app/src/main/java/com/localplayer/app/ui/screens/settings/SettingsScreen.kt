package com.localplayer.app.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.app.R
import com.localplayer.app.data.model.ThemeMode
import com.localplayer.app.ui.components.DetailTopBar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val candidates by viewModel.folderCandidates.collectAsStateWithLifecycle()
    var addingFolder by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.settings), onBack = onBack)
        val s = settings ?: return@Column
        LazyColumn(Modifier.fillMaxSize()) {
            // F20: тема
            item { Header(stringResource(R.string.settings_appearance)) }
            item {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(
                            text = stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                            selected = s.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_hint),
                        checked = s.dynamicColor,
                        onChange = viewModel::setDynamicColor,
                    )
                }
            }

            // F21: фильтры библиотеки
            item { Header(stringResource(R.string.settings_library)) }
            item { MinDurationRow(s.minDurationSec, viewModel::setMinDuration) }
            item {
                Text(
                    stringResource(R.string.settings_excluded_folders),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                Text(
                    stringResource(R.string.settings_excluded_folders_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(s.excludedFolders.sorted(), key = { "ex:$it" }) { path ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { viewModel.removeExcludedFolder(path) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_remove_folder))
                    }
                }
            }
            item {
                TextButton(onClick = { addingFolder = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_add_folder))
                }
            }

            // F22: воспроизведение
            item { Header(stringResource(R.string.settings_playback)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_pause_on_disconnect),
                    subtitle = stringResource(R.string.settings_pause_on_disconnect_hint),
                    checked = s.pauseOnDisconnect,
                    onChange = viewModel::setPauseOnDisconnect,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_resume_bluetooth),
                    subtitle = stringResource(R.string.settings_resume_bluetooth_hint),
                    checked = s.resumeOnBluetooth,
                    onChange = viewModel::setResumeOnBluetooth,
                )
            }

            // F23
            item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAbout).heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.about), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (addingFolder) {
        AddFolderDialog(
            candidates = candidates,
            onDismiss = { addingFolder = false },
            onAdd = {
                viewModel.addExcludedFolder(it)
                addingFolder = false
            },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** F2/F21: порог коротких файлов, 0–120 с с шагом 5 с. Сохраняется по отпусканию ползунка. */
@Composable
private fun MinDurationRow(seconds: Int, onChange: (Int) -> Unit) {
    var value by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_min_duration), style = MaterialTheme.typography.bodyLarge)
        Text(
            if (value.roundToInt() == 0) stringResource(R.string.settings_min_duration_off)
            else stringResource(R.string.settings_min_duration_value, value.roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = 0f..120f,
            steps = 23,
        )
    }
}

/** Выбор папки из найденных или ввод фрагмента пути вручную (например, «WhatsApp/Media»). */
@Composable
private fun AddFolderDialog(candidates: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var manual by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_add_folder)) },
        text = {
            Column {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text(stringResource(R.string.settings_folder_manual)) },
                    placeholder = { Text("WhatsApp/Media") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (candidates.isNotEmpty()) {
                    Text(
                        stringResource(R.string.settings_folder_found),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(candidates, key = { it }) { path ->
                            Text(
                                path,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(path) }
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(manual.trim()) }, enabled = manual.isNotBlank()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
