package com.maik205.shoumeiplayer.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.SearchHintDto
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.ui.screens.library.GridTileUi
import com.maik205.shoumeiplayer.ui.screens.library.toGridTile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 350L
const val MIN_QUERY_LENGTH = 2

/** §5.6 — the suggested row's own request budget; independent of the search debounce pipeline. */
private const val SUGGESTIONS_LIMIT = 20

/** Search hints carry no aspect ratio worth trusting once cropped into the 2:3 tile frame. */
private const val SUGGESTION_IMAGE_WIDTH = 320

data class SearchUiState(
    val results: List<GridTileUi> = emptyList(),
    val loading: Boolean = false,
    /**
     * §5.6 — true from the keystroke until the debounced request resolves; the grid renders
     * skeleton tiles for that window. There is no searching label and no spinner.
     */
    val searching: Boolean = false,
    val error: String? = null,
)

// `debounce` is @FlowPreview and `flatMapLatest` is @ExperimentalCoroutinesApi; both are stable in
// practice and central to §5.6's debounced search, so opt in once here rather than at each call.
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val libraryRepository: LibraryRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /**
     * §5.6 — the "Suggested" row shown while the query is blank. `Search/Hints` with an empty
     * term is what the keyboard grid needs before the viewer has typed anything; if the server
     * rejects a blank hint term this falls back to the same resume list Home's Continue watching
     * row draws from, so the row is never empty just because the hint endpoint is fussy.
     */
    private val _suggestions = MutableStateFlow<List<GridTileUi>>(emptyList())
    val suggestions: StateFlow<List<GridTileUi>> = _suggestions.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { term -> searchFlow(term) }
                .collect { state -> _uiState.value = state }
        }
        loadSuggestions()
    }

    private fun loadSuggestions() {
        viewModelScope.launch {
            when (
                val hints = libraryRepository.searchHints(
                    term = "",
                    limit = SUGGESTIONS_LIMIT,
                )
            ) {
                is ApiResult.Success -> _suggestions.value = hints.data.map {
                    it.toGridTile(imageUrlBuilder)
                }

                is ApiResult.Failure -> when (val resume = libraryRepository.resumeItems()) {
                    is ApiResult.Success -> _suggestions.value = resume.data.map {
                        it.toGridTile(images = imageUrlBuilder)
                    }
                    // Both requests failed: the suggested row simply stays empty rather than
                    // surfacing a second error state next to the keyboard.
                    is ApiResult.Failure -> Unit
                }
            }
        }
    }

    /**
     * §5.6 — the error state ships one action, and this is it: re-issue the query already in the
     * field. The debounced pipeline is left alone; a retry is a one-shot request, not a keystroke.
     */
    fun retry() {
        val term = _query.value
        if (term.trim().length < MIN_QUERY_LENGTH) {
            _uiState.value = SearchUiState()
            return
        }
        viewModelScope.launch {
            searchFlow(term).collect { state -> _uiState.value = state }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        // Flip to searching on the keystroke — the debounce window is dead air otherwise.
        val longEnough = newQuery.trim().length >= MIN_QUERY_LENGTH
        _uiState.update { it.copy(searching = longEnough, error = if (longEnough) it.error else null) }
    }

    /** §3.3 — the on-screen keyboard drives the query through the pure [applyKey] reducer. */
    fun onKey(action: KeyAction) {
        onQueryChange(applyKey(_query.value, action))
    }

    private fun searchFlow(term: String) = if (term.trim().length < MIN_QUERY_LENGTH) {
        flowOf(SearchUiState())
    } else {
        flow {
            emit(SearchUiState(loading = true, searching = true))
            when (val result = libraryRepository.search(term.trim())) {
                is ApiResult.Success -> emit(
                    SearchUiState(
                        // §5.6 — episode hits are drawn from their thumb; a series poster next to
                        // an `Episode` label tells you nothing about which episode matched.
                        results = result.data.map {
                            it.toGridTile(
                                images = imageUrlBuilder,
                                typeLabel = it.typeLabel(),
                                preferEpisodeThumb = true,
                            )
                        },
                    ),
                )

                is ApiResult.Failure -> emit(SearchUiState(error = result.error.displayMessage))
            }
        }
    }
}

/** §5.6 — `Film` / `Series` / `Episode`, sentence case; mixed-type results are otherwise ambiguous. */
internal fun BaseItemDto.typeLabel(): String = typeLabelFor(type)

private fun typeLabelFor(type: String?): String = when (type) {
    "Movie" -> "Film"
    "Series" -> "Series"
    "Episode" -> "Episode"
    "Season" -> "Season"
    else -> type?.replaceFirstChar { it.uppercase() } ?: "Item"
}

/**
 * `SearchHintDto` is not a `BaseItemDto` (§3.6): it carries no `ImageBlurHashes` and no `UserData`,
 * so the mapped tile gets no blurhash and no progress bar/watched mark, by design.
 */
private fun SearchHintDto.toGridTile(images: ImageUrlBuilder): GridTileUi {
    val thumbUrl = if (type == "Episode") {
        images.thumb(thumbImageItemId ?: itemId, thumbImageTag, SUGGESTION_IMAGE_WIDTH)
    } else {
        null
    }
    val posterUrl = images.primary(itemId, primaryImageTag, SUGGESTION_IMAGE_WIDTH)
    return GridTileUi(
        id = itemId,
        title = name.orEmpty(),
        subtitle = when (type) {
            "Episode" -> "S${parentIndexNumber ?: 0}:E${indexNumber ?: 0}"
            else -> productionYear?.toString()
        },
        imageUrl = thumbUrl ?: posterUrl,
        progressFraction = null,
        watched = false,
        typeLabel = typeLabelFor(type),
        blurHash = null,
        aspect = null,
    )
}
