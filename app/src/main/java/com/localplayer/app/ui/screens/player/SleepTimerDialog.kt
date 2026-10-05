package com.localplayer.app.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localplayer.app.R
import com.localplayer.app.playback.SleepTimerState
import com.localplayer.app.util.formatDuration

private val SLEEP_OPTIONS_MIN = listOf(15, 30, 45, 60)

/** F13: 15 / 30 / 45 / 60 минут или «до конца трека». */
@Composable
fun SleepTimerDialog(
    state: SleepTimerState,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column {
                if (state.active) {
                    Text(
                        text = stringResource(R.string.sleep_timer_active, sleepTimerLabel(state)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                SLEEP_OPTIONS_MIN.forEach { minutes ->
                    Option(pluralStringResource(R.plurals.minutes, minutes, minutes)) {
                        onSelectMinutes(minutes)
                        onDismiss()
                    }
                }
                Option(stringResource(R.string.sleep_timer_end_of_track)) {
                    onSelectEndOfTrack()
                    onDismiss()
                }
            }
        },
        confirmButton = {
            if (state.active) {
                TextButton(onClick = { onCancelTimer(); onDismiss() }) { Text(stringResource(R.string.sleep_timer_off)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .wrapContentHeight(),
    )
}

/** «12:34» или «до конца трека» — подпись активного таймера. */
@Composable
fun sleepTimerLabel(state: SleepTimerState): String =
    if (state.endOfTrack) stringResource(R.string.sleep_timer_end_of_track_short)
    else formatDuration(state.remainingMs)
