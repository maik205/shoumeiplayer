package com.maik205.shoumeiplayer.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

@Serializable
data class PairedRemoteClient(
    val id: String,
    val deviceName: String,
    val pairingToken: String,
    val pairedAtMs: Long,
    val expiresAtMs: Long,
)

/**
 * Persists paired companion remote devices on the TV, enforcing a 7-day pairing expiration.
 */
class PairedRemoteStore(
    private val prefs: SharedPreferences? = null,
) {
    companion object {
        private const val PREFS_NAME = "shoumei_tv_paired_remotes"
        private const val KEY_PAIRED_REMOTES = "paired_remotes_json"
    }

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val inMemoryClients = mutableListOf<PairedRemoteClient>()

    private val _pairedClients = MutableStateFlow<List<PairedRemoteClient>>(loadClients())
    val pairedClients: StateFlow<List<PairedRemoteClient>> = _pairedClients.asStateFlow()

    private fun loadClients(): List<PairedRemoteClient> {
        val json = prefs?.getString(KEY_PAIRED_REMOTES, null)
        if (json != null) {
            return try {
                RemoteJson.decodeFromString<List<PairedRemoteClient>>(json)
            } catch (_: Exception) {
                emptyList()
            }
        }
        return inMemoryClients.toList()
    }

    @Synchronized
    private fun persist(clients: List<PairedRemoteClient>) {
        _pairedClients.value = clients
        inMemoryClients.clear()
        inMemoryClients.addAll(clients)
        if (prefs != null) {
            val json = RemoteJson.encodeToString(clients)
            prefs.edit().putString(KEY_PAIRED_REMOTES, json).apply()
        }
    }

    @Synchronized
    fun createPairing(
        clientId: String,
        deviceName: String,
        nowMs: Long = System.currentTimeMillis(),
    ): PairedRemoteClient {
        val token = UUID.randomUUID().toString()
        val client = PairedRemoteClient(
            id = clientId,
            deviceName = deviceName.ifBlank { "Mobile Device" },
            pairingToken = token,
            pairedAtMs = nowMs,
            expiresAtMs = nowMs + PAIRING_EXPIRY_MS,
        )
        savePairedClient(client)
        return client
    }

    @Synchronized
    fun savePairedClient(client: PairedRemoteClient) {
        val current = _pairedClients.value.toMutableList()
        current.removeAll { it.pairingToken == client.pairingToken || it.id == client.id }
        current.add(0, client)
        persist(current)
    }

    @Synchronized
    fun findClient(pairingToken: String): PairedRemoteClient? {
        return _pairedClients.value.firstOrNull { it.pairingToken == pairingToken }
    }

    @Synchronized
    fun findValidClient(pairingToken: String, nowMs: Long = System.currentTimeMillis()): PairedRemoteClient? {
        val client = findClient(pairingToken) ?: return null
        if (nowMs >= client.expiresAtMs) {
            // Expired: remove client from store
            removeClient(pairingToken)
            return null
        }
        return client
    }

    @Synchronized
    fun removeClient(pairingToken: String) {
        val current = _pairedClients.value.toMutableList()
        current.removeAll { it.pairingToken == pairingToken }
        persist(current)
    }

    @Synchronized
    fun purgeExpired(nowMs: Long = System.currentTimeMillis()) {
        val valid = _pairedClients.value.filter { nowMs < it.expiresAtMs }
        if (valid.size != _pairedClients.value.size) {
            persist(valid)
        }
    }

    @Synchronized
    fun clearAll() {
        persist(emptyList())
    }
}
