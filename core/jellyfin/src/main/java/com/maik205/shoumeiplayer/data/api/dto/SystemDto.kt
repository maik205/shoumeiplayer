package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PublicSystemInfo(
    @SerialName("Id") val id: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("ProductName") val productName: String? = null,
    @SerialName("StartupWizardCompleted") val startupWizardCompleted: Boolean? = null,
)
