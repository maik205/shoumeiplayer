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
import com.maik205.shoumeiplayer.data.session.UserConfigurationStore
import com.maik205.shoumeiplayer.data.session.normalizeServerUrl
import com.maik205.shoumeiplayer.player.PlaybackTrackPreferenceProvider
import com.maik205.shoumeiplayer.player.PlaybackTrackPreferences
import com.maik205.shoumeiplayer.player.ServerTrackPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class QuickConnectSessionReplacement(
    val session: Session,
    val tokenChanged: Boolean,
    val oldTokenRevoked: Boolean,
)

class AuthRepository(
    private val client: JellyfinClient,
    private val sessionStore: SessionStore,
    private val userConfigurationStore: UserConfigurationStore,
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
     * The signed-in account's server-owned preferences: audio/subtitle language, subtitle mode,
     * play-default-audio-track and next-episode autoplay.
     *
     * Falls back to the local mirror when the server cannot be reached, so an offline launch shows
     * the user's real preferences rather than the DTO's neutral defaults, and refreshes that mirror
     * whenever the server does answer. Returns null only when the account has never been seen on
     * this device.
     */
    suspend fun userConfiguration(): UserConfigurationDto? {
        val userId = sessionStore.current()?.userId
        val fromServer = currentUser()
            ?.takeIf { userId == null || it.id == userId }
            ?.configuration
        if (fromServer != null) {
            userConfigurationStore.cache(userId, fromServer)
            return fromServer
        }
        return userConfigurationStore.cachedOrNull(userId)
    }

    /**
     * Live view of the cached configuration for the active account, for screens that render these
     * preferences. Emits null until [userConfiguration] or [editUserConfiguration] has populated
     * the mirror for this account.
     */
    val cachedUserConfiguration: Flow<UserConfigurationDto?> =
        combine(sessionStore.session, userConfigurationStore.cached) { session, stored ->
            stored?.configuration?.takeIf { session != null && stored.userId == session.userId }
        }

    /**
     * Feeds [com.maik205.shoumeiplayer.player.TrackSelection] and, through
     * [ServerTrackPreferences], mpv's own `alang`/`slang` fallback -- one document, so the engine's
     * fallback can never contradict the stream index the app just chose.
     */
    override suspend fun preferences(): PlaybackTrackPreferences? =
        userConfiguration()?.toTrackPreferences()?.also { ServerTrackPreferences.publish(it) }

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
                userConfigurationStore.cache(sessionStore.current()?.userId, configuration)
                ServerTrackPreferences.publish(configuration.toTrackPreferences())
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
                userConfigurationStore.cache(userId, merged)
                ServerTrackPreferences.publish(merged.toTrackPreferences())
                ApiResult.Success(merged)
            }
        }
    }

    /**
     * Applies a user-facing change to the server-owned preferences.
     *
     * Optimistic on purpose: the local mirror, the in-process user cache and the engine's language
     * fallback are all moved to the new value *before* the network call, so the settings row the
     * user just changed keeps its new value even on a TV that is offline or on a server that is
     * slow. If the write does not land, the desired document is queued and
     * [retryPendingUserConfiguration] replays it; the caller still gets the failure so it can say
     * the change has not reached the account yet.
     *
     * The queued replay re-reads the server first and copies only the fields this app owns onto
     * it, so a preference another client changed in the meantime is preserved rather than
     * clobbered by a stale full document.
     */
    suspend fun editUserConfiguration(
        transform: (UserConfigurationDto) -> UserConfigurationDto,
    ): ApiResult<UserConfigurationDto> {
        val userId = sessionStore.current()?.userId
            ?: return ApiResult.Failure(ApiError.Unauthorized)
        val known = userConfiguration()
            ?: return ApiResult.Failure(ApiError.Network("No known configuration for this account"))
        // Fold any still-queued edit into the base first. Without this a new edit is built from a
        // document that predates the queued one, yet the success path below clears the queue -- so
        // an earlier offline change would be dropped having never reached the account.
        val pending = userConfigurationStore.pendingOrNull(userId)
        val base = pending?.applyOwnedFieldsTo(known) ?: known
        val desired = transform(base)
        userConfigurationStore.cache(userId, desired)
        cachedUser = cachedUser?.copy(configuration = desired)
        ServerTrackPreferences.publish(desired.toTrackPreferences())

        val result = updateUserConfiguration { current -> desired.applyOwnedFieldsTo(current) }
        if (result is ApiResult.Failure) {
            // updateUserConfiguration re-reads the account before writing, and on a GET that
            // succeeds ahead of a POST that fails that read has already replaced the optimistic
            // state above with the server's pre-edit document. Restore the viewer's choice on top
            // of the freshest document we now hold, or the row silently snaps back while the
            // banner claims the change is merely waiting to be sent.
            val restored = desired.applyOwnedFieldsTo(
                userConfigurationStore.cachedOrNull(userId) ?: base,
            )
            userConfigurationStore.cache(userId, restored)
            cachedUser = cachedUser?.copy(configuration = restored)
            ServerTrackPreferences.publish(restored.toTrackPreferences())
            userConfigurationStore.queuePending(userId, restored)
        } else {
            userConfigurationStore.clearPending(userId)
        }
        return result
    }

    /**
     * Retry a write-back left queued by an offline or failed [editUserConfiguration]. Mirrors
     * [retryPendingTokenRevocation]: returns true when nothing is owed or the debt was settled.
     */
    suspend fun retryPendingUserConfiguration(): Boolean {
        val userId = sessionStore.current()?.userId ?: return true
        val pending = userConfigurationStore.pendingOrNull(userId) ?: return true
        val result = updateUserConfiguration { current -> pending.applyOwnedFieldsTo(current) }
        if (result is ApiResult.Failure) return false
        userConfigurationStore.clearPending(userId)
        return true
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
        clearAccountScopedPreferences()
        sessionStore.clearAuth()
        return result
    }

    /**
     * A signed-out account's cached preferences (and any write-back still owed for them) must not
     * outlive the session: the next user on this TV would otherwise briefly see, and mpv would
     * fall back to, somebody else's languages.
     */
    private suspend fun clearAccountScopedPreferences() {
        userConfigurationStore.clear()
        ServerTrackPreferences.clear()
    }

    suspend fun logoutLocal() {
        cachedUser = null
        clearAccountScopedPreferences()
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
        // The auth response already carries UserConfiguration, so the account's preferences are
        // mirrored (and the engine fallback primed) from the first screen after sign-in rather
        // than only once something happens to ask for them.
        user.configuration?.let { configuration ->
            userConfigurationStore.cache(userId, configuration)
            ServerTrackPreferences.publish(configuration.toTrackPreferences())
        }
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

/**
 * The five `UserConfiguration` properties Shoumei's settings screen owns.
 *
 * A queued write-back replays only these onto a freshly read server document, so anything else the
 * account changed elsewhere while this device was offline survives the retry.
 */
private fun UserConfigurationDto.applyOwnedFieldsTo(
    current: UserConfigurationDto,
): UserConfigurationDto = current.copy(
    audioLanguagePreference = audioLanguagePreference,
    subtitleLanguagePreference = subtitleLanguagePreference,
    subtitleMode = subtitleMode,
    playDefaultAudioTrack = playDefaultAudioTrack,
    enableNextEpisodeAutoPlay = enableNextEpisodeAutoPlay,
)

internal fun UserConfigurationDto.toTrackPreferences() = PlaybackTrackPreferences(
    playDefaultAudioTrack = playDefaultAudioTrack,
    audioLanguagePreference = audioLanguagePreference,
    subtitleLanguagePreference = subtitleLanguagePreference,
    subtitleMode = subtitleMode,
)
