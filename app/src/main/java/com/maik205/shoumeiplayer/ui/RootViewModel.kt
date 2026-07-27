package com.maik205.shoumeiplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.session.SessionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface StartDestination {
    data object Loading : StartDestination
    data object ServerEntry : StartDestination
    data object Login : StartDestination
    data object Home : StartDestination
}

class RootViewModel(sessionStore: SessionStore) : ViewModel() {

    val start: StateFlow<StartDestination> = combine(
        sessionStore.session,
        sessionStore.serverUrl,
    ) { session, serverUrl ->
        when {
            session != null -> StartDestination.Home
            serverUrl != null -> StartDestination.Login
            else -> StartDestination.ServerEntry
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StartDestination.Loading,
    )
}
