package com.maik205.shoumeiplayer.domain.settings

/** Narrow persistence contract required by the playback feature. */
interface PlayerSettingsRepository {
    suspend fun current(): ClientSettings
    suspend fun setPreferredQuality(value: String)
    suspend fun setAudioDelayMs(value: Int)
    suspend fun setSubtitleDelayMs(value: Int)
}
