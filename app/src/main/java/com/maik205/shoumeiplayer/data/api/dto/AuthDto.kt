package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticateUserByName(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String,
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
    @SerialName("Configuration") val configuration: UserConfigurationDto? = null,
)

@Serializable
data class AuthenticationResult(
    @SerialName("User") val user: UserDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)
