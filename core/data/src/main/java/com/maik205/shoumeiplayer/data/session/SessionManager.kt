package com.maik205.shoumeiplayer.data.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface SessionEvent {
    data object Expired : SessionEvent
}

/**
 * Process-owned session lifecycle.
 *
 * Persistence remains in [SessionStore]; this class owns lifecycle events that application chrome
 * reacts to. Keeping it in the composition root avoids a global event bus and makes unauthorized
 * behavior independently testable.
 */
class SessionManager(
    private val store: SessionStore,
) {
    val session: Flow<Session?> = store.session

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SessionEvent> = _events

    suspend fun expireSession() {
        store.clearAuth()
        _events.emit(SessionEvent.Expired)
    }
}
