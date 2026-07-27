package com.maik205.shoumeiplayer.ui.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.blurHash
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.ui.components.MediaCardUi
import com.maik205.shoumeiplayer.ui.components.toCardUi
import com.maik205.shoumeiplayer.ui.components.toSpecLine
import com.maik205.shoumeiplayer.ui.theme.Dur
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ROW_CONTINUE_WATCHING = "continue_watching"
private const val ROW_NEXT_UP = "next_up"
private const val ROW_MY_MEDIA = "my_media"
private const val ROW_LATEST_PREFIX = "latest_"
private const val ROW_GENRE_PREFIX = "genre_"

private val LATEST_ELIGIBLE_COLLECTION_TYPES = setOf("movies", "tvshows")

/** §3.4 — at most four genre rows, so Home stays a page and not a directory. */
private const val GENRE_ROW_COUNT = 4
private const val GENRE_ROW_LIMIT = 20

/** §5 — the hero is the only 1920px request on Home; a card never asks for one. */
private const val HERO_BACKDROP_MAX_WIDTH = 1920
private const val HERO_LOGO_MAX_WIDTH = 640

/**
 * A row's title is resolved in the Composable via [stringResource][androidx.compose.ui.res.stringResource]
 * so the ViewModel never carries a hardcoded display string (F7): [titleRes] + optional [titleArg]
 * cover the four fixed-copy rows (Continue watching / Next up / Latest in <library> / My media);
 * [titleLiteral] is the one row whose title is genuinely server data — a genre name — with no
 * resource to localize.
 */
data class HomeRow(
    val key: String,
    val titleRes: Int? = null,
    val titleArg: String? = null,
    val titleLiteral: String? = null,
    val items: List<MediaCardUi>,
)

data class HomeUiState(
    val loading: Boolean = true,
    val rows: List<HomeRow> = emptyList(),
    val error: String? = null,
)

/**
 * §3.4 — everything the billboard hero draws, resolved in the ViewModel so the screen never has to
 * hold a [BaseItemDto]. Deliberately **not** a field of [HomeUiState]: see [HomeViewModel.hero].
 */
@Immutable
data class HeroUi(
    val itemId: String,
    val title: String,
    val logoUrl: String?,
    val backdropUrl: String?,
    val backdropBlurHash: String?,
    /** [toSpecLine] rhythm: year / runtime / rating. */
    val specLine: String,
    val overview: String?,
    val resumeTicks: Long,
    val canResume: Boolean,
)

/** Navigation target for a "My Media" (library view) card. */
data class HomeLibraryTarget(val id: String, val name: String, val collectionType: String?)

