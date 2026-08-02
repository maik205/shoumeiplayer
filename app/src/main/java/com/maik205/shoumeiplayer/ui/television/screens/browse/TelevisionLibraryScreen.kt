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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
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
    // Same seam as TelevisionHomeScreen: read straight from the store instead of growing
    // TelevisionLibraryState with a ClientSettings field (#89).
    val settingsStore = LocalAppContainer.current.settingsStore
    val settingsDefaults = remember { ClientSettings() }
    val settings by settingsStore.settings.collectAsState(initial = settingsDefaults)

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
        imageUrl = if (isMusic) resolveBackdropUrl(focused.value?.backdropUrl, settings.backdropImages) else null,
    ) {
      MaterialTheme(typography = televisionTypography(settings.interfaceScale)) {
        if (!isMusic) {
            val colors = TelevisionTheme.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(colors.LibraryBackground)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.Paper.copy(alpha = 0.035f),
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
                onRetry = onRetry,
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
                watchedIndicatorsEnabled = settings.watchedIndicators,
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
    watchedIndicatorsEnabled: Boolean = true,
) {
    var focusedMediaId by remember { mutableStateOf<String?>(null) }
    var snapTopTick by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val count = maxOf(state.totalCount, state.items.size)
    val countLabel = pluralStringResource(R.plurals.tv_library_item_count, count, count)
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
                        color = TelevisionTheme.colors.PaperSoft,
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
                            label = stringResource(R.string.tv_filter_all),
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
                            label = stringResource(R.string.tv_filter_new),
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
                            label = stringResource(R.string.tv_filter_favorites),
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
                        label = if (sortingByTitle) {
                            stringResource(R.string.tv_sort_title)
                        } else {
                            stringResource(R.string.tv_sort_recent)
                        },
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
                        label = stringResource(R.string.tv_loading_library),
                        shape = TelevisionLoadingShape.Grid,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                state.error != null && state.items.isEmpty() -> item(
                    key = "library-error",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    TelevisionErrorState(
                        title = stringResource(R.string.tv_library_error_title, state.title),
                        message = state.error.resolve(),
                        onRetry = onRetry,
                        retryLabel = stringResource(R.string.retry),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                state.items.isEmpty() -> item(
                    key = "library-empty",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    TelevisionEmptyState(
                        title = stringResource(
                            if (state.view == LibraryViewMode.All) {
                                R.string.library_empty_unfiltered
                            } else {
                                R.string.library_empty_filtered
                            },
                        ),
                        message = stringResource(
                            if (state.view == LibraryViewMode.All) {
                                R.string.empty_check_library_detail
                            } else {
                                R.string.tv_library_filter_detail
                            },
                        ),
                        actionLabel = stringResource(
                            if (state.view == LibraryViewMode.All) R.string.retry else R.string.action_clear_filter,
                        ),
                        onAction = if (state.view == LibraryViewMode.All) onRetry else {
                            { onSetView(LibraryViewMode.All) }
                        },
                        requestInitialFocus = true,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                else -> {
                    itemsIndexed(state.items, key = { index, media -> "${media.id}:$index" }) { _, media ->
                        TelevisionMediaTile(
                            // Only the badge/dimming this tile draws reads the toggle; onClick,
                            // onFocusChanged and the pagination check below all keep using the real
                            // `media` (#89).
                            item = media.withWatchedIndicatorPreference(watchedIndicatorsEnabled),
                            shape = ArtworkShape.Poster,
                            tileWidth = 115.5.dp,
                            tileHeight = 173.25.dp,
                            subtitleOverride = media.librarySubtitle(),
                            subtitleStyle = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 6.5.sp,
                                lineHeight = 8.5.sp,
                            ),
                            subtitleColor = TelevisionTheme.colors.Paper.copy(alpha = 0.75f),
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
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "library-loading-more") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.tv_loading_more),
                                    color = TelevisionTheme.colors.PaperMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    } else if (state.error != null) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "library-inline-error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_library_load_more_failed),
                                message = state.error.resolve(),
                                onRetry = onLoadMore,
                                retryLabel = stringResource(R.string.retry),
                                requestInitialFocus = false,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    if (state.exhausted) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "library-end") {
                            EndOfLibraryMessage(
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

@Composable
private fun MediaItemUi.librarySubtitle(): String? {
    val typeLabel = when (type) {
        "Series" -> stringResource(R.string.tv_detail_series_kind)
        "Movie" -> stringResource(R.string.tv_detail_movie)
        "BoxSet" -> stringResource(R.string.tv_detail_collection_kind)
        "AudioBook", "Book" -> stringResource(R.string.tv_detail_book_kind)
        else -> type.takeIf(String::isNotBlank)
    }
    return listOfNotNull(typeLabel, subtitle?.takeIf(String::isNotBlank))
        .distinct()
        .joinToString(" · ")
        .ifBlank { null }
}
