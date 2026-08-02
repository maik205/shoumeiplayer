package com.maik205.shoumeiplayer.data.api

import com.maik205.shoumeiplayer.data.api.dto.DeviceProfileDto
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import com.maik205.shoumeiplayer.domain.settings.HardwareCodecs
import com.maik205.shoumeiplayer.domain.settings.VideoCodecCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoumeiDeviceProfileTest {

    private fun codec(mimeType: String) =
        VideoCodecCapability(mimeType, true, false, false, false, false, emptySet(), 1920, 1080, 60.0)

    private fun DeviceProfileDto.videoCodec() = directPlayProfiles.first { it.type == "Video" }.videoCodec

    // Four distinct codecs, not just avc+hevc: with only avc+hevc enumerated, Automatic and
    // H264Hevc happen to produce the same "h264,hevc" string, so a broken/deleted
    // allowedVideoCodecs() mapping would pass those two tests undetected. av1 and vp9 in the
    // fixture make each HardwareCodecs branch below assert a genuinely different codec string.
    private val capabilities = DevicePlaybackCapabilities(
        videoCodecs = listOf(
            codec("video/avc"),
            codec("video/hevc"),
            codec("video/av01"),
            codec("video/x-vnd.on2.vp9"),
        ),
    )

    @Test
    fun `device profile advertises only enumerated video codecs`() {
        val profile = ShoumeiDeviceProfile.build(
            DevicePlaybackCapabilities(
                videoCodecs = listOf(codec("video/avc"), codec("video/hevc")),
            ),
        )

        assertEquals("h264,hevc", profile.videoCodec())
    }

    @Test
    fun `maxStreamingBitrateMbps set caps the advertised bitrate`() {
        val profile = ShoumeiDeviceProfile.build(
            capabilities,
            ClientSettings(maxStreamingBitrateMbps = 20),
        )

        assertEquals(20_000_000L, profile.maxStreamingBitrate)
    }

    @Test
    fun `maxStreamingBitrateMbps null falls back to the auto ceiling`() {
        val profile = ShoumeiDeviceProfile.build(
            capabilities,
            ClientSettings(maxStreamingBitrateMbps = null),
        )

        assertEquals(400_000_000L, profile.maxStreamingBitrate)
    }

    /**
     * `hardwareCodecs` picks which codecs mpv may hand to MediaCodec (`hwdec-codecs`, applied in
     * MpvEngine). It is a decode-side choice and must NOT reach the device profile: what this
     * device can direct-play is whatever MediaCodec enumerated, and mpv software-decodes the rest.
     *
     * Conflating the two is how "AV1" came to advertise av1 alone, so a perfectly direct-playable
     * h264/HEVC library became 100% server transcode -- the same failure the file's own KDoc had
     * argued against for `Disabled`. Every value must therefore produce the identical profile.
     */
    @Test
    fun `hardwareCodecs never narrows the advertised codec set`() {
        val advertised = HardwareCodecs.entries.associateWith { choice ->
            ShoumeiDeviceProfile.build(
                capabilities,
                ClientSettings(hardwareCodecs = choice),
            ).videoCodec()
        }

        assertEquals(
            HardwareCodecs.entries.associateWith { "h264,hevc,av1,vp9" },
            advertised,
        )
    }

    /**
     * The profile may only ever advertise what MediaCodec actually enumerated. Telling Jellyfin the
     * device direct-plays something it cannot decode makes playback fail outright rather than
     * transcode, so a device reporting one codec must advertise exactly that one.
     */
    @Test
    fun `the profile advertises only codecs the device enumerated`() {
        val av1Only = DevicePlaybackCapabilities(videoCodecs = listOf(codec("video/av01")))

        val profile = ShoumeiDeviceProfile.build(av1Only, ClientSettings())

        assertEquals("av1", profile.videoCodec())
    }
}
