package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.data.ApiError
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.api.dto.AuthenticateUserByName
import com.maik205.shoumeiplayer.data.api.dto.AuthenticationResult
import com.maik205.shoumeiplayer.data.api.dto.ForgotPasswordDto
import com.maik205.shoumeiplayer.data.api.dto.ForgotPasswordResult
import com.maik205.shoumeiplayer.data.api.dto.PublicSystemInfo
import com.maik205.shoumeiplayer.data.api.dto.QuickConnectDto
import com.maik205.shoumeiplayer.data.api.dto.QuickConnectResult
import com.maik205.shoumeiplayer.data.api.dto.UserConfigurationDto
import com.maik205.shoumeiplayer.data.api.dto.UserDto
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.normalizeServerUrl

class AuthRepository(private val client: JellyfinClient, private val sessionStore: SessionStore) {

    /**
     * Process-lifetime cache of the signed-in user. Populated either by [login] (the auth
     * response already carries it) or lazily via `GET /Users/Me`. Deliberately not persisted:
     * it is a one-request-per-launch read and the server is the source of truth.
     */
    @Volatile
    private var cachedUser: UserDto? = null

    suspend fun currentUser(): UserDto? {
        cachedUser?.let { return it }
        val user = (client.get<UserDto>("/Users/Me") as? ApiResult.Success)?.data ?: return null
        cachedUser = user
        return user
    }

    /**
     * The current user's default audio/subtitle preferences, or null when the server could not be
     * reached. Cached after the first successful read.
     */
    suspend fun userConfiguration(): UserConfigurationDto? {
        return currentUser()?.configuration
    }

    suspend fun validateServer(rawUrl: String): ApiResult<PublicSystemInfo> {
        val normalized = normalizeServerUrl(rawUrl)
        val result = client.get<PublicSystemInfo>(
            "/System/Info/Public",
            baseUrlOverride = normalized,
            includeToken = false,
            invalidateSessionOnUnauthorized = false,
        )
        if (result is ApiResult.Success) {
            if (sessionStore.serverUrlOrNull() != normalized) {
                cachedUser = null
                sessionStore.clearAuth()
            }
            sessionStore.setServerUrl(normalized)
        }
        return result
    }

    /** Explicit server switch entry point used by connection-management screens. */
    suspend fun changeServer(rawUrl: String): ApiResult<PublicSystemInfo> = validateServer(rawUrl)

    /** Public login profiles advertised by the currently selected server. */
    suspend fun publicUsers(): ApiResult<List<UserDto>> = client.get(
        "/Users/Public",
        includeToken = false,
        invalidateSessionOnUnauthorized = false,
    )

    suspend fun quickConnectEnabled(): ApiResult<Boolean> = client.get(
        "/QuickConnect/Enabled",
        includeToken = false,
        invalidateSessionOnUnauthorized = false,
    )

    suspend fun forgotPassword(username: String): ApiResult<ForgotPasswordResult> = client.post(
        "/Users/ForgotPassword",
        body = ForgotPasswordDto(username),
        includeToken = false,
        invalidateSessionOnUnauthorized = false,
    )

    suspend fun initiateQuickConnect(): ApiResult<QuickConnectResult> = client.post(
        "/QuickConnect/Initiate",
        includeToken = false,
        invalidateSessionOnUnauthorized = false,
    )

    suspend fun pollQuickConnect(secret: String): ApiResult<QuickConnectResult> = client.get(
        "/QuickConnect/Connect",
        params = mapOf("secret" to secret),
        includeToken = false,
        invalidateSessionOnUnauthorized = false,
    )

    suspend fun login(username: String, password: String): ApiResult<Session> {
        val result = client.post<AuthenticationResult>(
            "/Users/AuthenticateByName",
            body = AuthenticateUserByName(username = username, pw = password),
            includeToken = false,
            invalidateSessionOnUnauthorized = false,
        )
        return when (result) {
            is ApiResult.Failure -> result.asCredentialFailure()
            is ApiResult.Success -> persistAuthentication(result.data)
        }
    }

