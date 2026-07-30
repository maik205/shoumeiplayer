package com.maik205.shoumeiplayer.ui.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text crossing from non-Compose layers into the UI.
 *
 * Keep app-owned copy as [Resource] so it is resolved after the current locale is known.
 * [Dynamic] is reserved for server-provided text and other content the app does not own.
 */
sealed interface UiText {
    data class Resource(
        @StringRes val id: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> if (formatArgs.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *formatArgs.toTypedArray())
    }
    is UiText.Dynamic -> value
}

fun String.asDynamicUiText(): UiText = UiText.Dynamic(this)
