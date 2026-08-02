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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.ui.i18n.resolve
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBackground
import com.maik205.shoumeiplayer.ui.television.components.TelevisionAppTopNavigation
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.ui.television.components.rememberPlaybackLaunchState
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.TelevisionMediaTile
import com.maik205.shoumeiplayer.ui.television.components.TelevisionRowHeader
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.ui.television.model.HeroUi
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.domain.model.MediaShelf as MediaShelfUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import com.maik205.shoumeiplayer.ui.television.theme.televisionTypography
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    // Read directly from the store rather than threading it through TelevisionHomeState: this is
    // the same seam ShoumeiTelevisionTheme uses, and it is what lets a personalization toggle take
    // effect without every browse ViewModel growing a ClientSettings field it does not otherwise
    // need (#89).
    val settingsStore = LocalAppContainer.current.settingsStore
    val settingsDefaults = remember { ClientSettings() }
    val settings by settingsStore.settings.collectAsState(initial = settingsDefaults)

    val hero = state.hero
    val playbackLaunch = rememberPlaybackLaunchState(hero?.item?.id)
    val playFocus = remember { FocusRequester() }
    val fallbackContentFocus = remember { FocusRequester() }
    val partialErrorFocus = remember { FocusRequester() }
    val firstRailFocus = remember { FocusRequester() }
    val topNavigationFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val railScrollOffset = with(LocalDensity.current) { (-78).dp.roundToPx() }
    var focusedRail by rememberSaveable { mutableIntStateOf(-1) }
    var previousFocusedRail by rememberSaveable { mutableIntStateOf(-1) }
    val browsing = focusedRail > 0

    // Where the viewer was standing when they opened a card. This survives the trip to Detail or
    // Player in the back-stack entry's saved state, while `restorePending` does not -- it is plain
    // `remember`, so it is true exactly on the composition that follows coming back, and false for
    // every ordinary recomposition after that.
    var focusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    var restorePending by remember { mutableStateOf(true) }
    // One requester per rail's first card, so every rail has a named neighbour above and below.
    val railFirstItemFocus = remember(state.shelves.size) {
        List(state.shelves.size) { index -> if (index == 0) firstRailFocus else FocusRequester() }
    }
    val restoreItemId = focusedItemId.takeIf { restorePending && focusedRail >= 0 }
    val partialError = state.error != null && state.shelves.isNotEmpty()

    // Bring the remembered rail on screen before its cards ask for focus; the rail itself handles
    // choosing the card once it is laid out.
    LaunchedEffect(restoreItemId, state.shelves.size) {
        if (restoreItemId == null) return@LaunchedEffect
        if (focusedRail !in state.shelves.indices) {
            restorePending = false
            return@LaunchedEffect
        }
        listState.scrollToItem(focusedRail + 1, railScrollOffset)
    }

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

    // "Backdrop images" (#89): off means the hero's ambient background never renders, on this
    // screen or the compact hero that replaces it while browsing.
    TelevisionBackground(imageUrl = resolveBackdropUrl(hero?.item?.backdropUrl, settings.backdropImages)) {
        // "Interface scale" (#89): every text role under this screen is read from here rather than
        // the unscaled TelevisionTypography constant. ShoumeiTelevisionTheme still owns colours and
        // shapes -- only typography is overridden, and Comfortable reproduces its baseline exactly.
        MaterialTheme(typography = televisionTypography(settings.interfaceScale)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides HomeBringIntoViewSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
                contentPadding = PaddingValues(bottom = 68.dp),
            ) {
                item(key = "hero-space") {
                    if (state.loading) {
                        TelevisionLoadingState(
                            label = stringResource(R.string.tv_loading_home),
                            shape = TelevisionLoadingShape.Detail,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .padding(
                                    start = TelevisionDimensions.SafeHorizontal,
                                    end = TelevisionDimensions.SafeHorizontal,
                                    top = 24.dp,
                                ),
                        )
                    } else {
                        Spacer(Modifier.height(320.dp))
                    }
                }
                if (state.loading) {
                    item(key = "loading") {
                        TelevisionLoadingState(
                            label = stringResource(R.string.tv_loading_home),
                            shape = TelevisionLoadingShape.Home,
                            modifier = Modifier.padding(
                                start = TelevisionDimensions.SafeHorizontal,
                                top = 17.dp,
                            ),
                        )
                    }
                } else {
                    if (state.refreshing) {
                        item(key = "refreshing") {
                            Text(
                                text = stringResource(R.string.tv_refreshing),
                                style = MaterialTheme.typography.bodySmall,
                                color = TelevisionTheme.colors.PaperMuted,
                                modifier = Modifier.padding(
                                    start = TelevisionDimensions.SafeHorizontal,
                                    top = 17.dp,
                                ),
                            )
                        }
                    }
                    if (partialError) {
                        item(key = "partial-error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_home_partial_error_title),
                                message = state.error.resolve(),
                                onRetry = onRefresh,
                                retryLabel = stringResource(R.string.retry),
                                requestInitialFocus = false,
                                focusRequester = partialErrorFocus,
                                modifier = Modifier
                                    .padding(
                                        start = TelevisionDimensions.SafeHorizontal,
                                        top = 8.dp,
                                    )
                                    // Retry sits between the hero and the first rail, so it takes
                                    // over both ends of that chain while it is showing rather than
                                    // being something only spatial navigation can stumble into.
                                    .focusProperties {
                                        up = playFocus
                                        down = firstRailFocus
                                    },
                            )
                        }
                    }
                    if (state.error != null && state.shelves.isEmpty()) {
                        item(key = "error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_home_error_title),
                                message = state.error.resolve(),
                                onRetry = onRefresh,
                                retryLabel = stringResource(R.string.retry),
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
                                title = stringResource(R.string.home_empty_title),
                                message = stringResource(R.string.tv_home_empty_detail),
                                actionLabel = stringResource(R.string.retry),
                                onAction = onRefresh,
                                focusRequester = fallbackContentFocus,
                                requestInitialFocus = true,
                                modifier = Modifier.padding(
                                    start = TelevisionDimensions.SafeHorizontal,
                                    top = 17.dp,
                                ),
                            )
                        }
                    } else {
                        items(
                            count = state.shelves.size,
                            key = { index -> "${state.shelves[index].id}:$index" },
                        ) { railIndex ->
                            val shelf = state.shelves[railIndex]
                            HomeShelf(
                                shelf = shelf,
                                title = homeShelfTitle(shelf),
                                firstRail = railIndex == 0,
                                // The first rail's Up target is the partial-error Retry when one is
                                // showing, so a failed section cannot be stranded between the hero
                                // and the content it failed to load.
                                heroFocusRequester = if (railIndex == 0 && partialError) {
                                    partialErrorFocus
                                } else {
                                    playFocus
                                },
                                firstItemFocusRequester = railFirstItemFocus.getOrNull(railIndex),
                                upFocusRequester = railFirstItemFocus.getOrNull(railIndex - 1),
                                downFocusRequester = railFirstItemFocus.getOrNull(railIndex + 1),
                                onFocused = { item, itemIndex ->
                                    focusedRail = railIndex
                                    focusedItemId = item.id
                                    focusedItemIndex = itemIndex
                                    onItemFocused(item)
                                },
                                onClick = onOpenItem,
                                watchedIndicatorsEnabled = settings.watchedIndicators,
                                restoreItemId = restoreItemId.takeIf { railIndex == focusedRail },
                                restoreItemIndex = focusedItemIndex,
                                onRestored = { restorePending = false },
                            )
                        }
                        item(key = "end") {
                            EndOfLibraryMessage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 26.dp, bottom = 9.dp),
                            )
                        }
                    }
                }
            }

        }

        hero?.let { current ->
            if (!browsing) {
                HomeHero(
                    hero = current,
                    playable = current.item.type in DirectlyPlayableTypes,
                    playLoading = playbackLaunch.loading,
                    onPlay = { playbackLaunch.launch { onPlay(current.item) } },
                    onDetails = { onOpenItem(current.item) },
                    onToggleFavorite = { onToggleFavorite(current.item) },
                    onHeroFocused = { focusedRail = -1 },
                    focusRequester = playFocus,
                    firstRailFocusRequester = if (partialError) partialErrorFocus else firstRailFocus,
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
                state.shelves.isEmpty() && !state.loading -> fallbackContentFocus
                // Nothing below the navigation bar can hold focus while Home is still loading.
                // Saying so explicitly beats leaving Down pointed at a requester attached to no
                // node, which is a silent no-op that looks like a broken remote (HOME-003).
                state.loading -> FocusRequester.Cancel
                else -> null
            },
            selectedFocusRequester = topNavigationFocus,
            navigationState = navigationState,
            onNavigationFocused = { focusedRail = -1 },
            // Two claims on focus during route entry is one too many. The navbar only reclaims
            // the selected tab when the content has nothing better to offer -- neither a card the
            // viewer left from nor an empty state whose only action is the point of the screen.
            restoreFocusOnResume = restoreItemId == null &&
                !(state.shelves.isEmpty() && !state.loading && state.error == null),
        )
        }
    }
}

@Composable
private fun homeShelfTitle(shelf: MediaShelfUi): String = when {
    shelf.id == "continue" -> stringResource(R.string.continue_watching)
    shelf.id == "next-up" -> stringResource(R.string.next_up)
    shelf.id == "my-list" -> stringResource(R.string.tv_my_list)
    shelf.id.startsWith("latest:") -> stringResource(R.string.latest_in, shelf.title)
    else -> shelf.title
}
