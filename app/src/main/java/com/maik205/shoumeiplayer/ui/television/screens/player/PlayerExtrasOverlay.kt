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
import androidx.compose.ui.res.stringResource
import com.maik205.shoumeiplayer.R
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
internal fun PlayerExtrasOverlay(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenPerson: (CastMemberUi) -> Unit,
    onRetry: () -> Unit,
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
                label = stringResource(R.string.tv_back),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onDismiss,
                focusRequester = backFocus,
            )
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.tv_player_while_watching), style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(26.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (state.similar.isNotEmpty()) {
                item(key = "similar") {
                    ExtrasMediaRow(
                        title = stringResource(R.string.tv_player_more_like),
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
            if (state.shelvesError != null) {
                item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = state.shelvesError.resolveMessage(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TelevisionColors.PaperMuted,
                        )
                        PlayerActionButton(
                            label = stringResource(R.string.retry),
                            icon = Icons.Default.Refresh,
                            onClick = onRetry,
                        )
                    }
                }
            } else if (state.similar.isEmpty() && state.cast.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (state.shelvesLoading) {
                            stringResource(R.string.tv_loading_more)
                        } else {
                            stringResource(R.string.tv_player_empty_extras)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TelevisionColors.PaperMuted,
                    )
                }
            }
        }
    }
}

internal fun Modifier.playerModalFocusTrap(): Modifier =
    focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()

@Composable
private fun ExtrasMediaRow(
    title: String,
    items: List<PlayerShelfItem>,
    onOpen: (String) -> Unit,
) {
    val railFocusRequesters = remember(items.map(PlayerShelfItem::id)) {
        List(items.size) { FocusRequester() }
    }
    val railState = rememberLazyListState()
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = railState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(items, key = { index, item -> "${item.id}:$index" }) { index, item ->
                PlayerArtworkTile(
                    title = item.title,
                    subtitle = item.subtitle,
                    artworkUrl = item.artworkUrl,
                    width = 236.dp,
                    height = 133.dp,
                    onClick = { onOpen(item.id) },
                    focusRequester = railFocusRequesters.getOrNull(index),
                    modifier = Modifier.televisionHorizontalWrap(
                        index,
                        railFocusRequesters,
                        railState,
                    ),
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
    val railFocusRequesters = remember(cast.map(CastMemberUi::id)) {
        List(cast.size) { FocusRequester() }
    }
    val railState = rememberLazyListState()
    Column {
        Text(stringResource(R.string.tv_player_cast), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = railState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(cast, key = { index, person -> "${person.id}:$index" }) { index, person ->
                TelevisionFocusSurface(
                    onClick = { onOpen(person) },
                    focusRequester = railFocusRequesters.getOrNull(index),
                    scaleTo = 1f,
                    modifier = Modifier
                        .width(116.dp)
                        .televisionHorizontalWrap(index, railFocusRequesters, railState),
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
    modifier: Modifier = Modifier,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        modifier = modifier.width(width),
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
