package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
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
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
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
                    0f to TelevisionTheme.colors.Black.copy(alpha = 0.98f),
                    0.58f to TelevisionTheme.colors.Black.copy(alpha = 0.72f),
                    1f to TelevisionTheme.colors.Black.copy(alpha = 0.34f),
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
                stringResource(R.string.tv_player_playback_stopped),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = TelevisionTheme.colors.PaperMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerCompactActionButton(
                    label = stringResource(R.string.tv_player_retry),
                    icon = Icons.Default.Refresh,
                    onClick = onRetry,
                    focusRequester = retry,
                    selected = true,
                    expandedWidth = 110.dp,
                )
                PlayerCompactActionButton(
                    label = stringResource(R.string.tv_back),
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
                    0f to TelevisionTheme.colors.Black.copy(alpha = 0.94f),
                    0.62f to TelevisionTheme.colors.Black.copy(alpha = 0.58f),
                    1f to TelevisionTheme.colors.Black.copy(alpha = 0.2f),
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
                stringResource(R.string.tv_player_still_watching),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerCompactActionButton(
                    label = stringResource(R.string.tv_player_continue),
                    icon = Icons.Default.PlayArrow,
                    onClick = onContinue,
                    focusRequester = continueFocus,
                    selected = true,
                    expandedWidth = 116.dp,
                )
                PlayerCompactActionButton(
                    label = stringResource(R.string.tv_player_stop),
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
    browsingEpisodes: Boolean,
    onBrowsingEpisodesChange: (Boolean) -> Unit,
    onPlayNext: () -> Unit,
    onPlayEpisode: (String) -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = remember { FocusRequester() }
    var preview by remember(upNext?.itemId, episodes) { mutableStateOf(upNext ?: episodes.firstOrNull()) }
    LaunchedEffect(upNext?.itemId, browsingEpisodes) { runCatching { primary.requestFocus() } }
    Box(
        modifier = Modifier
            .playerModalFocusTrap()
            .fillMaxSize()
            .background(TelevisionTheme.colors.Black),
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
                        0f to TelevisionTheme.colors.Black.copy(alpha = 0.97f),
                        0.52f to TelevisionTheme.colors.Black.copy(alpha = 0.68f),
                        1f to TelevisionTheme.colors.Black.copy(alpha = 0.14f),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to TelevisionTheme.colors.Black.copy(alpha = 0.9f),
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
                        label = stringResource(R.string.tv_back),
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { onBrowsingEpisodesChange(false) },
                        focusRequester = primary,
                        expandedWidth = 54.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.tv_episodes),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Spacer(Modifier.height(18.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        itemsIndexed(episodes, key = { index, episode -> "${episode.itemId}:$index" }) { _, episode ->
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
                                        episode.subtitle ?: stringResource(R.string.tv_player_next),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelevisionTheme.colors.PaperMuted,
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Text(
                                        episode.title,
                                        style = if (focused) {
                                            MaterialTheme.typography.titleMedium
                                        } else {
                                            MaterialTheme.typography.bodyLarge
                                        },
                                        color = if (focused) TelevisionTheme.colors.Paper else TelevisionTheme.colors.PaperMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (focused) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = stringResource(R.string.play),
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
                        preview?.subtitle ?: stringResource(R.string.tv_episode),
                        style = MaterialTheme.typography.labelLarge,
                        color = TelevisionTheme.colors.PaperMuted,
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
                    upNext?.title ?: stringResource(R.string.tv_player_playback_complete),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                upNext?.subtitle?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = TelevisionTheme.colors.PaperMuted)
                }
                if (upNext == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.tv_player_that_is_all),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TelevisionTheme.colors.PaperMuted,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (upNext != null) {
                        val playNextLabel = if (countdownSeconds != null) {
                            stringResource(R.string.tv_player_play_next_countdown, countdownSeconds)
                        } else {
                            stringResource(R.string.tv_player_play_next)
                        }
                        PlayerCompactActionButton(
                            label = playNextLabel,
                            icon = Icons.Default.PlayArrow,
                            onClick = onPlayNext,
                            focusRequester = primary,
                            selected = true,
                            expandedWidth = 150.dp,
                        )
                        if (episodes.isNotEmpty()) {
                            PlayerCompactActionButton(
                                label = stringResource(R.string.tv_episodes),
                                icon = Icons.Default.VideoLibrary,
                                onClick = { onBrowsingEpisodesChange(true) },
                                expandedWidth = 118.dp,
                            )
                        }
                    } else {
                        PlayerCompactActionButton(
                            label = stringResource(R.string.tv_player_play_again),
                            icon = Icons.Default.Refresh,
                            onClick = onReplay,
                            focusRequester = primary,
                            selected = true,
                            expandedWidth = 116.dp,
                        )
                    }
                    PlayerCompactActionButton(
                        label = stringResource(R.string.tv_back),
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
                        .background(TelevisionTheme.colors.ProgressTrack),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((countdownSeconds / 10f).coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(TelevisionTheme.colors.PaperMuted),
                    )
                }
            }
        }
    }
}
