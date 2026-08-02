package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

@Stable
internal class PlaybackLaunchState internal constructor() {
    var loading by mutableStateOf(false)
        private set

    fun launch(action: () -> Unit) {
        if (loading) return
        loading = true
        action()
    }

    internal fun reset() {
        loading = false
    }
}

@Composable
internal fun rememberPlaybackLaunchState(key: Any?): PlaybackLaunchState {
    val state = remember(key) { PlaybackLaunchState() }

    LifecycleResumeEffect(state) {
        state.reset()
        onPauseOrDispose { }
    }

    return state
}
