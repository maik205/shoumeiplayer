package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.feature.player.AudioQueueItemUi
import com.maik205.shoumeiplayer.feature.player.LyricLineUi
import com.maik205.shoumeiplayer.feature.player.PlayerTimelineState
import com.maik205.shoumeiplayer.feature.player.PlayerUiState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun AudioCover(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    AsyncImage(
        model = state.albumArtworkUrl,
        contentDescription = state.title,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier
            .shadow(28.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(TelevisionColors.ImagePlaceholder),
    )
}

@Composable
internal fun AudioMetadata(
    state: PlayerUiState,
    titleSize: androidx.compose.ui.unit.TextUnit,
    titleLineHeight: androidx.compose.ui.unit.TextUnit,
) {
    state.album?.takeIf(String::isNotBlank)?.let { album ->
        Text(
            text = album,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
            fontWeight = FontWeight.SemiBold,
            color = TelevisionColors.PaperMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(
        text = state.title,
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            letterSpacing = (-1.9).sp,
        ),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    state.artist?.takeIf(String::isNotBlank)?.let { artist ->
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = state.artistArtworkUrl,
                contentDescription = artist,
                contentScale = ContentScale.Crop,
                alpha = 0.82f,
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(TelevisionColors.ImagePlaceholder, CircleShape),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                color = TelevisionColors.PaperMuted,
            )
        }
    }
    audioFormatLabels(state).takeIf { it.isNotEmpty() }?.let { format ->
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            format.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    color = TelevisionColors.PaperMuted.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun audioFormatLabels(state: PlayerUiState): List<String> = buildList {
    state.container?.uppercase()?.takeIf(String::isNotBlank)?.let(::add)
    state.audioDescription
        ?.split('·')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.forEach { label ->
            if (none { it.equals(label, ignoreCase = true) }) add(label)
        }
}

@Composable
internal fun AudioTimeline(
    timelineState: StateFlow<PlayerTimelineState>,
    seekIntervalSeconds: Int,
    focusRequester: FocusRequester,
    enabled: Boolean = true,
    onSeekBy: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by timelineState.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    val duration = state.durationMs?.takeIf { it > 0L }
    val fraction = duration?.let { (state.positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    TelevisionFocusSurface(
        onClick = {
            onInteraction()
            onTogglePlayPause()
        },
        enabled = enabled,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = 0.68f,
        focusedAlpha = 1f,
        onFocusChanged = {
            focused = it
            if (it) onInteraction()
        },
        modifier = modifier
            .height(29.dp)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val step = seekIntervalSeconds.toLong() * 1_000L
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onInteraction()
                        onSeekBy(-step)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onInteraction()
                        onSeekBy(step)
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.fillMaxWidth().height(if (focused) 3.dp else 1.5.dp)) {
                drawRect(TelevisionColors.Paper.copy(alpha = 0.20f))
                drawRect(TelevisionColors.Paper, size = size.copy(width = size.width * fraction))
                if (focused) {
                    drawCircle(
                        color = TelevisionColors.Paper,
                        radius = 4.5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * fraction, size.height / 2f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatAudioTime(state.positionMs), fontSize = 7.sp, color = TelevisionColors.PaperMuted)
                Text(
                    duration?.let(::formatAudioTime) ?: "--:--",
                    fontSize = 7.sp,
                    color = TelevisionColors.PaperMuted,
                )
            }
        }
    }
}

@Composable
internal fun AudioMainTransport(
    state: PlayerUiState,
    playPauseFocus: FocusRequester,
    enabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onInteraction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AudioCircleButton(
            label = "Previous",
            icon = Icons.Default.SkipPrevious,
            enabled = enabled,
            onClick = onPrevious,
            onInteraction = onInteraction,
        )
        AudioCircleButton(
            label = if (isAudioPlaying(state)) "Pause" else "Play",
            icon = if (isAudioPlaying(state)) Icons.Default.Pause else Icons.Default.PlayArrow,
            primary = true,
            enabled = enabled,
            focusRequester = playPauseFocus,
            onClick = onTogglePlayPause,
            onInteraction = onInteraction,
        )
        AudioCircleButton(
            label = "Next",
            icon = Icons.Default.SkipNext,
            enabled = enabled,
            onClick = onNext,
            onInteraction = onInteraction,
        )
    }
}

@Composable
internal fun AudioCircleButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    primary: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val buttonSize = if (primary) 37.dp else 30.dp
    TelevisionFocusSurface(
        onClick = {
            onInteraction()
            onClick()
        },
        enabled = enabled,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = if (primary || selected) 1f else 0.52f,
        focusedAlpha = 1f,
        onFocusChanged = { if (it) onInteraction() },
        modifier = Modifier.size(buttonSize),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TelevisionColors.Paper,
                modifier = Modifier.size(if (primary) 27.dp else 20.dp),
            )
        }
    }
}

@Composable
internal fun AudioLyricsActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    TelevisionFocusRevealButton(
        label = label,
        icon = icon,
        selected = selected,
        expandWhenSelected = false,
        focusRequester = focusRequester,
        collapsedWidth = 23.dp,
        expandedWidth = 64.dp,
        buttonHeight = 23.dp,
        iconSize = 14.dp,
        focusedScale = 1f,
        onClick = {
            onInteraction()
            onClick()
        },
        onFocusChanged = { if (it) onInteraction() },
    )
}

@Composable
internal fun AudioTools(
    shuffleEnabled: Boolean,
    repeatEnabled: Boolean,
    enabled: Boolean,
    lyricsControlFocus: FocusRequester,
    lyricsVisible: Boolean,
    queueVisible: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    onInteraction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AudioToolButton("Shuffle", Icons.Default.Shuffle, shuffleEnabled, onToggleShuffle, onInteraction, enabled)
        AudioToolButton("Repeat", Icons.Default.Repeat, repeatEnabled, onToggleRepeat, onInteraction, enabled)
        AudioToolButton("Queue", Icons.AutoMirrored.Filled.QueueMusic, queueVisible, onToggleQueue, onInteraction, enabled)
        AudioToolButton(
            label = "Lyrics",
            icon = Icons.Default.Lyrics,
            selected = lyricsVisible,
            enabled = enabled,
            focusRequester = lyricsControlFocus,
            collapseOnClick = true,
            onClick = onToggleLyrics,
            onInteraction = onInteraction,
        )
    }
}

@Composable
internal fun AudioToolButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    enabled: Boolean = true,
    collapseOnClick: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    TelevisionFocusRevealButton(
        label = label,
        icon = icon,
        enabled = enabled,
        selected = selected,
        expandWhenSelected = false,
        focusRequester = focusRequester,
        collapsedWidth = 19.dp,
        expandedWidth = 62.dp,
        buttonHeight = 24.dp,
        iconSize = 14.dp,
        focusedScale = 1f,
        onClick = {
            if (collapseOnClick) focusManager.clearFocus(force = true)
            onInteraction()
            onClick()
        },
        onFocusChanged = { if (it) onInteraction() },
    )
}
