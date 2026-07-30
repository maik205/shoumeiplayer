package com.maik205.shoumeiplayer.ui.television.screens.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.cache.LibraryCacheStore
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.domain.model.MediaPage
import com.maik205.shoumeiplayer.domain.model.MediaPageRequest
import com.maik205.shoumeiplayer.domain.model.MediaSort
import com.maik205.shoumeiplayer.domain.model.MediaView
import com.maik205.shoumeiplayer.domain.repository.MediaCatalog
import com.maik205.shoumeiplayer.ui.television.model.HeroUi
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.domain.model.MediaShelf as MediaShelfUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val catalog: MediaCatalog,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val libraryCacheStore: LibraryCacheStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TelevisionHomeState())
    val state: StateFlow<TelevisionHomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessionStore.current()
            if (settingsStore.current().cacheHomeContent && session != null) {
                libraryCacheStore.readHome(session.serverUrl, session.userId)?.let { cached ->
                    _state.value = TelevisionHomeState(
                        loading = false,
                        libraries = cached.libraries,
                        shelves = cached.shelves,
                        hero = cached.heroItemId
                            ?.let { id -> cached.shelves.flatMap { it.items }.firstOrNull { it.id == id } }
                            ?.let(::HeroUi),
                    )
                }
            }
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = !it.loading, error = null) }
            try {
                val viewsResult = catalog.libraries()
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
                val shelves = coroutineScope {
                    val resume = async { catalog.resumeItems(24).asItems() }
                    val nextUp = async { catalog.nextUp(24).asItems() }
                    val favorites = async {
                        when (val result = catalog.favoriteItems(24)) {
                            is ApiResult.Failure -> emptyList()
                            is ApiResult.Success -> result.data
                        }
                    }
                    val latest = views
                        .take(MAX_LATEST_LIBRARIES)
                        .map { view ->
                            async {
                                val items = catalog.latest(view.id, 24).asItems()
                                MediaShelfUi(
                                    id = "latest:${view.id}",
                                    title = "Latest in ${view.title}",
                                    items = items,
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
                                    items = resumeItems,
                                ),
                            )
                        }
                        val nextItems = nextUp.await()
                        if (nextItems.isNotEmpty()) {
                            add(
                                MediaShelfUi(
                                    id = "next-up",
                                    title = "Next up",
                                    items = nextItems,
                                ),
                            )
                        }
                        val favoriteItems = favorites.await()
                        if (favoriteItems.isNotEmpty()) {
                            add(
                                MediaShelfUi(
                                    id = "my-list",
                                    title = "My list",
                                    items = favoriteItems,
                                ),
                            )
                        }
                        addAll(latest.awaitAll().filter { it.items.isNotEmpty() })
                    }
                }

                val currentHero = _state.value.hero?.item?.id
                    ?.let { id -> shelves.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.id == id } }
                    ?: shelves.firstNotNullOfOrNull { it.items.firstOrNull() }

                val freshState = TelevisionHomeState(
                    loading = false,
                    refreshing = false,
                    libraries = views,
                    shelves = shelves,
                    hero = currentHero?.let(::HeroUi),
                    error = null,
                )
                _state.value = freshState
                val session = sessionStore.current()
                if (settingsStore.current().cacheHomeContent && session != null) {
                    libraryCacheStore.writeHome(
                        serverUrl = session.serverUrl,
                        userId = session.userId,
                        libraries = freshState.libraries,
                        shelves = freshState.shelves,
                        heroItemId = freshState.hero?.item?.id,
                    )
                }
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
            when (catalog.setFavorite(item.id, !item.favorite)) {
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

    private fun ApiResult<List<MediaItemUi>>.asItems(): List<MediaItemUi> =
        (this as? ApiResult.Success)?.data.orEmpty()

    private companion object {
        const val MAX_LATEST_LIBRARIES = 8
    }
}

@Immutable
enum class BrowseSort(
    val domain: MediaSort,
    val cacheKey: String,
    val label: String,
) {
    Name(MediaSort.Name, "name", "Name"),
    Recent(MediaSort.Recent, "recent", "Recent"),
    Premiere(MediaSort.PremiereDate, "premiere", "Release date"),
    CommunityRating(MediaSort.CommunityRating, "rating", "Rating"),
}

@Immutable
enum class LibraryViewMode(val domain: MediaView, val cacheKey: String) {
    All(MediaView.All, "all"),
    New(MediaView.New, "new"),
    Favorites(MediaView.Favorites, "favorites"),
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
    private val catalog: MediaCatalog,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val libraryCacheStore: LibraryCacheStore,
    private val libraryId: String,
    title: String,
    private val collectionType: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(
        TelevisionLibraryState(title = title, collectionType = collectionType),
    )
    val state: StateFlow<TelevisionLibraryState> = _state.asStateFlow()

    private var nextIndex = 0
    init {
        viewModelScope.launch {
            val session = sessionStore.current()
            if (settingsStore.current().cacheHomeContent && session != null) {
                libraryCacheStore.readLibrary(
                    session.serverUrl,
                    session.userId,
                    libraryId,
                    _state.value.sort.cacheKey,
                    _state.value.view.cacheKey,
                )?.let { cached ->
                    nextIndex = cached.items.size
                    _state.update {
                        it.copy(
                            loading = false,
                            items = cached.items,
                            totalCount = cached.totalCount,
                            exhausted = cached.exhausted,
                            error = null,
                        )
                    }
                }
            }
            reload()
        }
    }

    fun reload(
        sort: BrowseSort = _state.value.sort,
        view: LibraryViewMode = _state.value.view,
    ) {
        viewModelScope.launch {
            nextIndex = 0
            val previous = _state.value
            val keepCachedItems = previous.items.isNotEmpty() &&
                previous.sort == sort &&
                previous.view == view
            _state.update {
                it.copy(
                    loading = !keepCachedItems,
                    sort = sort,
                    view = view,
                    items = if (keepCachedItems) it.items else emptyList(),
                    totalCount = if (keepCachedItems) it.totalCount else 0,
                    error = null,
                    exhausted = if (keepCachedItems) it.exhausted else false,
                )
            }
            val result = loadPage(startIndex = 0, sort = sort, view = view)
            when (result) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.displayMessage)
                }

                is ApiResult.Success -> {
                    nextIndex = result.data.items.size
                    val freshItems = result.data.items
                    _state.update {
                        it.copy(
                            loading = false,
                            items = freshItems,
                            totalCount = result.data.totalCount,
                            exhausted = result.data.items.size < PAGE_SIZE,
                        )
                    }
                    val session = sessionStore.current()
                    if (settingsStore.current().cacheHomeContent && session != null) {
                        libraryCacheStore.writeLibrary(
                            serverUrl = session.serverUrl,
                            userId = session.userId,
                            libraryId = libraryId,
                            sort = sort.cacheKey,
                            view = view.cacheKey,
                            items = freshItems,
                            totalCount = result.data.totalCount,
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
                            items = it.items + result.data.items,
                            totalCount = result.data.totalCount,
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
    ): ApiResult<MediaPage> {
        return catalog.page(
            MediaPageRequest(
                libraryId = libraryId,
                collectionType = collectionType,
                sort = sort.domain,
                view = view.domain,
                startIndex = startIndex,
                limit = PAGE_SIZE,
            ),
        )
    }

    private companion object {
        const val PAGE_SIZE = 60
    }
}
