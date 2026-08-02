package com.maik205.shoumeiplayer.ui.television.screens.browse

import com.maik205.shoumeiplayer.R
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.cache.LibraryCacheStore
import com.maik205.shoumeiplayer.data.session.LibraryPresentation
import com.maik205.shoumeiplayer.data.session.PreferenceStore
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.data.session.UserScope
import com.maik205.shoumeiplayer.domain.model.MediaPage
import com.maik205.shoumeiplayer.domain.model.MediaPageRequest
import com.maik205.shoumeiplayer.domain.model.MediaSort
import com.maik205.shoumeiplayer.domain.model.MediaView
import com.maik205.shoumeiplayer.domain.repository.MediaCatalog
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.toUiText
import com.maik205.shoumeiplayer.ui.television.model.HeroUi
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.domain.model.MediaShelf as MediaShelfUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Immutable
data class TelevisionHomeState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val libraries: List<LibraryDestinationUi> = emptyList(),
    val shelves: List<MediaShelfUi> = emptyList(),
    val hero: HeroUi? = null,
    val error: UiText? = null,
    val shelfErrors: List<UiText> = emptyList(),
    /**
     * The library "Remember last library" (#88) says the viewer should be returned to, once
     * validated against the libraries this account actually has right now. Populated at most once
     * per process -- see [TelevisionHomeViewModel.restoreLibraryAttempted] -- and cleared by
     * [TelevisionHomeViewModel.consumeRestoreLibrary] once a caller has acted on it, so a viewer who
     * has since navigated elsewhere is never yanked back to it a second time.
     */
    val restoreLibrary: LibraryDestinationUi? = null,
)

