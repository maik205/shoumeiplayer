package com.maik205.shoumeiplayer.ui.television.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
sealed interface TelevisionStart {
    data object Loading : TelevisionStart
    data object Connect : TelevisionStart
    data object Profiles : TelevisionStart
    data object Home : TelevisionStart
    data object Error : TelevisionStart
}

class TelevisionRootViewModel(sessionStore: SessionStore) : ViewModel() {
    private val _start = MutableStateFlow<TelevisionStart>(TelevisionStart.Loading)
    val start: StateFlow<TelevisionStart> = _start.asStateFlow()

    private var resolveJob: Job? = null

    init {
        retry()
    }

    fun retry() {
        resolveJob?.cancel()
        _start.value = TelevisionStart.Loading
        resolveJob = viewModelScope.launch {
            try {
                val firstDestination = withTimeoutOrNull(8_000L) {
                    startFlow(sessionStore).first()
                }
                if (firstDestination == null) {
                    _start.value = TelevisionStart.Error
                    return@launch
                }
                _start.value = firstDestination
                // The root normally only resolves once, but keep it correct when a session is
                // cleared before navigation has been rebuilt.
                startFlow(sessionStore).drop(1).collect { _start.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _start.value = TelevisionStart.Error
            }
        }
    }

    fun continueToConnect() {
        resolveJob?.cancel()
        _start.value = TelevisionStart.Connect
    }

    private fun startFlow(sessionStore: SessionStore) = combine(
        sessionStore.session,
        sessionStore.serverUrl,
    ) { session, serverUrl ->
        when {
            session != null -> TelevisionStart.Home
            !serverUrl.isNullOrBlank() -> TelevisionStart.Profiles
            else -> TelevisionStart.Connect
        }
    }
}
