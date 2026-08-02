package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.feature.player.ChapterMark
import com.maik205.shoumeiplayer.feature.player.PlayerTimelineState
import com.maik205.shoumeiplayer.feature.player.PlayerUiState
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun VideoSurface(
    onSurface: (android.view.Surface?) -> Unit,
    onSurfaceSize: (Int, Int) -> Unit,
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            onSurface(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            onSurfaceSize(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            onSurface(null)
                        }
                    },
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun VideoPlayerChrome(
    state: PlayerUiState,
    timelineState: StateFlow<PlayerTimelineState>,
    dimmed: Boolean,
    timelineFocus: FocusRequester,
    playPauseFocus: FocusRequester,
    whileWatchingVisible: Boolean,
    onOpenWhileWatching: () -> Unit,
    onOpenItem: (String) -> Unit,
    onRetryWhileWatching: () -> Unit,
    exitArmed: Boolean,
    onExitButton: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onHideOsd: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPanel: (TelevisionPlayerPanel) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = state.state == PlayerState.Playing || state.state == PlayerState.Buffering
    val whileWatchingFocus = remember { FocusRequester() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (dimmed) 0.26f else 1f }
            .background(
                Brush.verticalGradient(
                    0f to TelevisionColors.Black.copy(alpha = 0f),
                    0.28f to TelevisionColors.Black.copy(alpha = 0f),
                    0.62f to TelevisionColors.Black.copy(alpha = 0.56f),
                    1f to TelevisionColors.Black.copy(alpha = 0.97f),
                ),
            )
            .padding(
                start = 67.dp,
                end = 67.dp,
                top = 150.dp,
                bottom = 32.dp,
            ),
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 24.sp,
                lineHeight = 27.sp,
                letterSpacing = (-0.8).sp,
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        videoMetadata(state)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TelevisionColors.PaperMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        VideoPlayerTimeline(
            timelineState = timelineState,
            chapters = state.chapters,
            seekIntervalMs = state.seekIntervalSeconds.toLong() * 1_000L,
            focusRequester = timelineFocus,
            downFocusRequester = playPauseFocus,
            onSeekBy = {
                onInteraction()
                onSeekBy(it)
            },
            onClick = {
                onInteraction()
                onTogglePlayPause()
            },
            onNavigateUp = onHideOsd,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                        return@onPreviewKeyEvent false
                    }
                    if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
                        return@onPreviewKeyEvent false
                    }
                    if (!whileWatchingVisible) {
                        onOpenWhileWatching()
                    } else {
                        runCatching { whileWatchingFocus.requestFocus() }
                    }
                    true
                },
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCompactActionButton(
                label = if (exitArmed) {
                    stringResource(R.string.tv_player_exit)
                } else {
                    stringResource(R.string.tv_back)
                },
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                expandedWidth = 92.dp,
                onFocused = onInteraction,
                onClick = {
                    onInteraction()
                    onExitButton()
                },
            )
            PlayerCompactActionButton(
                label = pluralStringResource(
                    R.plurals.tv_player_rewind,
                    state.seekIntervalSeconds,
                    state.seekIntervalSeconds,
                ),
                icon = Icons.Default.Replay10,
                expandedWidth = 122.dp,
                onFocused = onInteraction,
                onClick = {
                    onInteraction()
                    onSeekBy(-state.seekIntervalSeconds.toLong() * 1_000L)
                },
            )
            PlayerCompactActionButton(
                label = stringResource(
                    if (playing) R.string.tv_player_pause else R.string.play,
                ),
                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                selected = playing,
                focusRequester = playPauseFocus,
                expandedWidth = 104.dp,
                onFocused = onInteraction,
                onClick = {
                    onInteraction()
                    onTogglePlayPause()
                },
            )
            PlayerCompactActionButton(
                label = pluralStringResource(
                    R.plurals.tv_player_forward,
                    state.seekIntervalSeconds,
                    state.seekIntervalSeconds,
                ),
                icon = Icons.Default.Forward10,
                expandedWidth = 126.dp,
                onFocused = onInteraction,
                onClick = {
                    onInteraction()
                    onSeekBy(state.seekIntervalSeconds.toLong() * 1_000L)
                },
            )
            if (state.previousEpisodeId != null) {
                PlayerCompactActionButton(
                    label = stringResource(R.string.tv_player_previous),
                    icon = Icons.Default.SkipPrevious,
                    expandedWidth = 112.dp,
                    onFocused = onInteraction,
                    onClick = {
                        onInteraction()
                        onPrevious()
                    },
                )
            }
            if (state.nextEpisodeId != null) {
                PlayerCompactActionButton(
                    label = stringResource(R.string.tv_player_next),
                    icon = Icons.Default.SkipNext,
                    expandedWidth = 92.dp,
                    onFocused = onInteraction,
                    onClick = {
                        onInteraction()
                        onNext()
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.chapters.isNotEmpty()) {
                PlayerCompactActionButton(
                    label = stringResource(R.string.tv_detail_chapters),
                    icon = Icons.Default.VideoLibrary,
                    expandedWidth = 112.dp,
                    onFocused = onInteraction,
                    onClick = { onOpenPanel(TelevisionPlayerPanel.Chapters) },
                )
            }
            PlayerCompactActionButton(
                label = stringResource(R.string.tv_subtitles),
                icon = Icons.Default.ClosedCaption,
                selected = state.subtitleTracks.any(PlayerTrack::selected),
                expandedWidth = 122.dp,
                onFocused = onInteraction,
                onClick = { onOpenPanel(TelevisionPlayerPanel.Subtitles) },
            )
            if (state.audioTracks.isNotEmpty()) {
                PlayerCompactActionButton(
                    label = stringResource(R.string.tv_audio),
                    icon = Icons.Default.GraphicEq,
                    expandedWidth = 96.dp,
                    onFocused = onInteraction,
                    onClick = { onOpenPanel(TelevisionPlayerPanel.Audio) },
                )
            }
            PlayerCompactActionButton(
                label = stringResource(R.string.tv_player_options),
                icon = Icons.Default.Tune,
                expandedWidth = 92.dp,
                onFocused = onInteraction,
                onClick = { onOpenPanel(TelevisionPlayerPanel.More) },
            )
        }
        if (whileWatchingVisible) {
            Spacer(Modifier.height(18.dp))
            PlayerWhileWatchingRail(
                state = state,
                focusRequester = whileWatchingFocus,
                upFocusRequester = playPauseFocus,
                onOpenItem = onOpenItem,
                onRetry = onRetryWhileWatching,
            )
        }
    }
}

