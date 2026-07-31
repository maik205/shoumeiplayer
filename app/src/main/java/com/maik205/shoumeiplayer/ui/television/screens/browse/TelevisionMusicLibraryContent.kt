package com.maik205.shoumeiplayer.ui.television.screens.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBackground
import com.maik205.shoumeiplayer.ui.television.components.TelevisionAppTopNavigation
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.TelevisionMediaTile
import com.maik205.shoumeiplayer.ui.television.components.TelevisionArtworkPrefetch
import com.maik205.shoumeiplayer.ui.television.components.TelevisionRowHeader
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.ui.television.model.HeroUi
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.domain.model.MediaShelf as MediaShelfUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun MusicLibraryContent(
    state: TelevisionLibraryState,
    focused: MediaItemUi?,
    onFocused: (MediaItemUi) -> Unit,
    onOpenItem: (MediaItemUi) -> Unit,
    onLoadMore: () -> Unit,
    entryFocus: FocusRequester,
    topNavigationFocus: FocusRequester,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val heroActionFocus = remember { FocusRequester() }
    val firstShelfFocus = remember { FocusRequester() }
    var selectedView by rememberSaveable(state.title) { mutableStateOf("Overview") }
    val grouped = remember(state.items) {
        listOf(
            "Albums" to state.items.filter { it.type == "MusicAlbum" },
            "Artists" to state.items.filter { it.type == "MusicArtist" },
            "Playlists" to state.items.filter { it.type == "Playlist" },
        ).filter { it.second.isNotEmpty() }
    }

    fun selectView(label: String, sectionTitle: String? = null) {
        selectedView = label
        val targetIndex = sectionTitle
            ?.let { title -> grouped.indexOfFirst { it.first == title } }
            ?.takeIf { it >= 0 }
            ?.plus(2)
            ?: 0
        scope.launch { listState.animateScrollToItem(targetIndex) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(),
        contentPadding = PaddingValues(bottom = 64.dp),
    ) {
        item("music-heading") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        end = TelevisionDimensions.SafeHorizontal,
                        top = 84.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 29.sp,
                        lineHeight = 32.sp,
                        letterSpacing = (-1.3).sp,
                    ),
                )
                Spacer(Modifier.width(32.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TelevisionFocusRevealButton(
                        label = "Overview",
                        icon = Icons.Default.Headphones,
                        onClick = { selectView("Overview") },
                        selected = selectedView == "Overview",
                        focusRequester = entryFocus,
                        expandedWidth = 72.dp,
                        modifier = Modifier.focusProperties {
                            up = topNavigationFocus
                            down = heroActionFocus
                        },
                    )
                    TelevisionFocusRevealButton(
                        label = "Albums",
                        icon = Icons.Default.Album,
                        onClick = { selectView("Albums", "Albums") },
                        selected = selectedView == "Albums",
                        expandedWidth = 62.dp,
                        modifier = Modifier.focusProperties {
                            up = topNavigationFocus
                            down = heroActionFocus
                        },
                    )
                    TelevisionFocusRevealButton(
                        label = "Artists",
                        icon = Icons.Default.Person,
                        onClick = { selectView("Artists", "Artists") },
                        selected = selectedView == "Artists",
                        expandedWidth = 60.dp,
                        modifier = Modifier.focusProperties {
                            up = topNavigationFocus
                            down = heroActionFocus
                        },
                    )
                    TelevisionFocusRevealButton(
                        label = "Playlists",
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        onClick = { selectView("Playlists", "Playlists") },
                        selected = selectedView == "Playlists",
                        expandedWidth = 70.dp,
                        modifier = Modifier.focusProperties {
                            up = topNavigationFocus
                            down = heroActionFocus
                        },
                    )
                }
            }
        }
        item("music-hero") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        end = TelevisionDimensions.SafeHorizontal,
                        top = 23.dp,
                        bottom = 20.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .clip(RoundedCornerShape(TelevisionDimensions.FocusRadius))
                        .background(TelevisionColors.ImagePlaceholder),
                ) {
                    focused?.imageUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(Modifier.width(28.dp))
                Column(modifier = Modifier.width(520.dp)) {
                    Text(
                        text = focused?.musicEyebrow() ?: "Music",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TelevisionColors.PaperMuted,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = focused?.title ?: state.title,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 54.sp,
                            lineHeight = 54.sp,
                            letterSpacing = (-2.9).sp,
                        ),
                        maxLines = 2,
                    )
                    focused?.musicHeroMetadata()?.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            text = it.take(4).joinToString("   "),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 8.5.sp,
                                lineHeight = 11.sp,
                            ),
                            color = TelevisionColors.PaperMuted,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    focused?.let { item ->
                        TelevisionFocusRevealButton(
                            label = if (item.type == "Audio") "Play" else "Open",
                            icon = if (item.type == "Audio") Icons.Default.PlayArrow else Icons.Default.Info,
                            onClick = { onOpenItem(item) },
                            selected = true,
                            focusRequester = heroActionFocus,
                            expandedWidth = 96.dp,
                            modifier = Modifier.focusProperties {
                                up = entryFocus
                                if (grouped.isNotEmpty()) down = firstShelfFocus
                            },
                        )
                    }
                }
            }
        }
        items(grouped, key = { it.first }) { (title, media) ->
            MusicShelf(
                title = title,
                items = media,
                firstItemFocusRequester = firstShelfFocus.takeIf { title == grouped.first().first },
                upFocusRequester = heroActionFocus.takeIf { title == grouped.first().first },
                onFocused = onFocused,
                onOpenItem = onOpenItem,
            )
        }
        item("music-end") {
            LaunchedEffect(state.items.size) {
                if (!state.exhausted) onLoadMore()
            }
            Text(
                text = "End of music",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 9.sp),
                color = TelevisionColors.PaperMuted.copy(alpha = 0.56f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        end = TelevisionDimensions.SafeHorizontal,
                        top = 35.dp,
                        bottom = 8.dp,
                    ),
            )
        }
    }
}

