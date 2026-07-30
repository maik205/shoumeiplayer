package com.maik205.shoumeiplayer.data.platform

import com.maik205.shoumeiplayer.domain.settings.VideoCodecCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCodecCapabilityTest {
    private val capability = VideoCodecCapability(
        mimeType = "video/hevc",
        hardwareAccelerated = true,
        softwareOnly = false,
        vendor = true,
        secure = true,
        tunneled = false,
        profiles = emptySet(),
        maxWidth = 3_840,
        maxHeight = 2_160,
        maxFrameRate = 60.0,
    )

    @Test
    fun `accepts formats within decoder limits`() {
        assertTrue(supportsVideoFormat(capability, 3_840, 2_160, 59.94))
    }

    @Test
    fun `rejects formats outside decoder limits`() {
        assertFalse(supportsVideoFormat(capability, 7_680, 4_320, 60.0))
        assertFalse(supportsVideoFormat(capability, 1_920, 1_080, 120.0))
    }
}