    suspend fun authenticateWithQuickConnect(secret: String): ApiResult<Session> {
        val result = client.post<AuthenticationResult>(
            "/Users/AuthenticateWithQuickConnect",
            body = QuickConnectDto(secret),
            includeToken = false,
            invalidateSessionOnUnauthorized = false,
        )
        return when (result) {
            is ApiResult.Failure -> result.asCredentialFailure()
            is ApiResult.Success -> persistAuthentication(result.data)
        }
    }

    /** GET /Users/Me, bypassing the cache; refreshes it on success. */
    suspend fun refreshUserConfiguration(): ApiResult<UserConfigurationDto> {
        val result = client.get<UserDto>("/Users/Me")
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val configuration = result.data.configuration
                    ?: return ApiResult.Failure(ApiError.Unknown("User response missing configuration"))
                cachedUser = result.data
                ApiResult.Success(configuration)
            }
        }
    }

    /**
     * Read-modify-write. NEVER constructs a fresh UserConfigurationDto: refresh the whole
     * 16-property object, apply [transform], POST the WHOLE object to
     * /Users/Configuration?userId=..., replace the cache on 204. A read failure aborts without
     * writing.
     */
    suspend fun updateUserConfiguration(
        transform: (UserConfigurationDto) -> UserConfigurationDto,
    ): ApiResult<UserConfigurationDto> {
        val refreshed = refreshUserConfiguration()
        val current = when (refreshed) {
            is ApiResult.Failure -> return refreshed
            is ApiResult.Success -> refreshed.data
        }
        val userId = client.currentSession()?.userId
            ?: return ApiResult.Failure(ApiError.Unauthorized)
        val merged = transform(current)
        val result = client.postEmpty(
            "/Users/Configuration",
            body = merged,
            params = mapOf("userId" to userId),
        )
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                cachedUser = cachedUser?.copy(configuration = merged)
                ApiResult.Success(merged)
            }
        }
    }

    /**
     * Ends the server session when possible, then always removes local credentials.
     * The server result is returned so callers may surface an offline logout without
     * trapping the user in a signed-in local state.
     */
    suspend fun logout(): ApiResult<Unit> {
        val result = if (sessionStore.current() == null) {
            ApiResult.Success(Unit)
        } else {
            client.postEmpty("/Sessions/Logout")
        }
        cachedUser = null
        sessionStore.clearAuth()
        return result
    }

    suspend fun logoutLocal() {
        cachedUser = null
        sessionStore.clearAuth()
    }

    suspend fun clearLocalAuth() = logoutLocal()

    suspend fun forgetServer() {
        cachedUser = null
        sessionStore.clearServer()
    }

    suspend fun clearServer() = forgetServer()

    private suspend fun persistAuthentication(result: AuthenticationResult): ApiResult<Session> {
        val accessToken = result.accessToken
        val userId = result.user?.id
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) {
            return ApiResult.Failure(ApiError.Unknown("Authentication response missing token or user id"))
        }
        val userName = result.user.name.orEmpty()
        val serverUrl = sessionStore.serverUrlOrNull()
            ?: return ApiResult.Failure(ApiError.Network("No server configured"))
        cachedUser = result.user
        sessionStore.saveAuth(accessToken = accessToken, userId = userId, userName = userName)
        return ApiResult.Success(
            Session(
                serverUrl = serverUrl,
                accessToken = accessToken,
                userId = userId,
                userName = userName,
                deviceId = sessionStore.deviceId(),
            ),
        )
    }

    private fun <T> ApiResult.Failure.asCredentialFailure(): ApiResult<T> =
        if (error == ApiError.Unauthorized) {
            ApiResult.Failure(ApiError.InvalidCredentials)
        } else {
            this
        }
}
