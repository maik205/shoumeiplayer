package com.maik205.shoumeiplayer.domain.result

sealed interface ApiError {
    data object Unauthorized : ApiError
    /** Authentication failed before a session existed; this is not an expired stored session. */
    data object InvalidCredentials : ApiError
    data class Network(val message: String) : ApiError
    data class Http(val code: Int, val message: String) : ApiError
    data class Serialization(val message: String) : ApiError
    data class Unknown(val message: String) : ApiError

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
