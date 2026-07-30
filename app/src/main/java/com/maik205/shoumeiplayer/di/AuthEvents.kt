package com.maik205.shoumeiplayer.di

import com.maik205.shoumeiplayer.di.AuthEvents.emitUnauthorized
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-wide "the server rejected our token" bus (docs/plan.md M6.2).
 *
 * A plain `object` rather than an [AppContainer] member so the data layer can
 * emit without taking a DI dependency, and so the collector in `NavGraph` needs
 * no plumbing. `extraBufferCapacity = 1` makes [emitUnauthorized] non-suspending
 * and non-blocking: if nobody is collecting (e.g. in unit tests) the event is
 * simply buffered/dropped instead of stalling the HTTP call that raised it.
 */
object AuthEvents {
    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorized: SharedFlow<Unit> = _unauthorized

    fun emitUnauthorized() {
        _unauthorized.tryEmit(Unit)
    }
}
