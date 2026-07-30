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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

private object SessionKeys {
    val SERVERS = stringPreferencesKey("remembered_servers_v2")
    val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id_v2")
    val SERVER_URL = stringPreferencesKey("server_url")
    val ACCESS_TOKEN = stringPreferencesKey("access_token")
    val USER_ID = stringPreferencesKey("user_id")
    val USER_NAME = stringPreferencesKey("user_name")
    val DEVICE_ID = stringPreferencesKey("device_id")
}

@Serializable
private data class PersistedServers(
    val entries: List<RememberedServer> = emptyList(),
)

/**
 * Persistent auth/session state.
 *
 * The primary constructor takes the backing [DataStore] so JVM unit tests can
 * substitute an in-memory implementation; production code uses the
 * [Context] secondary constructor, which is the signature pinned by
 * docs/plan.md appendix A.1.
 */
class SessionStore(private val store: DataStore<Preferences>) : SessionProvider {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("shoumei_session") },
    )

    val rememberedServers: Flow<List<RememberedServer>> = store.data.map { prefs ->
        readServers(prefs)
            .sortedByDescending(RememberedServer::lastUsedAt)
    }

    val activeServer: Flow<RememberedServer?> = store.data.map(::readActiveServer)

    val session: Flow<Session?> = store.data.map { prefs ->
        readActiveServer(prefs)?.toSession(prefs[SessionKeys.DEVICE_ID].orEmpty())
    }

    val serverUrl: Flow<String?> = activeServer.map { it?.url }

    override suspend fun current(): Session? = session.first()

    override suspend fun deviceId(): String {
        val existing = store.data.first()[SessionKeys.DEVICE_ID]
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        store.edit { prefs -> prefs[SessionKeys.DEVICE_ID] = generated }
        return generated
    }

    override suspend fun serverUrlOrNull(): String? = readActiveServer(store.data.first())?.url

    suspend fun pendingRevocationTokenOrNull(): String? =
        readActiveServer(store.data.first())?.pendingRevocationToken

    suspend fun setServerUrl(url: String) {
        activateServer(url = url)
    }

    suspend fun activateServer(
        url: String,
        serverId: String? = null,
        serverName: String? = null,
    ): RememberedServer {
        val normalized = normalizeServerUrl(url)
        var selected: RememberedServer? = null
        store.edit { prefs ->
            val servers = readServers(prefs).toMutableList()
            val targetIndex = servers.indexOfFirst { server ->
                (!serverId.isNullOrBlank() && server.id == serverId) || server.url == normalized
            }
            val existing = servers.getOrNull(targetIndex)
            val updated = RememberedServer(
                id = serverId?.takeIf(String::isNotBlank) ?: existing?.id ?: normalized,
                name = serverName?.takeIf(String::isNotBlank) ?: existing?.name ?: serverDisplayName(normalized),
                url = normalized,
                accessToken = existing?.accessToken,
                userId = existing?.userId,
                userName = existing?.userName,
                pendingRevocationToken = existing?.pendingRevocationToken,
                lastUsedAt = System.currentTimeMillis(),
            )
            if (targetIndex >= 0) servers[targetIndex] = updated else servers += updated
            writeServers(prefs, servers)
            prefs[SessionKeys.ACTIVE_SERVER_ID] = updated.id
            clearLegacySessionKeys(prefs)
            selected = updated
        }
        return checkNotNull(selected)
    }

    suspend fun saveAuth(accessToken: String, userId: String, userName: String) {
        replaceAuth(
            accessToken = accessToken,
            userId = userId,
            userName = userName,
            previousTokenToRevoke = null,
        )
    }

    suspend fun replaceAuth(
        accessToken: String,
        userId: String,
        userName: String,
        previousTokenToRevoke: String?,
    ) {
        store.edit { prefs ->
            val active = readActiveServer(prefs) ?: return@edit
            val servers = readServers(prefs).map { server ->
                if (server.id == active.id) {
                    server.copy(
                        accessToken = accessToken,
                        userId = userId,
                        userName = userName,
                        pendingRevocationToken = previousTokenToRevoke
                            ?.takeUnless { it == accessToken },
                        lastUsedAt = System.currentTimeMillis(),
                    )
                } else {
                    server
                }
            }
            writeServers(prefs, servers)
            prefs[SessionKeys.ACTIVE_SERVER_ID] = active.id
            clearLegacySessionKeys(prefs)
        }
    }

    suspend fun clearPendingRevocation(expectedToken: String) {
        store.edit { prefs ->
            val active = readActiveServer(prefs) ?: return@edit
            writeServers(
                prefs,
                readServers(prefs).map { server ->
                    if (server.id == active.id && server.pendingRevocationToken == expectedToken) {
                        server.copy(pendingRevocationToken = null)
                    } else {
                        server
                    }
                },
            )
        }
    }

    suspend fun clearAuth() {
        store.edit { prefs ->
            val activeId = prefs[SessionKeys.ACTIVE_SERVER_ID] ?: readActiveServer(prefs)?.id
            writeServers(
                prefs,
                readServers(prefs).map { server ->
                    if (server.id == activeId) {
                        server.copy(
                            accessToken = null,
                            userId = null,
                            userName = null,
                            pendingRevocationToken = null,
                        )
                    } else {
                        server
                    }
                },
            )
            if (activeId != null) prefs[SessionKeys.ACTIVE_SERVER_ID] = activeId
            clearLegacySessionKeys(prefs)
        }
    }

    /** Forget only the selected server. Other server sessions and the stable device id survive. */
    suspend fun clearServer() {
        store.edit { prefs ->
            val activeId = prefs[SessionKeys.ACTIVE_SERVER_ID] ?: readActiveServer(prefs)?.id
            writeServers(prefs, readServers(prefs).filterNot { it.id == activeId })
            prefs.remove(SessionKeys.ACTIVE_SERVER_ID)
            clearLegacySessionKeys(prefs)
        }
    }

    suspend fun forgetServer(serverId: String) {
        store.edit { prefs ->
            val activeId = prefs[SessionKeys.ACTIVE_SERVER_ID] ?: readActiveServer(prefs)?.id
            writeServers(prefs, readServers(prefs).filterNot { it.id == serverId })
            if (activeId == serverId) {
                prefs.remove(SessionKeys.ACTIVE_SERVER_ID)
                clearLegacySessionKeys(prefs)
            }
        }
    }

    suspend fun clearAll() {
        store.edit { prefs -> prefs.clear() }
    }

    private fun readActiveServer(prefs: Preferences): RememberedServer? {
        val servers = readServers(prefs)
        val activeId = prefs[SessionKeys.ACTIVE_SERVER_ID]
        return servers.firstOrNull { it.id == activeId }
            ?: legacyServer(prefs)
    }

    private fun readServers(prefs: Preferences): List<RememberedServer> {
        val decoded = prefs[SessionKeys.SERVERS]
            ?.let { encoded ->
                runCatching { json.decodeFromString<PersistedServers>(encoded).entries }
                    .getOrDefault(emptyList())
            }
            .orEmpty()
        val legacy = legacyServer(prefs)
        return if (legacy == null || decoded.any { it.url == legacy.url }) decoded else decoded + legacy
    }

    private fun legacyServer(prefs: Preferences): RememberedServer? {
        val url = prefs[SessionKeys.SERVER_URL] ?: return null
        return RememberedServer(
            id = url,
            name = serverDisplayName(url),
            url = url,
            accessToken = prefs[SessionKeys.ACCESS_TOKEN],
            userId = prefs[SessionKeys.USER_ID],
            userName = prefs[SessionKeys.USER_NAME],
        )
    }

    private fun writeServers(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        servers: List<RememberedServer>,
    ) {
        prefs[SessionKeys.SERVERS] = json.encodeToString(PersistedServers(servers))
    }

    private fun clearLegacySessionKeys(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(SessionKeys.SERVER_URL)
        prefs.remove(SessionKeys.ACCESS_TOKEN)
        prefs.remove(SessionKeys.USER_ID)
        prefs.remove(SessionKeys.USER_NAME)
    }
}

private fun RememberedServer.toSession(deviceId: String): Session? {
    val token = accessToken ?: return null
    val selectedUserId = userId ?: return null
    return Session(
        serverUrl = url,
        accessToken = token,
        userId = selectedUserId,
        userName = userName.orEmpty(),
        deviceId = deviceId,
    )
}

private fun serverDisplayName(url: String): String =
    runCatching { java.net.URI(url).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: url.removePrefix("http://").removePrefix("https://")

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
