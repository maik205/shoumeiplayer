package com.maik205.shoumeiplayer.ui.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.result.ApiError

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

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Resource -> if (formatArgs.isEmpty()) {
        context.getString(id)
    } else {
        context.getString(id, *formatArgs.toTypedArray())
    }
    is UiText.Dynamic -> value
}

fun String.asDynamicUiText(): UiText = UiText.Dynamic(this)

/**
 * [ApiError]'s copy is app-owned (not server-provided text), so it belongs behind [UiText.Resource]
 * rather than [UiText.Dynamic] -- see the class doc above. This is the one place that copy is
 * spelled out; core/model only carries the error shape (code, message), not the English string.
 */
fun ApiError.toUiText(): UiText = when (this) {
    is ApiError.Unauthorized -> UiText.Resource(R.string.error_session_expired)
    is ApiError.InvalidCredentials -> UiText.Resource(R.string.error_invalid_credentials)
    is ApiError.Network -> UiText.Resource(R.string.error_network, listOf(message))
    is ApiError.Http -> UiText.Resource(R.string.error_http, listOf(code, message))
    is ApiError.Serialization -> UiText.Resource(R.string.error_serialization, listOf(message))
    is ApiError.Unknown -> UiText.Resource(R.string.error_unknown, listOf(message))
}
