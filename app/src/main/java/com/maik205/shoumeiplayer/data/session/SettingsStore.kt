package com.maik205.shoumeiplayer.data.session

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private object SettingsKeys {
    val FOCUS_SCALE_ENABLED = booleanPreferencesKey("focus_scale_enabled")
    val CLOCK_IN_OSD = booleanPreferencesKey("clock_in_osd")
    val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
    val AUTOPLAY_NEXT_EPISODE = booleanPreferencesKey("autoplay_next_episode")
}

@Immutable
data class ClientSettings(
    val focusScaleEnabled: Boolean = true,
    val clockInOsd: Boolean = true,
    /** Label only: "Auto" | "4K" | "1080p" | "720p" | "480p". Bitrate mapping is player-owned. */
    val preferredQuality: String = "Auto",
    val autoplayNextEpisode: Boolean = true,
)

/**
 * Client-only settings, persisted separately from [SessionStore] (`shoumei_settings`).
 *
 * The primary constructor takes the backing [DataStore] so JVM unit tests can
 * substitute an in-memory implementation; production code uses the [Context]
 * secondary constructor, mirroring [SessionStore]'s shape.
 */
class SettingsStore(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("shoumei_settings") },
    )

    val settings: Flow<ClientSettings> = store.data.map { prefs ->
        ClientSettings(
            focusScaleEnabled = prefs[SettingsKeys.FOCUS_SCALE_ENABLED] ?: true,
            clockInOsd = prefs[SettingsKeys.CLOCK_IN_OSD] ?: true,
            preferredQuality = prefs[SettingsKeys.PREFERRED_QUALITY] ?: "Auto",
            autoplayNextEpisode = prefs[SettingsKeys.AUTOPLAY_NEXT_EPISODE] ?: true,
        )
    }

    suspend fun current(): ClientSettings = settings.first()

    suspend fun setFocusScaleEnabled(value: Boolean) {
        store.edit { prefs -> prefs[SettingsKeys.FOCUS_SCALE_ENABLED] = value }
    }

    suspend fun setClockInOsd(value: Boolean) {
        store.edit { prefs -> prefs[SettingsKeys.CLOCK_IN_OSD] = value }
    }

    suspend fun setPreferredQuality(value: String) {
        store.edit { prefs -> prefs[SettingsKeys.PREFERRED_QUALITY] = value }
    }

    suspend fun setAutoplayNextEpisode(value: Boolean) {
        store.edit { prefs -> prefs[SettingsKeys.AUTOPLAY_NEXT_EPISODE] = value }
    }
}
