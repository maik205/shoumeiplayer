package com.maik205.shoumeiplayer.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.maik205.shoumeiplayer.data.api.dto.UserConfigurationDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private object UserConfigurationKeys {
    val CACHED = stringPreferencesKey("cached_user_configuration")
    val PENDING = stringPreferencesKey("pending_user_configuration")
}

/**
 * One account's `UserConfiguration`, tagged with the user it belongs to.
 *
 * The tag matters because the account switcher can hand the same device to a different Jellyfin
 * user: without it, a cached preference would leak across accounts and be posted back to the wrong
 * one on the next retry.
 */
@Serializable
data class StoredUserConfiguration(
    val userId: String,
    val configuration: UserConfigurationDto,
)

/**
 * Local mirror of the signed-in account's server-owned preferences, plus the one write-back the
 * server has not accepted yet.
 *
 * Server-owned preferences (audio/subtitle language, subtitle mode, next-episode autoplay) are
 * authoritative but not always reachable, and the settings screen has to render *something* on a
 * cold start with no network. Caching the last known document lets those rows show real values
 * offline, and queueing the desired document lets an edit made offline still reach the account --
 * the same shape as the pending token revocation [SessionStore] already persists and
 * `AuthRepository.retryPendingTokenRevocation` drains.
 *
 * The primary constructor takes the backing [DataStore] so JVM unit tests can substitute an
 * in-memory implementation; production code uses the [Context] secondary constructor, mirroring
 * [SessionStore] and [SettingsStore].
 */
class UserConfigurationStore(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("shoumei_user_configuration")
        },
    )

    private val json = Json {
        // The DTO gains properties as Jellyfin does; a cache entry written by an older build must
        // stay readable rather than throwing and wiping the user's offline view.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * The cached entry as stored, account tag included. Consumers pair it with the active session
     * themselves so a stale entry from a previous account is never shown as the current one.
     */
    val cached: Flow<StoredUserConfiguration?> =
        store.data.map { prefs -> prefs.decode(UserConfigurationKeys.CACHED) }

    /** Last-known configuration for [userId], or null when nothing was ever cached for them. */
    suspend fun cachedOrNull(userId: String?): UserConfigurationDto? =
        store.data.first().read(UserConfigurationKeys.CACHED, userId)

    suspend fun cache(userId: String?, configuration: UserConfigurationDto) {
        if (userId.isNullOrBlank()) return
        store.edit { prefs ->
            prefs[UserConfigurationKeys.CACHED] =
                json.encodeToString(StoredUserConfiguration(userId, configuration))
        }
    }

    /** The configuration the user asked for that the server has not accepted yet. */
    suspend fun pendingOrNull(userId: String?): UserConfigurationDto? =
        store.data.first().read(UserConfigurationKeys.PENDING, userId)

    suspend fun queuePending(userId: String?, configuration: UserConfigurationDto) {
        if (userId.isNullOrBlank()) return
        store.edit { prefs ->
            // Deliberately last-write-wins rather than a list of edits: each entry is a complete
            // desired document already folded on top of the previous one, so replaying only the
            // newest reproduces every queued change in one round trip.
            prefs[UserConfigurationKeys.PENDING] =
                json.encodeToString(StoredUserConfiguration(userId, configuration))
        }
    }

    suspend fun clearPending(userId: String?) {
        val queued = pendingOrNull(userId) ?: return
        store.edit { prefs ->
            // Re-check inside the transaction: a concurrent edit may have queued a newer document
            // between the retry starting and succeeding, and that one still owes the server a write.
            val current = prefs.read(UserConfigurationKeys.PENDING, userId)
            if (current == queued) prefs.remove(UserConfigurationKeys.PENDING)
        }
    }

    /** Drops everything: used when the account this device is signed into changes. */
    suspend fun clear() {
        store.edit { prefs ->
            prefs.remove(UserConfigurationKeys.CACHED)
            prefs.remove(UserConfigurationKeys.PENDING)
        }
    }

    private fun Preferences.decode(key: Preferences.Key<String>): StoredUserConfiguration? {
        val encoded = this[key] ?: return null
        return runCatching { json.decodeFromString<StoredUserConfiguration>(encoded) }.getOrNull()
    }

    private fun Preferences.read(
        key: Preferences.Key<String>,
        userId: String?,
    ): UserConfigurationDto? {
        if (userId.isNullOrBlank()) return null
        val stored = decode(key) ?: return null
        return stored.configuration.takeIf { stored.userId == userId }
    }
}
