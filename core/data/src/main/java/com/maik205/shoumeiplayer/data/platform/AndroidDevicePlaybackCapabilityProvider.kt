package com.maik205.shoumeiplayer.data.platform

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import android.os.Build.VERSION_CODES
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackProfile
import com.maik205.shoumeiplayer.domain.settings.VideoCodecCapability
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilityProvider

class AndroidDevicePlaybackCapabilityProvider(
    context: Context,
) : DevicePlaybackCapabilityProvider {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    override fun current(): DevicePlaybackCapabilities {
        return profile().capabilities
    }

    override fun profile(): DevicePlaybackProfile {
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val encodings = audioManager
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .orEmpty()
            .asSequence()
            .filterNot { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            .flatMap { it.encodings.asSequence() }
            .toSet()

        val displayHdr = display?.hdrCapabilities
        val capabilities = resolveDevicePlaybackCapabilities(
            refreshRates = display?.supportedModes?.map { it.refreshRate },
            isHdr = display?.isHdr,
            audioEncodings = encodings,
        ).copy(
            hdrTypes = displayHdr?.supportedHdrTypes?.toSet().orEmpty(),
            maxDisplayLuminance = displayHdr?.desiredMaxLuminance,
            videoCodecs = enumerateVideoCodecs(),
        )
        return DevicePlaybackProfile(
            capabilities = capabilities,
            deviceFingerprint = Build.FINGERPRINT,
        )
    }

    private fun enumerateVideoCodecs(): List<VideoCodecCapability> =
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filterNot { it.isEncoder }
                .flatMap { info ->
                    info.supportedTypes.asSequence()
                        .filter { it.startsWith("video/") }
                        .mapNotNull { mime -> info.toVideoCodecCapability(mime) }
                }
                .distinctBy { codec ->
                    listOf(codec.mimeType, codec.hardwareAccelerated, codec.softwareOnly, codec.vendor, codec.profiles)
                }
                .toList()
        }.getOrDefault(emptyList())

    private fun MediaCodecInfo.toVideoCodecCapability(mimeType: String): VideoCodecCapability? =
        runCatching {
            val capabilities = getCapabilitiesForType(mimeType)
            val video = capabilities.videoCapabilities
            VideoCodecCapability(
                mimeType = mimeType,
                hardwareAccelerated = Build.VERSION.SDK_INT >= VERSION_CODES.Q && isHardwareAccelerated,
                softwareOnly = Build.VERSION.SDK_INT >= VERSION_CODES.Q && isSoftwareOnly,
                vendor = Build.VERSION.SDK_INT >= VERSION_CODES.Q && isVendor,
                secure = capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback),
                tunneled = capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback),
                profiles = capabilities.profileLevels.map { it.profile }.toSet(),
                maxWidth = video?.supportedWidths?.upper,
                maxHeight = video?.supportedHeights?.upper,
                maxFrameRate = video?.supportedFrameRates?.upper?.toDouble(),
            )
        }.getOrNull()
}

internal fun supportsVideoFormat(
    capability: VideoCodecCapability,
    width: Int,
    height: Int,
    frameRate: Double,
): Boolean = width > 0 && height > 0 && frameRate > 0.0 &&
    (capability.maxWidth == null || width <= capability.maxWidth) &&
    (capability.maxHeight == null || height <= capability.maxHeight) &&
    (capability.maxFrameRate == null || frameRate <= capability.maxFrameRate + 0.01)

internal fun resolveDevicePlaybackCapabilities(
    refreshRates: List<Float>?,
    isHdr: Boolean?,
    audioEncodings: Set<Int>?,
): DevicePlaybackCapabilities {
        // Some TV firmware reports no encodings for HDMI/eARC. Treat that as unknown and retain
        // the controls; only hide a format when the active external route supplied a real list.
        val hasReportedAudioCapabilities = !audioEncodings.isNullOrEmpty()
        return DevicePlaybackCapabilities(
            supportsRefreshRateSwitching = refreshRates
                ?.map { (it * 100f).toInt() }
                ?.distinct()
                ?.let { it.size > 1 }
                ?: true,
            supportsHdrOutput = isHdr ?: true,
            supportsDolbyDigitalPassthrough = !hasReportedAudioCapabilities ||
                AudioFormat.ENCODING_AC3 in audioEncodings.orEmpty(),
            supportsDolbyDigitalPlusPassthrough = !hasReportedAudioCapabilities ||
                AudioFormat.ENCODING_E_AC3 in audioEncodings.orEmpty() ||
                AudioFormat.ENCODING_E_AC3_JOC in audioEncodings.orEmpty(),
            supportsDtsPassthrough = !hasReportedAudioCapabilities ||
                AudioFormat.ENCODING_DTS in audioEncodings.orEmpty() ||
                AudioFormat.ENCODING_DTS_HD in audioEncodings.orEmpty(),
        )
}
