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
