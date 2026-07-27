package com.maik205.shoumeiplayer.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigateToHome = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToHome = _navigateToHome.asSharedFlow()

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.username.isBlank()) {
            _uiState.value = state.copy(error = "Enter a username")
            return
        }
        if (state.loading) return

        _uiState.value = state.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = authRepository.login(state.username, state.password)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(loading = false, error = null)
                    _navigateToHome.emit(Unit)
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
