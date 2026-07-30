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
import com.maik205.shoumeiplayer.ui.television.components.TelevisionRowHeader
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.model.ArtworkShape
import com.maik205.shoumeiplayer.ui.television.model.HeroUi
import com.maik205.shoumeiplayer.ui.television.model.LibraryDestinationUi
import com.maik205.shoumeiplayer.ui.television.model.MediaItemUi
import com.maik205.shoumeiplayer.ui.television.model.MediaShelfUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DirectlyPlayableTypes = setOf(
    "Movie",
    "Episode",
    "Video",
    "Audio",
    "AudioBook",
    "MusicVideo",
    "TvChannel",
    "LiveTvChannel",
)

/**
 * Keeps a focused Home card at its current vertical position when it is already visible. The
 * platform TV pivot otherwise pulls the first rail over the hero as soon as Down moves focus.
 */
@OptIn(ExperimentalFoundationApi::class)
private object HomeBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val trailingEdge = offset + size
        return when {
            offset >= 0f && trailingEdge <= containerSize -> 0f
            offset < 0f && trailingEdge > containerSize -> 0f
            abs(offset) < abs(trailingEdge - containerSize) -> offset
            else -> trailingEdge - containerSize
        }
    }
}

/**
 * Matches the prototype's library-grid relocation: leave a fully visible card in place, then
 * center only cards that have crossed a viewport edge.
 */
@OptIn(ExperimentalFoundationApi::class)
private object LibraryBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val trailingEdge = offset + size
        return if (offset >= 0f && trailingEdge <= containerSize) {
            0f
        } else {
            offset - ((containerSize - size) / 2f)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelevisionHomeScreen(
    state: TelevisionHomeState,
    userName: String,
    avatarUrl: String?,
    onRefresh: () -> Unit,
    onItemFocused: (MediaItemUi) -> Unit,
    onOpenItem: (MediaItemUi) -> Unit,
    onPlay: (MediaItemUi) -> Unit,
    onToggleFavorite: (MediaItemUi) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit,
    navigationState: LazyListState,
) {
    val hero = state.hero
    val playFocus = remember { FocusRequester() }
    val fallbackContentFocus = remember { FocusRequester() }
    val firstRailFocus = remember { FocusRequester() }
    val topNavigationFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val railScrollOffset = with(LocalDensity.current) { (-78).dp.roundToPx() }
    var focusedRail by rememberSaveable { mutableIntStateOf(-1) }
    var previousFocusedRail by rememberSaveable { mutableIntStateOf(-1) }
    val browsing = focusedRail > 0

    LaunchedEffect(focusedRail) {
        val railBeforeFocus = previousFocusedRail
        previousFocusedRail = focusedRail

        when {
            focusedRail > 0 -> {
                listState.animateScrollToItem(
                    index = focusedRail + 1,
                    scrollOffset = railScrollOffset,
                )
            }
            // The first rail stays put when entered from the hero, but returning upward from a
            // deeper rail must reveal the full hero before focus continues into its controls.
            focusedRail == 0 && railBeforeFocus > 0 -> {
                listState.animateScrollToItem(0)
            }
            // Hero controls are a top-of-page destination. Restore that position whenever they
            // are reached from a rail, without affecting the initial first-rail Down transition.
            focusedRail < 0 && railBeforeFocus >= 0 -> {
                listState.animateScrollToItem(0)
            }
        }
    }

    TelevisionBackground(imageUrl = hero?.item?.backdropUrl) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides HomeBringIntoViewSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
                contentPadding = PaddingValues(bottom = 68.dp),
            ) {
                item(key = "hero-space") {
                    Spacer(Modifier.height(320.dp))
                }
                if (state.loading) {
                    item(key = "loading") {
                        TelevisionLoadingState(
                            shape = TelevisionLoadingShape.Home,
                            modifier = Modifier.padding(
                                start = TelevisionDimensions.SafeHorizontal,
                                top = 17.dp,
                            ),
                        )
                    }
                } else if (state.error != null && state.shelves.isEmpty()) {
                    item(key = "error") {
                        TelevisionErrorState(
                            title = "Your libraries are out of reach",
                            message = state.error,
                            onRetry = onRefresh,
                            modifier = Modifier.padding(
                                start = TelevisionDimensions.SafeHorizontal,
                                top = 17.dp,
                            ),
                            focusRequester = fallbackContentFocus,
                            requestInitialFocus = false,
                        )
                    }
                } else if (state.shelves.isEmpty()) {
                    item(key = "empty") {
                        TelevisionEmptyState(
                            title = "Nothing to watch yet",
                            message = "New media will appear here when your libraries are ready.",
                            modifier = Modifier.padding(
                                start = TelevisionDimensions.SafeHorizontal,
                                top = 17.dp,
                            ),
                        )
                    }
                } else {
                    items(
                        count = state.shelves.size,
                        key = { state.shelves[it].id },
                    ) { railIndex ->
                        val shelf = state.shelves[railIndex]
                        HomeShelf(
                            shelf = shelf,
                            firstRail = railIndex == 0,
                            heroFocusRequester = playFocus,
                            firstItemFocusRequester = if (railIndex == 0) firstRailFocus else null,
                            onFocused = {
                                focusedRail = railIndex
                                onItemFocused(it)
                            },
                            onClick = onOpenItem,
                        )
                    }
                    item(key = "end") {
                        EndOfLibraryMessage(
                            seed = state.shelves.sumOf { it.items.size },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 26.dp, bottom = 9.dp),
                        )
                    }
                }
            }
        }

        hero?.let { current ->
            if (!browsing) {
                HomeHero(
                    hero = current,
                    playable = current.item.type in DirectlyPlayableTypes,
                    onPlay = { onPlay(current.item) },
                    onDetails = { onOpenItem(current.item) },
                    onToggleFavorite = { onToggleFavorite(current.item) },
                    onHeroFocused = { focusedRail = -1 },
                    focusRequester = playFocus,
                    firstRailFocusRequester = firstRailFocus,
                    topNavigationFocusRequester = topNavigationFocus,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = TelevisionDimensions.SafeHorizontal, top = 90.dp),
                )
            }
            AnimatedVisibility(
                visible = browsing,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(220),
                ) + fadeIn(tween(160)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(170),
                ) + fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f),
            ) {
                CompactHomeHero(hero = current)
            }
        }

        AppTopNavigation(
            libraries = state.libraries,
            selectedKey = "home",
            userName = userName,
            avatarUrl = avatarUrl,
            onNavigateHome = onNavigateHome,
            onNavigateSearch = onNavigateSearch,
            onNavigateLibrary = onNavigateLibrary,
            onNavigateSettings = onNavigateSettings,
            onNavigateProfile = onNavigateProfile,
            contentFocusRequester = when {
                hero != null && !browsing -> playFocus
                state.error != null && state.shelves.isEmpty() -> fallbackContentFocus
                else -> null
            },
            selectedFocusRequester = topNavigationFocus,
            navigationState = navigationState,
            onNavigationFocused = { focusedRail = -1 },
        )
    }
}

