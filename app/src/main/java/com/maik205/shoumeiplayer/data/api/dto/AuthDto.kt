package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticateUserByName(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String,
)

@Serializable
data class QuickConnectDto(
    @SerialName("Secret") val secret: String,
)

@Serializable
data class QuickConnectResult(
    @SerialName("Authenticated") val authenticated: Boolean = false,
    @SerialName("Secret") val secret: String? = null,
    @SerialName("Code") val code: String? = null,
    @SerialName("DeviceId") val deviceId: String? = null,
    @SerialName("DeviceName") val deviceName: String? = null,
    @SerialName("AppName") val appName: String? = null,
    @SerialName("AppVersion") val appVersion: String? = null,
    @SerialName("DateAdded") val dateAdded: String? = null,
)

@Serializable
data class ForgotPasswordDto(
    @SerialName("EnteredUsername") val enteredUsername: String,
)

@Serializable
data class ForgotPasswordResult(
    @SerialName("Action") val action: String? = null,
    @SerialName("PinFile") val pinFile: String? = null,
    @SerialName("PinExpirationDate") val pinExpirationDate: String? = null,
)

/**
 * `UserConfiguration` — all 16 server-side properties (`additionalProperties: false`
 * on the Jellyfin schema, so an unknown key is a 400).
 *
 * `POST /Users/Configuration` replaces the whole object; never build this instance
 * from scratch for a write. Always read (`GET /Users/Me`), copy-transform, and POST
 * back the full 16-property object — see `AuthRepository.updateUserConfiguration`.
 *
 * Returned inside `UserDto.Configuration` by `GET /Users/Me` and `POST /Users/AuthenticateByName`.
 */
@Serializable
data class UserConfigurationDto(
    @SerialName("AudioLanguagePreference") val audioLanguagePreference: String? = null,
    @SerialName("PlayDefaultAudioTrack") val playDefaultAudioTrack: Boolean = true,
    @SerialName("SubtitleLanguagePreference") val subtitleLanguagePreference: String? = null,
    /** `SubtitlePlaybackMode`: Default | Always | OnlyForced | None | Smart. */
    @SerialName("SubtitleMode") val subtitleMode: String? = null,
    @SerialName("RememberAudioSelections") val rememberAudioSelections: Boolean = true,
    @SerialName("RememberSubtitleSelections") val rememberSubtitleSelections: Boolean = true,
    /** Drives the Up Next countdown (docs/osd-v3.md §5): without it the card offers, never counts. */
    @SerialName("EnableNextEpisodeAutoPlay") val enableNextEpisodeAutoPlay: Boolean = true,
    @SerialName("DisplayMissingEpisodes") val displayMissingEpisodes: Boolean = false,
    @SerialName("GroupedFolders") val groupedFolders: List<String> = emptyList(),
    @SerialName("DisplayCollectionsView") val displayCollectionsView: Boolean = false,
    @SerialName("EnableLocalPassword") val enableLocalPassword: Boolean = false,
    @SerialName("OrderedViews") val orderedViews: List<String> = emptyList(),
    @SerialName("LatestItemsExcludes") val latestItemsExcludes: List<String> = emptyList(),
    @SerialName("MyMediaExcludes") val myMediaExcludes: List<String> = emptyList(),
    @SerialName("HidePlayedInLatest") val hidePlayedInLatest: Boolean = true,
    @SerialName("CastReceiverId") val castReceiverId: String? = null,
)

@Serializable
data class UserDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("HasPassword") val hasPassword: Boolean = false,
    @SerialName("HasConfiguredPassword") val hasConfiguredPassword: Boolean = false,
    @SerialName("HasConfiguredEasyPassword") val hasConfiguredEasyPassword: Boolean = false,
    @SerialName("EnableAutoLogin") val enableAutoLogin: Boolean? = null,
    @SerialName("LastLoginDate") val lastLoginDate: String? = null,
    @SerialName("LastActivityDate") val lastActivityDate: String? = null,
    @SerialName("Configuration") val configuration: UserConfigurationDto? = null,
    @SerialName("Policy") val policy: UserPolicyDto? = null,
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
)

@Serializable
data class UserPolicyDto(
    @SerialName("IsAdministrator") val isAdministrator: Boolean = false,
    @SerialName("IsHidden") val isHidden: Boolean = false,
    @SerialName("IsDisabled") val isDisabled: Boolean = false,
    @SerialName("EnableUserPreferenceAccess") val enableUserPreferenceAccess: Boolean = false,
    @SerialName("EnableRemoteAccess") val enableRemoteAccess: Boolean = false,
    @SerialName("EnableLiveTvAccess") val enableLiveTvAccess: Boolean = false,
    @SerialName("EnableMediaPlayback") val enableMediaPlayback: Boolean = false,
    @SerialName("EnableAudioPlaybackTranscoding") val enableAudioPlaybackTranscoding: Boolean = false,
    @SerialName("EnableVideoPlaybackTranscoding") val enableVideoPlaybackTranscoding: Boolean = false,
    @SerialName("EnablePlaybackRemuxing") val enablePlaybackRemuxing: Boolean = false,
    @SerialName("EnableContentDownloading") val enableContentDownloading: Boolean = false,
    @SerialName("InvalidLoginAttemptCount") val invalidLoginAttemptCount: Int = 0,
    @SerialName("LoginAttemptsBeforeLockout") val loginAttemptsBeforeLockout: Int = 0,
    @SerialName("MaxActiveSessions") val maxActiveSessions: Int = 0,
    @SerialName("RemoteClientBitrateLimit") val remoteClientBitrateLimit: Int = 0,
    @SerialName("AuthenticationProviderId") val authenticationProviderId: String = "",
    @SerialName("PasswordResetProviderId") val passwordResetProviderId: String = "",
)

@Serializable
data class AuthenticationResult(
    @SerialName("User") val user: UserDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)
