package com.maik205.shoumeiplayer.ui.television.navigation

import kotlinx.serialization.Serializable

@Serializable data object ConnectRoute
@Serializable data object ProfilesRoute
@Serializable data class LoginRoute(val userName: String = "")
@Serializable data class RecoveryRoute(val userName: String = "")
@Serializable data object SessionExpiredRoute
@Serializable data object AccountLockedRoute
@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
@Serializable data object LiveTvRoute
@Serializable data class LibraryRoute(
    val libraryId: String,
    val title: String,
    val collectionType: String? = null,
)
@Serializable data class DetailRoute(val itemId: String)
@Serializable data class PersonRoute(val personId: String, val name: String)
@Serializable data class PlayerRoute(
    val itemId: String,
    val startPositionTicks: Long = 0,
    val audioOnly: Boolean = false,
    val initialAudioStreamIndex: Int? = null,
    val initialSubtitleStreamIndex: Int? = null,
    val initialQualityLabel: String? = null,
)
