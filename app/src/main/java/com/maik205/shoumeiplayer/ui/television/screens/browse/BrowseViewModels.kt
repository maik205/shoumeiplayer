package com.maik205.shoumeiplayer.ui.television.screens.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.QueryResult
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.ui.television.model.HeroUi
import com.maik205.shoumeiplayer.ui.television.model.LibraryDestinationUi
import com.maik205.shoumeiplayer.ui.television.model.MediaItemUi
import com.maik205.shoumeiplayer.ui.television.model.MediaShelfUi
import com.maik205.shoumeiplayer.ui.television.model.toTelevisionUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

@Immutable
data class TelevisionHomeState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val libraries: List<LibraryDestinationUi> = emptyList(),
    val shelves: List<MediaShelfUi> = emptyList(),
    val hero: HeroUi? = null,
    val error: String? = null,
)

class TelevisionHomeViewModel(
    private val repository: LibraryRepository,
    private val images: ImageUrlBuilder,
) : ViewModel() {
    private val _state = MutableStateFlow(TelevisionHomeState())
    val state: StateFlow<TelevisionHomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = !it.loading, error = null) }
            try {
                val viewsResult = repository.userViews()
                val views = (viewsResult as? ApiResult.Success)?.data.orEmpty()
                if (viewsResult is ApiResult.Failure && _state.value.shelves.isEmpty()) {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = viewsResult.error.displayMessage,
                        )
                    }
                    return@launch
                }
                val visibleViews = views.filterNot { view ->
                    view.collectionType.equals("photos", ignoreCase = true)
                }

                val shelves = coroutineScope {
                    val resume = async { repository.resumeItems(24).asItems() }
                    val nextUp = async { repository.nextUp(24).asItems() }
                    val favorites = async {
                        when (
                            val result = repository.items(
                                filters = listOf("IsFavorite"),
                                sortBy = "DateCreated",
                                sortOrder = "Descending",
                                limit = 24,
                            )
                        ) {
                            is ApiResult.Failure -> emptyList()
                            is ApiResult.Success -> result.data.items
                        }
                    }
                    val latest = visibleViews
                        .take(MAX_LATEST_LIBRARIES)
                        .map { view ->
                            async {
                                val items = repository.latest(view.id, 24).asItems()
                                MediaShelfUi(
                                    id = "latest:${view.id}",
                                    title = view.name?.let { "Latest in $it" } ?: "Latest",
                                    items = items.map { item -> item.toTelevisionUi(images) },
                                )
                            }
                        }

                    buildList {
                        val resumeItems = resume.await()
                        if (resumeItems.isNotEmpty()) {
                            add(
                                MediaShelfUi(
                                    id = "continue",
                                    title = "Continue watching",
                                    items = resumeItems.map { it.toTelevisionUi(images) },
                                ),
                            )
                        }
                        val nextItems = nextUp.await()
                        if (nextItems.isNotEmpty()) {
                            add(
                                MediaShelfUi(
                                    id = "next-up",
                                    title = "Next up",
                                    items = nextItems.map { it.toTelevisionUi(images) },
                                ),
                            )
                        }
                        val favoriteItems = favorites.await()
                        if (favoriteItems.isNotEmpty()) {
                            add(
                                MediaShelfUi(
                                    id = "my-list",
                                    title = "My list",
                                    items = favoriteItems.map { it.toTelevisionUi(images) },
                                ),
                            )
                        }
                        addAll(latest.awaitAll().filter { it.items.isNotEmpty() })
                    }
                }

                val currentHero = _state.value.hero?.item?.id
                    ?.let { id -> shelves.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.id == id } }
                    ?: shelves.firstNotNullOfOrNull { it.items.firstOrNull() }

                _state.value = TelevisionHomeState(
                    loading = false,
                    refreshing = false,
                    libraries = visibleViews.map {
                        LibraryDestinationUi(
                            id = it.id,
                            title = it.name.orEmpty(),
                            collectionType = it.collectionType,
                        )
                    },
                    shelves = shelves,
                    hero = currentHero?.let(::HeroUi),
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = error.message ?: "Couldn't load your libraries",
                    )
                }
            }
        }
    }

    fun focus(item: MediaItemUi) {
        if (_state.value.hero?.item?.id == item.id) return
        _state.update { it.copy(hero = HeroUi(item)) }
    }

    fun toggleFavorite(item: MediaItemUi) {
        viewModelScope.launch {
            when (repository.setFavorite(item.id, !item.favorite)) {
                is ApiResult.Failure -> Unit
                is ApiResult.Success -> _state.update { state ->
                    val nowFavorite = !item.favorite
                    val updatedItem = item.copy(favorite = nowFavorite)
                    val replace: (MediaItemUi) -> MediaItemUi = { media ->
                        if (media.id == item.id) media.copy(favorite = nowFavorite) else media
                    }
                    val updatedShelves = state.shelves.mapNotNull { shelf ->
                        if (shelf.id != "my-list") {
                            shelf.copy(items = shelf.items.map(replace))
                        } else {
                            val items = if (nowFavorite) {
                                listOf(updatedItem) + shelf.items
                                    .filterNot { it.id == item.id }
                                    .map(replace)
                            } else {
                                shelf.items.filterNot { it.id == item.id }.map(replace)
                            }
                            shelf.copy(items = items).takeIf { items.isNotEmpty() }
                        }
                    }.toMutableList()
                    if (nowFavorite && updatedShelves.none { it.id == "my-list" }) {
                        val insertAt = updatedShelves.indexOfLast {
                            it.id == "continue" || it.id == "next-up"
                        }.let { if (it < 0) 0 else it + 1 }
                        updatedShelves.add(
                            insertAt,
                            MediaShelfUi(
                                id = "my-list",
                                title = "My list",
                                items = listOf(updatedItem),
                            ),
                        )
                    }
                    state.copy(
                        shelves = updatedShelves,
                        hero = state.hero?.let { current -> HeroUi(replace(current.item)) },
                    )
                }
            }
        }
    }

    private fun ApiResult<List<BaseItemDto>>.asItems(): List<BaseItemDto> =
        (this as? ApiResult.Success)?.data.orEmpty()

    private companion object {
        const val MAX_LATEST_LIBRARIES = 8
    }
}

