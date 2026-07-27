package com.maik205.shoumeiplayer.ui.screens.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.ui.components.GenreUi
import com.maik205.shoumeiplayer.ui.navigation.LibraryRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_LIMIT = 100
private const val GENRE_LIMIT = 40

enum class LibrarySort(val apiValue: String, val labelRes: Int) {
    NAME("SortName", R.string.sort_name),
    DATE_ADDED("DateCreated", R.string.sort_date_added),
    PREMIERE("PremiereDate", R.string.sort_release_date),
}

/** §3.5 — replaces the old boolean `LibraryFilter` enum's role as the "watched" axis. */
enum class WatchedFilter(val apiValue: String?, val labelRes: Int) {
    All(null, R.string.filter_all),
    Unwatched("IsUnplayed", R.string.filter_unwatched),
    Watched("IsPlayed", R.string.filter_watched),
}

/** §3.5 — the whole chip-row filter state: watched axis, genre (uuid-backed) and sort. */
@Immutable
data class ListingFilter(
    val watched: WatchedFilter = WatchedFilter.All,
    val genre: GenreUi? = null,
    val sort: LibrarySort = LibrarySort.NAME,
) {
    fun apiFilters(): List<String> = listOfNotNull(watched.apiValue)
    fun apiGenreIds(): List<String> = listOfNotNull(genre?.id)
}

data class LibraryUiState(
    val title: String = "",
    val items: List<GridTileUi> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val filter: ListingFilter = ListingFilter(),
    val genres: List<GenreUi> = emptyList(),
    /** The focus metadata strip's current subject — mirrors whichever tile last took D-pad focus. */
    val focused: GridTileUi? = null,
    val error: String? = null,
)

/**
 * Backs [LibraryScreen]: a paged, chip-filterable grid over a single library
 * (or a synthetic "all" library when [LibraryRoute.libraryId] points at a view).
 */
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
    private val route: LibraryRoute,
) : ViewModel() {

    /** `movies` -> Movie, `tvshows` -> Series, everything else unfiltered by type. */
    private val includeItemTypes: List<String> = when (route.collectionType) {
        "movies" -> listOf("Movie")
        "tvshows" -> listOf("Series")
        else -> emptyList()
    }

    private val _uiState = MutableStateFlow(LibraryUiState(title = route.title))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** Guards against overlapping page requests from rapid scroll-triggered calls. */
    private var loadInFlight = false

    init {
        loadPage(reset = true)
        loadGenres()
    }

    fun retry() {
        loadPage(reset = _uiState.value.items.isEmpty())
    }

    fun setFilter(filter: ListingFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter) }
        loadPage(reset = true)
    }

    fun setWatched(watched: WatchedFilter) {
        setFilter(_uiState.value.filter.copy(watched = watched))
    }

    fun setGenre(genre: GenreUi?) {
        setFilter(_uiState.value.filter.copy(genre = genre))
    }

    fun setSort(sort: LibrarySort) {
        setFilter(_uiState.value.filter.copy(sort = sort))
    }

    /** M-B11 — mirrors the tile that just took D-pad focus into the metadata strip. */
    fun onTileFocused(tile: GridTileUi) {
        _uiState.update { it.copy(focused = tile) }
    }

    /** Called when the grid scrolls near its end; no-op while a page is already in flight or exhausted. */
    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore) return
        if (state.items.isNotEmpty() && state.items.size >= state.total) return
        loadPage(reset = false)
    }

    private fun loadGenres() {
        viewModelScope.launch {
            val result = libraryRepository.genres(
                parentId = route.libraryId.takeIf { it.isNotBlank() },
                limit = GENRE_LIMIT,
            )
            if (result is ApiResult.Success) {
                val genres = result.data.map { GenreUi(id = it.id, name = it.name.orEmpty()) }
                _uiState.update { it.copy(genres = genres) }
            }
        }
    }

    private fun loadPage(reset: Boolean) {
        if (loadInFlight) return
        loadInFlight = true
        val requestState = _uiState.value
        val startIndex = if (reset) 0 else requestState.items.size
        val sortOrder = when (requestState.filter.sort) {
            LibrarySort.DATE_ADDED, LibrarySort.PREMIERE -> "Descending"
            LibrarySort.NAME -> "Ascending"
        }
        _uiState.update {
            if (reset) it.copy(loading = true, error = null) else it.copy(loadingMore = true, error = null)
        }
        viewModelScope.launch {
            val result = libraryRepository.items(
                // A person-filtered grid has no parent library to scope to; blank means "everything".
                parentId = route.libraryId.takeIf { it.isNotBlank() },
                personIds = listOfNotNull(route.personId),
                includeItemTypes = includeItemTypes,
                recursive = true,
                sortBy = requestState.filter.sort.apiValue,
                sortOrder = sortOrder,
                filters = requestState.filter.apiFilters(),
                genreIds = requestState.filter.apiGenreIds(),
                startIndex = startIndex,
                limit = PAGE_LIMIT,
            )
            when (result) {
                is ApiResult.Success -> {
                    val page = result.data.items.map { it.toGridTile(imageUrlBuilder) }
                    _uiState.update {
                        val items = if (reset) page else it.items + page
                        it.copy(
                            items = items,
                            total = result.data.totalRecordCount,
                            loading = false,
                            loadingMore = false,
                            error = null,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(loading = false, loadingMore = false, error = result.error.displayMessage)
                    }
                }
            }
            loadInFlight = false
        }
    }
}
