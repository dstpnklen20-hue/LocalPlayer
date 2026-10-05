package com.localplayer.app.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.localplayer.app.R
import com.localplayer.app.playback.PlayerState
import com.localplayer.app.ui.components.CoverImage
import com.localplayer.app.ui.components.cover.AudioCover
import com.localplayer.app.ui.navigation.isWideLayout
import com.localplayer.app.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Дополнительные кнопки полного плеера (избранное, таймер сна) — подключаются на этапах 4–5. */
data class PlayerExtras(
    val isFavorite: Boolean = false,
    val onToggleFavorite: (() -> Unit)? = null,
    val sleepTimerLabel: String? = null,
    val onSleepTimer: (() -> Unit)? = null,
)

@Composable
fun FullPlayer(
    state: PlayerState,
    positionMs: () -> Long,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onArtistClick: () -> Unit,
    extras: PlayerExtras,
    modifier: Modifier = Modifier,
) {
    val accent = rememberCoverColor(state.currentTrackId)
    val surface = MaterialTheme.colorScheme.surface
    val top by animateColorAsState(
        targetValue = if (accent != null) lerp(surface, accent, 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(600),
        label = "playerBackground",
    )

    // Свайп вниз — свернуть: плеер едет за пальцем
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffset.value.coerceAtLeast(0f) }
            .background(Brush.verticalGradient(listOf(top, surface)))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragOffset.value > 150.dp.toPx()) {
                                onCollapse()
                                dragOffset.snapTo(0f)
                            } else {
                                dragOffset.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dragOffset.animateTo(0f) } },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        scope.launch { dragOffset.snapTo((dragOffset.value + dy).coerceAtLeast(0f)) }
                    },
                )
            },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse))
                }
                Text(
                    stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                // Симметрия с кнопкой «Свернуть»; очередь — в нижнем ряду управления
                Spacer(Modifier.size(48.dp))
            }

            if (isWideLayout()) {
                // Планшет / альбомная ориентация: обложка слева, управление справа
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        SwipeableCover(state.currentTrackId, onNext, onPrevious)
                    }
                    // На низком экране (телефон боком) контролы можно прокрутить
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PlayerControls(state, positionMs, onTogglePlay, onNext, onPrevious, onSeek, onToggleShuffle, onCycleRepeat, onOpenQueue, onArtistClick, extras)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SwipeableCover(state.currentTrackId, onNext, onPrevious)
                    }
                    PlayerControls(state, positionMs, onTogglePlay, onNext, onPrevious, onSeek, onToggleShuffle, onCycleRepeat, onOpenQueue, onArtistClick, extras)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Обложка; свайп влево — следующий трек, вправо — предыдущий. */
@Composable
private fun SwipeableCover(trackId: Long?, onNext: () -> Unit, onPrevious: () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Квадрат по меньшей стороне доступной области — не вылезает ни по ширине, ни по высоте
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 420.dp)
        CoverImage(
            trackId = trackId,
            modifier = Modifier
                .size(side)
                .graphicsLayer {
                    translationX = offset.value
                    alpha = 1f - (abs(offset.value) / (size.width * 1.5f)).coerceIn(0f, 0.6f)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = 80.dp.toPx()
                            val value = offset.value
                            when {
                                value < -threshold -> onNext()
                                value > threshold -> onPrevious()
                            }
                            scope.launch { offset.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + dx) }
                        },
                    )
                },
            shape = RoundedCornerShape(20.dp),
            iconSize = 96.dp,
        )
    }
}

@Composable
private fun PlayerControls(
    state: PlayerState,
    positionMs: () -> Long,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onArtistClick: () -> Unit,
    extras: PlayerExtras,
) {
    Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).clickable(onClick = onArtistClick),
                )
            }
            if (extras.onToggleFavorite != null) {
                IconButton(onClick = extras.onToggleFavorite) {
                    Icon(
                        if (extras.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(
                            if (extras.isFavorite) R.string.favorite_remove else R.string.favorite_add,
                        ),
                        tint = if (extras.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SeekBar(positionMs = positionMs, durationMs = state.durationMs, onSeek = onSeek)

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = stringResource(if (state.shuffle) R.string.player_shuffle_off else R.string.player_shuffle_on),
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                val (icon, label) = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne to R.string.player_repeat_one
                    Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat to R.string.player_repeat_all
                    else -> Icons.Rounded.Repeat to R.string.player_repeat_off
                }
                Icon(
                    icon,
                    contentDescription = stringResource(label),
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (extras.onSleepTimer != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = extras.onSleepTimer) {
                        Icon(
                            Icons.Rounded.Bedtime,
                            contentDescription = stringResource(R.string.sleep_timer),
                            tint = if (extras.sleepTimerLabel != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    extras.sleepTimerLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            IconButton(onClick = onOpenQueue) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.player_queue),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Ползунок перемотки: пока тянем — показываем позицию пальца, отпустили — перематываем. */
@Composable
private fun SeekBar(positionMs: () -> Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val current = dragging?.let { (it * duration).toLong() } ?: positionMs()
    Column {
        Slider(
            value = dragging ?: (current.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(current), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Доминирующий цвет обложки (Palette) для фона плеера; null — обложки нет. */
@Composable
private fun rememberCoverColor(trackId: Long?): Color? {
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(trackId) {
        if (trackId == null) {
            color = null
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(AudioCover(trackId))
            .size(128)
            .allowHardware(false) // Palette нужен программный Bitmap
            .build()
        val result = context.imageLoader.execute(request)
        color = if (result is SuccessResult) {
            withContext(Dispatchers.Default) {
                val palette = Palette.from(result.image.toBitmap()).generate()
                val swatch = palette.vibrantSwatch ?: palette.dominantSwatch
                swatch?.rgb?.let { Color(it) }
            }
        } else {
            null
        }
    }
    return color
}