@Immutable
enum class BrowseSort(val api: String, val label: String) {
    Name("SortName", "Name"),
    Recent("DateLastContentAdded", "Recent"),
    Premiere("PremiereDate", "Release date"),
    CommunityRating("CommunityRating", "Rating"),
}

@Immutable
enum class LibraryViewMode {
    All,
    New,
    Favorites,
}

@Immutable
data class TelevisionLibraryState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val title: String = "",
    val collectionType: String? = null,
    val items: List<MediaItemUi> = emptyList(),
    val sort: BrowseSort = BrowseSort.Recent,
    val view: LibraryViewMode = LibraryViewMode.All,
    val totalCount: Int = 0,
    val error: String? = null,
    val exhausted: Boolean = false,
)

class TelevisionLibraryViewModel(
    private val repository: LibraryRepository,
    private val images: ImageUrlBuilder,
    private val libraryId: String,
    title: String,
    private val collectionType: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(
        TelevisionLibraryState(title = title, collectionType = collectionType),
    )
    val state: StateFlow<TelevisionLibraryState> = _state.asStateFlow()

    private var nextIndex = 0
    private val newItemsCutoff = Instant.now()
        .minus(365, ChronoUnit.DAYS)
        .toString()

    init {
        reload()
    }

    fun reload(
        sort: BrowseSort = _state.value.sort,
        view: LibraryViewMode = _state.value.view,
    ) {
        viewModelScope.launch {
            nextIndex = 0
            _state.update {
                it.copy(
                    loading = true,
                    sort = sort,
                    view = view,
                    items = emptyList(),
                    totalCount = 0,
                    error = null,
                    exhausted = false,
                )
            }
            val result = loadPage(startIndex = 0, sort = sort, view = view)
            when (result) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.displayMessage)
                }

                is ApiResult.Success -> {
                    nextIndex = result.data.items.size
                    _state.update {
                        it.copy(
                            loading = false,
                            items = result.data.items.map { item -> item.toTelevisionUi(images) },
                            totalCount = result.data.totalRecordCount,
                            exhausted = result.data.items.size < PAGE_SIZE,
                        )
                    }
                }
            }
        }
    }

    fun setSort(sort: BrowseSort) {
        if (_state.value.sort != sort) reload(sort)
    }

    fun setView(view: LibraryViewMode) {
        if (_state.value.view != view) reload(view = view)
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.loadingMore || snapshot.exhausted) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val result = loadPage(nextIndex, snapshot.sort, snapshot.view)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loadingMore = false, error = result.error.displayMessage)
                }

                is ApiResult.Success -> {
                    nextIndex += result.data.items.size
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            items = it.items + result.data.items.map { item -> item.toTelevisionUi(images) },
                            totalCount = result.data.totalRecordCount,
                            exhausted = result.data.items.size < PAGE_SIZE,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadPage(
        startIndex: Int,
        sort: BrowseSort,
        view: LibraryViewMode,
    ): ApiResult<QueryResult<BaseItemDto>> {
        return repository.items(
            parentId = libraryId.ifBlank { null },
            includeItemTypes = collectionItemTypes(collectionType),
            recursive = true,
            sortBy = sort.api,
            sortOrder = if (sort == BrowseSort.Name) "Ascending" else "Descending",
            filters = if (view == LibraryViewMode.Favorites) listOf("IsFavorite") else emptyList(),
            minDateLastSavedForUser = newItemsCutoff.takeIf { view == LibraryViewMode.New },
            startIndex = startIndex,
            limit = PAGE_SIZE,
        )
    }

    private companion object {
        const val PAGE_SIZE = 60
    }
}

internal fun collectionItemTypes(collectionType: String?): List<String> = when (
    collectionType?.lowercase()
) {
    "movies" -> listOf("Movie")
    "tvshows" -> listOf("Series")
    "music" -> listOf("MusicAlbum", "MusicArtist", "Audio", "Playlist")
    "musicvideos" -> listOf("MusicVideo")
    "books" -> listOf("AudioBook", "Book")
    "boxsets" -> listOf("BoxSet")
    "playlists" -> listOf("Playlist")
    "livetv" -> listOf("LiveTvChannel", "Recording")
    "photos" -> listOf("PhotoAlbum")
    else -> listOf(
        "Movie",
        "Series",
        "Episode",
        "Video",
        "MusicAlbum",
        "MusicArtist",
        "Audio",
        "AudioBook",
        "Book",
        "BoxSet",
        "Playlist",
        "Recording",
    )
}
