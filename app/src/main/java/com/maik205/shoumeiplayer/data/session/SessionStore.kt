package com.maik205.shoumeiplayer.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private object SessionKeys {
    val SERVER_URL = stringPreferencesKey("server_url")
    val ACCESS_TOKEN = stringPreferencesKey("access_token")
    val USER_ID = stringPreferencesKey("user_id")
    val USER_NAME = stringPreferencesKey("user_name")
    val DEVICE_ID = stringPreferencesKey("device_id")
}

/**
 * Persistent auth/session state.
 *
 * The primary constructor takes the backing [DataStore] so JVM unit tests can
 * substitute an in-memory implementation; production code uses the
 * [Context] secondary constructor, which is the signature pinned by
 * docs/plan.md appendix A.1.
 */
class SessionStore(private val store: DataStore<Preferences>) : SessionProvider {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("shoumei_session") },
    )

    val session: Flow<Session?> = store.data.map { prefs ->
        val serverUrl = prefs[SessionKeys.SERVER_URL]
        val accessToken = prefs[SessionKeys.ACCESS_TOKEN]
        val userId = prefs[SessionKeys.USER_ID]
        if (serverUrl != null && accessToken != null && userId != null) {
            Session(
                serverUrl = serverUrl,
                accessToken = accessToken,
                userId = userId,
                userName = prefs[SessionKeys.USER_NAME].orEmpty(),
                deviceId = prefs[SessionKeys.DEVICE_ID].orEmpty(),
            )
        } else {
            null
        }
    }

    val serverUrl: Flow<String?> = store.data.map { prefs -> prefs[SessionKeys.SERVER_URL] }

    override suspend fun current(): Session? = session.first()

    override suspend fun deviceId(): String {
        val existing = store.data.first()[SessionKeys.DEVICE_ID]
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        store.edit { prefs -> prefs[SessionKeys.DEVICE_ID] = generated }
        return generated
    }

    override suspend fun serverUrlOrNull(): String? = store.data.first()[SessionKeys.SERVER_URL]

    suspend fun setServerUrl(url: String) {
        val normalized = normalizeServerUrl(url)
        store.edit { prefs -> prefs[SessionKeys.SERVER_URL] = normalized }
    }

    suspend fun saveAuth(accessToken: String, userId: String, userName: String) {
        store.edit { prefs ->
            prefs[SessionKeys.ACCESS_TOKEN] = accessToken
            prefs[SessionKeys.USER_ID] = userId
            prefs[SessionKeys.USER_NAME] = userName
        }
    }

    suspend fun clearAuth() {
        store.edit { prefs ->
            prefs.remove(SessionKeys.ACCESS_TOKEN)
            prefs.remove(SessionKeys.USER_ID)
            prefs.remove(SessionKeys.USER_NAME)
        }
    }

    suspend fun clearAll() {
        store.edit { prefs -> prefs.clear() }
    }
}

internal fun normalizeServerUrl(raw: String): String {
    var url = raw.trim()
    if (!url.contains("://")) {
        url = "http://$url"
    }
    url = url.trimEnd('/')
    val webIndexSuffix = "/web/index.html"
    if (url.endsWith(webIndexSuffix, ignoreCase = true)) {
        url = url.substring(0, url.length - webIndexSuffix.length)
    }
    return url.trimEnd('/')
}
