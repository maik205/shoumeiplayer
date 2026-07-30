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
