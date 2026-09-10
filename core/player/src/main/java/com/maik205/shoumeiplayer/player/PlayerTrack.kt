package com.maik205.shoumeiplayer.player

enum class TrackType { VIDEO, AUDIO, SUBTITLE }

data class PlayerTrack(
    val id: Int,                 // Jellyfin MediaStream.Index (or -1 for "Off")
    val type: TrackType,
    val label: String,
    val language: String? = null,
    val title: String? = null,
    val codec: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val selected: Boolean = false,
)
