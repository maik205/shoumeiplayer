package com.maik205.shoumeiplayer.ui.television.screens.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.ui.television.model.MediaItemUi
import com.maik205.shoumeiplayer.ui.television.model.toTelevisionUi
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
    private val repository: LibraryRepository,
    private val images: ImageUrlBuilder,
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
        when (val result = repository.searchAllMedia(term, 100)) {
            is ApiResult.Failure -> _state.update {
                it.copy(searching = false, error = result.error.displayMessage)
            }

            is ApiResult.Success -> _state.update {
                it.copy(
                    searching = false,
                    results = result.data.map { item -> item.toTelevisionUi(images) },
                    error = null,
                )
            }
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
