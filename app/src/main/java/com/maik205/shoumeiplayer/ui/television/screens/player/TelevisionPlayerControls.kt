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
import com.maik205.shoumeiplayer.ui.components.MediaCardUi
import com.maik205.shoumeiplayer.ui.screens.player.CastMemberUi
import com.maik205.shoumeiplayer.ui.screens.player.ChapterMark
import com.maik205.shoumeiplayer.ui.screens.player.PlayerUiState
import com.maik205.shoumeiplayer.ui.screens.player.UpNextUi
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import java.util.Locale

internal enum class TelevisionPlayerPanel {
    Audio,
    Subtitles,
    Chapters,
    Quality,
    Speed,
    Frame,
    Hdr,
    VideoTrack,
    AudioDelay,
    SubtitleDelay,
    Deinterlace,
    Sleep,
    Information,
    Options,
    More,
    Extras,
}

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

@Composable
internal fun PlayerSelectionPanel(
    title: String,
    rows: List<PlayerSelectionRow>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(title, rows.size) {
        runCatching { first.requestFocus() }
    }
    Column(
        modifier = modifier
            .playerModalFocusTrap()
            .width(310.dp)
            .fillMaxHeight()
            .playerDrawerSurface()
            .padding(
                start = 32.dp,
                end = 48.dp,
                top = 32.dp,
                bottom = 38.dp,
            ),
    ) {
        TelevisionFocusRevealButton(
            label = "Back",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onDismiss,
            expandedWidth = 54.dp,
        )
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.5.dp)) {
            items(rows, key = PlayerSelectionRow::key) { row ->
                PlayerSelectionPanelRow(
                    row = row,
                    focusRequester = if (row == rows.firstOrNull()) first else null,
                )
            }
        }
    }
}

