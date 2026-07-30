package com.maik205.shoumeiplayer.ui.television.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.ui.television.model.LibraryDestinationUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TelevisionShellState(
    val userName: String = "",
    val avatarUrl: String? = null,
    val libraries: List<LibraryDestinationUi> = emptyList(),
    val loadingLibraries: Boolean = false,
    val error: String? = null,
)

/**
 * Process-level chrome data shared by every authenticated destination. The shell owns no
 * navigation state; it only keeps the current Jellyfin profile and available libraries live.
 */
class TelevisionShellViewModel(
    private val sessionStore: SessionStore,
    private val libraryRepository: LibraryRepository,
    private val authRepository: AuthRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
) : ViewModel() {
    private val _state = MutableStateFlow(TelevisionShellState())
    val state: StateFlow<TelevisionShellState> = _state.asStateFlow()

    private var libraryLoad: Job? = null
    private var profileLoad: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.session.collectLatest { session ->
                if (session == null) {
                    libraryLoad?.cancel()
                    profileLoad?.cancel()
                    _state.value = TelevisionShellState()
                } else {
                    _state.update {
                        it.copy(
                            userName = session.userName,
                            avatarUrl = null,
                        )
                    }
                    refreshLibraries()
                    profileLoad?.cancel()
                    profileLoad = viewModelScope.launch {
                        val user = authRepository.currentUser()
                        if (sessionStore.current()?.userId == session.userId) {
                            _state.update {
                                it.copy(
                                    userName = user?.name ?: session.userName,
                                    avatarUrl = user?.primaryImageTag?.let { tag ->
                                        imageUrlBuilder.userPrimary(session.userId, tag)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun refreshLibraries() {
        if (libraryLoad?.isActive == true) return
        libraryLoad = viewModelScope.launch {
            _state.update { it.copy(loadingLibraries = true, error = null) }
            when (val result = libraryRepository.userViews()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loadingLibraries = false,
                        error = result.error.displayMessage,
                    )
                }

                is ApiResult.Success -> _state.update {
                    it.copy(
                        loadingLibraries = false,
                        libraries = result.data
                            .filterNot { view ->
                                view.collectionType.equals("photos", ignoreCase = true)
                            }
                            .map { view ->
                                LibraryDestinationUi(
                                    id = view.id,
                                    title = view.name.orEmpty(),
                                    collectionType = view.collectionType,
                                )
                            },
                    )
                }
            }
        }
    }
}
