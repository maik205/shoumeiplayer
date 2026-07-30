package com.maik205.shoumeiplayer.data.session

import kotlinx.serialization.Serializable

data class Session(
    val serverUrl: String,      // normalized, no trailing slash
    val accessToken: String,
    val userId: String,
    val userName: String,
    val deviceId: String,
)

@Serializable
data class RememberedServer(
    val id: String,
    val name: String,
    val url: String,
    val accessToken: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val pendingRevocationToken: String? = null,
    val lastUsedAt: Long = 0L,
) {
    val hasSession: Boolean
        get() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()
}

interface SessionProvider {
    suspend fun current(): Session?
    suspend fun deviceId(): String
    suspend fun serverUrlOrNull(): String?
}
