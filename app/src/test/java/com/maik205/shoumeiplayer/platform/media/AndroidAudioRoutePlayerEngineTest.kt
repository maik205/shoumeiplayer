package com.maik205.shoumeiplayer.platform.media

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAudioRoutePlayerEngineTest {
    @Test
    fun `HDMI route exposes only reported passthrough formats`() {
        val route = resolveAudioRoutePolicy(
            type = AudioDeviceInfo.TYPE_HDMI,
            label = "Receiver",
            encodings = setOf(AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3),
        )

        assertTrue(route.supportsAc3)
        assertTrue(route.supportsEac3)
        assertFalse(route.supportsDts)
    }

    @Test
    fun `Bluetooth route always falls back to decoded audio`() {
        val route = resolveAudioRoutePolicy(
            type = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            label = "Headphones",
            encodings = setOf(AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_DTS_HD),
        )

        assertFalse(route.supportsAc3)
        assertFalse(route.supportsDts)
    }

    @Test
    fun `route policy preserves user choice while disabling unsupported formats`() {
        val settings = ClientSettings(
            dolbyDigitalPassthrough = true,
            dolbyDigitalPlusPassthrough = true,
            dtsPassthrough = true,
        )
        val route = resolveAudioRoutePolicy(
            type = AudioDeviceInfo.TYPE_HDMI,
            label = null,
            encodings = setOf(AudioFormat.ENCODING_AC3),
        )

        val resolved = settings.forAudioRoute(route)

        assertTrue(resolved.dolbyDigitalPassthrough)
        assertFalse(resolved.dolbyDigitalPlusPassthrough)
        assertFalse(resolved.dtsPassthrough)
    }
}
