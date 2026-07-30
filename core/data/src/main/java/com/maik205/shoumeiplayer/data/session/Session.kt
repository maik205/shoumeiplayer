package com.maik205.shoumeiplayer.data.session

import kotlinx.serialization.Serializable

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
