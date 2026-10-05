package com.localplayer.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localplayer.app.data.model.Track
import com.localplayer.app.util.formatDuration
import androidx.compose.foundation.background

/**
 * Строка трека: обложка 48 dp (или номер трека), название, «исполнитель · длительность», хвост (меню «⋮»).
 * Играющий трек подсвечен; в режиме выбора слева — отметка.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    /** Номер вместо обложки — для экрана альбома. */
    number: Int? = null,
    /** Файл недоступен (удалён) — строка серая. */
    unavailable: Boolean = false,
    subtitle: String = "${track.artist} · ${formatDuration(track.durationMs)}",
    trailing: (@Composable () -> Unit)? = null,
) {
    val highlight = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp)
            .alpha(if (unavailable) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        if (number != null) {
            Box(Modifier.size(width = 32.dp, height = 48.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = highlight)
                } else {
                    Text(
                        text = if (number > 0) number.toString() else "–",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center) {
                CoverImage(trackId = track.id, modifier = Modifier.size(48.dp))
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small),
                    )
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = highlight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null && !selectionMode) trailing()
    }
}