internal data class PlayerSelectionRow(
    val key: String,
    val label: String,
    val detail: String? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun PlayerSelectionPanelRow(
    row: PlayerSelectionRow,
    focusRequester: FocusRequester?,
) {
    TelevisionFocusSurface(
        onClick = row.onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = if (row.selected) 0.96f else 0.66f,
        modifier = Modifier.fillMaxWidth().height(26.dp),
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (focused) 4.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = TelevisionColors.Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = TelevisionColors.PaperMuted,
                        maxLines = 1,
                    )
                }
            }
            if (row.selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = TelevisionColors.Paper,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
internal fun PlaybackOptionsPanel(
    audioDelayMs: Long,
    subtitleDelayMs: Long,
    onAudioDelayChange: (Long) -> Unit,
    onSubtitleDelayChange: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        modifier = modifier
            .playerModalFocusTrap()
            .width(310.dp)
            .fillMaxHeight()
            .playerDrawerSurface()
            .padding(
                start = 32.dp,
                end = 48.dp,
                top = 32.dp,
                bottom = 38.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TelevisionFocusRevealButton(
            label = "Back",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onDismiss,
            expandedWidth = 54.dp,
        )
        Spacer(Modifier.height(17.dp))
        Text("Playback options", style = MaterialTheme.typography.headlineMedium)
        Text(
            "LEFT / RIGHT adjusts without an artificial limit. CENTER resets that row.",
            style = MaterialTheme.typography.bodySmall,
            color = TelevisionColors.PaperMuted,
        )
        Spacer(Modifier.height(4.dp))
        DelayRow(
            label = "Audio delay",
            valueMs = audioDelayMs,
            onChange = onAudioDelayChange,
            focusRequester = first,
        )
        DelayRow(
            label = "Subtitle delay",
            valueMs = subtitleDelayMs,
            onChange = onSubtitleDelayChange,
        )
        PlayerTextButton(
            label = "Reset both",
            icon = Icons.Default.Refresh,
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            height = 26.dp,
        )
    }
}

@Composable
internal fun PlaybackDelayPanel(
    title: String,
    valueMs: Long,
    onChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(title) { runCatching { first.requestFocus() } }
    Column(
        modifier = modifier
            .playerModalFocusTrap()
            .width(310.dp)
            .fillMaxHeight()
            .playerDrawerSurface()
            .padding(
                start = 32.dp,
                end = 48.dp,
                top = 32.dp,
                bottom = 38.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TelevisionFocusRevealButton(
            label = "Back",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onDismiss,
            expandedWidth = 54.dp,
        )
        Spacer(Modifier.height(17.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            "LEFT / RIGHT adjusts by 100 ms. CENTER resets.",
            style = MaterialTheme.typography.bodySmall,
            color = TelevisionColors.PaperMuted,
        )
        Spacer(Modifier.height(4.dp))
        DelayRow(
            label = title,
            valueMs = valueMs,
            onChange = onChange,
            focusRequester = first,
        )
    }
}

@Composable
private fun DelayRow(
    label: String,
    valueMs: Long,
    onChange: (Long) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TelevisionFocusSurface(
        onClick = { onChange(0L) },
        focusRequester = focusRequester,
        scaleTo = 1f,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onChange(valueMs - 100L)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onChange(valueMs + 100L)
                        true
                    }

                    else -> false
                }
            },
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (focused) TelevisionColors.Paper else TelevisionColors.Paper.copy(alpha = 0.1f),
                    RoundedCornerShape(TelevisionDimensions.FocusRadius),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (focused) TelevisionColors.Black else TelevisionColors.Paper,
                    )
                    Text(
                        "CENTER resets",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (focused) {
                            TelevisionColors.Black.copy(alpha = 0.62f)
                        } else {
                            TelevisionColors.PaperMuted
                        },
                    )
                }
                Text(
                    signedDelay(valueMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (focused) TelevisionColors.Black else TelevisionColors.Paper,
                )
            }
            Spacer(Modifier.height(2.5.dp))
            Canvas(Modifier.fillMaxWidth().height(2.5.dp)) {
                val track = if (focused) TelevisionColors.Black.copy(alpha = 0.2f) else TelevisionColors.ProgressTrack
                val marker = if (focused) TelevisionColors.Black else TelevisionColors.Paper
                val centerX = size.width / 2f
                val normalized = valueMs.toDouble() / (kotlin.math.abs(valueMs.toDouble()) + 1_000.0)
                val thumbX = centerX + (normalized.toFloat() * size.width * 0.45f)
                drawRoundRect(
                    color = track,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
                drawLine(
                    color = marker.copy(alpha = 0.5f),
                    start = androidx.compose.ui.geometry.Offset(centerX, -2f),
                    end = androidx.compose.ui.geometry.Offset(centerX, size.height + 2f),
                    strokeWidth = 2f,
                )
                drawCircle(
                    color = marker,
                    radius = if (focused) 7f else 5f,
                    center = androidx.compose.ui.geometry.Offset(thumbX, size.height / 2f),
                )
            }
        }
    }
}

private fun Modifier.playerDrawerSurface(): Modifier = background(
    Brush.horizontalGradient(
        0f to Color.Transparent,
        0.18f to TelevisionColors.Black.copy(alpha = 0.18f),
        0.5f to TelevisionColors.Black.copy(alpha = 0.68f),
        0.78f to TelevisionColors.Black.copy(alpha = 0.94f),
        1f to TelevisionColors.Black.copy(alpha = 0.99f),
    ),
)

@Composable
internal fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
    Box(
        Modifier
            .playerModalFocusTrap()
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to TelevisionColors.Black.copy(alpha = 0.98f),
                    0.58f to TelevisionColors.Black.copy(alpha = 0.72f),
                    1f to TelevisionColors.Black.copy(alpha = 0.34f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(680.dp)
                .padding(start = 67.dp, bottom = 54.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Playback stopped",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = TelevisionColors.PaperMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerCompactActionButton(
                    label = "Retry",
                    icon = Icons.Default.Refresh,
                    onClick = onRetry,
                    focusRequester = retry,
                    selected = true,
                    expandedWidth = 110.dp,
                )
                PlayerCompactActionButton(
                    label = "Back",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    expandedWidth = 90.dp,
                )
            }
        }
    }
}

