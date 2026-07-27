package com.maik205.shoumeiplayer.player

import com.maik205.shoumeiplayer.data.repo.DEFAULT_MAX_STREAMING_BITRATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/osd-v3.md §5 — the quality ladder and its `MaxStreamingBitrate` mapping. */
class VideoQualityTest {

    @Test
    fun `Auto carries no cap of its own`() {
        assertNull(VideoQuality.AUTO.maxStreamingBitrate)
        assertFalse(VideoQuality.AUTO.forcesTranscode)
    }

    @Test
    fun `every explicit rung caps the bitrate and forces a transcode`() {
        val explicit = VideoQuality.entries - VideoQuality.AUTO
        assertEquals(4, explicit.size)
        explicit.forEach { quality ->
            assertNotNull("${quality.label} must carry a cap", quality.maxStreamingBitrate)
            assertTrue("${quality.label} must force a transcode", quality.forcesTranscode)
        }
    }

    @Test
    fun `caps descend with resolution and stay under the Auto ceiling`() {
        assertEquals(80_000_000L, VideoQuality.UHD.maxStreamingBitrate)
        assertEquals(20_000_000L, VideoQuality.FHD.maxStreamingBitrate)
        assertEquals(8_000_000L, VideoQuality.HD.maxStreamingBitrate)
        assertEquals(3_000_000L, VideoQuality.SD.maxStreamingBitrate)

        val caps = listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD, VideoQuality.SD)
            .map { it.maxStreamingBitrate!! }
        assertEquals(caps.sortedDescending(), caps)
        assertTrue("every rung must sit under the Auto ceiling", caps.all { it < DEFAULT_MAX_STREAMING_BITRATE })
    }

    @Test
    fun `ladder is Auto first then descending resolution`() {
        assertEquals(
            listOf(VideoQuality.AUTO, VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD, VideoQuality.SD),
            VideoQuality.Ladder,
        )
        assertEquals(listOf("Auto", "4K", "1080p", "720p", "480p"), VideoQuality.Ladder.map { it.label })
    }

    @Test
    fun `forBitrate round-trips every rung and treats anything else as Auto`() {
        VideoQuality.entries.forEach { quality ->
            assertEquals(quality, VideoQuality.forBitrate(quality.maxStreamingBitrate))
        }
        // The uncapped ceiling a default resolve uses is Auto, not a rung of its own.
        assertEquals(VideoQuality.AUTO, VideoQuality.forBitrate(DEFAULT_MAX_STREAMING_BITRATE))
        assertEquals(VideoQuality.AUTO, VideoQuality.forBitrate(null))
        assertEquals(VideoQuality.AUTO, VideoQuality.forBitrate(12_345L))
    }
}

/** docs/osd-v3.md §5 — the speed ladder. */
class PlaybackSpeedTest {

    @Test
    fun `steps are the six rates the spec names, in order`() {
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), PlaybackSpeed.Steps)
    }

    @Test
    fun `clamp keeps a rate inside the ladder`() {
        assertEquals(0.5f, PlaybackSpeed.clamp(0.1f))
        assertEquals(2.0f, PlaybackSpeed.clamp(8f))
        assertEquals(1.25f, PlaybackSpeed.clamp(1.25f))
    }

    @Test
    fun `labels drop trailing zeros`() {
        assertEquals("1×", PlaybackSpeed.label(1.0f))
        assertEquals("1.5×", PlaybackSpeed.label(1.5f))
        assertEquals("0.75×", PlaybackSpeed.label(0.75f))
        assertEquals("2×", PlaybackSpeed.label(2.0f))
    }

    @Test
    fun `isNormal only tolerates float noise around 1x`() {
        assertTrue(PlaybackSpeed.isNormal(1.0f))
        assertTrue(PlaybackSpeed.isNormal(1.0001f))
        assertFalse(PlaybackSpeed.isNormal(1.25f))
        assertFalse(PlaybackSpeed.isNormal(0.75f))
    }

    @Test
    fun `nearestStep snaps an engine-reported rate onto the ladder`() {
        assertEquals(1.5f, PlaybackSpeed.nearestStep(1.49f))
        assertEquals(2.0f, PlaybackSpeed.nearestStep(1.9f))
        assertEquals(0.5f, PlaybackSpeed.nearestStep(0.4f))
    }
}
