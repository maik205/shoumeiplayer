package com.maik205.shoumeiplayer.ui.screens.serverentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ServerEntryUiState(
    val url: String = "",
    val loading: Boolean = false,
    val serverName: String? = null,
    /** §5.5 — the success line names the version when the server reported one, and omits it otherwise. */
    val serverVersion: String? = null,
    val error: String? = null,
)

class ServerEntryViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerEntryUiState())
    val uiState: StateFlow<ServerEntryUiState> = _uiState.asStateFlow()

    private val _navigateToLogin = Channel<Unit>(Channel.BUFFERED)
    val navigateToLogin = _navigateToLogin.receiveAsFlow()

    fun onUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(url = url, error = null)
    }

    fun connect() {
        val url = _uiState.value.url
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter a server URL")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = authRepository.validateServer(url)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        serverName = result.data.serverName,
                        serverVersion = result.data.version,
                        error = null,
                    )
                    _navigateToLogin.trySend(Unit)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = result.error.displayMessage,
                    )
                }
            }
        }
    }
}
