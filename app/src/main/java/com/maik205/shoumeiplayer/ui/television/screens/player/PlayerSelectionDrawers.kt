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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
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
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import java.util.Locale

@Composable
internal fun PlayerSelectionPanel(
    title: String,
    rows: List<PlayerSelectionRow>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    entryRowKey: String? = null,
) {
    val entry = remember { FocusRequester() }
    val colors = TelevisionTheme.colors
    // Open on the option that is currently in effect, not on the top of the list. Every drawer
    // here answers "what is this set to?" -- audio track, subtitles, quality, speed, HDR, frame --
    // and starting at the first row both hid the answer and made changing it a scroll away.
    //
    // [entryRowKey] overrides that when this panel is being returned to from a child it opened:
    // the row that opened the child is where the viewer was, and it is where they expect to be
    // when they come back (PLAYER-008).
    val entryIndex = rows.indexOfFirst { it.key == entryRowKey && it.interactive }
        .takeIf { it >= 0 }
        ?: rows.indexOfFirst { it.selected && it.interactive }.takeIf { it >= 0 }
        ?: rows.indexOfFirst(PlayerSelectionRow::interactive).takeIf { it >= 0 }
        ?: 0
    val hasInteractiveRow = rows.any(PlayerSelectionRow::interactive)
    val backFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(title, rows.size, entryIndex, hasInteractiveRow, entryRowKey) {
        if (rows.isNotEmpty()) listState.scrollToItem(entryIndex)
        withFrameNanos { }
        // A read-only panel has nothing in its list to stand on, so Back owns the entry focus.
        runCatching { (if (hasInteractiveRow) entry else backFocus).requestFocus() }
    }
    Column(
        modifier = modifier
            .playerModalFocusTrap()
            .width(310.dp)
            .fillMaxHeight()
            .playerDrawerSurface(colors.Black)
            .padding(
                start = 32.dp,
                end = 48.dp,
                top = 32.dp,
                bottom = 38.dp,
            ),
    ) {
        TelevisionFocusRevealButton(
            label = stringResource(R.string.tv_back),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onDismiss,
            expandedWidth = 54.dp,
            focusRequester = backFocus,
        )
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.5.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                PlayerSelectionPanelRow(
                    row = row,
                    focusRequester = if (index == entryIndex) entry else null,
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
    /**
     * Whether the row does anything when it is selected. Playback Information is a read-only
     * report, and rendering it as buttons meant six focus stops where Center produced nothing at
     * all -- the remote looked broken rather than the panel looking informational.
     */
    val interactive: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun PlayerSelectionPanelRow(
    row: PlayerSelectionRow,
    focusRequester: FocusRequester?,
) {
    if (!row.interactive) {
        PlayerSelectionRowContent(row = row, focused = false, modifier = Modifier.height(26.dp))
        return
    }
    TelevisionFocusSurface(
        onClick = row.onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = if (row.selected) 0.96f else 0.66f,
        modifier = Modifier.fillMaxWidth().height(26.dp),
    ) { focused ->
        PlayerSelectionRowContent(row = row, focused = focused, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun PlayerSelectionRowContent(
    row: PlayerSelectionRow,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (focused) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.label,
                style = MaterialTheme.typography.labelLarge,
                color = TelevisionTheme.colors.Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TelevisionTheme.colors.PaperMuted,
                    maxLines = 1,
                )
            }
        }
        if (row.selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.tv_selected),
                tint = TelevisionTheme.colors.Paper,
                modifier = Modifier.size(10.dp),
            )
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
    val colors = TelevisionTheme.colors
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        modifier = modifier
            .playerModalFocusTrap()
            .width(310.dp)
            .fillMaxHeight()
            .playerDrawerSurface(colors.Black)
            .padding(
                start = 32.dp,
                end = 48.dp,
                top = 32.dp,
                bottom = 38.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TelevisionFocusRevealButton(
            label = stringResource(R.string.tv_back),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onDismiss,
            expandedWidth = 54.dp,
        )
        Spacer(Modifier.height(17.dp))
        Text(
            stringResource(R.string.tv_player_playback_options),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(R.string.tv_player_delay_adjust_unbounded),
            style = MaterialTheme.typography.bodySmall,
            color = colors.PaperMuted,
        )
        Spacer(Modifier.height(4.dp))
        DelayRow(
            label = stringResource(R.string.tv_player_audio_delay),
            valueMs = audioDelayMs,
            onChange = onAudioDelayChange,
            focusRequester = first,
        )
        DelayRow(
            label = stringResource(R.string.tv_player_subtitle_delay),
            valueMs = subtitleDelayMs,
            onChange = onSubtitleDelayChange,
        )
        PlayerTextButton(
            label = stringResource(R.string.tv_player_reset_both),
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
    val colors = TelevisionTheme.colors
    LaunchedEffect(title) { runCatching { first.requestFocus() } }
    Column(
        modifier = modifier
            .playerModalFocusTrap()
            .width(310.dp)
            .fillMaxHeight()
            .playerDrawerSurface(colors.Black)
            .padding(
                start = 32.dp,
                end = 48.dp,
                top = 32.dp,
                bottom = 38.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TelevisionFocusRevealButton(
            label = stringResource(R.string.tv_back),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onDismiss,
            expandedWidth = 54.dp,
        )
        Spacer(Modifier.height(17.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.tv_player_delay_adjust),
            style = MaterialTheme.typography.bodySmall,
            color = colors.PaperMuted,
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
    val colors = TelevisionTheme.colors
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
                    if (focused) colors.Paper else colors.Paper.copy(alpha = 0.1f),
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
                        color = if (focused) colors.Black else colors.Paper,
                    )
                    Text(
                        stringResource(R.string.tv_player_center_resets),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (focused) {
                            colors.Black.copy(alpha = 0.62f)
                        } else {
                            colors.PaperMuted
                        },
                    )
                }
                Text(
                    signedDelay(valueMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (focused) colors.Black else colors.Paper,
                )
            }
            Spacer(Modifier.height(2.5.dp))
            Canvas(Modifier.fillMaxWidth().height(2.5.dp)) {
                val track = if (focused) colors.Black.copy(alpha = 0.2f) else colors.ProgressTrack
                val marker = if (focused) colors.Black else colors.Paper
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

/**
 * Not `@Composable`: it is chained inside `Modifier` expressions passed as a default-scope argument,
 * which is not a composable context. Callers hoist [TelevisionTheme.colors] and pass its `Black`
 * token in rather than reading the local here.
 */
private fun Modifier.playerDrawerSurface(black: Color): Modifier = background(
    Brush.horizontalGradient(
        0f to Color.Transparent,
        0.18f to black.copy(alpha = 0.18f),
        0.5f to black.copy(alpha = 0.68f),
        0.78f to black.copy(alpha = 0.94f),
        1f to black.copy(alpha = 0.99f),
    ),
)
