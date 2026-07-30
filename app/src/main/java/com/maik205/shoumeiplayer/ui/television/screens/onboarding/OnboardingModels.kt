package com.maik205.shoumeiplayer.ui.television.screens.onboarding

import androidx.compose.runtime.Immutable
import com.maik205.shoumeiplayer.ui.i18n.UiText

@Immutable
data class ServerChoiceUi(
    val id: String,
    val name: String,
    val address: String,
)

@Immutable
data class ConnectUiState(
    val address: String = "",
    val servers: List<ServerChoiceUi> = emptyList(),
    val discovering: Boolean = true,
    val connecting: Boolean = false,
    val error: UiText? = null,
)

@Immutable
data class ProfileUi(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val hasPassword: Boolean,
)

@Immutable
data class ProfilesUiState(
    val loading: Boolean = true,
    val profiles: List<ProfileUi> = emptyList(),
    val error: UiText? = null,
)

@Immutable
data class LoginUiState(
    val userName: String = "",
    val password: String = "",
    val signingIn: Boolean = false,
    val quickConnectLoading: Boolean = false,
    val quickConnectCode: String? = null,
    val quickConnectAvailable: Boolean = false,
    val error: UiText? = null,
)

@Immutable
data class RecoveryUiState(
    val userName: String = "",
    val requesting: Boolean = false,
    val result: UiText? = null,
    val error: UiText? = null,
)