@Composable
private fun MusicShelf(
    title: String,
    items: List<MediaItemUi>,
    firstItemFocusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    onFocused: (MediaItemUi) -> Unit,
    onOpenItem: (MediaItemUi) -> Unit,
) {
    var focusedItemId by remember(title) { mutableStateOf<String?>(null) }
    val itemIdentity = items.fold(1) { hash, item -> 31 * hash + item.id.hashCode() }
    val railFocusRequesters = remember(items.size, itemIdentity, firstItemFocusRequester) {
        List(items.size) { index ->
            if (index == 0 && firstItemFocusRequester != null) {
                firstItemFocusRequester
            } else {
                FocusRequester()
            }
        }
    }
    val railState = rememberLazyListState()
    TelevisionArtworkPrefetch(
        items = items,
        listState = railState,
        tileWidth = 146.dp,
        tileHeight = 146.dp,
        shapeForItem = { ArtworkShape.Square },
    )

    Column(
        modifier = Modifier.padding(
            top = 20.dp,
            bottom = 16.dp,
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.4).sp,
            ),
            modifier = Modifier.padding(horizontal = TelevisionDimensions.SafeHorizontal),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = railState,
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .focusRestorer(),
            contentPadding = PaddingValues(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                bottom = 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.5.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "${item.id}:$index" }) { index, item ->
                TelevisionMediaTile(
                    item = item,
                    shape = ArtworkShape.Square,
                    tileWidth = 146.dp,
                    tileHeight = 146.dp,
                    artworkShape = if (item.type == "MusicArtist") {
                        CircleShape
                    } else {
                        RoundedCornerShape(TelevisionDimensions.FocusRadius)
                    },
                    focusScale = 1.045f,
                    restingAlpha = if (focusedItemId != null && focusedItemId != item.id) {
                        0.34f
                    } else {
                        0.68f
                    },
                    focusedTranslationY = (-2.7).dp,
                    focusAnimationMillis = 210,
                    titleStyle = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    ),
                    subtitleOverride = item.musicShelfSubtitle(),
                    subtitleStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                    ),
                    focusRequester = railFocusRequesters.getOrNull(index),
                    modifier = Modifier
                        .televisionHorizontalWrap(index, railFocusRequesters, railState)
                        .then(
                            if (index == 0 && upFocusRequester != null) {
                                Modifier.focusProperties { up = upFocusRequester }
                            } else {
                                Modifier
                            },
                        ),
                    onFocusChanged = { focused ->
                        if (focused) {
                            focusedItemId = item.id
                            onFocused(item)
                        } else if (focusedItemId == item.id) {
                            focusedItemId = null
                        }
                    },
                    onClick = { onOpenItem(item) },
                )
            }
        }
    }
}

private fun String?.musicTypeLabel(): String = when (this) {
    "MusicAlbum" -> "Album"
    "MusicArtist" -> "Artist"
    "Playlist" -> "Playlist"
    "Audio" -> "Song"
    else -> "Music"
}

private fun MediaItemUi.musicEyebrow(): String = when (type) {
    "MusicAlbum", "Audio" -> subtitle?.takeIf(String::isNotBlank) ?: type.musicTypeLabel()
    else -> type.musicTypeLabel()
}

private fun MediaItemUi.musicHeroMetadata(): List<String> =
    listOf(type.musicTypeLabel()) + metadata

private fun MediaItemUi.musicShelfSubtitle(): String? = when (type) {
    "MusicAlbum", "Audio" -> subtitle
    "MusicArtist", "Playlist" -> metadata.firstOrNull()
    else -> subtitle
}
