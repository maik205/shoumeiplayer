package com.maik205.shoumeiplayer.player

enum class TrackType { AUDIO, SUBTITLE }

data class PlayerTrack(
    val id: Int,                 // Jellyfin MediaStream.Index (or -1 for "Off")
    val type: TrackType,
    val label: String,
    val language: String? = null,
    val isDefault: Boolean = false,
    val selected: Boolean = false,
)
