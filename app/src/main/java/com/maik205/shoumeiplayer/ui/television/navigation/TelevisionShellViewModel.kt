package com.maik205.shoumeiplayer.ui.television.navigation

import com.maik205.shoumeiplayer.R
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
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.toUiText
import kotlinx.coroutines.CancellationException
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
    val error: UiText? = null,
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
                        try {
                            val cached = if (settingsStore.current().cacheHomeContent) {
                                libraryCacheStore.read(session.serverUrl, session.userId)
                            } else {
                                emptyList()
                            }
                            cached.takeIf { it.isNotEmpty() }?.let {
                                if (sessionStore.current()?.userId == session.userId) {
                                    _state.update { state -> state.copy(libraries = it, loadingLibraries = true, error = null) }
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // A corrupt/unreadable cache is a cache miss. The network refresh
                            // below must still run so the shell cannot become inert.
                        }
                        refreshLibraries()
                    }
                    profileLoad?.cancel()
                    profileLoad = viewModelScope.launch {
                        try {
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
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // Keep the session's persisted profile label when profile hydration fails.
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
            try {
                when (val result = mediaCatalog.libraries()) {
                    is ApiResult.Failure -> {
                        if (sessionStore.current()?.let { it.serverUrl == requestSession.serverUrl && it.userId == requestSession.userId } == true) {
                            _state.update {
                                it.copy(
                                    loadingLibraries = false,
                                    error = result.error.toUiText(),
                                )
                            }
                        }
                    }

                    is ApiResult.Success -> {
                        val libraries = result.data
                        if (sessionStore.current()?.let { it.serverUrl == requestSession.serverUrl && it.userId == requestSession.userId } == true) {
                            _state.update { it.copy(loadingLibraries = false, libraries = libraries) }
                            try {
                                if (settingsStore.current().cacheHomeContent) {
                                    libraryCacheStore.write(requestSession.serverUrl, requestSession.userId, libraries)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                // Caching is best-effort; a successful library refresh remains successful.
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (sessionStore.current()?.let { it.serverUrl == requestSession.serverUrl && it.userId == requestSession.userId } == true) {
                    _state.update {
                        it.copy(
                            loadingLibraries = false,
                            error = UiText.Resource(R.string.tv_libraries_refresh_failed),
                        )
                    }
                }
            }
        }
    }
}
