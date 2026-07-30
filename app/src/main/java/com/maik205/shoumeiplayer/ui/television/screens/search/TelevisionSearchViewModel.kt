package com.maik205.shoumeiplayer.ui.television.screens.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.domain.repository.MediaCatalog
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TelevisionSearchState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<MediaItemUi> = emptyList(),
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class TelevisionSearchViewModel(
    private val catalog: MediaCatalog,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(TelevisionSearchState())
    val state: StateFlow<TelevisionSearchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            query
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest(::search)
        }
    }

    fun setQuery(value: String) {
        query.value = value
        _state.update {
            it.copy(
                query = value,
                searching = value.trim().length >= MIN_QUERY_LENGTH,
                error = null,
                results = if (value.isBlank()) emptyList() else it.results,
            )
        }
    }

    fun retry() {
        viewModelScope.launch { search(query.value) }
    }

    private suspend fun search(raw: String) {
        val term = raw.trim()
        if (term.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(searching = false, results = emptyList(), error = null) }
            return
        }

        _state.update { it.copy(searching = true, error = null) }
        // A TV search only renders a single viewport plus a small scroll-ahead window. Keep the
        // first response bounded so JSON mapping and artwork work do not scale with the total
        // number of matches; pagination can request more results explicitly later.
        when (val result = catalog.search(term, SEARCH_PAGE_SIZE)) {
            is ApiResult.Failure -> _state.update {
                it.copy(searching = false, error = result.error.displayMessage)
            }

            is ApiResult.Success -> _state.update {
                it.copy(
                    searching = false,
                    results = result.data,
                    error = null,
                )
            }
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_PAGE_SIZE = 40
    }
}