class TelevisionHomeViewModel(
    private val catalog: MediaCatalog,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val libraryCacheStore: LibraryCacheStore,
    private val preferenceStore: PreferenceStore,
) : ViewModel() {
    private val shelfRequestSemaphore = Semaphore(MAX_SHELF_REQUESTS)
    private val _state = MutableStateFlow(TelevisionHomeState())
    val state: StateFlow<TelevisionHomeState> = _state.asStateFlow()

    /**
     * Restoring the last library is a one-shot decision made from the first library list this
     * ViewModel ever sees. Without this guard, a later pull-to-refresh would re-read the persisted
     * id and repopulate [TelevisionHomeState.restoreLibrary] even after a caller already consumed
     * (and acted on) the first offer, surprising a viewer who has since started browsing Home.
     */
    private var restoreLibraryAttempted = false

    init {
        viewModelScope.launch {
            try {
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Cache corruption or an unavailable cache must not prevent the network refresh.
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
                if (viewsResult is ApiResult.Failure) {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = viewsResult.error.toUiText(),
                        )
                    }
                    return@launch
                }
                val views = (viewsResult as ApiResult.Success).data
                val shelves = coroutineScope {
                    val resume = async {
                        shelfRequestSemaphore.withPermit {
                            loadShelf("continue", "continue", catalog.resumeItems(24))
                        }
                    }
                    val nextUp = async {
                        shelfRequestSemaphore.withPermit {
                            loadShelf("next-up", "next-up", catalog.nextUp(24))
                        }
                    }
                    val favorites = async {
                        shelfRequestSemaphore.withPermit {
                            loadShelf("my-list", "my-list", catalog.favoriteItems(24))
                        }
                    }
                    val latest = views
                        .take(MAX_LATEST_LIBRARIES)
                        .map { view ->
                            async {
                                shelfRequestSemaphore.withPermit {
                                    loadShelf(
                                        id = "latest:${view.id}",
                                        title = view.title,
                                        result = catalog.latest(view.id, 24),
                                    )
                                }
                            }
                        }

                    listOf(resume.await(), nextUp.await(), favorites.await()) + latest.awaitAll()
                }

                val shelfErrors = shelves.mapNotNull { it.error }
                val previousShelves = _state.value.shelves.associateBy(MediaShelfUi::id)
                val freshShelves = shelves.mapNotNull { result ->
                    result.shelf ?: previousShelves[result.id]
                }.filter { it.items.isNotEmpty() }

                val currentHero = _state.value.hero?.item?.id
                    ?.let { id -> freshShelves.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.id == id } }
                    ?: freshShelves.firstNotNullOfOrNull { it.items.firstOrNull() }

                val restoreLibrary = if (restoreLibraryAttempted) {
                    _state.value.restoreLibrary
                } else {
                    restoreLibraryAttempted = true
                    restoreLibraryTarget(views)
                }

                val freshState = TelevisionHomeState(
                    loading = false,
                    refreshing = false,
                    libraries = views,
                    shelves = freshShelves,
                    hero = currentHero?.let(::HeroUi),
                    error = shelfErrors.firstOrNull(),
                    shelfErrors = shelfErrors,
                    restoreLibrary = restoreLibrary,
                )
                _state.value = freshState
                try {
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
                } catch (_: Throwable) {
                    // A cache write must never turn a successful refresh into a screen error.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = UiText.Resource(R.string.tv_home_load_failed),
                    )
                }
            }
        }
    }

    fun focus(item: MediaItemUi) {
        if (_state.value.hero?.item?.id == item.id) return
        _state.update { it.copy(hero = HeroUi(item)) }
    }

    /** Called once a caller has navigated to (or otherwise acted on) [TelevisionHomeState.restoreLibrary]. */
    fun consumeRestoreLibrary() {
        _state.update { it.copy(restoreLibrary = null) }
    }

    /**
     * The library "Remember last library" should send the viewer back to, or null when the toggle
     * is off, nobody is signed in, nothing has been opened yet, or the remembered library no longer
     * exists on the server -- a retired library must degrade to no restoration rather than crash or
     * point at a destination [TelevisionLibraryViewModel] cannot load.
     */
    private suspend fun restoreLibraryTarget(libraries: List<LibraryDestinationUi>): LibraryDestinationUi? =
        try {
            val scope = UserScope.of(sessionStore.current())
            if (scope != null && settingsStore.current().rememberLastLibrary) {
                val lastLibraryId = preferenceStore.lastLibraryId(scope)
                libraries.firstOrNull { it.id == lastLibraryId }
            } else {
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

    fun toggleFavorite(item: MediaItemUi) {
        viewModelScope.launch {
            try {
                when (val result = catalog.setFavorite(item.id, !item.favorite)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(error = result.error.toUiText())
                }
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
                                title = "my-list",
                                items = listOf(updatedItem),
                            ),
                        )
                    }
                    state.copy(
                        shelves = updatedShelves,
                        hero = state.hero?.let { current -> HeroUi(replace(current.item)) },
                        error = null,
                    )
                }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.update {
                    it.copy(error = UiText.Resource(R.string.tv_home_action_failed))
                }
            }
        }
    }

    private fun loadShelf(
        id: String,
        title: String,
        result: ApiResult<List<MediaItemUi>>,
    ): ShelfLoadResult = when (result) {
        is ApiResult.Failure -> ShelfLoadResult(id = id, shelf = null, error = result.error.toUiText())
        is ApiResult.Success -> ShelfLoadResult(
            id = id,
            shelf = MediaShelfUi(id = id, title = title, items = result.data),
            error = null,
        )
    }

    private data class ShelfLoadResult(
        val id: String,
        val shelf: MediaShelfUi?,
        val error: UiText?,
    )

    private companion object {
        const val MAX_SHELF_REQUESTS = 3
        const val MAX_LATEST_LIBRARIES = 8
    }
}

@Immutable
enum class BrowseSort(
    val domain: MediaSort,
    val cacheKey: String,
) {
    Name(MediaSort.Name, "name"),
    Recent(MediaSort.Recent, "recent"),
    Premiere(MediaSort.PremiereDate, "premiere"),
    CommunityRating(MediaSort.CommunityRating, "rating"),
    ;

    companion object {
        /**
         * A stored [cacheKey] this build no longer knows -- an enum value retired in a later
         * release -- must degrade to the default sort rather than crash the library screen.
         */
        fun fromCacheKey(cacheKey: String?): BrowseSort =
            entries.firstOrNull { it.cacheKey == cacheKey } ?: Recent
    }
}

@Immutable
enum class LibraryViewMode(val domain: MediaView, val cacheKey: String) {
    All(MediaView.All, "all"),
    New(MediaView.New, "new"),
    Favorites(MediaView.Favorites, "favorites"),
    ;

    companion object {
        /** Same degrade-to-default contract as [BrowseSort.fromCacheKey]. */
        fun fromCacheKey(cacheKey: String?): LibraryViewMode =
            entries.firstOrNull { it.cacheKey == cacheKey } ?: All
    }
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
    val error: UiText? = null,
    val exhausted: Boolean = false,
)

