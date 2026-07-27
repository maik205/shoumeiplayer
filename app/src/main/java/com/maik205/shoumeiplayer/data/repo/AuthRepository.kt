package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.data.ApiError
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.api.dto.AuthenticateUserByName
import com.maik205.shoumeiplayer.data.api.dto.AuthenticationResult
import com.maik205.shoumeiplayer.data.api.dto.PublicSystemInfo
import com.maik205.shoumeiplayer.data.api.dto.UserConfigurationDto
import com.maik205.shoumeiplayer.data.api.dto.UserDto
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.normalizeServerUrl

class AuthRepository(private val client: JellyfinClient, private val sessionStore: SessionStore) {

    /**
     * Process-lifetime cache of the signed-in user's `UserConfiguration`. Populated either by
     * [login] (the auth response already carries it) or lazily by [userConfiguration] via
     * `GET /Users/Me`. Deliberately not persisted: it is a one-request-per-launch read and the
     * server is the source of truth.
     */
    @Volatile
    private var cachedConfiguration: UserConfigurationDto? = null

    /**
     * The current user's default audio/subtitle preferences, or null when the server could not be
     * reached. Cached after the first successful read.
     */
    suspend fun userConfiguration(): UserConfigurationDto? {
        cachedConfiguration?.let { return it }
        val result = client.get<UserDto>("/Users/Me")
        val configuration = (result as? ApiResult.Success)?.data?.configuration ?: return null
        cachedConfiguration = configuration
        return configuration
    }

    suspend fun validateServer(rawUrl: String): ApiResult<PublicSystemInfo> {
        val normalized = normalizeServerUrl(rawUrl)
        val result = client.get<PublicSystemInfo>(
            "/System/Info/Public",
            baseUrlOverride = normalized,
        )
        if (result is ApiResult.Success) {
            sessionStore.setServerUrl(normalized)
        }
        return result
    }

    suspend fun login(username: String, password: String): ApiResult<Session> {
        val result = client.post<AuthenticationResult>(
            "/Users/AuthenticateByName",
            body = AuthenticateUserByName(username = username, pw = password),
        )
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val accessToken = result.data.accessToken
                val userId = result.data.user?.id
                if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) {
                    return ApiResult.Failure(ApiError.Unknown("Authentication response missing token or user id"))
                }
                val userName = result.data.user.name.orEmpty()
                cachedConfiguration = result.data.user.configuration
                sessionStore.saveAuth(accessToken = accessToken, userId = userId, userName = userName)
                val serverUrl = sessionStore.serverUrlOrNull()
                    ?: return ApiResult.Failure(ApiError.Network("No server configured"))
                val deviceId = sessionStore.deviceId()
                ApiResult.Success(
                    Session(
                        serverUrl = serverUrl,
                        accessToken = accessToken,
                        userId = userId,
                        userName = userName,
                        deviceId = deviceId,
                    ),
                )
            }
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
                cachedConfiguration = configuration
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
                cachedConfiguration = merged
                ApiResult.Success(merged)
            }
        }
    }

    suspend fun logout() {
        cachedConfiguration = null
        sessionStore.clearAuth()
    }
}
