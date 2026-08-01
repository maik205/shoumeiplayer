package com.maik205.shoumeiplayer.ui.television.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.cache.LibraryCacheStore
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.domain.repository.MediaCatalog
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
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
    private val mediaCatalog: MediaCatalog,
    private val libraryCacheStore: LibraryCacheStore,
    private val settingsStore: SettingsStore,
    private val authRepository: AuthRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
) : ViewModel() {
    private val _state = MutableStateFlow(TelevisionShellState())
    val state: StateFlow<TelevisionShellState> = _state.asStateFlow()

    private var libraryLoad: Job? = null
    private var libraryRestore: Job? = null
    private var profileLoad: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.session.collectLatest { session ->
                if (session == null) {
                    libraryLoad?.cancel()
                    libraryRestore?.cancel()
                    profileLoad?.cancel()
                    _state.value = TelevisionShellState()
                } else {
                    libraryLoad?.cancel()
                    _state.update {
                        it.copy(
                            userName = session.userName,
                            avatarUrl = null,
                        )
                    }
                    libraryRestore?.cancel()
                    libraryRestore = viewModelScope.launch {
                        if (settingsStore.current().cacheHomeContent) {
                            libraryCacheStore.read(session.serverUrl, session.userId)
                        } else {
                            emptyList()
                        }
                            .takeIf { it.isNotEmpty() }
                            ?.let { cached ->
                                if (sessionStore.current()?.userId == session.userId) {
                                    _state.update { it.copy(libraries = cached, loadingLibraries = true, error = null) }
                                }
                            }
                        refreshLibraries()
                    }
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
            val requestSession = sessionStore.current() ?: return@launch
            _state.update { it.copy(loadingLibraries = true, error = null) }
            when (val result = mediaCatalog.libraries()) {
                is ApiResult.Failure -> {
                    if (sessionStore.current()?.let { it.serverUrl == requestSession.serverUrl && it.userId == requestSession.userId } == true) {
                        _state.update {
                            it.copy(
                                loadingLibraries = false,
                                error = result.error.displayMessage,
                            )
                        }
                    }
                }

                is ApiResult.Success -> {
                    val libraries = result.data
                    if (sessionStore.current()?.let { it.serverUrl == requestSession.serverUrl && it.userId == requestSession.userId } == true) {
                        _state.update { it.copy(loadingLibraries = false, libraries = libraries) }
                        if (settingsStore.current().cacheHomeContent) {
                            libraryCacheStore.write(requestSession.serverUrl, requestSession.userId, libraries)
                        }
                    }
                }
            }
        }
    }
}
