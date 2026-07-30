package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.session.ClientSettings
import com.maik205.shoumeiplayer.data.session.SettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreExpansionTest {

    @Test
    fun `approved TV settings surface round-trips through preferences`() = runTest {
        val store = SettingsStore(InMemoryPreferencesDataStore())
        val expected = ClientSettings(
            preferredQuality = "1080p",
            maxStreamingBitrateMbps = 12,
            maxRemoteBitrateMbps = 8,
            refreshRateSwitching = "Match video",
            autoplayNextEpisode = false,
            resumeBehavior = "Always",
            seekIntervalSeconds = 30,
            skipIntroPrompt = false,
            rememberPlaybackSpeed = true,
            renderingProfile = "Quality",
            hardwareDecoding = "MediaCodec",
            hardwareCodecs = "H.264 / HEVC",
            hdrMode = "Force SDR",
            toneMapping = "BT.2390",
            deinterlaceMode = "Bob",
            frameInterpolation = true,
            preferredAudioLanguage = "jpn",
            rememberSeriesAudio = false,
            pitchCorrection = false,
            downmixStereo = true,
            dolbyDigitalPassthrough = true,
            dolbyDigitalPlusPassthrough = true,
            dtsPassthrough = true,
            audioDelayMs = 125,
            preferredSubtitleLanguage = "eng",
            subtitleMode = "Always",
            burnSubtitles = "All",
            subtitleSizePercent = 115,
            subtitleColor = "Yellow",
            subtitleStroke = "Heavy",
            boldSubtitles = true,
            scaleSubtitlesWithWindow = false,
            useVideoMargins = true,
            pgsDirectPlay = false,
            assSsaDirectPlay = "Disabled",
            subtitleDelayMs = -250,
            networkCacheEnabled = false,
            cacheDurationSeconds = 60,
            readAheadSeconds = 45,
            forwardCacheMiB = 128,
            backwardCacheMiB = 48,
            resumeBufferSeconds = 3,
            networkTimeoutSeconds = 25,
            verifyTlsCertificates = false,
            focusScaleEnabled = false,
            clockInOsd = false,
            displayLanguage = "Vietnamese",
            interfaceScale = "Compact",
            theme = "System",
            backdropImages = false,
            backdropRotationSeconds = 45,
            watchedIndicators = false,
            rememberLastLibrary = false,
            screensaverTimeoutMinutes = 15,
            screensaverContent = "Movies",
            screensaverImageDurationSeconds = 30,
            screensaverShuffle = false,
            screensaverAvoidRepeats = false,
            screensaverClock = false,
            kidsMode = true,
        )

        store.save(expected)

        assertEquals(expected, store.current())
    }

    @Test
    fun `atomic update preserves unrelated values and nullable limits can be cleared`() = runTest {
        val store = SettingsStore(InMemoryPreferencesDataStore())
        store.save(
            ClientSettings(
                preferredQuality = "4K",
                maxStreamingBitrateMbps = 80,
                preferredAudioLanguage = "jpn",
                backdropImages = false,
            ),
        )

        store.update {
            it.copy(
                maxStreamingBitrateMbps = null,
                preferredAudioLanguage = null,
                subtitleSizePercent = 125,
            )
        }
        val current = store.current()

        assertEquals("4K", current.preferredQuality)
        assertNull(current.maxStreamingBitrateMbps)
        assertNull(current.preferredAudioLanguage)
        assertEquals(125, current.subtitleSizePercent)
        assertFalse(current.backdropImages)
        assertTrue(current.networkCacheEnabled)
    }
}
