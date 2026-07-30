package com.maik205.shoumeiplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jellyfin default track selection. Fixture source below is a typical anime release: Japanese
 * audio default, an English dub, English full subs, English forced signs/songs.
 */
class TrackSelectionTest {

    private fun audio(index: Int, language: String?, isDefault: Boolean = false) =
        PlaybackMediaStream(index = index, type = "Audio", language = language, isDefault = isDefault)

    private fun subtitle(index: Int, language: String?, isForced: Boolean = false, isDefault: Boolean = false) =
        PlaybackMediaStream(index = index, type = "Subtitle", language = language, isForced = isForced, isDefault = isDefault)

    private val streams = listOf(
        PlaybackMediaStream(index = 0, type = "Video"),
        audio(1, "jpn", isDefault = true),
        audio(2, "eng"),
        subtitle(3, "eng"),
        subtitle(4, "eng", isForced = true),
        subtitle(5, "spa"),
    )

    private fun config(
        playDefaultAudioTrack: Boolean = true,
        audioLanguagePreference: String? = null,
        subtitleLanguagePreference: String? = null,
        subtitleMode: String? = null,
    ) = PlaybackTrackPreferences(
        audioLanguagePreference = audioLanguagePreference,
        playDefaultAudioTrack = playDefaultAudioTrack,
        subtitleLanguagePreference = subtitleLanguagePreference,
        subtitleMode = subtitleMode,
    )

    // --- audio ----------------------------------------------------------------

    @Test
    fun `PlayDefaultAudioTrack true uses the server default and ignores the language preference`() {
        val index = TrackSelection.selectAudioIndex(
            streams = streams,
            configuration = config(playDefaultAudioTrack = true, audioLanguagePreference = "eng"),
            defaultAudioStreamIndex = 1,
        )
        assertEquals(1, index)
    }

    @Test
    fun `PlayDefaultAudioTrack false prefers the language match`() {
        val index = TrackSelection.selectAudioIndex(
            streams = streams,
            configuration = config(playDefaultAudioTrack = false, audioLanguagePreference = "eng"),
            defaultAudioStreamIndex = 1,
        )
        assertEquals(2, index)
    }

    @Test
    fun `PlayDefaultAudioTrack false falls back to the default when no language matches`() {
        val index = TrackSelection.selectAudioIndex(
            streams = streams,
            configuration = config(playDefaultAudioTrack = false, audioLanguagePreference = "fre"),
            defaultAudioStreamIndex = 1,
        )
        assertEquals(1, index)
    }

    @Test
    fun `audio falls back to the IsDefault stream when the server sends no default index`() {
        val index = TrackSelection.selectAudioIndex(streams, configuration = null, defaultAudioStreamIndex = null)
        assertEquals(1, index)
    }

    @Test
    fun `audio is null when the source has no audio streams`() {
        val index = TrackSelection.selectAudioIndex(
            streams = listOf(PlaybackMediaStream(index = 0, type = "Video")),
            configuration = config(),
            defaultAudioStreamIndex = null,
        )
        assertNull(index)
    }

    // --- subtitles: one test per SubtitlePlaybackMode --------------------------

