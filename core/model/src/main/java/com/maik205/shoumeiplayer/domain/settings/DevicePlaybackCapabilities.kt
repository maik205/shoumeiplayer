package com.maik205.shoumeiplayer.domain.settings

data class DevicePlaybackCapabilities(
    val supportsRefreshRateSwitching: Boolean = true,
    val supportsHdrOutput: Boolean = true,
    val supportsDolbyDigitalPassthrough: Boolean = true,
    val supportsDolbyDigitalPlusPassthrough: Boolean = true,
    val supportsDtsPassthrough: Boolean = true,
)

fun interface DevicePlaybackCapabilityProvider {
    fun current(): DevicePlaybackCapabilities
}