@Composable
private fun VideoPlayerTimeline(
    timelineState: StateFlow<PlayerTimelineState>,
    chapters: List<ChapterMark>,
    seekIntervalMs: Long,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onSeekBy: (Long) -> Unit,
    onClick: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeline by timelineState.collectAsStateWithLifecycle()
    PlayerTimeline(
        positionMs = timeline.positionMs,
        durationMs = timeline.durationMs,
        bufferedMs = timeline.bufferedMs,
        chapters = chapters,
        seekIntervalMs = seekIntervalMs,
        focusRequester = focusRequester,
        downFocusRequester = downFocusRequester,
        onSeekBy = onSeekBy,
        onClick = onClick,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

@Composable
private fun videoMetadata(state: PlayerUiState): String? {
    val seasonNumber = state.seasonNumber
    val episodeNumber = state.episodeNumber
    val episode = if (state.isEpisode) {
        val metadata = mutableListOf<String>()
        if (seasonNumber != null) {
            metadata += stringResource(R.string.tv_player_season_number, seasonNumber)
        }
        if (episodeNumber != null) {
            metadata += stringResource(R.string.tv_player_episode_number, episodeNumber)
        }
        metadata.joinToString(" ")
    } else {
        null
    }
    return listOfNotNull(
        state.seriesName?.takeIf { it != state.title },
        episode?.takeIf(String::isNotBlank),
        state.year?.toString(),
    ).takeIf(List<String>::isNotEmpty)?.joinToString("  ·  ")
}

internal data class PlayerSelectionOption<T>(
    val value: T,
    val label: String,
)

internal fun <T> selectionRows(
    prefix: String,
    selected: T,
    values: List<PlayerSelectionOption<T>>,
    onSelect: (T) -> Unit,
): List<PlayerSelectionRow> = values.map { option ->
    PlayerSelectionRow(
        key = "$prefix:${option.value}",
        label = option.label,
        selected = option.value == selected,
        onClick = { onSelect(option.value) },
    )
}

@Composable
internal fun signedPlaybackDelay(valueMs: Long): String = when {
    valueMs > 0L -> stringResource(R.string.tv_player_delay_positive, valueMs)
    valueMs < 0L -> stringResource(R.string.tv_player_delay_negative, valueMs)
    else -> stringResource(R.string.tv_player_delay_zero)
}
