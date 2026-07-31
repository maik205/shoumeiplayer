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
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.ui.television.model.HeroUi
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.domain.model.MediaShelf as MediaShelfUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
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
                        key = { index -> "${state.shelves[index].id}:$index" },
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
