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
internal fun LyricsPane(
    lyrics: List<LyricLineUi>,
    positionMs: Long,
    synced: Boolean,
    focusRequester: FocusRequester,
) {
    val activeIndex = if (synced) {
        lyrics.indexOfLast { line ->
            line.startMs?.let { it <= positionMs } == true
        }.takeIf { it >= 0 }
    } else {
        null
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeIndex) {
        if (activeIndex != null) listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Lyrics",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
            fontWeight = FontWeight.SemiBold,
            color = TelevisionColors.PaperMuted,
        )
        Spacer(Modifier.height(7.dp))
        if (lyrics.isEmpty()) {
            Text(
                "Lyrics are not available for this track.",
                color = TelevisionColors.PaperMuted,
                modifier = Modifier.focusRequester(focusRequester).focusable(),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                            return@onPreviewKeyEvent false
                        }
                        val target = when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                            KeyEvent.KEYCODE_DPAD_DOWN ->
                                (listState.firstVisibleItemIndex + 1).coerceAtMost(lyrics.lastIndex)
                            else -> return@onPreviewKeyEvent false
                        }
                        scope.launch { listState.animateScrollToItem(target) }
                        true
                    }
                    .focusable(),
            ) {
                itemsIndexed(
                    items = lyrics,
                    key = { index, line -> "${line.startMs}:$index:${line.text.hashCode()}" },
                ) { index, line ->
                    val current = index == activeIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(39.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            line.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 23.sp,
                                lineHeight = 25.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = TelevisionColors.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(
                                when {
                                    current -> 1f
                                    activeIndex != null && index == activeIndex + 1 -> 0.82f
                                    else -> 0.17f
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LyricsPaneHost(
    lyrics: List<LyricLineUi>,
    timelineState: StateFlow<PlayerTimelineState>,
    synced: Boolean,
    focusRequester: FocusRequester,
) {
    val timeline by timelineState.collectAsStateWithLifecycle()
    LyricsPane(
        lyrics = lyrics,
        positionMs = timeline.positionMs,
        synced = synced,
        focusRequester = focusRequester,
    )
}

@Composable
internal fun AudioContextColumns(
    upNext: List<AudioQueueItemUi>,
    suggested: List<AudioQueueItemUi>,
    loading: Boolean,
    upNextCoverMode: Boolean,
    suggestedCoverMode: Boolean,
    firstItemFocus: FocusRequester,
    onToggleUpNextCoverMode: () -> Unit,
    onToggleSuggestedCoverMode: () -> Unit,
    onPlayItem: (String) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(57.dp),
    ) {
        AudioQueueColumn(
            title = "Up next",
            items = upNext,
            coverMode = upNextCoverMode,
            firstItemFocus = if (upNext.isNotEmpty()) firstItemFocus else null,
            emptyMessage = if (loading) "Loading queue…" else "End of queue",
            onToggleCoverMode = onToggleUpNextCoverMode,
            onPlayItem = onPlayItem,
            onInteraction = onInteraction,
            modifier = Modifier.width(AudioColumnWidth),
        )
        AudioQueueColumn(
            title = "Suggested",
            items = suggested,
            coverMode = suggestedCoverMode,
            firstItemFocus = if (upNext.isEmpty() && suggested.isNotEmpty()) firstItemFocus else null,
            emptyMessage = if (loading) "Finding tracks…" else "No suggestions",
            onToggleCoverMode = onToggleSuggestedCoverMode,
            onPlayItem = onPlayItem,
            onInteraction = onInteraction,
            modifier = Modifier.width(AudioColumnWidth),
        )
    }
}

@Composable
private fun AudioQueueColumn(
    title: String,
    items: List<AudioQueueItemUi>,
    coverMode: Boolean,
    firstItemFocus: FocusRequester?,
    emptyMessage: String,
    onToggleCoverMode: () -> Unit,
    onPlayItem: (String) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverFocusRequesters = remember(items.map(AudioQueueItemUi::itemId), firstItemFocus) {
        List(items.size) { index ->
            if (index == 0 && firstItemFocus != null) firstItemFocus else FocusRequester()
        }
    }
    val coverRailState = rememberLazyListState()
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(19.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.SemiBold,
                color = TelevisionColors.PaperMuted,
            )
            Spacer(Modifier.weight(1f))
            TelevisionFocusRevealButton(
                label = if (coverMode) "Tracks" else "Covers",
                icon = if (coverMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                onClick = {
                    onInteraction()
                    onToggleCoverMode()
                },
                expandedWidth = 52.dp,
                collapsedWidth = 15.dp,
                buttonHeight = 19.dp,
                iconSize = 12.dp,
                focusedScale = 1f,
                onFocusChanged = { if (it) onInteraction() },
            )
        }
        Spacer(Modifier.height(5.dp))
        when {
            items.isEmpty() -> Text(emptyMessage, fontSize = 8.sp, color = TelevisionColors.PaperSoft)
            coverMode -> LazyRow(
                state = coverRailState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().focusGroup(),
            ) {
                itemsIndexed(items, key = { _, item -> item.itemId }) { index, item ->
                    AudioCoverItem(
                        item = item,
                        focusRequester = coverFocusRequesters.getOrNull(index),
                        modifier = Modifier.televisionHorizontalWrap(
                            index,
                            coverFocusRequesters,
                            coverRailState,
                        ),
                        onClick = {
                            onInteraction()
                            onPlayItem(item.itemId)
                        },
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusGroup(),
            ) {
                itemsIndexed(items, key = { _, item -> item.itemId }) { index, item ->
                    AudioTrackRow(
                        index = index,
                        item = item,
                        focusRequester = if (index == 0) firstItemFocus else null,
                        onClick = {
                            onInteraction()
                            onPlayItem(item.itemId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTrackRow(
    index: Int,
    item: AudioQueueItemUi,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = 0.38f,
        modifier = Modifier.fillMaxWidth().height(21.dp),
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                fontSize = 6.sp,
                color = TelevisionColors.PaperMuted,
                modifier = Modifier.width(17.dp),
            )
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall.televisionItemTitle(),
                fontWeight = FontWeight.SemiBold,
                color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            item.durationMs?.let {
                Text(
                    formatAudioTime(it),
                    fontSize = 6.sp,
                    color = TelevisionColors.PaperMuted,
                )
            }
        }
    }
}

@Composable
private fun AudioCoverItem(
    item: AudioQueueItemUi,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = 0.42f,
        modifier = modifier.width(89.dp).height(116.dp),
    ) { focused ->
        Column {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(89.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TelevisionColors.ImagePlaceholder),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall.televisionItemTitle(),
                fontWeight = FontWeight.SemiBold,
                color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.artist?.let {
                Text(
                    it,
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    color = TelevisionColors.PaperMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun isAudioPlaying(state: PlayerUiState): Boolean =
    state.state == PlayerState.Playing || state.state == PlayerState.Buffering

internal fun formatAudioTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
