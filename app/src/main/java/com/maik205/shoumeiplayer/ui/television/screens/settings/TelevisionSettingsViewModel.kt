package com.maik205.shoumeiplayer.ui.television.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.api.dto.UserConfigurationDto
import com.maik205.shoumeiplayer.data.cache.ArtworkCache
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import com.maik205.shoumeiplayer.domain.settings.ServerLanguage
import com.maik205.shoumeiplayer.domain.settings.SubtitleMode
import com.maik205.shoumeiplayer.domain.settings.storedOption
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.toUiText
import kotlinx.coroutines.CancellationException
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

/**
 * The Jellyfin account preferences the settings screen edits, in UI terms.
 *
 * These live on the server, not in [ClientSettings]: the same values drive the Jellyfin web client
 * and every other device on the account, and they are what `TrackSelection` actually uses to pick a
 * track. A null language is Jellyfin's real "no preference" state, not missing data.
 */
@Immutable
data class ServerPreferences(
    /**
     * The account's raw `AudioLanguagePreference`, not a parsed enum. Jellyfin accepts any ISO 639
     * code and other clients write ones this app has no label for, so the value is carried through
     * verbatim: [ServerLanguage] is only a display aid. Parsing here would turn every unrecognised
     * code into `null` and erase it from the account on the next unrelated edit.
     */
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.Default,
    val playDefaultAudioTrack: Boolean = true,
    val autoplayNextEpisode: Boolean = true,
)

/**
 * Write side for [ServerPreferences], handed to the row catalog inside the state so the screen
 * composable does not have to thread a second callback through.
 */
fun interface ServerPreferenceEditor {
    fun edit(transform: (ServerPreferences) -> ServerPreferences)
}

@Immutable
data class TelevisionSettingsState(
    val settings: ClientSettings = ClientSettings(),
    val serverPreferences: ServerPreferences = ServerPreferences(),
    val editServerPreferences: ServerPreferenceEditor = ServerPreferenceEditor {},
    val capabilities: DevicePlaybackCapabilities = DevicePlaybackCapabilities(),
    val userName: String = "",
    val serverUrl: String = "",
    val serverName: String? = null,
    val serverVersion: String? = null,
    val testingConnection: Boolean = false,
    val connectionMessage: UiText? = null,
    val quickConnectLoading: Boolean = false,
    val quickConnectCode: UiText? = null,
    val artworkCacheSize: UiText = UiText.Resource(R.string.tv_cache_calculating),
    val clearingArtworkCache: Boolean = false,
    val error: UiText? = null,
)

sealed interface TelevisionSettingsEvent {
    data object Profiles : TelevisionSettingsEvent
    data object Connect : TelevisionSettingsEvent
    data class CacheMessage(val message: UiText) : TelevisionSettingsEvent
}

