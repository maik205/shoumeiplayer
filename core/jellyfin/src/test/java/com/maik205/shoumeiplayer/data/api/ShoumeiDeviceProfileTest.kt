package com.maik205.shoumeiplayer.data.api

import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import com.maik205.shoumeiplayer.domain.settings.VideoCodecCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoumeiDeviceProfileTest {
    @Test
    fun `device profile advertises only enumerated video codecs`() {
        val profile = ShoumeiDeviceProfile.build(
            DevicePlaybackCapabilities(
                videoCodecs = listOf(
                    VideoCodecCapability("video/avc", true, false, false, false, false, emptySet(), 1920, 1080, 60.0),
                    VideoCodecCapability("video/hevc", true, false, false, false, false, emptySet(), 3840, 2160, 60.0),
                ),
            ),
        )

        assertEquals("h264,hevc", profile.directPlayProfiles.first { it.type == "Video" }.videoCodec)
    }
}
