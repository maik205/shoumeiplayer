package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch

/**
 * Requests all scrollable ancestors to reveal this node when D-pad focus arrives. On Android TV,
 * Compose's platform bring-into-view policy supplies the lean-back pivot behavior.
 */
fun Modifier.televisionBringIntoViewOnFocus(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    this
        .bringIntoViewRequester(requester)
        .onFocusChanged { state ->
            if (state.isFocused || state.hasFocus) {
                scope.launch { requester.bringIntoView() }
            }
        }
}
