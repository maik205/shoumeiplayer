package com.maik205.shoumeiplayer.data.session

data class Session(
    val serverUrl: String,      // normalized, no trailing slash
    val accessToken: String,
    val userId: String,
    val userName: String,
    val deviceId: String,
)

interface SessionProvider {
    suspend fun current(): Session?
    suspend fun deviceId(): String
    suspend fun serverUrlOrNull(): String?
}