class TelevisionLibraryViewModel(
    private val catalog: MediaCatalog,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val libraryCacheStore: LibraryCacheStore,
    private val preferenceStore: PreferenceStore,
    private val libraryId: String,
    title: String,
    private val collectionType: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(
        TelevisionLibraryState(title = title, collectionType = collectionType),
    )
    val state: StateFlow<TelevisionLibraryState> = _state.asStateFlow()

    private var nextIndex = 0
    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null
    init {
        viewModelScope.launch {
            // Restores the sort/view this library was last browsed with (#90) and records this as
            // the last-opened library when "remember last library" (#88) is on. Runs before the
            // cache read below, which keys its lookup off `_state.value.sort`/`.view`, so a restored
            // presentation is what the cache is actually consulted with -- not the Recent/All
            // default.
            restorePresentationAndTrackLastLibrary()
            try {
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Treat a cache read failure as a miss; reload below remains authoritative.
            }
            reload()
        }
    }

    fun reload(
        sort: BrowseSort = _state.value.sort,
        view: LibraryViewMode = _state.value.view,
    ) {
        reloadJob?.cancel()
        loadMoreJob?.cancel()
        reloadJob = viewModelScope.launch {
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
            try {
                val result = loadPage(startIndex = 0, sort = sort, view = view)
                when (result) {
                    is ApiResult.Failure -> _state.update {
                        it.copy(loading = false, error = result.error.toUiText())
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
                        try {
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
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // Cache persistence is best effort; the network result is already visible.
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = UiText.Resource(R.string.tv_library_load_failed),
                    )
                }
            }
        }
    }

    fun setSort(sort: BrowseSort) {
        if (_state.value.sort == sort) return
        val view = _state.value.view
        reload(sort = sort, view = view)
        persistPresentation(sort = sort, view = view)
    }

    fun setView(view: LibraryViewMode) {
        if (_state.value.view == view) return
        val sort = _state.value.sort
        reload(sort = sort, view = view)
        persistPresentation(sort = sort, view = view)
    }

    /**
     * Restores the sort/view this library was last browsed with, scoped per user per library
     * (#90), and -- when the "remember last library" toggle is on -- records that this library was
     * just opened, so [TelevisionHomeViewModel] can offer to return to it (#88). Best-effort: a
     * store failure must not block the network reload below, which stays authoritative either way.
     */
    private suspend fun restorePresentationAndTrackLastLibrary() {
        try {
            val scope = UserScope.of(sessionStore.current()) ?: return
            preferenceStore.libraryPresentation(scope, libraryId)?.let { presentation ->
                _state.update {
                    it.copy(
                        sort = BrowseSort.fromCacheKey(presentation.sort),
                        view = LibraryViewMode.fromCacheKey(presentation.view),
                    )
                }
            }
            if (settingsStore.current().rememberLastLibrary) {
                preferenceStore.setLastLibraryId(scope, libraryId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Restoring presentation/last-library is best effort; reload() stays authoritative.
        }
    }

    /** Persists a sort/view the viewer explicitly picked. Best-effort: never blocks the UI update. */
    private fun persistPresentation(sort: BrowseSort, view: LibraryViewMode) {
        viewModelScope.launch {
            try {
                val scope = UserScope.of(sessionStore.current()) ?: return@launch
                preferenceStore.setLibraryPresentation(
                    scope,
                    libraryId,
                    LibraryPresentation(sort = sort.cacheKey, view = view.cacheKey),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Persisting the chosen presentation is best effort; in-memory state already reflects it.
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.loadingMore || snapshot.exhausted) return
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                when (val result = loadPage(nextIndex, snapshot.sort, snapshot.view)) {
                    is ApiResult.Failure -> _state.update {
                        it.copy(loadingMore = false, error = result.error.toUiText())
                    }

                    is ApiResult.Success -> {
                        nextIndex += result.data.items.size
                        _state.update {
                            it.copy(
                                loadingMore = false,
                                items = it.items + result.data.items,
                                totalCount = result.data.totalCount,
                                error = null,
                                exhausted = result.data.items.size < PAGE_SIZE,
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        loadingMore = false,
                        error = UiText.Resource(R.string.tv_library_load_more_failed),
                    )
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
