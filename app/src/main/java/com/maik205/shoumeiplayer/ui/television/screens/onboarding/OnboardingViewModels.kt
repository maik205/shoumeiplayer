package com.maik205.shoumeiplayer.ui.television.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.result.ApiError
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.JellyfinDiscoveryRepository
import com.maik205.shoumeiplayer.data.session.RememberedServer
import com.maik205.shoumeiplayer.data.session.normalizeServerUrl
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ConnectEvent {
    data class Connected(val resumeSession: Boolean) : ConnectEvent
}

class ConnectViewModel(
    private val auth: AuthRepository,
    private val discovery: JellyfinDiscoveryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ConnectEvent>()
    val events: SharedFlow<ConnectEvent> = _events.asSharedFlow()

    private var discoveredServers: List<ServerChoiceUi> = emptyList()
    private var rememberedServers: List<RememberedServer> = emptyList()
    private var activeServerId: String? = null

    init {
        viewModelScope.launch {
            combine(auth.rememberedServers, auth.activeServer) { remembered, active ->
                remembered to active?.id
            }.collect { (remembered, activeId) ->
                rememberedServers = remembered
                activeServerId = activeId
                publishServers()
            }
        }
        refresh()
    }

    fun setAddress(value: String) {
        _state.update {
            it.copy(
                address = value
                    .trim()
                    .removePrefix("https://")
                    .removePrefix("http://"),
                error = null,
            )
        }
    }

    fun refresh() {
        if (_state.value.discovering && _state.value.servers.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(discovering = true, error = null) }
            discoveredServers = runCatching { discovery.discover() }
                .getOrDefault(emptyList())
                .map {
                    ServerChoiceUi(
                        id = it.id ?: it.address,
                        name = it.name?.takeIf(String::isNotBlank) ?: "Jellyfin",
                        address = it.address,
                    )
                }
            publishServers(discovering = false)
        }
    }

    fun connectSelected(server: ServerChoiceUi) {
        connect(listOf(server.address))
    }

    fun connectManual() {
        val raw = _state.value.address.trim()
        if (raw.isBlank()) {
            _state.update { it.copy(error = UiText.Resource(R.string.tv_enter_server_address)) }
            return
        }
        connect(
            if (raw.contains("://")) {
                listOf(raw)
            } else {
                listOf("https://$raw", "http://$raw")
            },
        )
    }

    private fun connect(addresses: List<String>) {
        if (_state.value.connecting) return
        viewModelScope.launch {
            _state.update { it.copy(connecting = true, error = null) }
            var lastFailure: ApiResult.Failure? = null
            for (address in addresses.distinct()) {
                when (val result = auth.validateServer(address)) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(connecting = false) }
                        _events.emit(
                            ConnectEvent.Connected(
                                resumeSession = auth.hasActiveSession(),
                            ),
                        )
                        return@launch
                    }

                    is ApiResult.Failure -> lastFailure = result
                }
            }
            if (lastFailure != null) {
                _state.update {
                    it.copy(
                        connecting = false,
                        error = connectionMessage(),
                    )
                }
            }
        }
    }

    private fun publishServers(discovering: Boolean = _state.value.discovering) {
        val remembered = rememberedServers.map { server ->
            ServerChoiceUi(
                id = server.id,
                name = server.name,
                address = server.url,
                remembered = true,
                active = server.id == activeServerId,
                hasSession = server.hasSession,
                userName = server.userName,
            )
        }
        val knownIds = remembered.mapTo(mutableSetOf()) { it.id }
        val knownUrls = remembered.mapTo(mutableSetOf()) { normalizeServerUrl(it.address) }
        val nearbyOnly = discoveredServers.filterNot {
            it.id in knownIds || normalizeServerUrl(it.address) in knownUrls
        }
        _state.update {
            it.copy(
                discovering = discovering,
                servers = remembered + nearbyOnly,
            )
        }
    }
}

sealed interface ProfilesEvent {
    data object Home : ProfilesEvent
    data class Login(val userName: String) : ProfilesEvent
}

class ProfilesViewModel(
    private val auth: AuthRepository,
    private val images: ImageUrlBuilder,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ProfilesEvent>()
    val events: SharedFlow<ProfilesEvent> = _events.asSharedFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val activeUserId = auth.activeUserId()
            when (val result = auth.accountSwitcherUsers()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.toUiText())
                }

                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        profiles = result.data.map { user ->
                            ProfileUi(
                                id = user.id,
                                name = user.name.orEmpty(),
                                imageUrl = user.primaryImageTag?.let { tag ->
                                    images.userPrimary(user.id, tag)
                                },
                                hasPassword = user.hasPassword || user.hasConfiguredPassword,
                                active = user.id == activeUserId,
                            )
                        },
                    )
                }
            }
        }
    }

    fun choose(profile: ProfileUi) {
        if (profile.active) {
            viewModelScope.launch { _events.emit(ProfilesEvent.Home) }
            return
        }
        if (profile.hasPassword) {
            viewModelScope.launch { _events.emit(ProfilesEvent.Login(profile.name)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = auth.login(profile.name, "")) {
                is ApiResult.Success -> _events.emit(ProfilesEvent.Home)
                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false) }
                    _events.emit(ProfilesEvent.Login(profile.name))
                }
            }
        }
    }

    fun useAnotherAccount() {
        viewModelScope.launch { _events.emit(ProfilesEvent.Login("")) }
    }
}