@Composable
internal fun StillWatchingOverlay(
    onContinue: () -> Unit,
    onStop: () -> Unit,
) {
    val continueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { continueFocus.requestFocus() } }
    Box(
        Modifier
            .playerModalFocusTrap()
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to TelevisionColors.Black.copy(alpha = 0.94f),
                    0.62f to TelevisionColors.Black.copy(alpha = 0.58f),
                    1f to TelevisionColors.Black.copy(alpha = 0.2f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(740.dp)
                .padding(start = 67.dp, bottom = 62.dp),
        ) {
            Text(
                "Still watching?",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerCompactActionButton(
                    label = "Continue",
                    icon = Icons.Default.PlayArrow,
                    onClick = onContinue,
                    focusRequester = continueFocus,
                    selected = true,
                    expandedWidth = 116.dp,
                )
                PlayerCompactActionButton(
                    label = "Stop",
                    icon = Icons.Default.Stop,
                    onClick = onStop,
                    expandedWidth = 88.dp,
                )
            }
        }
    }
}

@Composable
internal fun PostPlayOverlay(
    upNext: UpNextUi?,
    episodes: List<UpNextUi>,
    countdownSeconds: Int?,
    onPlayNext: () -> Unit,
    onPlayEpisode: (String) -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = remember { FocusRequester() }
    var browsingEpisodes by remember { mutableStateOf(false) }
    var preview by remember(upNext?.itemId, episodes) { mutableStateOf(upNext ?: episodes.firstOrNull()) }
    LaunchedEffect(upNext?.itemId, browsingEpisodes) { runCatching { primary.requestFocus() } }
    Box(
        modifier = Modifier
            .playerModalFocusTrap()
            .fillMaxSize()
            .background(TelevisionColors.Black),
    ) {
        preview?.thumbUrl?.let { artwork ->
            AsyncImage(
                model = artwork,
                contentDescription = preview?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to TelevisionColors.Black.copy(alpha = 0.97f),
                        0.52f to TelevisionColors.Black.copy(alpha = 0.68f),
                        1f to TelevisionColors.Black.copy(alpha = 0.14f),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to TelevisionColors.Black.copy(alpha = 0.9f),
                    ),
                ),
        )

        if (browsingEpisodes && episodes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 67.dp, end = 67.dp, top = 34.dp, bottom = 42.dp),
                horizontalArrangement = Arrangement.spacedBy(68.dp),
            ) {
                Column(Modifier.width(390.dp)) {
                    TelevisionFocusRevealButton(
                        label = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { browsingEpisodes = false },
                        focusRequester = primary,
                        expandedWidth = 54.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Episodes", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(18.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(episodes, key = UpNextUi::itemId) { episode ->
                            TelevisionFocusSurface(
                                onClick = { onPlayEpisode(episode.itemId) },
                                scaleTo = 1f,
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                            ) { focused ->
                                if (focused) preview = episode
                                Row(
                                    Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        episode.subtitle ?: "Next",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelevisionColors.PaperMuted,
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Text(
                                        episode.title,
                                        style = if (focused) {
                                            MaterialTheme.typography.titleMedium
                                        } else {
                                            MaterialTheme.typography.bodyLarge
                                        },
                                        color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (focused) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.Bottom),
                ) {
                    Text(
                        preview?.subtitle ?: "Episode",
                        style = MaterialTheme.typography.labelLarge,
                        color = TelevisionColors.PaperMuted,
                    )
                    Text(
                        preview?.title.orEmpty(),
                        style = MaterialTheme.typography.displayMedium,
                        maxLines = 2,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(620.dp)
                    .padding(start = 67.dp, bottom = 48.dp),
            ) {
                Text(
                    if (upNext != null) "Up next" else "Playback complete",
                    style = MaterialTheme.typography.labelLarge,
                    color = TelevisionColors.PaperMuted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    upNext?.title ?: "That’s all",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                upNext?.subtitle?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = TelevisionColors.PaperMuted)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (upNext != null) {
                        PlayerCompactActionButton(
                            label = countdownSeconds?.let { "Play next · $it" } ?: "Play next",
                            icon = Icons.Default.PlayArrow,
                            onClick = onPlayNext,
                            focusRequester = primary,
                            selected = true,
                            expandedWidth = 150.dp,
                        )
                        if (episodes.isNotEmpty()) {
                            PlayerCompactActionButton(
                                label = "Episodes",
                                icon = Icons.Default.VideoLibrary,
                                onClick = { browsingEpisodes = true },
                                expandedWidth = 118.dp,
                            )
                        }
                    } else {
                        PlayerCompactActionButton(
                            label = "Play again",
                            icon = Icons.Default.Refresh,
                            onClick = onReplay,
                            focusRequester = primary,
                            selected = true,
                            expandedWidth = 116.dp,
                        )
                    }
                    PlayerCompactActionButton(
                        label = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        expandedWidth = 90.dp,
                    )
                }
            }
            if (countdownSeconds != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(TelevisionColors.ProgressTrack),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((countdownSeconds / 10f).coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(TelevisionColors.PaperMuted),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyPostPlayOverlay(
    upNext: UpNextUi?,
    countdownSeconds: Int?,
    onPlayNext: () -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = remember { FocusRequester() }
    LaunchedEffect(upNext?.itemId) { runCatching { primary.requestFocus() } }
    ModalScrim {
        if (upNext != null) {
            Text("Up next", style = MaterialTheme.typography.titleMedium, color = TelevisionColors.PaperMuted)
            AsyncImage(
                model = upNext.thumbUrl,
                contentDescription = upNext.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(420.dp)
                    .height(236.dp)
                    .background(TelevisionColors.ImagePlaceholder),
            )
            Text(
                upNext.title,
                style = MaterialTheme.typography.headlineMedium.televisionItemTitle(),
                maxLines = 2,
            )
            upNext.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TelevisionColors.PaperMuted)
            }
            PlayerTextButton(
                label = countdownSeconds?.let { "Play next · $it" } ?: "Play next",
                onClick = onPlayNext,
                focusRequester = primary,
                modifier = Modifier.width(220.dp),
            )
        } else {
            Text("Playback complete", style = MaterialTheme.typography.displaySmall)
            PlayerTextButton(
                label = "Play again",
                icon = Icons.Default.Refresh,
                onClick = onReplay,
                focusRequester = primary,
                modifier = Modifier.width(190.dp),
            )
        }
        PlayerTextButton(
            label = "Back",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            modifier = Modifier.width(160.dp),
        )
    }
}

@Composable
private fun ModalScrim(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .playerModalFocusTrap()
            .fillMaxSize()
            .background(TelevisionColors.Black.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(540.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun PlayerExtrasOverlay(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenPerson: (CastMemberUi) -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
    Column(
        modifier = Modifier
            .playerModalFocusTrap()
            .fillMaxSize()
            .background(TelevisionColors.Black.copy(alpha = 0.97f))
            .padding(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                top = TelevisionDimensions.SafeTop,
                bottom = TelevisionDimensions.SafeBottom,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerActionButton(
                label = "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onDismiss,
                focusRequester = backFocus,
            )
            Spacer(Modifier.width(16.dp))
            Text("While you watch", style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(26.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (state.similar.isNotEmpty()) {
                item(key = "similar") {
                    ExtrasMediaRow(
                        title = "More like this",
                        items = state.similar,
                        onOpen = onOpenItem,
                    )
                }
            }
            if (state.cast.isNotEmpty()) {
                item(key = "cast") {
                    CastRow(state.cast, onOpenPerson)
                }
            }
            if (state.similar.isEmpty() && state.cast.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (state.shelvesLoading) "Loading…" else "Nothing else is available for this title.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TelevisionColors.PaperMuted,
                    )
                }
            }
        }
    }
}

private fun Modifier.playerModalFocusTrap(): Modifier =
    focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()

@Composable
private fun ExtrasMediaRow(
    title: String,
    items: List<MediaCardUi>,
    onOpen: (String) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.focusGroup(),
        ) {
            items(items, key = MediaCardUi::id) { item ->
                PlayerArtworkTile(
                    title = item.title,
                    subtitle = item.subtitle,
                    artworkUrl = item.imageUrl,
                    width = 236.dp,
                    height = 133.dp,
                    onClick = { onOpen(item.id) },
                )
            }
        }
    }
}

@Composable
private fun CastRow(
    cast: List<CastMemberUi>,
    onOpen: (CastMemberUi) -> Unit,
) {
    Column {
        Text("Cast", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.focusGroup()) {
            items(cast, key = CastMemberUi::id) { person ->
                TelevisionFocusSurface(
                    onClick = { onOpen(person) },
                    scaleTo = 1f,
                    modifier = Modifier.width(116.dp),
                ) { focused ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = person.imageUrl,
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(94.dp)
                                .clip(CircleShape)
                                .background(TelevisionColors.ImagePlaceholder, CircleShape),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            person.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        person.role?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = TelevisionColors.PaperSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerArtworkTile(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        modifier = Modifier.width(width),
    ) { focused ->
        Column {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(width)
                    .height(height)
                    .background(
                        if (focused) TelevisionColors.Paper.copy(alpha = 0.18f) else TelevisionColors.ImagePlaceholder,
                        RoundedCornerShape(TelevisionDimensions.FocusRadius),
                    ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.televisionItemTitle(),
                color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TelevisionColors.PaperSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun audioRows(
    tracks: List<PlayerTrack>,
    onSelect: (PlayerTrack) -> Unit,
): List<PlayerSelectionRow> = tracks.map { track ->
    val language = displayTrackLanguage(track.language)
    val label = track.label
        .takeUnless { it.isBlank() || it.equals(track.language, ignoreCase = true) }
        ?: language
    PlayerSelectionRow(
        key = "audio:${track.id}",
        label = label,
        detail = language.takeUnless { it.equals(label, ignoreCase = true) },
        selected = track.selected,
        onClick = { onSelect(track) },
    )
}

internal fun subtitleRows(
    tracks: List<PlayerTrack>,
    onSelect: (PlayerTrack) -> Unit,
): List<PlayerSelectionRow> = tracks.map { track ->
    val language = displayTrackLanguage(track.language)
    val label = track.label
        .takeUnless { it.isBlank() || it.equals(track.language, ignoreCase = true) }
        ?: language
    PlayerSelectionRow(
        key = "subtitle:${track.id}",
        label = label,
        detail = language.takeUnless { it.equals(label, ignoreCase = true) },
        selected = track.selected,
        onClick = { onSelect(track) },
    )
}

internal fun chapterRows(
    chapters: List<ChapterMark>,
    onSelect: (ChapterMark) -> Unit,
): List<PlayerSelectionRow> = chapters.mapIndexed { index, chapter ->
    PlayerSelectionRow(
        key = "chapter:${chapter.positionMs}",
        label = chapter.name?.takeIf(String::isNotBlank) ?: "Chapter ${index + 1}",
        detail = formatPlayerTime(chapter.positionMs),
        onClick = { onSelect(chapter) },
    )
}

private fun displayTrackLanguage(language: String?): String {
    val value = language?.trim().orEmpty()
    return when (value.lowercase(Locale.ROOT)) {
        "ja", "jpn" -> "Japanese"
        "en", "eng" -> "English"
        "vi", "vie" -> "Vietnamese"
        "und", "" -> "Unknown language"
        else -> value
    }
}

internal fun qualityRows(
    selected: VideoQuality,
    onSelect: (VideoQuality) -> Unit,
): List<PlayerSelectionRow> = VideoQuality.Ladder.map { quality ->
    PlayerSelectionRow(
        key = "quality:${quality.name}",
        label = quality.label,
        detail = if (quality == VideoQuality.AUTO) "Prefer direct play" else "Maximum stream quality",
        selected = quality == selected,
        onClick = { onSelect(quality) },
    )
}

internal fun speedRows(
    selected: Float,
    onSelect: (Float) -> Unit,
): List<PlayerSelectionRow> = PlaybackSpeed.Steps.map { speed ->
    PlayerSelectionRow(
        key = "speed:$speed",
        label = PlaybackSpeed.label(speed),
        selected = PlaybackSpeed.nearestStep(selected) == speed,
        onClick = { onSelect(speed) },
    )
}

private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private fun signedDelay(valueMs: Long): String = when {
    valueMs > 0L -> "+${valueMs} ms"
    valueMs < 0L -> "${valueMs} ms"
    else -> "0 ms"
}
