package com.maik205.shoumeiplayer.domain.settings

data class DevicePlaybackCapabilities(
    val supportsRefreshRateSwitching: Boolean = true,
    val supportsHdrOutput: Boolean = true,
    val supportsDolbyDigitalPassthrough: Boolean = true,
    val supportsDolbyDigitalPlusPassthrough: Boolean = true,
    val supportsDtsPassthrough: Boolean = true,
    val hdrTypes: Set<Int> = emptySet(),
    val maxDisplayLuminance: Float? = null,
    val videoCodecs: List<VideoCodecCapability> = emptyList(),
)

data class VideoCodecCapability(
    val mimeType: String,
    val hardwareAccelerated: Boolean,
    val softwareOnly: Boolean,
    val vendor: Boolean,
    val secure: Boolean,
    val tunneled: Boolean,
    val profiles: Set<Int>,
    val maxWidth: Int?,
    val maxHeight: Int?,
    val maxFrameRate: Double?,
)

data class DevicePlaybackProfile(
    val capabilities: DevicePlaybackCapabilities,
    val deviceFingerprint: String,
)

fun interface DevicePlaybackCapabilityProvider {
    fun current(): DevicePlaybackCapabilities

    fun profile(): DevicePlaybackProfile = DevicePlaybackProfile(current(), "unknown")
}
