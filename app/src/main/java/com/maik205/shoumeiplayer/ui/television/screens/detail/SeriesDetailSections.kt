package com.maik205.shoumeiplayer.ui.television.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusScale
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionMediaTile
import com.maik205.shoumeiplayer.ui.television.components.TelevisionProgressMark
import com.maik205.shoumeiplayer.ui.television.components.TelevisionRowHeader
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SeriesDetailSection(
    nextUp: MediaItemUi?,
    nextUpItem: DetailItem?,
    seasons: List<MediaItemUi>,
    selectedSeasonId: String?,
    onSelect: (String) -> Unit,
    episodes: List<MediaItemUi>,
    episodeTitle: String,
    currentEpisodeId: String? = null,
    onPlay: (MediaItemUi) -> Unit,
    /** Given to whichever row is topmost here, so the hero above has one thing to point Down at. */
    entryFocusRequester: FocusRequester? = null,
    /** The hero, so the topmost row has somewhere defined to go Up to. */
    upFocusRequester: FocusRequester? = null,
) {
    val ownNextUpFocus = remember { FocusRequester() }
    val ownSeasonFocus = remember { FocusRequester() }
    val ownEpisodeFocus = remember { FocusRequester() }
    val hasSeasonSelector = seasons.isNotEmpty()
    val hasEpisodeRow = episodes.isNotEmpty()

    // Which row is topmost depends on what this item actually has. Whichever it is takes the
    // section's entry requester, so the hero's Down target does not have to know (DETAIL-005),
    // and the routing below stays correct when a neighbouring row is absent (DETAIL-006).
    val nextUpFocus = if (nextUp != null) entryFocusRequester ?: ownNextUpFocus else ownNextUpFocus
    val seasonFocus = if (nextUp == null && hasSeasonSelector) {
        entryFocusRequester ?: ownSeasonFocus
    } else {
        ownSeasonFocus
    }
    val episodeFocus = if (nextUp == null && !hasSeasonSelector && hasEpisodeRow) {
        entryFocusRequester ?: ownEpisodeFocus
    } else {
        ownEpisodeFocus
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to TelevisionTheme.colors.Black.copy(alpha = 0f),
                    0.12f to TelevisionTheme.colors.Black.copy(alpha = 0.94f),
                    1f to TelevisionTheme.colors.Black,
                ),
            )
            .padding(top = 66.dp, bottom = 12.dp),
    ) {
        if (nextUp != null) {
            SeriesNextUp(
                episode = nextUp,
                item = nextUpItem,
                focusRequester = nextUpFocus,
                upFocusRequester = upFocusRequester,
                downFocusRequester = when {
                    hasSeasonSelector -> seasonFocus
                    hasEpisodeRow -> episodeFocus
                    else -> null
                },
                onPlay = { onPlay(nextUp) },
            )
            Spacer(Modifier.height(36.dp))
        }
        EpisodeRail(
            title = episodeTitle,
            episodes = episodes,
            seasons = seasons,
            selectedSeasonId = selectedSeasonId,
            currentEpisodeId = currentEpisodeId,
            seasonFocusRequester = seasonFocus.takeIf { hasSeasonSelector },
            seasonUpFocusRequester = if (nextUp != null) nextUpFocus else upFocusRequester,
            episodeFocusRequester = episodeFocus.takeIf { hasEpisodeRow },
            episodeUpFocusRequester = when {
                hasSeasonSelector -> seasonFocus
                nextUp != null -> nextUpFocus
                else -> upFocusRequester
            },
            onSelectSeason = onSelect,
            onPlay = onPlay,
        )
    }
}

