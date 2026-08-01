package com.maik205.shoumeiplayer.domain.result

sealed interface ApiError {
    data object Unauthorized : ApiError
    /** Authentication failed before a session existed; this is not an expired stored session. */
    data object InvalidCredentials : ApiError
    data class Network(val message: String) : ApiError
    data class Http(val code: Int, val message: String) : ApiError
    data class Serialization(val message: String) : ApiError
    data class Unknown(val message: String) : ApiError

    /**
     * English copy baked into a data-layer model -- not localizable, and this module has no
     * Android resources to route it through anyway. Kept only so the many existing plain-`String`
     * UI-state fields still compile; new code (and any state field that already speaks `UiText`)
     * should go through `com.maik205.shoumeiplayer.ui.i18n.toUiText()` in the app module instead,
     * which resolves this same information from `strings.xml`.
     */
    @Deprecated(
        "App-owned copy does not belong in core/model. Use ApiError.toUiText() (ui.i18n) instead.",
        ReplaceWith("this.toUiText()", "com.maik205.shoumeiplayer.ui.i18n.toUiText"),
    )
    val displayMessage: String
        get() = when (this) {
            is Unauthorized -> "Session expired. Please sign in again."
            is InvalidCredentials -> "The username or password is incorrect."
            is Network -> "Network error: $message"
            is Http -> "Server error ($code): $message"
            is Serialization -> "Failed to parse server response: $message"
            is Unknown -> "Unexpected error: $message"
        }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(data)
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Failure -> null
}
