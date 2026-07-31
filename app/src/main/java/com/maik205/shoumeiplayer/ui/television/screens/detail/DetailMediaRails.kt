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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusScale
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionMediaTile
import com.maik205.shoumeiplayer.ui.television.components.TelevisionArtworkPrefetch
import com.maik205.shoumeiplayer.ui.television.components.TelevisionProgressMark
import com.maik205.shoumeiplayer.ui.television.components.TelevisionRowHeader
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TrackRail(
    title: String,
    tracks: List<MediaItemUi>,
    onPlay: (MediaItemUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                top = 38.dp,
            ),
    ) {
        TelevisionRowHeader(title = title)
        Spacer(Modifier.height(10.dp))
        tracks.take(24).forEachIndexed { index, track ->
            TelevisionFocusSurface(
                onClick = { onPlay(track) },
                restingAlpha = if (track.watched) 0.46f else 0.66f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .televisionBringIntoViewOnFocus(),
            ) { focused ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        color = TelevisionColors.PaperMuted,
                        modifier = Modifier.width(42.dp),
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.televisionItemTitle(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    track.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = TelevisionColors.PaperMuted,
                            modifier = Modifier.padding(start = 18.dp),
                        )
                    }
                    track.metadata.lastOrNull()?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = TelevisionColors.PaperMuted,
                            modifier = Modifier.padding(start = 22.dp),
                        )
                    }
                    if (focused) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 14.dp)
                                .size(21.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailMediaRail(
    title: String,
    items: List<MediaItemUi>,
    onOpen: (MediaItemUi) -> Unit,
) {
    val itemIdentity = items.fold(1) { hash, item -> 31 * hash + item.id.hashCode() }
    val railFocusRequesters = remember(items.size, itemIdentity) {
        List(items.size) { FocusRequester() }
    }
    val railState = rememberLazyListState()
    TelevisionArtworkPrefetch(
        items = items,
        listState = railState,
        shapeForItem = { item ->
            when (item.type) {
                "Episode", "Video", "Recording" -> ArtworkShape.Landscape
                else -> item.shape
            }
        },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 42.dp),
    ) {
        TelevisionRowHeader(
            title = title,
            modifier = Modifier.padding(horizontal = TelevisionDimensions.SafeHorizontal),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = railState,
            modifier = Modifier
                .focusGroup()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = TelevisionDimensions.SafeHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TelevisionDimensions.TileGap),
        ) {
            itemsIndexed(items, key = { index, item -> "${item.id}:$index" }) { index, item ->
                TelevisionMediaTile(
                    item = item,
                    onClick = { onOpen(item) },
                    focusRequester = railFocusRequesters.getOrNull(index),
                    modifier = Modifier.televisionHorizontalWrap(
                        index,
                        railFocusRequesters,
                        railState,
                    ),
                    shape = when (item.type) {
                        "Episode", "Video", "Recording" -> ArtworkShape.Landscape
                        else -> item.shape
                    },
                )
            }
        }
    }
}

@Composable
internal fun PeopleRail(
    people: List<PersonUi>,
    onOpen: (PersonUi) -> Unit,
) {
    val peopleIdentity = people.fold(1) { hash, person -> 31 * hash + person.id.hashCode() }
    val railFocusRequesters = remember(people.size, peopleIdentity) {
        List(people.size) { FocusRequester() }
    }
    val railState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 42.dp),
    ) {
        TelevisionRowHeader(
            title = "Cast and crew",
            modifier = Modifier.padding(horizontal = TelevisionDimensions.SafeHorizontal),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = railState,
            modifier = Modifier
                .focusGroup()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = TelevisionDimensions.SafeHorizontal),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(people, key = { index, person -> "${person.id}:$index" }) { index, person ->
                TelevisionFocusSurface(
                    onClick = { onOpen(person) },
                    focusRequester = railFocusRequesters.getOrNull(index),
                    restingAlpha = 0.68f,
                    scaleTo = TelevisionFocusScale.Poster,
                    modifier = Modifier
                        .width(132.dp)
                        .televisionHorizontalWrap(index, railFocusRequesters, railState)
                        .televisionBringIntoViewOnFocus(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .background(TelevisionColors.ImagePlaceholder),
                        ) {
                            person.imageUrl?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = person.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.titleSmall.televisionItemTitle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        person.role?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = TelevisionColors.PaperMuted,
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