@Composable
private fun SeriesNextUp(
    episode: MediaItemUi,
    item: DetailItem?,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester?,
    onPlay: () -> Unit,
    upFocusRequester: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TelevisionDimensions.SafeHorizontal),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TelevisionFocusSurface(
            onClick = onPlay,
            focusRequester = focusRequester,
            restingAlpha = 0.78f,
            scaleTo = 1.025f,
            modifier = Modifier
                .weight(1.35f)
                .focusProperties {
                    upFocusRequester?.let { up = it }
                    downFocusRequester?.let { down = it }
                }
                .televisionBringIntoViewOnFocus(),
        ) { focused ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(TelevisionDimensions.FocusRadius))
                    .background(TelevisionTheme.colors.ImagePlaceholder),
            ) {
                episode.imageUrl?.let { image ->
                    AsyncImage(
                        model = image,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to TelevisionTheme.colors.Black.copy(alpha = 0f),
                                1f to TelevisionTheme.colors.Black.copy(alpha = 0.28f),
                            ),
                        ),
                )
                if (focused) {
                    Icon(
                        imageVector = if (episode.watched) Icons.Default.Replay else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (episode.watched) R.string.tv_detail_replay_item else R.string.tv_detail_play_item,
                            episode.title,
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                            .size(42.dp),
                    )
                }
                TelevisionProgressMark(
                    progress = episode.progress ?: if (episode.watched) 1f else null,
                    watched = false,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        Column(modifier = Modifier.weight(0.65f)) {
            Text(
                text = episodeNumberLabel(episode),
                style = MaterialTheme.typography.labelMedium,
                color = TelevisionTheme.colors.PaperMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.next_up),
                style = MaterialTheme.typography.titleSmall,
                color = TelevisionTheme.colors.PaperMuted,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineMedium.televisionItemTitle(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.overview?.let { overview ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TelevisionTheme.colors.PaperMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val metadata = episodeMetadata(episode, item)
            if (metadata.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = metadata.joinToString("   "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TelevisionTheme.colors.PaperMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            episodeCredits(item)?.let { credits ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = credits,
                    style = MaterialTheme.typography.bodySmall,
                    color = TelevisionTheme.colors.PaperMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun EpisodeRail(
    title: String,
    episodes: List<MediaItemUi>,
    seasons: List<MediaItemUi> = emptyList(),
    selectedSeasonId: String? = null,
    currentEpisodeId: String? = null,
    seasonFocusRequester: FocusRequester? = null,
    seasonUpFocusRequester: FocusRequester? = null,
    episodeFocusRequester: FocusRequester? = null,
    episodeUpFocusRequester: FocusRequester? = null,
    onSelectSeason: (String) -> Unit = {},
    onPlay: (MediaItemUi) -> Unit,
) {
    val currentEpisodeIndex = episodes.indexOfFirst { it.id == currentEpisodeId }.coerceAtLeast(0)
    val entryEpisodeIndex = if (currentEpisodeId != null) currentEpisodeIndex else 0
    val railFocusRequesters = remember(
        episodes.size,
        episodes.fold(1) { hash, episode -> 31 * hash + episode.id.hashCode() },
        entryEpisodeIndex,
        episodeFocusRequester,
    ) {
        List(episodes.size) { index ->
            if (index == entryEpisodeIndex && episodeFocusRequester != null) {
                episodeFocusRequester
            } else {
                FocusRequester()
            }
        }
    }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = currentEpisodeIndex)
    LaunchedEffect(selectedSeasonId, episodes.firstOrNull()?.id) {
        if (episodes.isNotEmpty()) rowState.scrollToItem(currentEpisodeIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TelevisionDimensions.SafeHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelevisionRowHeader(
                title = title,
                modifier = Modifier.weight(1f),
            )
            if (seasons.isNotEmpty()) {
                SeasonSelector(
                    seasons = seasons,
                    selectedSeasonId = selectedSeasonId,
                    focusRequester = seasonFocusRequester,
                    upFocusRequester = seasonUpFocusRequester,
                    downFocusRequester = episodeFocusRequester,
                    onSelect = onSelectSeason,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        LazyRow(
            state = rowState,
            modifier = Modifier
                .focusGroup()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = TelevisionDimensions.SafeHorizontal),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            itemsIndexed(episodes, key = { index, episode -> "${episode.id}:$index" }) { index, episode ->
                TelevisionFocusSurface(
                    onClick = { onPlay(episode) },
                    focusRequester = railFocusRequesters.getOrNull(index),
                    restingAlpha = when {
                        episode.id == currentEpisodeId -> 1f
                        episode.watched -> 0.50f
                        else -> 0.72f
                    },
                    scaleTo = 1.045f,
                    focusedTranslationY = (-2).dp,
                    modifier = Modifier
                        .width(210.dp)
                        .televisionHorizontalWrap(index, railFocusRequesters, rowState)
                        .focusProperties {
                            episodeUpFocusRequester?.let { up = it }
                        }
                        .televisionBringIntoViewOnFocus(),
                ) { focused ->
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(TelevisionDimensions.FocusRadius))
                                .background(TelevisionTheme.colors.ImagePlaceholder),
                        ) {
                            episode.imageUrl?.let { image ->
                                AsyncImage(
                                    model = image,
                                    contentDescription = episode.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                TelevisionTheme.colors.Black.copy(alpha = 0.84f),
                                                TelevisionTheme.colors.Black.copy(alpha = 0.26f),
                                                TelevisionTheme.colors.Black.copy(alpha = 0f),
                                            ),
                                            center = androidx.compose.ui.geometry.Offset(22f, 148f),
                                            radius = 190f,
                                        ),
                                    ),
                            )
                            if (focused) {
                                Icon(
                                    imageVector = if (episode.watched) Icons.Default.Replay else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(
                                        if (episode.watched) {
                                            R.string.tv_detail_replay_item
                                        } else {
                                            R.string.tv_detail_play_item
                                        },
                                        episode.title,
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(11.dp)
                                        .size(21.dp),
                                )
                            }
                            TelevisionProgressMark(
                                progress = episode.progress,
                                watched = episode.watched,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = listOfNotNull(
                                episodeNumberLabel(episode),
                                episode.runtimeLabel,
                                episodeQualityLabel(episode),
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = TelevisionTheme.colors.PaperMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = episode.title,
                            style = MaterialTheme.typography.titleSmall.televisionItemTitle(),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        episode.overview?.let { overview ->
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (focused) TelevisionTheme.colors.PaperSoft else TelevisionTheme.colors.PaperMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = listOfNotNull(
                                detailDateLabel(episode.premiereDate),
                                episode.officialRating,
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = TelevisionTheme.colors.PaperMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonSelector(
    seasons: List<MediaItemUi>,
    selectedSeasonId: String?,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onSelect: (String) -> Unit,
) {
    val defaultSelectorFocus = remember { FocusRequester() }
    val selectorFocus = focusRequester ?: defaultSelectorFocus
    val selectedFocus = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(false) }
    var openedOnce by remember { mutableStateOf(false) }
    val selected = seasons.firstOrNull { it.id == selectedSeasonId } ?: seasons.first()
    val selectedIndex = seasons.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
    val seasonListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(expanded, selected.id) {
        if (expanded) {
            openedOnce = true
            seasonListState.scrollToItem(selectedIndex)
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        } else if (openedOnce) {
            runCatching { selectorFocus.requestFocus() }
        }
    }
    BackHandler(enabled = expanded) { expanded = false }

    TelevisionFocusSurface(
        onClick = { expanded = true },
        focusRequester = selectorFocus,
        restingAlpha = 0.56f,
        scaleTo = 1.08f,
        modifier = Modifier
            .height(40.dp)
            .focusProperties {
                upFocusRequester?.let { up = it }
                downFocusRequester?.let { down = it }
            }
            .televisionBringIntoViewOnFocus(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = selected.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.tv_detail_choose_season),
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (expanded) {
        Popup(
            alignment = Alignment.CenterEnd,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to TelevisionTheme.colors.Black.copy(alpha = 0f),
                            0.58f to TelevisionTheme.colors.Black.copy(alpha = 0.74f),
                            1f to TelevisionTheme.colors.Black,
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(330.dp)
                        .background(
                            Brush.horizontalGradient(
                                0f to TelevisionTheme.colors.Black.copy(alpha = 0f),
                                0.20f to TelevisionTheme.colors.Black.copy(alpha = 0.96f),
                                1f to TelevisionTheme.colors.Black,
                            ),
                        )
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup()
                        .padding(
                            start = 72.dp,
                            end = TelevisionDimensions.SafeHorizontal,
                            top = 72.dp,
                            bottom = TelevisionDimensions.SafeBottom,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tv_detail_season),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        state = seasonListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(seasons, key = { index, season -> "${season.id}:$index" }) { _, season ->
                            val isSelected = season.id == selected.id
                            TelevisionFocusSurface(
                                onClick = {
                                    onSelect(season.id)
                                    expanded = false
                                },
                                focusRequester = if (isSelected) selectedFocus else null,
                                restingAlpha = if (isSelected) 1f else 0.54f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .televisionBringIntoViewOnFocus(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = season.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = stringResource(R.string.tv_selected),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun episodeNumberLabel(episode: MediaItemUi): String =
    if (episode.seasonNumber != null || episode.episodeNumber != null) {
        stringResource(
            R.string.tv_season_episode,
            episode.seasonNumber ?: 0,
            episode.episodeNumber ?: 0,
        )
    } else {
        stringResource(R.string.tv_episode)
    }

@Composable
private fun episodeMetadata(episode: MediaItemUi, item: DetailItem?): List<String> = listOfNotNull(
    episode.runtimeLabel,
    detailDateLabel(episode.premiereDate),
    episode.officialRating,
    episodeQualityLabel(episode, item),
)

@Composable
private fun episodeQualityLabel(episode: MediaItemUi, item: DetailItem? = null): String? {
    val height = item?.mediaStreams
        ?.firstOrNull { it.type.equals("Video", ignoreCase = true) }
        ?.height
        ?: episode.videoHeight
    return when {
        height == null -> episode.metadata.firstOrNull {
            it.contains("4K", ignoreCase = true) ||
                it.contains("1080", ignoreCase = true) ||
                it.contains("720", ignoreCase = true)
        }

        height >= 2160 -> stringResource(R.string.tv_resolution_4k)
        height >= 1080 -> stringResource(R.string.tv_resolution_1080p)
        height >= 720 -> stringResource(R.string.tv_resolution_720p)
        else -> stringResource(R.string.tv_resolution_height, height)
    }
}

@Composable
private fun episodeCredits(item: DetailItem?): String? {
    item ?: return null
    val director = item.people.firstOrNull { it.type.equals("Director", ignoreCase = true) }?.name
    val writer = item.people.firstOrNull { it.type.equals("Writer", ignoreCase = true) }?.name
    val credits = mutableListOf<String>()
    if (director != null) {
        credits += stringResource(R.string.tv_detail_directed_by, director)
    }
    if (writer != null) {
        credits += stringResource(R.string.tv_detail_written_by, writer)
    }
    return credits.joinToString("  ·  ").ifBlank { null }
}

internal fun detailDateLabel(value: String?): String? {
    val isoDate = value?.take(10)?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        LocalDate.parse(isoDate).format(
            DateTimeFormatter
                .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault()),
        )
    }.getOrDefault(isoDate)
}
