package com.maik205.shoumeiplayer.ui.television.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.session.SessionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
sealed interface TelevisionStart {
    data object Loading : TelevisionStart
    data object Connect : TelevisionStart
    data object Profiles : TelevisionStart
    data object Home : TelevisionStart
}

class TelevisionRootViewModel(sessionStore: SessionStore) : ViewModel() {
    val start: StateFlow<TelevisionStart> = combine(
        sessionStore.session,
        sessionStore.serverUrl,
    ) { session, serverUrl ->
        when {
            session != null -> TelevisionStart.Home
            !serverUrl.isNullOrBlank() -> TelevisionStart.Profiles
            else -> TelevisionStart.Connect
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TelevisionStart.Loading,
    )
}