sealed interface LoginEvent {
    data object Home : LoginEvent
    data object AccountLocked : LoginEvent
}

class TelevisionLoginViewModel(
    private val auth: AuthRepository,
    initialUserName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState(userName = initialUserName))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    private var quickConnectSecret: String? = null
    private var quickConnectJob: Job? = null

    init {
        viewModelScope.launch {
            val enabled = (auth.quickConnectEnabled() as? ApiResult.Success)?.data == true
            _state.update { it.copy(quickConnectAvailable = enabled) }
        }
    }

    fun setUserName(value: String) {
        _state.update { it.copy(userName = value, error = null) }
    }

    fun setPassword(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun signIn() {
        val snapshot = _state.value
        if (snapshot.userName.isBlank() || snapshot.signingIn) return
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, error = null) }
            when (val result = auth.login(snapshot.userName.trim(), snapshot.password)) {
                is ApiResult.Success -> _events.emit(LoginEvent.Home)
                is ApiResult.Failure -> {
                    val error = result.error
                    if (error is ApiError.Http && error.code == 403) {
                        _state.update { it.copy(signingIn = false) }
                        _events.emit(LoginEvent.AccountLocked)
                    } else {
                        _state.update {
                            it.copy(
                                signingIn = false,
                                error = error.toUiText(),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * The button is deliberately regenerative: pressing it with a visible code cancels that poll
     * and requests a fresh Jellyfin Quick Connect challenge.
     */
    fun generateQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectSecret = null
        quickConnectJob = viewModelScope.launch {
            _state.update {
                it.copy(quickConnectLoading = true, quickConnectCode = null, error = null)
            }
            when (val initiated = auth.initiateQuickConnect()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        quickConnectLoading = false,
                        error = initiated.error.toUiText(),
                    )
                }

                is ApiResult.Success -> {
                    val secret = initiated.data.secret
                    val code = initiated.data.code
                    if (secret.isNullOrBlank() || code.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                quickConnectLoading = false,
                                error = UiText.Resource(R.string.tv_quick_connect_code_missing),
                            )
                        }
                        return@launch
                    }
                    quickConnectSecret = secret
                    _state.update {
                        it.copy(quickConnectLoading = false, quickConnectCode = code)
                    }
                    pollQuickConnect(secret)
                }
            }
        }
    }

    private suspend fun pollQuickConnect(secret: String) {
        while (viewModelScope.isActive && quickConnectSecret == secret) {
            delay(QUICK_CONNECT_POLL_MS)
            when (val state = auth.pollQuickConnect(secret)) {
                is ApiResult.Failure -> Unit
                is ApiResult.Success -> if (state.data.authenticated) {
                    when (val authenticated = auth.authenticateWithQuickConnect(secret)) {
                        is ApiResult.Success -> {
                            quickConnectSecret = null
                            _events.emit(LoginEvent.Home)
                            return
                        }

                        is ApiResult.Failure -> {
                            _state.update {
                                it.copy(error = authenticated.error.toUiText())
                            }
                            return
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        quickConnectJob?.cancel()
    }

    private companion object {
        const val QUICK_CONNECT_POLL_MS = 2_000L
    }
}

class TelevisionRecoveryViewModel(
    private val auth: AuthRepository,
    initialUserName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(RecoveryUiState(userName = initialUserName))
    val state: StateFlow<RecoveryUiState> = _state.asStateFlow()

    fun setUserName(value: String) {
        _state.update { it.copy(userName = value, result = null, error = null) }
    }

    fun requestReset() {
        val userName = _state.value.userName.trim()
        if (userName.isBlank() || _state.value.requesting) return
        viewModelScope.launch {
            _state.update { it.copy(requesting = true, result = null, error = null) }
            when (val response = auth.forgotPassword(userName)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        requesting = false,
                        error = response.error.toUiText(),
                    )
                }

                is ApiResult.Success -> {
                    val message = UiText.Resource(
                        when (response.data.action) {
                            "PinCode" -> R.string.tv_recovery_pin
                            "InNetworkRequired" -> R.string.tv_recovery_local_network
                            else -> R.string.tv_recovery_admin
                        },
                    )
                    _state.update { it.copy(requesting = false, result = message) }
                }
            }
        }
    }
}

private val connectionMessages = intArrayOf(
    R.string.tv_connection_quiet,
    R.string.tv_connection_media_room,
    R.string.tv_connection_no_answer,
    R.string.tv_connection_no_signal,
    R.string.tv_connection_slipped_away,
    R.string.tv_connection_nowhere,
    R.string.tv_connection_asleep,
    R.string.tv_connection_port,
    R.string.tv_connection_not_found,
    R.string.tv_connection_out_of_reach,
    R.string.tv_connection_closed,
    R.string.tv_connection_handshake,
    R.string.tv_connection_timed_out,
    R.string.tv_connection_off_air,
    R.string.tv_connection_library,
    R.string.tv_connection_path,
    R.string.tv_connection_not_ready,
    R.string.tv_connection_address,
    R.string.tv_connection_signal_faded,
    R.string.tv_connection_session,
)

private fun connectionMessage(): UiText {
    val index = (System.nanoTime() and Long.MAX_VALUE).rem(connectionMessages.size).toInt()
    return UiText.Resource(connectionMessages[index])
}
