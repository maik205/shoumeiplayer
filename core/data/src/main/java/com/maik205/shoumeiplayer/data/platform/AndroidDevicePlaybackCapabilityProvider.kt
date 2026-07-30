package com.maik205.shoumeiplayer.data.platform

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.view.Display
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilityProvider

class AndroidDevicePlaybackCapabilityProvider(
    context: Context,
) : DevicePlaybackCapabilityProvider {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    override fun current(): DevicePlaybackCapabilities {
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val encodings = audioManager
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .orEmpty()
            .asSequence()
            .filterNot { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            .flatMap { it.encodings.asSequence() }
            .toSet()

        return resolveDevicePlaybackCapabilities(
            refreshRates = display?.supportedModes?.map { it.refreshRate },
            isHdr = display?.isHdr,
            audioEncodings = encodings,
        )
    }
}

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
