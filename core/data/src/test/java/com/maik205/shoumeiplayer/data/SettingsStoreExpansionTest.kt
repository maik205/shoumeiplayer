package com.maik205.shoumeiplayer.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.domain.settings.AppTheme
import com.maik205.shoumeiplayer.domain.settings.AssSsaDirectPlay
import com.maik205.shoumeiplayer.domain.settings.BurnSubtitles
import com.maik205.shoumeiplayer.domain.settings.DeinterlaceMode
import com.maik205.shoumeiplayer.domain.settings.DisplayLanguage
import com.maik205.shoumeiplayer.domain.settings.HardwareCodecs
import com.maik205.shoumeiplayer.domain.settings.HardwareDecoding
import com.maik205.shoumeiplayer.domain.settings.HdrMode
import com.maik205.shoumeiplayer.domain.settings.InterfaceScale
import com.maik205.shoumeiplayer.domain.settings.PreferredQuality
import com.maik205.shoumeiplayer.domain.settings.RefreshRateSwitching
import com.maik205.shoumeiplayer.domain.settings.RenderingProfile
import com.maik205.shoumeiplayer.domain.settings.ResumeBehavior
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import com.maik205.shoumeiplayer.domain.settings.SubtitleColor
import com.maik205.shoumeiplayer.domain.settings.SubtitleMode
import com.maik205.shoumeiplayer.domain.settings.SubtitleStroke
import com.maik205.shoumeiplayer.domain.settings.ToneMapping
import kotlinx.coroutines.flow.first
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
            preferredQuality = PreferredQuality.FullHd,
            maxStreamingBitrateMbps = 12,
            maxRemoteBitrateMbps = 8,
            refreshRateSwitching = RefreshRateSwitching.MatchVideo,
            autoplayNextEpisode = false,
            resumeBehavior = ResumeBehavior.Always,
            seekIntervalSeconds = 30,
            skipIntroPrompt = false,
            rememberPlaybackSpeed = true,
            renderingProfile = RenderingProfile.Quality,
            hardwareDecoding = HardwareDecoding.MediaCodec,
            hardwareCodecs = HardwareCodecs.H264Hevc,
            hdrMode = HdrMode.ForceSdr,
            toneMapping = ToneMapping.Bt2390,
            deinterlaceMode = DeinterlaceMode.Bob,
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
            subtitleMode = SubtitleMode.Always,
            burnSubtitles = BurnSubtitles.Always,
            subtitleSizePercent = 115,
            subtitleColor = SubtitleColor.Yellow,
            subtitleStroke = SubtitleStroke.Heavy,
            boldSubtitles = true,
            scaleSubtitlesWithWindow = false,
            useVideoMargins = true,
            pgsDirectPlay = false,
            assSsaDirectPlay = AssSsaDirectPlay.Disabled,
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
            displayLanguage = DisplayLanguage.Vietnamese,
            interfaceScale = InterfaceScale.Compact,
            theme = AppTheme.System,
            backdropImages = false,
            backdropRotationSeconds = 45,
            watchedIndicators = false,
            rememberLastLibrary = false,
            screensaverTimeoutMinutes = 15,
            screensaverContent = ScreensaverContent.Movies,
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
                preferredQuality = PreferredQuality.Uhd4k,
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

        assertEquals(PreferredQuality.Uhd4k, current.preferredQuality)
        assertNull(current.maxStreamingBitrateMbps)
        assertNull(current.preferredAudioLanguage)
        assertEquals(125, current.subtitleSizePercent)
        assertFalse(current.backdropImages)
        assertTrue(current.networkCacheEnabled)
    }

    @Test
    fun `legacy display labels migrate to typed options and save stable ids`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        backing.updateData {
            mutablePreferencesOf(
                stringPreferencesKey("rendering_profile") to "Quality",
                stringPreferencesKey("hardware_decoding") to "MediaCodec copy",
                stringPreferencesKey("hdr_mode") to "Force SDR",
                stringPreferencesKey("subtitle_stroke") to "Heavy",
            )
        }
        val store = SettingsStore(backing)

        val migrated = store.current()

        assertEquals(RenderingProfile.Quality, migrated.renderingProfile)
        assertEquals(HardwareDecoding.MediaCodecCopy, migrated.hardwareDecoding)
        assertEquals(HdrMode.ForceSdr, migrated.hdrMode)
        assertEquals(SubtitleStroke.Heavy, migrated.subtitleStroke)

        store.save(migrated)
        val persisted = backing.data.first()
        assertEquals("quality", persisted[stringPreferencesKey("rendering_profile")])
        assertEquals("mediacodec_copy", persisted[stringPreferencesKey("hardware_decoding")])
        assertEquals("force_sdr", persisted[stringPreferencesKey("hdr_mode")])
        assertEquals("heavy", persisted[stringPreferencesKey("subtitle_stroke")])
    }
}