    @Test
    fun `SubtitleMode None is always off`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "None", subtitleLanguagePreference = "eng"),
            defaultSubtitleStreamIndex = 3,
            selectedAudioIndex = 1,
        )
        assertEquals(TrackSelection.NO_TRACK, index)
    }

    @Test
    fun `SubtitleMode Always prefers the language match`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "Always", subtitleLanguagePreference = "spa"),
            defaultSubtitleStreamIndex = null,
            selectedAudioIndex = 1,
        )
        assertEquals(5, index)
    }

    @Test
    fun `SubtitleMode Always without a language match takes the first non-forced stream`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "Always", subtitleLanguagePreference = "fre"),
            defaultSubtitleStreamIndex = null,
            selectedAudioIndex = 1,
        )
        assertEquals(3, index)
    }

    @Test
    fun `SubtitleMode OnlyForced picks a forced stream in the preferred language`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "OnlyForced", subtitleLanguagePreference = "eng"),
            defaultSubtitleStreamIndex = 3,
            selectedAudioIndex = 1,
        )
        assertEquals(4, index)
    }

    @Test
    fun `SubtitleMode OnlyForced is off when no forced stream exists`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams.filterNot { it.isForced },
            configuration = config(subtitleMode = "OnlyForced", subtitleLanguagePreference = "eng"),
            defaultSubtitleStreamIndex = 3,
            selectedAudioIndex = 1,
        )
        assertEquals(TrackSelection.NO_TRACK, index)
    }

    @Test
    fun `SubtitleMode Default uses the server default subtitle index`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "Default", subtitleLanguagePreference = "eng"),
            defaultSubtitleStreamIndex = 5,
            selectedAudioIndex = 1,
        )
        assertEquals(5, index)
    }

    @Test
    fun `SubtitleMode Default is off when the server marks no default subtitle`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "Default", subtitleLanguagePreference = "eng"),
            defaultSubtitleStreamIndex = null,
            selectedAudioIndex = 1,
        )
        assertEquals(TrackSelection.NO_TRACK, index)
    }

    @Test
    fun `SubtitleMode Smart subtitles foreign audio`() {
        // Japanese audio, English subtitle preference -> English subtitles on.
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "Smart", subtitleLanguagePreference = "eng"),
            defaultSubtitleStreamIndex = null,
            selectedAudioIndex = 1,
        )
        assertEquals(3, index)
    }

    @Test
    fun `SubtitleMode Smart stays off when audio is already in the preferred language`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "Smart", subtitleLanguagePreference = "eng"),
            defaultSubtitleStreamIndex = 3,
            selectedAudioIndex = 2,
        )
        assertEquals(TrackSelection.NO_TRACK, index)
    }

    @Test
    fun `SubtitleMode Smart is off when no subtitle matches the preference`() {
        val index = TrackSelection.selectSubtitleIndex(
            streams = streams,
            configuration = config(subtitleMode = "Smart", subtitleLanguagePreference = "fre"),
            defaultSubtitleStreamIndex = null,
            selectedAudioIndex = 1,
        )
        assertEquals(TrackSelection.NO_TRACK, index)
    }

    @Test
    fun `an unknown or absent subtitle mode behaves like Default`() {
        assertEquals(
            3,
            TrackSelection.selectSubtitleIndex(streams, config(subtitleMode = null), 3, 1),
        )
    }

    @Test
    fun `no user configuration falls back to the server defaults`() {
        assertEquals(1, TrackSelection.selectAudioIndex(streams, null, 1))
        assertEquals(3, TrackSelection.selectSubtitleIndex(streams, null, 3, 1))
        assertEquals(TrackSelection.NO_TRACK, TrackSelection.selectSubtitleIndex(streams, null, null, 1))
    }

    @Test
    fun `sources with no subtitle streams are off`() {
        assertEquals(
            TrackSelection.NO_TRACK,
            TrackSelection.selectSubtitleIndex(
                streams = streams.filterNot { it.type == "Subtitle" },
                configuration = config(subtitleMode = "Always", subtitleLanguagePreference = "eng"),
                defaultSubtitleStreamIndex = null,
                selectedAudioIndex = 1,
            ),
        )
    }

    @Test
    fun `language matching tolerates 2-letter and 3-letter ISO codes`() {
        assertTrue(TrackSelection.languageMatches("eng", "en"))
        assertTrue(TrackSelection.languageMatches("EN", "eng"))
        assertFalse(TrackSelection.languageMatches("jpn", "eng"))
        assertFalse(TrackSelection.languageMatches(null, "eng"))
        assertFalse(TrackSelection.languageMatches("eng", null))
    }
}
