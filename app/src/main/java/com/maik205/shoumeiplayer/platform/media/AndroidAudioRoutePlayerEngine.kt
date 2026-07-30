package com.maik205.shoumeiplayer.platform.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.player.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AndroidAudioRoutePlayerEngine(
    context: Context,
    private val delegate: PlayerEngine,
) : PlayerEngine by delegate {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val _route = MutableStateFlow(resolveCurrentRoute())
    private var configuredSettings: ClientSettings? = null

    val route: StateFlow<AudioRoutePolicy> = _route.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshRoute()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshRoute()
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    override fun configure(settings: ClientSettings) {
        configuredSettings = settings
        delegate.configure(settings.forAudioRoute(_route.value))
    }

    override fun release() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        delegate.release()
    }

    private fun refreshRoute() {
        val route = resolveCurrentRoute()
        if (_route.value == route) return
        _route.value = route
        configuredSettings?.let { delegate.configure(it.forAudioRoute(route)) }
    }

    private fun resolveCurrentRoute(): AudioRoutePolicy {
        val device = activeOutputDevice() ?: return AudioRoutePolicy.Unknown
        val encodings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.audioProfiles.mapTo(mutableSetOf()) { it.format }
        } else {
            device.encodings.toSet()
        }
        return resolveAudioRoutePolicy(
            type = device.type,
            label = device.productName?.toString(),
            encodings = encodings,
            channelCounts = device.channelCounts.toSet(),
            sampleRates = device.sampleRates.toSet(),
        )
    }

    private fun activeOutputDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            audioManager.getDevicesForAttributes(attributes).firstOrNull()?.let { return it }
        }
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .maxByOrNull { audioRoutePriority(it.type) }
    }
}

internal data class AudioRoutePolicy(
    val type: Int,
    val label: String,
    val supportsAc3: Boolean,
    val supportsEac3: Boolean,
    val supportsDts: Boolean,
    val supportsTrueHd: Boolean,
    val channelCounts: Set<Int>,
    val sampleRates: Set<Int>,
) {
    val description: String
        get() = buildString {
            append(label)
            channelCounts.maxOrNull()?.let { append(" · ").append(it).append(" ch") }
            sampleRates.maxOrNull()?.let { append(" · ").append(it / 1_000).append(" kHz") }
        }

    companion object {
        val Unknown = AudioRoutePolicy(
            type = AudioDeviceInfo.TYPE_UNKNOWN,
            label = "Unknown output",
            supportsAc3 = false,
            supportsEac3 = false,
            supportsDts = false,
            supportsTrueHd = false,
            channelCounts = emptySet(),
            sampleRates = emptySet(),
        )
    }
}

internal fun resolveAudioRoutePolicy(
    type: Int,
    label: String?,
    encodings: Set<Int>,
    channelCounts: Set<Int> = emptySet(),
    sampleRates: Set<Int> = emptySet(),
): AudioRoutePolicy {
    val passthroughEligible = type == AudioDeviceInfo.TYPE_HDMI ||
        type == AudioDeviceInfo.TYPE_HDMI_ARC ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_HDMI_EARC)
    return AudioRoutePolicy(
        type = type,
        label = label?.takeIf(String::isNotBlank) ?: audioRouteLabel(type),
        supportsAc3 = passthroughEligible && AudioFormat.ENCODING_AC3 in encodings,
        supportsEac3 = passthroughEligible && (
            AudioFormat.ENCODING_E_AC3 in encodings || AudioFormat.ENCODING_E_AC3_JOC in encodings
            ),
        supportsDts = passthroughEligible && (
            AudioFormat.ENCODING_DTS in encodings || AudioFormat.ENCODING_DTS_HD in encodings
            ),
        supportsTrueHd = passthroughEligible && AudioFormat.ENCODING_DOLBY_TRUEHD in encodings,
        channelCounts = channelCounts,
        sampleRates = sampleRates,
    )
}

internal fun ClientSettings.forAudioRoute(route: AudioRoutePolicy): ClientSettings = copy(
    dolbyDigitalPassthrough = dolbyDigitalPassthrough && route.supportsAc3,
    dolbyDigitalPlusPassthrough = dolbyDigitalPlusPassthrough && route.supportsEac3,
    dtsPassthrough = dtsPassthrough && route.supportsDts,
)

private fun audioRoutePriority(type: Int): Int = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    -> 5
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    -> 4
    AudioDeviceInfo.TYPE_HDMI_EARC,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_HDMI,
    -> 3
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    -> 2
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 1
    else -> 0
}

private fun audioRouteLabel(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
    AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    -> "Bluetooth"
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    -> "USB audio"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "TV speakers"
    else -> "Audio output"
}
