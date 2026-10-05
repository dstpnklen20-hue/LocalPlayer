package com.localplayer.app.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.localplayer.app.R
import com.localplayer.app.data.model.TrackSort
import com.localplayer.app.data.model.TrackSortField

private fun TrackSortField.label(): Int = when (this) {
    TrackSortField.TITLE -> R.string.sort_by_title
    TrackSortField.ARTIST -> R.string.sort_by_artist
    TrackSortField.ALBUM -> R.string.sort_by_album
    TrackSortField.DATE_ADDED -> R.string.sort_by_date
    TrackSortField.DURATION -> R.string.sort_by_duration
}

/** F5: выбор поля и направления сортировки; применяется сразу и сохраняется. */
@Composable
fun SortDialog(current: TrackSort, onSelect: (TrackSort) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_title)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    TrackSortField.entries.forEach { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = current.field == field,
                                    onClick = { onSelect(current.copy(field = field)) },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current.field == field, onClick = null)
                            Text(
                                stringResource(field.label()),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = current.ascending,
                        onClick = { onSelect(current.copy(ascending = true)) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.sort_ascending)) }
                    SegmentedButton(
                        selected = !current.ascending,
                        onClick = { onSelect(current.copy(ascending = false)) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.sort_descending)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}