// `debounce` is @FlowPreview; it is stable in practice and is the whole point of guardrail 7's
// ">=300ms settle before the hero commits", so opt in once here rather than at the call site.
@OptIn(FlowPreview::class)
class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _hero = MutableStateFlow<HeroUi?>(null)

    /**
     * Guardrail 7 — a SECOND flow, never a field of [HomeUiState]: the hero changes on every card
     * focus, and folding it into the row state would recompose the whole row list on each D-pad
     * press. Only the backdrop and the copy column collect this.
     *
     * Emissions are debounced by [Dur.HeroDebounce] so a held direction key sweeps the row without
     * the room strobing; the hero commits once per settle.
     */
    val hero: StateFlow<HeroUi?> = _hero.asStateFlow()

    /** The un-debounced focus signal. Writing it is O(1) and touches nothing the rows read. */
    private val focusedHero = MutableStateFlow<HeroUi?>(null)

    /** Raw library views, kept so "My Media" clicks can resolve name/collectionType. */
    private var libraryViews: List<BaseItemDto> = emptyList()

    /** rowKey → (cardId → source item), so a focus event resolves to a hero without a round trip. */
    private var itemsByRow: Map<String, Map<String, BaseItemDto>> = emptyMap()

    init {
        viewModelScope.launch {
            focusedHero
                .filterNotNull()
                .debounce(Dur.HeroDebounce.toLong())
                .distinctUntilChanged()
                .collect { _hero.value = it }
        }
        load()
    }

    fun retry() {
        load()
    }

    /**
     * Called from [com.maik205.shoumeiplayer.ui.components.MediaRow]'s existing `onItemFocused`.
     * Cheap by construction: a map lookup and one [MutableStateFlow] write that no row observes.
     */
    fun onCardFocused(rowKey: String, cardId: String) {
        val item = itemsByRow[rowKey]?.get(cardId) ?: return
        focusedHero.value = item.toHeroUi()
    }

    /** Resolves a "My Media" card id back to its library route arguments, if known. */
    fun libraryRouteFor(id: String): HomeLibraryTarget? =
        libraryViews.find { it.id == id }?.let {
            HomeLibraryTarget(id = it.id, name = it.name.orEmpty(), collectionType = it.collectionType)
        }

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            coroutineScope {
                val resumeDeferred = async { libraryRepository.resumeItems(limit = 20) }
                val nextUpDeferred = async { libraryRepository.nextUp(limit = 20) }
                val viewsDeferred = async { libraryRepository.userViews() }

                val resumeResult = resumeDeferred.await()
                val nextUpResult = nextUpDeferred.await()
                val viewsResult = viewsDeferred.await()

                val rows = mutableListOf<HomeRow>()
                val sources = mutableMapOf<String, Map<String, BaseItemDto>>()

                fun addRow(
                    key: String,
                    items: List<BaseItemDto>,
                    cards: List<MediaCardUi>,
                    titleRes: Int? = null,
                    titleArg: String? = null,
                    titleLiteral: String? = null,
                ) {
                    rows += HomeRow(
                        key = key,
                        titleRes = titleRes,
                        titleArg = titleArg,
                        titleLiteral = titleLiteral,
                        items = cards,
                    )
                    sources[key] = items.associateBy { it.id }
                }

                val resumeItems = (resumeResult as? ApiResult.Success)?.data.orEmpty()
                if (resumeItems.isNotEmpty()) {
                    addRow(
                        key = ROW_CONTINUE_WATCHING,
                        titleRes = R.string.continue_watching,
                        items = resumeItems,
                        cards = resumeItems.map { it.toCardUi(imageUrlBuilder) },
                    )
                }

                val nextUpItems = (nextUpResult as? ApiResult.Success)?.data.orEmpty()
                if (nextUpItems.isNotEmpty()) {
                    addRow(
                        key = ROW_NEXT_UP,
                        titleRes = R.string.next_up,
                        items = nextUpItems,
                        // §5.1 — Next Up is a wide card (280×158), like My Media.
                        cards = nextUpItems.map { it.toCardUi(imageUrlBuilder, wide = true) },
                    )
                }

                val views = (viewsResult as? ApiResult.Success)?.data.orEmpty()
                libraryViews = views

                val eligibleViews = views.filter { it.collectionType in LATEST_ELIGIBLE_COLLECTION_TYPES }
                val moviesViewId = views.firstOrNull { it.collectionType == "movies" }?.id

                // Genre enumeration runs alongside the Latest fan-out; both are network-bound.
                val genresDeferred = async {
                    (libraryRepository.genres(parentId = moviesViewId) as? ApiResult.Success)
                        ?.data
                        .orEmpty()
                        .filter { !it.name.isNullOrBlank() }
                        .take(GENRE_ROW_COUNT)
                }
                val latestDeferreds = eligibleViews.map { view ->
                    view to async { libraryRepository.latest(parentId = view.id, limit = 20) }
                }

                var firstLatestItem: BaseItemDto? = null
                for ((view, deferred) in latestDeferreds) {
                    val latestItems = (deferred.await() as? ApiResult.Success)?.data.orEmpty()
                    if (latestItems.isNotEmpty()) {
                        if (firstLatestItem == null) firstLatestItem = latestItems.first()
                        addRow(
                            key = "$ROW_LATEST_PREFIX${view.id}",
                            titleRes = R.string.latest_in,
                            titleArg = view.name.orEmpty(),
                            items = latestItems,
                            cards = latestItems.map { it.toCardUi(imageUrlBuilder) },
                        )
                    }
                }

                // §1.1 — genreIds (uuid), never genres (name): a name carrying a comma would split
                // into two filters on the way out.
                val genreDeferreds = genresDeferred.await().map { genre ->
                    genre to async {
                        libraryRepository.items(
                            sortBy = "Random",
                            limit = GENRE_ROW_LIMIT,
                            genreIds = listOf(genre.id),
                        )
                    }
                }
                for ((genre, deferred) in genreDeferreds) {
                    val genreItems = (deferred.await() as? ApiResult.Success)?.data?.items.orEmpty()
                    if (genreItems.isNotEmpty()) {
                        addRow(
                            key = "$ROW_GENRE_PREFIX${genre.id}",
                            // §3.4 — the row title is the plain genre name, no prefix. Server data,
                            // not app copy, so there is no string resource to route it through.
                            titleLiteral = genre.name.orEmpty(),
                            items = genreItems,
                            cards = genreItems.map { it.toCardUi(imageUrlBuilder) },
                        )
                    }
                }

                if (views.isNotEmpty()) {
                    addRow(
                        key = ROW_MY_MEDIA,
                        titleRes = R.string.my_media,
                        items = views,
                        // §5.1 — My Media sets the library name *inside* the art over CardFoot.
                        cards = views.map {
                            it.toCardUi(imageUrlBuilder, wide = true, labelInsideArt = true)
                        },
                    )
                }

                val error = if (rows.isEmpty()) {
                    (resumeResult as? ApiResult.Failure)?.error?.displayMessage
                        ?: (nextUpResult as? ApiResult.Failure)?.error?.displayMessage
                        ?: (viewsResult as? ApiResult.Failure)?.error?.displayMessage
                } else {
                    null
                }

                itemsByRow = sources
                _uiState.update { it.copy(loading = false, rows = rows, error = error) }

                // §3.4 — the hero before the first card focus: resume, else next up, else latest.
                // Set directly, not through the debounce: an opening screen should not be empty for
                // 320ms. A focus that lands during the load still wins, because it arrives later.
                val default = resumeItems.firstOrNull() ?: nextUpItems.firstOrNull() ?: firstLatestItem
                if (default != null && _hero.value == null) {
                    _hero.value = default.toHeroUi()
                }
            }
        }
    }

    /**
     * §5 — the hero pairs its backdrop URL with the blurhash of the **same** tag that won the
     * fallback chain, mirroring `ItemMapping`'s rule: a hash from a tag we did not request would
     * blur up in the wrong colour.
     */
    private fun BaseItemDto.toHeroUi(): HeroUi {
        val backdropTag = backdropImageTags.firstOrNull()
            ?: parentBackdropImageTags.firstOrNull()?.takeIf { parentBackdropItemId != null }
        val resume = userData?.playbackPositionTicks ?: 0L
        return HeroUi(
            itemId = id,
            title = name.orEmpty(),
            logoUrl = imageUrlBuilder.logoWithParentFallback(this, HERO_LOGO_MAX_WIDTH),
            backdropUrl = imageUrlBuilder.backdropWithParentFallback(this, HERO_BACKDROP_MAX_WIDTH),
            backdropBlurHash = imageBlurHashes.blurHash("Backdrop", backdropTag),
            specLine = toSpecLine(),
            overview = overview?.takeIf { it.isNotBlank() },
            resumeTicks = resume,
            canResume = resume > 0L,
        )
    }
}