@Composable
private fun HomeHero(
    hero: HeroUi,
    playable: Boolean,
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
                label = if (playable) hero.actionLabel else "Details",
                icon = if (playable) Icons.Default.PlayArrow else Icons.Default.Info,
                onClick = if (playable) onPlay else onDetails,
                focusRequester = focusRequester,
                expandedWidth = if (playable) 66.dp else 72.dp,
                onFocusChanged = { if (it) onHeroFocused() },
                modifier = firstRailFocusModifier,
            )
            if (playable) {
                TelevisionFocusRevealButton(
                    label = "Details",
                    icon = Icons.Default.Info,
                    onClick = onDetails,
                    expandedWidth = 68.dp,
                    onFocusChanged = { if (it) onHeroFocused() },
                    modifier = firstRailFocusModifier,
                )
            }
            TelevisionFocusRevealButton(
                label = if (hero.item.favorite) "In my list" else "My list",
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
private fun CompactHomeHero(
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
private fun HomeShelf(
    shelf: MediaShelfUi,
    firstRail: Boolean,
    heroFocusRequester: FocusRequester,
    firstItemFocusRequester: FocusRequester?,
    onFocused: (MediaItemUi) -> Unit,
    onClick: (MediaItemUi) -> Unit,
) {
    val railFocusRequesters = remember(shelf.items.map(MediaItemUi::id), firstItemFocusRequester) {
        List(shelf.items.size) { index ->
            if (index == 0 && firstItemFocusRequester != null) {
                firstItemFocusRequester
            } else {
                FocusRequester()
            }
        }
    }
    val railState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (firstRail) 0.dp else TelevisionDimensions.ShelfGap),
    ) {
        TelevisionRowHeader(
            title = shelf.title,
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
                key = { shelf.items[it].id },
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

@Composable
fun TelevisionLibraryScreen(
    state: TelevisionLibraryState,
    libraryId: String,
    libraries: List<LibraryDestinationUi>,
    userName: String,
    avatarUrl: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSetSort: (BrowseSort) -> Unit,
    onSetView: (LibraryViewMode) -> Unit,
    onOpenItem: (MediaItemUi) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit,
    navigationState: LazyListState,
) {
    val isMusic = state.collectionType.equals("music", ignoreCase = true)
    val focused = remember(state.items, isMusic) {
        mutableStateOf(
            if (isMusic) {
                state.items.firstOrNull { it.type == "MusicAlbum" }
                    ?: state.items.firstOrNull()
            } else {
                state.items.firstOrNull()
            },
        )
    }
    val entryFocus = remember { FocusRequester() }
    val topNavigationFocus = remember { FocusRequester() }
    val selectedNavigationKey = "library:$libraryId"

    TelevisionBackground(
        imageUrl = if (isMusic) focused.value?.backdropUrl else null,
    ) {
        if (!isMusic) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(TelevisionColors.LibraryBackground)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    TelevisionColors.Paper.copy(alpha = 0.035f),
                                    androidx.compose.ui.graphics.Color.Transparent,
                                ),
                                center = Offset(size.width * 0.18f, 0f),
                                radius = size.width * 0.36f,
                            ),
                        )
                    },
            )
        }
        if (isMusic) {
            MusicLibraryContent(
                state = state,
                focused = focused.value,
                onFocused = { focused.value = it },
                onOpenItem = onOpenItem,
                onLoadMore = onLoadMore,
                entryFocus = entryFocus,
                topNavigationFocus = topNavigationFocus,
            )
        } else {
            StandardLibraryContent(
                state = state,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
                onSetSort = onSetSort,
                onSetView = onSetView,
                onOpenItem = onOpenItem,
                entryFocus = entryFocus,
                topNavigationFocus = topNavigationFocus,
            )
        }

        AppTopNavigation(
            libraries = libraries,
            selectedKey = selectedNavigationKey,
            userName = userName,
            avatarUrl = avatarUrl,
            onNavigateHome = onNavigateHome,
            onNavigateSearch = onNavigateSearch,
            onNavigateLibrary = onNavigateLibrary,
            onNavigateSettings = onNavigateSettings,
            onNavigateProfile = onNavigateProfile,
            contentFocusRequester = entryFocus,
            selectedFocusRequester = topNavigationFocus,
            navigationState = navigationState,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandardLibraryContent(
    state: TelevisionLibraryState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSetSort: (BrowseSort) -> Unit,
    onSetView: (LibraryViewMode) -> Unit,
    onOpenItem: (MediaItemUi) -> Unit,
    entryFocus: FocusRequester,
    topNavigationFocus: FocusRequester,
) {
    var focusedMediaId by remember { mutableStateOf<String?>(null) }
    var snapTopTick by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val count = maxOf(state.totalCount, state.items.size)
    val countLabel = "$count ${if (count == 1) "item" else "items"}"
    val sortingByTitle = state.sort == BrowseSort.Name

    CompositionLocalProvider(LocalBringIntoViewSpec provides LibraryBringIntoViewSpec) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer(),
            contentPadding = PaddingValues(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                top = 59.dp,
                bottom = 46.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item(
                key = "library-heading",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 26.sp,
                            lineHeight = 33.sp,
                            letterSpacing = (-1.17).sp,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 7.sp,
                            lineHeight = 9.sp,
                        ),
                        color = TelevisionColors.PaperSoft,
                        modifier = Modifier.padding(top = 8.5.dp),
                    )
                }
            }

            item(
                key = "library-views",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryViewButton(
                            label = "All",
                            icon = Icons.Default.GridView,
                            selected = state.view == LibraryViewMode.All,
                            focusRequester = entryFocus,
                            upFocusRequester = topNavigationFocus,
                            onClick = { onSetView(LibraryViewMode.All) },
                            onFocused = {
                                focusedMediaId = null
                                snapTopTick++
                            },
                        )
                        LibraryViewButton(
                            label = "New",
                            icon = Icons.Default.Schedule,
                            selected = state.view == LibraryViewMode.New,
                            upFocusRequester = topNavigationFocus,
                            onClick = { onSetView(LibraryViewMode.New) },
                            onFocused = {
                                focusedMediaId = null
                                snapTopTick++
                            },
                        )
                        LibraryViewButton(
                            label = "Favorites",
                            icon = Icons.Default.Favorite,
                            selected = state.view == LibraryViewMode.Favorites,
                            upFocusRequester = topNavigationFocus,
                            onClick = { onSetView(LibraryViewMode.Favorites) },
                            onFocused = {
                                focusedMediaId = null
                                snapTopTick++
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TelevisionFocusRevealButton(
                        label = if (sortingByTitle) "Title" else "Recent",
                        icon = if (sortingByTitle) {
                            Icons.Default.SortByAlpha
                        } else {
                            Icons.Default.CalendarToday
                        },
                        onClick = {
                            onSetSort(
                                if (sortingByTitle) BrowseSort.Recent else BrowseSort.Name,
                            )
                        },
                        expandedWidth = 60.dp,
                        onFocusChanged = {
                            if (it) {
                                focusedMediaId = null
                                snapTopTick++
                            }
                        },
                        modifier = Modifier.focusProperties { up = topNavigationFocus },
                    )
                }
            }

            when {
                state.loading -> item(
                    key = "library-loading",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    TelevisionLoadingState(
                        shape = TelevisionLoadingShape.Grid,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                state.error != null && state.items.isEmpty() -> item(
                    key = "library-error",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    TelevisionErrorState(
                        title = "Couldn't open ${state.title}",
                        message = state.error,
                        onRetry = onRetry,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                state.items.isEmpty() -> item(
                    key = "library-empty",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    TelevisionEmptyState(
                        title = "Nothing here yet",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                else -> {
                    items(state.items, key = MediaItemUi::id) { media ->
                        TelevisionMediaTile(
                            item = media,
                            shape = ArtworkShape.Poster,
                            tileWidth = 115.5.dp,
                            tileHeight = 173.25.dp,
                            subtitleOverride = media.librarySubtitle(),
                            subtitleStyle = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 6.5.sp,
                                lineHeight = 8.5.sp,
                            ),
                            subtitleColor = TelevisionColors.Paper.copy(alpha = 0.75f),
                            showUnfocusedVeil = false,
                            focusedTranslationY = (-2.5).dp,
                            focusAnimationMillis = 240,
                            restingAlpha = if (focusedMediaId != null) 0.5f else 1f,
                            onFocusChanged = { focused ->
                                if (focused) {
                                    focusedMediaId = media.id
                                } else if (focusedMediaId == media.id) {
                                    focusedMediaId = null
                                }
                            },
                            onClick = { onOpenItem(media) },
                        )
                        if (media == state.items.lastOrNull()) {
                            LaunchedEffect(media.id) { onLoadMore() }
                        }
                    }
                    if (state.exhausted) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "library-end") {
                            EndOfLibraryMessage(
                                seed = state.title.hashCode(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 31.dp, bottom = 11.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(state.sort, state.view, snapTopTick) {
        gridState.scrollToItem(0)
    }
}

@Composable
private fun LibraryViewButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = 1.08f,
        restingAlpha = if (selected) 0.82f else 0.4f,
        focusAnimationMillis = 180,
        onFocusChanged = { if (it) onFocused() },
        modifier = Modifier
            .height(21.dp)
            .then(
                upFocusRequester?.let { target ->
                    Modifier.focusProperties { up = target }
                } ?: Modifier,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 7.5.sp,
                    lineHeight = 9.5.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun MediaItemUi.librarySubtitle(): String? {
    val typeLabel = when (type) {
        "Series" -> "Series"
        "Movie" -> "Movie"
        "BoxSet" -> "Collection"
        "AudioBook", "Book" -> "Book"
        else -> type.takeIf(String::isNotBlank)
    }
    return listOfNotNull(typeLabel, subtitle?.takeIf(String::isNotBlank))
        .distinct()
        .joinToString(" · ")
        .ifBlank { null }
}

@Composable
private fun MusicLibraryContent(
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
                        icon = Icons.Default.QueueMusic,
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
    val railFocusRequesters = remember(items.map(MediaItemUi::id), firstItemFocusRequester) {
        List(items.size) { index ->
            if (index == 0 && firstItemFocusRequester != null) {
                firstItemFocusRequester
            } else {
                FocusRequester()
            }
        }
    }
    val railState = rememberLazyListState()

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
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
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

@Composable
fun TelevisionSearchScreen(
    query: String,
    searching: Boolean,
    results: List<MediaItemUi>,
    error: String?,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenItem: (MediaItemUi) -> Unit,
    onBack: () -> Unit,
    libraries: List<LibraryDestinationUi>,
    userName: String,
    avatarUrl: String?,
    onNavigateHome: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit,
    navigationState: LazyListState,
) {
    val fieldFocus = remember { FocusRequester() }
    val topNavigationFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TelevisionColors.Black),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 122.dp)
                .focusRestorer(),
            contentPadding = PaddingValues(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                bottom = 54.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            when {
                searching -> item(span = { GridItemSpan(maxLineSpan) }) {
                    TelevisionLoadingState(
                        label = "Searching",
                        shape = TelevisionLoadingShape.Search,
                    )
                }
                error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    TelevisionErrorState(
                        title = "Search is unavailable",
                        message = error,
                        onRetry = onRetry,
                    )
                }
                query.trim().length < 2 -> item(span = { GridItemSpan(maxLineSpan) }) {
                    TelevisionEmptyState(
                        title = "Type to search",
                        message = "Use the Google TV keyboard.",
                    )
                }
                results.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    TelevisionEmptyState(title = "Nothing matched “$query”")
                }
                else -> items(results, key = MediaItemUi::id) { item ->
                    TelevisionMediaTile(
                        item = item,
                        onClick = { onOpenItem(item) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    end = TelevisionDimensions.SafeHorizontal,
                    top = 46.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelevisionFocusRevealButton(
                label = "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                expandedWidth = 92.dp,
            )
            Spacer(Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .focusRequester(fieldFocus)
                    .onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    color = TelevisionColors.Paper,
                ),
                cursorBrush = SolidColor(TelevisionColors.Paper),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                ),
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = "Search",
                                style = MaterialTheme.typography.displaySmall,
                                color = TelevisionColors.PaperSoft,
                            )
                        }
                        input()
                    }
                },
            )
        }

        AppTopNavigation(
            libraries = libraries,
            selectedKey = "search",
            userName = userName,
            avatarUrl = avatarUrl,
            onNavigateHome = onNavigateHome,
            onNavigateSearch = {},
            onNavigateLibrary = onNavigateLibrary,
            onNavigateSettings = onNavigateSettings,
            onNavigateProfile = onNavigateProfile,
            contentFocusRequester = fieldFocus,
            selectedFocusRequester = topNavigationFocus,
            navigationState = navigationState,
        )
    }
}

@Composable
private fun AppTopNavigation(
    libraries: List<LibraryDestinationUi>,
    selectedKey: String?,
    userName: String,
    avatarUrl: String?,
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit,
    contentFocusRequester: FocusRequester? = null,
    selectedFocusRequester: FocusRequester? = null,
    navigationState: LazyListState? = null,
    onNavigationFocused: () -> Unit = {},
) {
    TelevisionAppTopNavigation(
        libraries = libraries,
        selectedKey = selectedKey,
        userName = userName,
        avatarUrl = avatarUrl,
        onNavigateHome = onNavigateHome,
        onNavigateSearch = onNavigateSearch,
        onNavigateLibrary = onNavigateLibrary,
        onNavigateSettings = onNavigateSettings,
        onNavigateProfile = onNavigateProfile,
        contentFocusRequester = contentFocusRequester,
        selectedFocusRequester = selectedFocusRequester,
        navigationState = navigationState,
        onNavigationFocused = onNavigationFocused,
    )
}

private val endMessages = listOf(
    "That’s everything",
    "You’ve reached the end",
    "All caught up",
    "Nothing else hiding down here",
    "That’s the whole shelf",
    "End of the library",
    "You found everything",
    "That’s all for now",
    "The shelves end here",
    "No more titles below",
    "You made it through",
    "Everything’s accounted for",
)

@Composable
private fun EndOfLibraryMessage(
    seed: Int,
    modifier: Modifier = Modifier,
) {
    val message = remember(seed) {
        val index = (System.nanoTime() and Long.MAX_VALUE).rem(endMessages.size).toInt()
        endMessages[index]
    }
    Text(
        text = message,
        style = MaterialTheme.typography.titleMedium,
        color = TelevisionColors.PaperSoft,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
