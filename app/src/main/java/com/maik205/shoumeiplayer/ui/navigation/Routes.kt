package com.maik205.shoumeiplayer.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object ServerEntryRoute
@Serializable data object LoginRoute
@Serializable data object HomeRoute
/**
 * [personId] carries the OSD Cast shelf's person filter (docs/player-controls.md §5) into the existing
 * library grid rather than a new screen: navigate with a blank [libraryId] and a person id and the
 * grid queries `/Items?personIds=` across the whole library, titled with the person's name.
 */
@Serializable data class LibraryRoute(
    val libraryId: String,
    val title: String,
    val collectionType: String? = null,
    val personId: String? = null,
)
@Serializable data class DetailRoute(val itemId: String)
@Serializable data class PlayerRoute(val itemId: String, val startPositionTicks: Long = 0)
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
/** §3.1 — the rail's Libraries destination: the user's views as one poster grid. */
@Serializable data object LibrariesRoute
