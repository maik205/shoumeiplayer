package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.domain.result.ApiError
import com.maik205.shoumeiplayer.domain.result.ApiResult
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
import com.maik205.shoumeiplayer.player.PlaybackTrackPreferenceProvider
import com.maik205.shoumeiplayer.player.PlaybackTrackPreferences

data class QuickConnectSessionReplacement(
    val session: Session,
    val tokenChanged: Boolean,
    val oldTokenRevoked: Boolean,
)

class AuthRepository(
    private val client: JellyfinClient,
    private val sessionStore: SessionStore,
) : PlaybackTrackPreferenceProvider {
    val rememberedServers = sessionStore.rememberedServers
    val activeServer = sessionStore.activeServer
    suspend fun activeUserId(): String? = sessionStore.current()?.userId

    /**
     * Process-lifetime cache of the signed-in user. Populated either by [login] (the auth
     * response already carries it) or lazily via `GET /Users/Me`. Deliberately not persisted:
     * it is a one-request-per-launch read and the server is the source of truth.
     */
    @Volatile
    private var cachedUser: UserDto? = null

    suspend fun currentUser(): UserDto? {
        val sessionUserId = sessionStore.current()?.userId
        cachedUser
            ?.takeIf { sessionUserId == null || it.id == sessionUserId }
            ?.let { return it }
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

    override suspend fun preferences(): PlaybackTrackPreferences? =
        userConfiguration()?.let { configuration ->
            PlaybackTrackPreferences(
                playDefaultAudioTrack = configuration.playDefaultAudioTrack,
                audioLanguagePreference = configuration.audioLanguagePreference,
                subtitleLanguagePreference = configuration.subtitleLanguagePreference,
                subtitleMode = configuration.subtitleMode,
            )
        }

    suspend fun probeServer(rawUrl: String): ApiResult<PublicSystemInfo> {
        val normalized = normalizeServerUrl(rawUrl)
        return client.get<PublicSystemInfo>(
            "/System/Info/Public",
            baseUrlOverride = normalized,
            includeToken = false,
            invalidateSessionOnUnauthorized = false,
        )
    }

    suspend fun activateServer(rawUrl: String, systemInfo: PublicSystemInfo) {
        val normalized = normalizeServerUrl(rawUrl)
        if (sessionStore.serverUrlOrNull() != normalized) cachedUser = null
        sessionStore.activateServer(
            url = normalized,
            serverId = systemInfo.id,
            serverName = systemInfo.serverName,
        )
    }

    suspend fun validateServer(rawUrl: String): ApiResult<PublicSystemInfo> {
        val result = probeServer(rawUrl)
        if (result is ApiResult.Success) {
            activateServer(rawUrl, result.data)
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

    /**
     * Profiles suitable for the account switcher.
     *
     * Jellyfin deliberately omits hidden users from `/Users/Public`, including the user whose
     * session is already active. Keep that public list for discoverable accounts, but always put
     * the authenticated user first. The persisted session is a usable offline fallback when
     * `/Users/Me` cannot be refreshed.
     */
    suspend fun accountSwitcherUsers(): ApiResult<List<UserDto>> {
        val publicResult = publicUsers()
        val session = sessionStore.current() ?: return publicResult
        val activeUser = currentUser()
            ?.takeIf { it.id == session.userId }
            ?: UserDto(
                id = session.userId,
                name = session.userName,
            )
        val publicUsers = (publicResult as? ApiResult.Success)?.data.orEmpty()
        return ApiResult.Success(
            buildList {
                add(activeUser)
                addAll(publicUsers.filterNot { it.id == session.userId })
            },
        )
    }

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

    /**
     * Replace the active server session through an authorized Quick Connect challenge.
     *
     * The replacement token is verified before local persistence. Persistence records the old
     * token as pending revocation in the same transaction, so a process death cannot lose the new
     * working session or forget that cleanup is still owed.
     */
    suspend fun replaceSessionWithQuickConnect(
        secret: String,
    ): ApiResult<QuickConnectSessionReplacement> {
        val previous = sessionStore.current()
            ?: return ApiResult.Failure(ApiError.Unauthorized)
        val authentication = client.post<AuthenticationResult>(
            "/Users/AuthenticateWithQuickConnect",
            body = QuickConnectDto(secret),
            includeToken = false,
            invalidateSessionOnUnauthorized = false,
        )
        val result = when (authentication) {
            is ApiResult.Failure -> return authentication
            is ApiResult.Success -> authentication.data
        }
        val replacementToken = result.accessToken
            ?.takeIf(String::isNotBlank)
            ?: return ApiResult.Failure(ApiError.Unknown("Quick Connect response missing token"))
        val expectedUserId = result.user?.id
            ?.takeIf(String::isNotBlank)
            ?: return ApiResult.Failure(ApiError.Unknown("Quick Connect response missing user"))
        if (expectedUserId != previous.userId) {
            return ApiResult.Failure(
                ApiError.Unknown("Quick Connect must be approved by the current user"),
            )
        }

        val verifiedUser = when (
            val verification = client.getWithToken<UserDto>("/Users/Me", replacementToken)
        ) {
            is ApiResult.Failure -> return verification
            is ApiResult.Success -> verification.data
        }
        if (verifiedUser.id != expectedUserId) {
            return ApiResult.Failure(ApiError.Unknown("Quick Connect token belongs to another user"))
        }

        val tokenChanged = replacementToken != previous.accessToken
        sessionStore.replaceAuth(
            accessToken = replacementToken,
            userId = expectedUserId,
            userName = verifiedUser.name.orEmpty(),
            previousTokenToRevoke = previous.accessToken.takeIf { tokenChanged },
        )
        cachedUser = verifiedUser

        val oldTokenRevoked = if (tokenChanged) {
            revokePendingToken(previous.accessToken)
        } else {
            true
        }
        val session = sessionStore.current()
            ?: return ApiResult.Failure(ApiError.Unknown("Replacement session was not persisted"))
        return ApiResult.Success(
            QuickConnectSessionReplacement(
                session = session,
                tokenChanged = tokenChanged,
                oldTokenRevoked = oldTokenRevoked,
            ),
        )
    }

    /** Retry cleanup left pending by a process death or transient logout failure. */
    suspend fun retryPendingTokenRevocation(): Boolean {
        val token = sessionStore.pendingRevocationTokenOrNull() ?: return true
        return revokePendingToken(token)
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

    suspend fun hasActiveSession(): Boolean = sessionStore.current() != null

    private suspend fun persistAuthentication(result: AuthenticationResult): ApiResult<Session> {
        val accessToken = result.accessToken
        val user = result.user
        val userId = user?.id
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) {
            return ApiResult.Failure(ApiError.Unknown("Authentication response missing token or user id"))
        }
        val userName = user.name.orEmpty()
        val serverUrl = sessionStore.serverUrlOrNull()
            ?: return ApiResult.Failure(ApiError.Network("No server configured"))
        cachedUser = user
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

    private suspend fun revokePendingToken(token: String): Boolean {
        val result = client.postEmptyWithToken("/Sessions/Logout", token)
        val revoked = result is ApiResult.Success ||
            (result is ApiResult.Failure && result.error == ApiError.Unauthorized)
        if (revoked) sessionStore.clearPendingRevocation(token)
        return revoked
    }
}
