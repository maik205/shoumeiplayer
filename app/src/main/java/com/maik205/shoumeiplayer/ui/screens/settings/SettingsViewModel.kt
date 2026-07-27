package com.maik205.shoumeiplayer.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.api.dto.UserConfigurationDto
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.session.ClientSettings
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** §5.7 v2 — the two-pane group list on the left. Order is the on-screen order. */
enum class SettingsGroup { Playback, Appearance, Server, About }

/**
 * Ledger contents for the Settings screen (docs/ui-design.md §5.7, browse-v2-plan M-B13).
 *
 * [serverName] is null until the `/System/Info/Public` probe answers (renders as the plain
 * `PENDING` word in the meantime, never a spinner). [configuration] is null until the first
 * `/Users/Me` read resolves; every server-backed row falls back to its own default until then.
 * [saving] gates every server-backed row's control while a write is in flight (optimistic UI is
 * banned — see [SettingsViewModel]'s class doc). [errorMessage] is the inline §6 error line for
 * the most recent failed write; it clears on the next attempt, success or failure.
 */
data class SettingsUiState(
    val serverUrl: String = "",
    val serverName: String? = null,
    val serverVersion: String? = null,
    val userName: String = "",
    val appVersion: String = "",
    val engineName: String = "",
    val signingOut: Boolean = false,
    val group: SettingsGroup = SettingsGroup.Playback,
    val settings: ClientSettings = ClientSettings(),
    val configuration: UserConfigurationDto? = null,
    val saving: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Settings screen state holder (docs/browse-v2-plan.md M-B13).
 *
 * Every server-backed field (subtitle mode, subtitle/audio language, `PlayDefaultAudioTrack`,
 * `EnableNextEpisodeAutoPlay`, `RememberAudioSelections`, `RememberSubtitleSelections`) writes
 * through [AuthRepository.updateUserConfiguration], which is read-modify-write over the whole
 * 16-property `/Users/Configuration` object — see that function's doc for why this class never
 * builds a `UserConfigurationDto` from scratch.
 *
 * **Optimistic UI is banned.** [SettingsUiState.configuration] only changes once the POST returns
 * 204; a row reads the previous value for the whole round trip and, on failure, keeps it and
 * surfaces [SettingsUiState.errorMessage] instead of silently reverting a value the user never saw
 * change.
 *
 * Client-only fields (preferred quality, focus scale, clock in OSD) write straight through
 * [SettingsStore]; there is no server round trip to guard, so those calls are fire-and-forget and
 * the screen reflects them via the live [SettingsStore.settings] flow.
 *
 * Sign out delegates to [AuthRepository.logout], which clears the token / user id / user name from
 * [SessionStore] but deliberately keeps the server URL so the user lands on Login rather than
 * Server Entry.
 */
class SettingsViewModel(
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val authRepository: AuthRepository,
    appVersion: String,
    engineName: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(appVersion = appVersion, engineName = engineName),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Emits once after the session has been cleared; the screen navigates to Login. */
    private val _signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signedOut: SharedFlow<Unit> = _signedOut.asSharedFlow()

    init {
        viewModelScope.launch {
            val session = sessionStore.current()
            _uiState.update { state ->
                state.copy(
                    serverUrl = session?.serverUrl.orEmpty(),
                    userName = session?.userName.orEmpty(),
                )
            }
            val serverUrl = session?.serverUrl ?: return@launch
            // Re-probes the public system endpoint purely to label the server;
            // normalizeServerUrl is idempotent so re-persisting the same URL is a no-op.
            when (val result = authRepository.validateServer(serverUrl)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        serverName = result.data.serverName,
                        serverVersion = result.data.version,
                    )
                }
                // Offline / unreachable server must not break the ledger: the
                // URL and the local rows are still the useful information.
                is ApiResult.Failure -> Unit
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(configuration = authRepository.userConfiguration()) }
        }
    }

    fun selectGroup(group: SettingsGroup) {
        _uiState.update { it.copy(group = group) }
    }

    fun setSubtitleMode(value: String) = updateServerConfig { it.copy(subtitleMode = value) }

    fun setSubtitleLanguage(value: String?) =
        updateServerConfig { it.copy(subtitleLanguagePreference = value) }

    fun setAudioLanguage(value: String?) =
        updateServerConfig { it.copy(audioLanguagePreference = value) }

    fun setPlayDefaultAudioTrack(value: Boolean) =
        updateServerConfig { it.copy(playDefaultAudioTrack = value) }

    fun setNextEpisodeAutoPlay(value: Boolean) =
        updateServerConfig { it.copy(enableNextEpisodeAutoPlay = value) }

    fun setRememberAudioSelections(value: Boolean) =
        updateServerConfig { it.copy(rememberAudioSelections = value) }

    fun setRememberSubtitleSelections(value: Boolean) =
        updateServerConfig { it.copy(rememberSubtitleSelections = value) }

    fun setPreferredQuality(value: String) {
        viewModelScope.launch { settingsStore.setPreferredQuality(value) }
    }

    fun setFocusScaleEnabled(value: Boolean) {
        viewModelScope.launch { settingsStore.setFocusScaleEnabled(value) }
    }

    fun setClockInOsd(value: Boolean) {
        viewModelScope.launch { settingsStore.setClockInOsd(value) }
    }

    /**
     * The single write path for every server-backed row: refuses to overlap a write already in
     * flight (a second D-pad click while `saving` is racy, not a queued second edit), holds
     * [SettingsUiState.configuration] at its previous value until the POST resolves, and on
     * failure leaves it exactly there while surfacing [SettingsUiState.errorMessage].
     */
    private fun updateServerConfig(transform: (UserConfigurationDto) -> UserConfigurationDto) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.updateUserConfiguration(transform)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(configuration = result.data, saving = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(saving = false, errorMessage = result.error.displayMessage)
                }
            }
        }
    }

    fun signOut() {
        if (_uiState.value.signingOut) return
        _uiState.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            authRepository.logout()
            _signedOut.tryEmit(Unit)
        }
    }
}
