package com.localplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Буква индекса и позиция первого элемента с ней в списке. */
@Immutable
data class IndexEntry(val letter: String, val position: Int)

/** Строит индекс по уже отсортированному списку. */
fun <T> buildIndex(items: List<T>, letterOf: (T) -> String): List<IndexEntry> {
    val result = mutableListOf<IndexEntry>()
    var last: String? = null
    items.forEachIndexed { i, item ->
        val letter = letterOf(item)
        if (letter != last) {
            result += IndexEntry(letter, i)
            last = letter
        }
    }
    return result
}

/**
 * F7: буквенный индекс справа. Касание или протяжка по буквам прокручивает список,
 * рядом показывается пузырь с текущей буквой.
 */
@Composable
fun AlphabetScroller(
    entries: List<IndexEntry>,
    onScrollTo: suspend (position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.size < 2) return
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf<IndexEntry?>(null) }
    var bubbleY by remember { mutableStateOf(0f) }
    var columnHeight by remember { mutableStateOf(1) }

    fun select(y: Float) {
        val i = (y / columnHeight * entries.size).toInt().coerceIn(0, entries.lastIndex)
        val entry = entries[i]
        bubbleY = y.coerceIn(0f, columnHeight.toFloat())
        if (entry != active) {
            active = entry
            scope.launch { onScrollTo(entry.position) }
        }
    }

    Box(modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 8.dp)
                .onSizeChanged { columnHeight = it.height.coerceAtLeast(1) }
                .pointerInput(entries) {
                    detectTapGestures(
                        onPress = { offset ->
                            select(offset.y)
                            tryAwaitRelease()
                            active = null
                        },
                    )
                }
                .pointerInput(entries) {
                    detectVerticalDragGestures(
                        onDragStart = { select(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                        onVerticalDrag = { change, _ -> select(change.position.y) },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            entries.forEach { entry ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.letter,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontWeight = if (entry == active) FontWeight.Bold else FontWeight.Medium,
                        color = if (entry == active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        active?.let { entry ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = -64.dp.roundToPx(), y = (bubbleY - 24.dp.toPx()).roundToInt()) }
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.letter,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