private data class TelevisionSettingsSupplement(
    val serverName: String? = null,
    val serverVersion: String? = null,
    val testingConnection: Boolean = false,
    val connectionMessage: UiText? = null,
    val quickConnectLoading: Boolean = false,
    val quickConnectCode: UiText? = null,
    val artworkCacheSize: UiText = UiText.Resource(R.string.tv_cache_calculating),
    val clearingArtworkCache: Boolean = false,
    val error: UiText? = null,
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
    private var lastRetryAction: (() -> Unit)? = null

    /**
     * Stable across recompositions on purpose: [TelevisionSettingsState] is `@Immutable`, so a
     * fresh lambda on every emission would make every row look changed.
     */
    private val serverPreferenceEditor = ServerPreferenceEditor(::editServerPreferences)

    val state: StateFlow<TelevisionSettingsState> = combine(
        settingsStore.settings,
        authRepository.cachedUserConfiguration,
        sessionStore.session,
        sessionStore.serverUrl,
        supplement,
    ) { settings, configuration, session, serverUrl, supplement ->
        TelevisionSettingsState(
            settings = settings,
            serverPreferences = configuration.toServerPreferences(),
            editServerPreferences = serverPreferenceEditor,
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
            error = supplement.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TelevisionSettingsState(
            editServerPreferences = serverPreferenceEditor,
            capabilities = capabilities,
        ),
    )

    private val _events = MutableSharedFlow<TelevisionSettingsEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.retryPendingTokenRevocation()
        }
        viewModelScope.launch {
            // Opening Settings is the moment the account rows have to be right, so pull the live
            // document (which refreshes the local mirror the rows render from) and drain any
            // write-back an earlier offline edit left queued.
            runCatching { authRepository.refreshUserConfiguration() }
            runCatching { authRepository.retryPendingUserConfiguration() }
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
        lastRetryAction = { update(transform) }
        viewModelScope.launch {
            try {
                settingsStore.update(transform)
                supplement.update { it.copy(error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                supplement.update { it.copy(error = UiText.Resource(R.string.tv_settings_save_failed)) }
            }
        }
    }

    /**
     * Writes a server-owned preference back to the Jellyfin account.
     *
     * `AuthRepository.editUserConfiguration` moves the local mirror first, so the row shows the new
     * value straight away; a failure therefore is not "your change was lost" but "your change has
     * not reached the account yet", which is what the offline banner says. Retry replays the queued
     * write rather than re-deriving the transform, so it stays correct even after a process death.
     */
    private fun editServerPreferences(transform: (ServerPreferences) -> ServerPreferences) {
        lastRetryAction = ::retryPendingServerPreferences
        viewModelScope.launch {
            try {
                val result = authRepository.editUserConfiguration { configuration ->
                    transform(configuration.toServerPreferences()).applyTo(configuration)
                }
                supplement.update {
                    it.copy(
                        error = if (result is ApiResult.Failure) {
                            UiText.Resource(R.string.tv_settings_offline)
                        } else {
                            null
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                supplement.update { it.copy(error = UiText.Resource(R.string.tv_settings_offline)) }
            }
        }
    }

    private fun retryPendingServerPreferences() {
        viewModelScope.launch {
            val settled = runCatching { authRepository.retryPendingUserConfiguration() }
                .getOrDefault(false)
            supplement.update {
                it.copy(error = if (settled) null else UiText.Resource(R.string.tv_settings_offline))
            }
        }
    }

    fun retryLastUpdate() {
        lastRetryAction?.invoke()
    }

    fun clearArtworkCache() {
        if (supplement.value.clearingArtworkCache) return
        lastRetryAction = ::clearArtworkCache
        viewModelScope.launch {
            supplement.update { it.copy(clearingArtworkCache = true) }
            try {
                val clearedBytes = withContext(Dispatchers.IO) { artworkCache.clear() }
                supplement.update {
                    it.copy(
                        artworkCacheSize = formatCacheSize(0L),
                        clearingArtworkCache = false,
                        error = null,
                    )
                }
                _events.emit(
                    TelevisionSettingsEvent.CacheMessage(
                        if (clearedBytes > 0) {
                            UiText.Resource(
                                R.string.tv_cache_cleared,
                                listOf(formatCacheSize(clearedBytes).asDisplayValue()),
                            )
                        } else {
                            UiText.Resource(R.string.tv_cache_already_empty)
                        },
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                supplement.update {
                    it.copy(
                        clearingArtworkCache = false,
                        error = UiText.Resource(R.string.tv_cache_clear_failed),
                    )
                }
                _events.emit(
                    TelevisionSettingsEvent.CacheMessage(
                        UiText.Resource(R.string.tv_cache_clear_failed),
                    ),
                )
                refreshArtworkCacheSize()
            }
        }
    }

    private fun refreshArtworkCacheSize() {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { artworkCache.estimatedSizeBytes() }
                supplement.update { it.copy(artworkCacheSize = formatCacheSize(bytes)) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                supplement.update {
                    it.copy(artworkCacheSize = UiText.Resource(R.string.tv_cache_size_failed))
                }
            }
        }
    }

    private fun formatCacheSize(bytes: Long): UiText = when {
        bytes < 1024L -> if (bytes == 0L) {
            UiText.Resource(R.string.tv_cache_empty)
        } else {
            UiText.Resource(R.string.tv_cache_bytes, listOf(bytes))
        }
        bytes < 1024L * 1024L -> UiText.Resource(
            R.string.tv_cache_kib,
            listOf("%.1f".format(bytes / 1024.0)),
        )
        bytes < 1024L * 1024L * 1024L -> UiText.Resource(
            R.string.tv_cache_mib,
            listOf("%.1f".format(bytes / (1024.0 * 1024.0))),
        )
        else -> UiText.Resource(
            R.string.tv_cache_gib,
            listOf("%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))),
        )
    }

    private fun UiText.asDisplayValue(): String = when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> formatArgs.joinToString()
    }

    fun testConnection() {
        viewModelScope.launch {
            try {
                val serverUrl = sessionStore.serverUrlOrNull()
                if (serverUrl == null) {
                    supplement.update {
                        it.copy(
                            testingConnection = false,
                            connectionMessage = UiText.Resource(R.string.tv_not_connected),
                        )
                    }
                } else {
                    probeServer(serverUrl, announceResult = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                supplement.update {
                    it.copy(
                        testingConnection = false,
                        connectionMessage = UiText.Resource(R.string.tv_settings_connection_failed),
                    )
                }
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
            try {
                when (val result = authRepository.initiateQuickConnect()) {
                    is ApiResult.Failure -> supplement.update {
                        it.copy(
                            quickConnectLoading = false,
                            quickConnectCode = result.error.toUiText(),
                        )
                    }

                    is ApiResult.Success -> {
                        val secret = result.data.secret?.takeIf(String::isNotBlank)
                        val code = result.data.code?.takeIf(String::isNotBlank)
                        if (secret == null || code == null) {
                            supplement.update {
                                it.copy(
                                    quickConnectLoading = false,
                                    quickConnectCode = UiText.Resource(R.string.tv_quick_connect_unavailable),
                                )
                            }
                            return@launch
                        }
                        quickConnectSecret = secret
                        supplement.update {
                            it.copy(
                                quickConnectLoading = false,
                                quickConnectCode = UiText.Dynamic(code),
                            )
                        }
                        pollQuickConnectReplacement(secret)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                quickConnectSecret = null
                supplement.update {
                    it.copy(
                        quickConnectLoading = false,
                        quickConnectCode = UiText.Resource(R.string.tv_quick_connect_failed),
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
        lastRetryAction = ::signOut
        viewModelScope.launch {
            try {
                authRepository.logout()
                supplement.update { it.copy(error = null) }
                _events.emit(TelevisionSettingsEvent.Profiles)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                supplement.update {
                    it.copy(error = UiText.Resource(R.string.tv_settings_account_action_failed))
                }
            }
        }
    }

    fun changeServer() {
        viewModelScope.launch {
            _events.emit(TelevisionSettingsEvent.Connect)
        }
    }

    fun forgetServer() {
        lastRetryAction = ::forgetServer
        viewModelScope.launch {
            try {
                authRepository.forgetServer()
                supplement.update { it.copy(error = null) }
                _events.emit(TelevisionSettingsEvent.Connect)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                supplement.update {
                    it.copy(error = UiText.Resource(R.string.tv_settings_account_action_failed))
                }
            }
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
        try {
            when (val result = authRepository.validateServer(serverUrl)) {
                is ApiResult.Failure -> supplement.update {
                    it.copy(
                        serverName = null,
                        serverVersion = null,
                        testingConnection = false,
                        connectionMessage = if (announceResult) result.error.toUiText() else null,
                    )
                }

                is ApiResult.Success -> supplement.update {
                    it.copy(
                        serverName = result.data.serverName,
                        serverVersion = result.data.version,
                        testingConnection = false,
                        connectionMessage = if (announceResult) {
                            UiText.Resource(R.string.tv_connected)
                        } else {
                            null
                        },
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            supplement.update {
                it.copy(
                    serverName = null,
                    serverVersion = null,
                    testingConnection = false,
                    connectionMessage = if (announceResult) {
                        UiText.Resource(R.string.tv_settings_connection_failed)
                    } else {
                        null
                    },
                )
            }
        }
    }

    private suspend fun pollQuickConnectReplacement(secret: String) {
        try {
            repeat(QUICK_CONNECT_MAX_POLLS) {
                if (quickConnectSecret != secret) return
                delay(QUICK_CONNECT_POLL_MS)
                when (val state = authRepository.pollQuickConnect(secret)) {
                    is ApiResult.Failure -> {
                        quickConnectSecret = null
                        supplement.update {
                            it.copy(
                                quickConnectLoading = false,
                                quickConnectCode = state.error.toUiText(),
                            )
                        }
                        return
                    }
                    is ApiResult.Success -> if (state.data.authenticated) {
                        val message = when (
                            val replacement = authRepository.replaceSessionWithQuickConnect(secret)
                        ) {
                            is ApiResult.Failure -> replacement.error.toUiText()
                            is ApiResult.Success -> when {
                                !replacement.data.tokenChanged -> UiText.Resource(R.string.tv_session_verified)
                                replacement.data.oldTokenRevoked -> UiText.Resource(R.string.tv_session_replaced)
                                else -> UiText.Resource(R.string.tv_session_replaced_pending)
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
                        quickConnectCode = UiText.Resource(R.string.tv_quick_connect_timed_out),
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            quickConnectSecret = null
            supplement.update {
                it.copy(
                    quickConnectLoading = false,
                    quickConnectCode = UiText.Resource(R.string.tv_quick_connect_failed),
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

/**
 * Null means "no configuration for this account has ever reached this device" -- a first launch
 * that has not talked to the server yet. Jellyfin's own defaults are the honest thing to show
 * there, and they are exactly [UserConfigurationDto]'s defaults.
 */
internal fun UserConfigurationDto?.toServerPreferences(): ServerPreferences {
    val configuration = this ?: UserConfigurationDto()
    return ServerPreferences(
        audioLanguage = configuration.audioLanguagePreference,
        subtitleLanguage = configuration.subtitleLanguagePreference,
        subtitleMode = storedOption(
            configuration.subtitleMode,
            SubtitleMode.Default,
            SubtitleMode.entries.toTypedArray(),
        ),
        playDefaultAudioTrack = configuration.playDefaultAudioTrack,
        autoplayNextEpisode = configuration.enableNextEpisodeAutoPlay,
    )
}

/**
 * Folds the edited preferences back onto the live document. Only the five properties this screen
 * owns are touched -- the other eleven are the server's and must round-trip untouched.
 *
 * Choosing an audio language also clears `PlayDefaultAudioTrack`: that flag means "ignore language,
 * always take the server-marked default track", so leaving it on would make
 * `TrackSelection.selectAudioIndex` short-circuit past the language the user just picked and the
 * row would appear to do nothing. With no language chosen the flag is left exactly as the account
 * has it, since there is then nothing for it to override.
 */
internal fun ServerPreferences.applyTo(configuration: UserConfigurationDto): UserConfigurationDto =
    configuration.copy(
        audioLanguagePreference = audioLanguage,
        subtitleLanguagePreference = subtitleLanguage,
        subtitleMode = subtitleMode.storageId,
        playDefaultAudioTrack = if (audioLanguage != null) false else configuration.playDefaultAudioTrack,
        enableNextEpisodeAutoPlay = autoplayNextEpisode,
    )
