package com.maik205.shoumeiplayer.ui.television.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.cache.ArtworkCache
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withContext

@Immutable
data class TelevisionSettingsState(
    val settings: ClientSettings = ClientSettings(),
    val capabilities: DevicePlaybackCapabilities = DevicePlaybackCapabilities(),
    val userName: String = "",
    val serverUrl: String = "",
    val serverName: String? = null,
    val serverVersion: String? = null,
    val testingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val quickConnectLoading: Boolean = false,
    val quickConnectCode: String? = null,
    val artworkCacheSize: String = "Calculating…",
    val clearingArtworkCache: Boolean = false,
)

sealed interface TelevisionSettingsEvent {
    data object Profiles : TelevisionSettingsEvent
    data object Connect : TelevisionSettingsEvent
    data class CacheMessage(val message: String) : TelevisionSettingsEvent
}

private data class TelevisionSettingsSupplement(
    val serverName: String? = null,
    val serverVersion: String? = null,
    val testingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val quickConnectLoading: Boolean = false,
    val quickConnectCode: String? = null,
    val artworkCacheSize: String = "Calculating…",
    val clearingArtworkCache: Boolean = false,
)

class TelevisionSettingsViewModel(
    private val settingsStore: SettingsStore,
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
    private val artworkCache: ArtworkCache,
    private val capabilities: DevicePlaybackCapabilities = DevicePlaybackCapabilities(),
) : ViewModel() {
    private val supplement = MutableStateFlow(TelevisionSettingsSupplement())
    private var quickConnectJob: Job? = null
    private var quickConnectSecret: String? = null

    val state: StateFlow<TelevisionSettingsState> = combine(
        settingsStore.settings,
        sessionStore.session,
        sessionStore.serverUrl,
        supplement,
    ) { settings, session, serverUrl, supplement ->
        TelevisionSettingsState(
            settings = settings,
            capabilities = capabilities,
            userName = session?.userName.orEmpty(),
            serverUrl = serverUrl.orEmpty(),
            serverName = supplement.serverName,
            serverVersion = supplement.serverVersion,
            testingConnection = supplement.testingConnection,
            connectionMessage = supplement.connectionMessage,
            quickConnectLoading = supplement.quickConnectLoading,
            quickConnectCode = supplement.quickConnectCode,
            artworkCacheSize = supplement.artworkCacheSize,
            clearingArtworkCache = supplement.clearingArtworkCache,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TelevisionSettingsState(capabilities = capabilities),
    )

    private val _events = MutableSharedFlow<TelevisionSettingsEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.retryPendingTokenRevocation()
        }
        refreshArtworkCacheSize()
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

    fun clearArtworkCache() {
        if (supplement.value.clearingArtworkCache) return
        viewModelScope.launch {
            supplement.update { it.copy(clearingArtworkCache = true) }
            runCatching {
                withContext(Dispatchers.IO) { artworkCache.clear() }
            }.onSuccess { clearedBytes ->
                supplement.update {
                    it.copy(
                        artworkCacheSize = formatCacheSize(0L),
                        clearingArtworkCache = false,
                    )
                }
                _events.emit(
                    TelevisionSettingsEvent.CacheMessage(
                        if (clearedBytes > 0) {
                            "${formatCacheSize(clearedBytes)} of artwork cache cleared"
                        } else {
                            "Artwork cache is already empty"
                        },
                    ),
                )
            }.onFailure {
                supplement.update { it.copy(clearingArtworkCache = false) }
                _events.emit(TelevisionSettingsEvent.CacheMessage("Couldn't clear artwork cache"))
                refreshArtworkCacheSize()
            }
        }
    }

    private fun refreshArtworkCacheSize() {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { artworkCache.estimatedSizeBytes() }
            supplement.update { it.copy(artworkCacheSize = formatCacheSize(bytes)) }
        }
    }

    private fun formatCacheSize(bytes: Long): String = when {
        bytes < 1024L -> if (bytes == 0L) "Empty" else "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KiB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
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
        quickConnectJob?.cancel()
        quickConnectSecret = null
        quickConnectJob = viewModelScope.launch {
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

                is ApiResult.Success -> {
                    val secret = result.data.secret?.takeIf(String::isNotBlank)
                    val code = result.data.code?.takeIf(String::isNotBlank)
                    if (secret == null || code == null) {
                        supplement.update {
                            it.copy(
                                quickConnectLoading = false,
                                quickConnectCode = "Unavailable",
                            )
                        }
                        return@launch
                    }
                    quickConnectSecret = secret
                    supplement.update {
                        it.copy(
                            quickConnectLoading = false,
                            quickConnectCode = code,
                        )
                    }
                    pollQuickConnectReplacement(secret)
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
            _events.emit(TelevisionSettingsEvent.Connect)
        }
    }

    fun forgetServer() {
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

    private suspend fun pollQuickConnectReplacement(secret: String) {
        repeat(QUICK_CONNECT_MAX_POLLS) {
            if (quickConnectSecret != secret) return
            delay(QUICK_CONNECT_POLL_MS)
            when (val state = authRepository.pollQuickConnect(secret)) {
                is ApiResult.Failure -> Unit
                is ApiResult.Success -> if (state.data.authenticated) {
                    val message = when (
                        val replacement = authRepository.replaceSessionWithQuickConnect(secret)
                    ) {
                        is ApiResult.Failure -> replacement.error.displayMessage
                        is ApiResult.Success -> when {
                            !replacement.data.tokenChanged -> "Session verified"
                            replacement.data.oldTokenRevoked -> "Session replaced"
                            else -> "Replaced · revocation pending"
                        }
                    }
                    quickConnectSecret = null
                    supplement.update {
                        it.copy(
                            quickConnectLoading = false,
                            quickConnectCode = message,
                        )
                    }
                    return
                }
            }
        }
        if (quickConnectSecret == secret) {
            quickConnectSecret = null
            supplement.update {
                it.copy(
                    quickConnectLoading = false,
                    quickConnectCode = "Timed out · Try again",
                )
            }
        }
    }

    override fun onCleared() {
        quickConnectJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val QUICK_CONNECT_POLL_MS = 2_000L
        const val QUICK_CONNECT_MAX_POLLS = 45
    }
}
