package com.maik205.shoumeiplayer.data.platform

import android.media.AudioFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDevicePlaybackCapabilityProviderTest {
    @Test
    fun `unreported platform capabilities keep controls available`() {
        val capabilities = resolveDevicePlaybackCapabilities(
            refreshRates = null,
            isHdr = null,
            audioEncodings = emptySet(),
        )

        assertTrue(capabilities.supportsRefreshRateSwitching)
        assertTrue(capabilities.supportsHdrOutput)
        assertTrue(capabilities.supportsDolbyDigitalPassthrough)
        assertTrue(capabilities.supportsDolbyDigitalPlusPassthrough)
        assertTrue(capabilities.supportsDtsPassthrough)
    }

    @Test
    fun `reported display and audio routes expose only supported controls`() {
        val capabilities = resolveDevicePlaybackCapabilities(
            refreshRates = listOf(60f, 60f),
            isHdr = false,
            audioEncodings = setOf(AudioFormat.ENCODING_AC3),
        )

        assertFalse(capabilities.supportsRefreshRateSwitching)
        assertFalse(capabilities.supportsHdrOutput)
        assertTrue(capabilities.supportsDolbyDigitalPassthrough)
        assertFalse(capabilities.supportsDolbyDigitalPlusPassthrough)
        assertFalse(capabilities.supportsDtsPassthrough)
    }
}
