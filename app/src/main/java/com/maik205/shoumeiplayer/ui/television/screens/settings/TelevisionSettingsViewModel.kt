package com.maik205.shoumeiplayer.ui.television.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.session.ClientSettings
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TelevisionSettingsState(
    val settings: ClientSettings = ClientSettings(),
    val userName: String = "",
    val serverUrl: String = "",
    val serverName: String? = null,
    val serverVersion: String? = null,
    val testingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val quickConnectLoading: Boolean = false,
    val quickConnectCode: String? = null,
)

sealed interface TelevisionSettingsEvent {
    data object Profiles : TelevisionSettingsEvent
    data object Connect : TelevisionSettingsEvent
}

private data class TelevisionSettingsSupplement(
    val serverName: String? = null,
    val serverVersion: String? = null,
    val testingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val quickConnectLoading: Boolean = false,
    val quickConnectCode: String? = null,
)

class TelevisionSettingsViewModel(
    private val settingsStore: SettingsStore,
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val supplement = MutableStateFlow(TelevisionSettingsSupplement())

    val state: StateFlow<TelevisionSettingsState> = combine(
        settingsStore.settings,
        sessionStore.session,
        sessionStore.serverUrl,
        supplement,
    ) { settings, session, serverUrl, supplement ->
        TelevisionSettingsState(
            settings = settings,
            userName = session?.userName.orEmpty(),
            serverUrl = serverUrl.orEmpty(),
            serverName = supplement.serverName,
            serverVersion = supplement.serverVersion,
            testingConnection = supplement.testingConnection,
            connectionMessage = supplement.connectionMessage,
            quickConnectLoading = supplement.quickConnectLoading,
            quickConnectCode = supplement.quickConnectCode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TelevisionSettingsState(),
    )

    private val _events = MutableSharedFlow<TelevisionSettingsEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            sessionStore.serverUrl
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { probeServer(it, announceResult = false) }
        }
    }

    fun update(transform: (ClientSettings) -> ClientSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    fun testConnection() {
        viewModelScope.launch {
            val serverUrl = sessionStore.serverUrlOrNull()
            if (serverUrl == null) {
                supplement.update { it.copy(connectionMessage = "Not connected") }
            } else {
                probeServer(serverUrl, announceResult = true)
            }
        }
    }

    fun generateQuickConnect() {
        if (supplement.value.quickConnectLoading) return
        viewModelScope.launch {
            supplement.update {
                it.copy(
                    quickConnectLoading = true,
                    quickConnectCode = null,
                )
            }
            when (val result = authRepository.initiateQuickConnect()) {
                is ApiResult.Failure -> supplement.update {
                    it.copy(
                        quickConnectLoading = false,
                        quickConnectCode = result.error.displayMessage,
                    )
                }

                is ApiResult.Success -> supplement.update {
                    it.copy(
                        quickConnectLoading = false,
                        quickConnectCode = result.data.code ?: "Unavailable",
                    )
                }
            }
        }
    }

    fun switchProfile() {
        viewModelScope.launch {
            _events.emit(TelevisionSettingsEvent.Profiles)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(TelevisionSettingsEvent.Profiles)
        }
    }

    fun changeServer() {
        viewModelScope.launch {
            authRepository.forgetServer()
            _events.emit(TelevisionSettingsEvent.Connect)
        }
    }

    private suspend fun probeServer(
        serverUrl: String,
        announceResult: Boolean,
    ) {
        supplement.update {
            it.copy(
                testingConnection = true,
                connectionMessage = if (announceResult) null else it.connectionMessage,
            )
        }
        when (val result = authRepository.validateServer(serverUrl)) {
            is ApiResult.Failure -> supplement.update {
                it.copy(
                    serverName = null,
                    serverVersion = null,
                    testingConnection = false,
                    connectionMessage = if (announceResult) result.error.displayMessage else null,
                )
            }

            is ApiResult.Success -> supplement.update {
                it.copy(
                    serverName = result.data.serverName,
                    serverVersion = result.data.version,
                    testingConnection = false,
                    connectionMessage = if (announceResult) "Connected" else null,
                )
            }
        }
    }
}
