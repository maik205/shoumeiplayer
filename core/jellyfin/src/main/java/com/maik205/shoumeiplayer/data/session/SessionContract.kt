package com.maik205.shoumeiplayer.data.session

data class Session(
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val userName: String,
    val deviceId: String,
)

interface SessionProvider {
    suspend fun current(): Session?
    suspend fun deviceId(): String
    suspend fun serverUrlOrNull(): String?
    suspend fun invalidate() = Unit
}
