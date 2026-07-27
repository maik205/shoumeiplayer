package com.maik205.shoumeiplayer.ui.screens.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.ui.components.EmptyView
import com.maik205.shoumeiplayer.ui.components.ErrorView
import com.maik205.shoumeiplayer.ui.components.NavRailDestination
import com.maik205.shoumeiplayer.ui.components.NavRailScaffold
import com.maik205.shoumeiplayer.ui.components.SkeletonGrid
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.screens.library.GridTileUi
import com.maik205.shoumeiplayer.ui.screens.library.PosterGrid
import com.maik205.shoumeiplayer.ui.screens.library.toGridTile
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Scrims
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the pinned heading sits, and where the grid's first row starts under it. */
private val HeaderTop = Dimens.OverscanVertical
private val GridContentTop = 140.dp
private val GridContentBottom = 54.dp

/** §5.4 — the vignette band under the pinned heading, so tiles dissolve as they scroll beneath it. */
private val VignetteHeight = 72.dp

/** §6 — where a state view parks, clear of the pinned heading. */
private val StatesTop = 220.dp

/**
 * Which branch of the content `when` owns `contentFocusRequester` this frame. M6.3 — the requester
 * is one node per state, never one node bound only to the happy path: in [Error] and [Empty] it is
 * the state view's slab, so arrival focus lands there and the rail's RIGHT target stays attached.
 * [Loading] composes a skeleton grid and nothing focusable, so it is the one state with no target.
 */
private enum class ContentFocusTarget { Loading, Error, Empty, Tiles }

/** Navigation target for a library tile: the arguments `LibraryRoute` needs. */
data class LibraryTarget(val id: String, val title: String, val collectionType: String?)

data class LibrariesUiState(
    val loading: Boolean = true,
    val tiles: List<GridTileUi> = emptyList(),
    val error: String? = null,
)

/**
 * The rail's Libraries destination: `/UserViews`, drawn as the same `Fixed(5)` contact sheet
 * Library and Search use. No second grid idiom, no per-view row — the sheet *is* the directory.
 */
class LibrariesViewModel(
    private val libraryRepository: LibraryRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibrariesUiState())
    val uiState: StateFlow<LibrariesUiState> = _uiState.asStateFlow()

    /** Tile id → route arguments; the grid only carries ids, so the mapping is held here. */
    private var targets: Map<String, LibraryTarget> = emptyMap()

    init {
        load()
    }

    fun retry() = load()

    fun targetFor(id: String): LibraryTarget? = targets[id]

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = libraryRepository.userViews()) {
                is ApiResult.Success -> {
                    val views = result.data
                    targets = views.associate { view ->
                        view.id to LibraryTarget(
                            id = view.id,
                            title = view.name.orEmpty(),
                            collectionType = view.collectionType,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            tiles = views.map { view -> view.toGridTile(imageUrlBuilder) },
                            error = null,
                        )
                    }
                }

                is ApiResult.Failure -> _uiState.update {
                    it.copy(loading = false, error = result.error.displayMessage)
                }
            }
        }
    }
}

/**
 * §5.4 / §3.1 — the libraries sheet: the screen name at the pinned header anchor, the user's views
 * below it as one poster grid, and the collapsed rail on the leading edge with `Libraries` lit.
 *
 * Focus: tile 0 is the scaffold's `contentFocusRequester`, so it is both the screen's initial focus
 * and the rail's RIGHT target — one node, never two competing ones. When there is no grid the empty
 * and error views take that binding on their slab, so no state arrives with focus nowhere and the
 * rail's RIGHT target is never unattached. LEFT out of the content opens the rail.
 */
@Composable
fun LibrariesScreen(
    onNavigateToLibrary: (String, String, String?) -> Unit,
    onNavigate: (NavRailDestination) -> Unit = {},
) {
    val viewModel = containerViewModel { container ->
        LibrariesViewModel(container.libraryRepository, container.imageUrlBuilder)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavRailScaffold(
        selected = NavRailDestination.Libraries,
        onSelect = onNavigate,
    ) { contentFocusRequester, railFocusRequester ->
        val hasTiles = uiState.tiles.isNotEmpty()
        // M6.3 — the first actionable element takes focus once it exists, in *every* state: tile 0
        // when there are tiles, the state view's slab when there are not. Lazy items compose during
        // layout, so retry across a few frames rather than assuming the node is already attached.
        val focusTarget = when {
            uiState.loading && !hasTiles -> ContentFocusTarget.Loading
            !hasTiles && uiState.error != null -> ContentFocusTarget.Error
            !hasTiles -> ContentFocusTarget.Empty
            else -> ContentFocusTarget.Tiles
        }
        LaunchedEffect(focusTarget) {
            if (focusTarget == ContentFocusTarget.Loading) return@LaunchedEffect
            repeat(5) {
                withFrameNanos { }
                if (runCatching { contentFocusRequester.requestFocus() }.isSuccess) {
                    return@LaunchedEffect
                }
            }
        }

        // The state views hold one slab; binding the requester to the group around it makes that
        // slab the arrival target, and LEFT off it opens the rail exactly as it does off the grid.
        val stateViewModifier = Modifier
            .focusRequester(contentFocusRequester)
            .focusProperties { left = railFocusRequester }
            .focusGroup()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink000),
        ) {
            when {
                // §6 — loading is a skeleton grid at the real tile dimensions, never a spinner.
                uiState.loading && !hasTiles -> SkeletonGrid(
                    modifier = Modifier.padding(top = GridContentTop),
                )

                !hasTiles && uiState.error != null -> ErrorView(
                    message = uiState.error.orEmpty(),
                    onRetry = viewModel::retry,
                    modifier = stateViewModifier,
                    startPadding = Dimens.OverscanHorizontal,
                    topPadding = StatesTop,
                )

                !hasTiles -> EmptyView(
                    message = stringResource(R.string.libraries_empty_title),
                    detail = stringResource(R.string.empty_check_library_detail),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = { onNavigate(NavRailDestination.Settings) },
                    modifier = stateViewModifier,
                    startPadding = Dimens.OverscanHorizontal,
                    topPadding = StatesTop,
                )

                else -> PosterGrid(
                    tiles = uiState.tiles,
                    onTileClick = { id ->
                        viewModel.targetFor(id)?.let { target ->
                            onNavigateToLibrary(target.id, target.title, target.collectionType)
                        }
                    },
                    contentPadding = PaddingValues(
                        start = Dimens.OverscanHorizontal,
                        end = Dimens.OverscanHorizontal,
                        top = GridContentTop,
                        bottom = GridContentBottom,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer()
                        // Anti-trap: LEFT off the leading column opens the rail. A tile with a
                        // left-hand neighbour resolves that first, so this only fires at the edge.
                        .focusProperties { left = railFocusRequester },
                    firstTileFocus = contentFocusRequester,
                )
            }

            // The dissolve band sits between the heading and the scrolling grid.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = GridContentTop - VignetteHeight)
                    .height(VignetteHeight)
                    .background(Scrims.TopVignette),
            )

            Text(
                text = stringResource(R.string.nav_libraries),
                style = MaterialTheme.typography.displaySmall,
                color = Paper,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = Dimens.OverscanHorizontal, top = HeaderTop),
            )
        }
    }
}
