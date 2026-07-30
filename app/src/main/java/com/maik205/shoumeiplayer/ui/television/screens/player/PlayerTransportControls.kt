package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.player.PlaybackSpeed
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.VideoQuality
import com.maik205.shoumeiplayer.feature.player.PlayerShelfItem
import com.maik205.shoumeiplayer.feature.player.CastMemberUi
import com.maik205.shoumeiplayer.feature.player.ChapterMark
import com.maik205.shoumeiplayer.feature.player.PlayerUiState
import com.maik205.shoumeiplayer.feature.player.UpNextUi
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import java.util.Locale

@Composable
internal fun PlayerActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    TelevisionFocusSurface(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = if (selected) 0.94f else 0.62f,
        modifier = modifier.size(width = 72.dp, height = 64.dp),
        onFocusChanged = { if (it) onFocused() },
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = when {
                        focused -> TelevisionColors.Paper
                        selected -> TelevisionColors.Paper.copy(alpha = 0.14f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(TelevisionDimensions.FocusRadius),
                )
                .padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (focused) TelevisionColors.Black else TelevisionColors.Paper,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (focused) TelevisionColors.Black else TelevisionColors.Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The player transport has to remain inside the 960x540 safe area. Controls therefore rest as
 * icon-only targets and reveal their label only while focused, keeping the focus target truthful
 * without permanently reserving label width for every action.
 */
@Composable
internal fun PlayerCompactActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    expandedWidth: Dp = 116.dp,
    onFocused: () -> Unit = {},
) {
    TelevisionFocusRevealButton(
        label = label,
        icon = icon,
        onClick = onClick,
        enabled = enabled,
        selected = selected,
        expandWhenSelected = false,
        focusRequester = focusRequester,
        expandedWidth = expandedWidth * 0.58f,
        collapsedWidth = 23.dp,
        buttonHeight = 29.dp,
        iconSize = 13.5.dp,
        modifier = modifier,
        onFocusChanged = { focused ->
            if (focused) onFocused()
        },
    )
}

@Composable
internal fun PlayerTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    height: Dp = 46.dp,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = 0.7f,
        modifier = modifier.height(height),
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (focused) TelevisionColors.Paper else TelevisionColors.Paper.copy(alpha = 0.1f),
                    RoundedCornerShape(TelevisionDimensions.FocusRadius),
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (focused) TelevisionColors.Black else TelevisionColors.Paper,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) TelevisionColors.Black else TelevisionColors.Paper,
            )
        }
    }
}

@Composable
internal fun PlayerTimeline(
    positionMs: Long,
    durationMs: Long?,
    bufferedMs: Long?,
    chapters: List<ChapterMark>,
    seekIntervalMs: Long,
    onSeekBy: (Long) -> Unit,
    onClick: () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    val duration = durationMs?.takeIf { it > 0L }
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = 0.82f,
        onFocusChanged = { if (it) onFocused() },
        modifier = modifier
            .height(54.dp)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onSeekBy(-seekIntervalMs)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onSeekBy(seekIntervalMs)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_UP -> {
                        onNavigateUp?.invoke()
                        onNavigateUp != null
                    }

                    else -> false
                }
            },
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (focused) 8.dp else 0.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(if (focused) 6.dp else 3.dp)) {
                val radius = size.height / 2f
                drawRoundRect(
                    color = TelevisionColors.ProgressTrack,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                val bufferedFraction = if (duration == null) {
                    0f
                } else {
                    ((bufferedMs ?: 0L).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }
                drawRoundRect(
                    color = TelevisionColors.PaperSoft,
                    size = size.copy(width = size.width * bufferedFraction),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                val playedFraction = if (duration == null) {
                    0f
                } else {
                    (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }
                drawRoundRect(
                    color = TelevisionColors.Paper,
                    size = size.copy(width = size.width * playedFraction),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                if (duration != null) {
                    chapters.forEach { chapter ->
                        val chapterFraction =
                            (chapter.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        val x = size.width * chapterFraction
                        drawLine(
                            color = if (chapter.positionMs <= positionMs) {
                                TelevisionColors.Paper
                            } else {
                                TelevisionColors.PaperSoft
                            },
                            start = androidx.compose.ui.geometry.Offset(x, -3f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height + 3f),
                            strokeWidth = 2f,
                        )
                    }
                }
                drawCircle(
                    color = TelevisionColors.Paper,
                    radius = if (focused) 8.5f else 6.5f,
                    center = androidx.compose.ui.geometry.Offset(
                        x = size.width * playedFraction,
                        y = size.height / 2f,
                    ),
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatPlayerTime(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TelevisionColors.Paper,
                )
                Text(
                    text = duration?.let {
                        "-${formatPlayerTime((it - positionMs).coerceAtLeast(0L))}"
                    } ?: "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelevisionColors.PaperMuted,
                )
            }
        }
    }
}

@Composable
internal fun MiniPlayerTimeline(
    positionMs: Long,
    durationMs: Long?,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs?.takeIf { it > 0L }
    val fraction = if (duration == null) 0f else (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(58.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to TelevisionColors.Black.copy(alpha = 0.08f),
                    1f to TelevisionColors.Black.copy(alpha = 0.72f),
                ),
            )
            .padding(horizontal = 67.dp)
            .padding(top = 25.dp, bottom = 13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatPlayerTime(positionMs), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(9.dp))
            Canvas(Modifier.weight(1f).height(2.dp)) {
                val radius = size.height / 2f
                drawRoundRect(
                    color = TelevisionColors.ProgressTrack,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                drawRoundRect(
                    color = TelevisionColors.Paper,
                    size = size.copy(width = size.width * fraction),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                duration?.let { "-${formatPlayerTime((it - positionMs).coerceAtLeast(0L))}" } ?: "--:--",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
