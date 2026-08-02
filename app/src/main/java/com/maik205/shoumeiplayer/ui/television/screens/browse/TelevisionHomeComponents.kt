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
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.res.stringResource
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
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun HomeHero(
    hero: HeroUi,
    playable: Boolean,
    playLoading: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHeroFocused: () -> Unit,
    focusRequester: FocusRequester,
    firstRailFocusRequester: FocusRequester,
    topNavigationFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val firstRailFocusModifier = Modifier.focusProperties {
        up = topNavigationFocusRequester
        down = firstRailFocusRequester
    }

    Column(
        modifier = modifier.width(400.dp),
    ) {
        if (hero.item.logoUrl != null) {
            AsyncImage(
                model = hero.item.logoUrl,
                contentDescription = hero.item.title,
                modifier = Modifier
                    .width(160.dp)
                    .height(65.dp),
                alignment = Alignment.CenterStart,
            )
            Spacer(Modifier.height(13.dp))
        } else {
            Text(
                text = hero.item.title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (hero.item.logoUrl != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = hero.item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (hero.item.metadata.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = hero.item.metadata.take(4).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TelevisionColors.PaperMuted,
                        maxLines = 1,
                    )
                }
            }
        } else if (hero.item.metadata.isNotEmpty()) {
            Text(
                text = hero.item.metadata.take(4).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = TelevisionColors.PaperMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelevisionFocusRevealButton(
                label = if (playable && playLoading) {
                    stringResource(R.string.tv_player_loading)
                } else if (playable) {
                    stringResource(if (hero.item.resumeTicks > 0) R.string.resume else R.string.play)
                } else {
                    stringResource(R.string.tv_details)
                },
                icon = if (playable) Icons.Default.PlayArrow else Icons.Default.Info,
                onClick = if (playable) onPlay else onDetails,
                loading = playable && playLoading,
                focusRequester = focusRequester,
                expandedWidth = if (playable) 66.dp else 72.dp,
                onFocusChanged = { if (it) onHeroFocused() },
                modifier = firstRailFocusModifier,
            )
            if (playable) {
                TelevisionFocusRevealButton(
                    label = stringResource(R.string.tv_details),
                    icon = Icons.Default.Info,
                    onClick = onDetails,
                    expandedWidth = 68.dp,
                    onFocusChanged = { if (it) onHeroFocused() },
                    modifier = firstRailFocusModifier,
                )
            }
            TelevisionFocusRevealButton(
                label = if (hero.item.favorite) {
                    stringResource(R.string.tv_in_my_list)
                } else {
                    stringResource(R.string.tv_my_list)
                },
                icon = if (hero.item.favorite) Icons.Default.Check else Icons.Default.Add,
                onClick = onToggleFavorite,
                selected = hero.item.favorite,
                expandWhenSelected = false,
                expandedWidth = 70.dp,
                onFocusChanged = { if (it) onHeroFocused() },
                modifier = firstRailFocusModifier,
            )
        }
    }
}

@Composable
internal fun CompactHomeHero(
    hero: HeroUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(
                Brush.verticalGradient(
                    0f to TelevisionColors.Black.copy(alpha = 0.96f),
                    0.68f to TelevisionColors.Black.copy(alpha = 0.78f),
                    1f to TelevisionColors.Black.copy(alpha = 0.08f),
                ),
            )
            .padding(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                top = 9.dp,
                bottom = 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hero.item.logoUrl != null) {
            AsyncImage(
                model = hero.item.logoUrl,
                contentDescription = hero.item.title,
                modifier = Modifier
                    .width(90.dp)
                    .height(44.dp),
                alignment = Alignment.CenterStart,
            )
            Spacer(Modifier.width(20.dp))
        }
        Column(
            modifier = Modifier.width(if (hero.item.logoUrl == null) 260.dp else 230.dp),
        ) {
            Text(
                text = hero.item.title,
                style = if (hero.item.logoUrl == null) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (hero.item.metadata.isNotEmpty()) {
                Spacer(Modifier.height(3.5.dp))
                Text(
                    text = hero.item.metadata.take(4).joinToString("   "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TelevisionColors.PaperMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun HomeShelf(
    shelf: MediaShelfUi,
    title: String = shelf.title,
    firstRail: Boolean,
    heroFocusRequester: FocusRequester,
    firstItemFocusRequester: FocusRequester?,
    onFocused: (MediaItemUi) -> Unit,
    onClick: (MediaItemUi) -> Unit,
) {
    // Keep the remember key allocation-free. The previous `items.map { id }` created a temporary
    // list on every recomposition before Compose could decide whether the rail changed.
    val itemIdentity = shelf.items.fold(1) { hash, item -> 31 * hash + item.id.hashCode() }
    val railFocusRequesters = remember(
        shelf.items.size,
        itemIdentity,
        firstItemFocusRequester,
    ) {
        List(shelf.items.size) { index ->
            if (index == 0 && firstItemFocusRequester != null) {
                firstItemFocusRequester
            } else {
                FocusRequester()
            }
        }
    }
    val railState = rememberLazyListState()
    TelevisionArtworkPrefetch(shelf.items, railState)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (firstRail) 0.dp else TelevisionDimensions.ShelfGap),
    ) {
        TelevisionRowHeader(
            title = title,
            modifier = Modifier.padding(horizontal = TelevisionDimensions.SafeHorizontal),
        )
        Spacer(Modifier.height(TelevisionDimensions.HeaderGap))
        LazyRow(
            state = railState,
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = TelevisionDimensions.SafeHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TelevisionDimensions.TileGap),
        ) {
            items(
                count = shelf.items.size,
                key = { index -> "${shelf.items[index].id}:$index" },
            ) { itemIndex ->
                val item = shelf.items[itemIndex]
                TelevisionMediaTile(
                    item = item,
                    onClick = { onClick(item) },
                    onFocused = onFocused,
                    focusRequester = railFocusRequesters.getOrNull(itemIndex),
                    bringIntoViewOnFocus = !(firstRail && itemIndex == 0),
                    modifier = Modifier
                        .televisionHorizontalWrap(itemIndex, railFocusRequesters, railState)
                        .then(
                            if (firstRail) {
                                Modifier.focusProperties { up = heroFocusRequester }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}
