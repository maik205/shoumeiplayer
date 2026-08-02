package com.maik205.shoumeiplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for GitHub issue #96: `TrackSelection.languageMatches` must unify an
 * arbitrary ISO 639 code pulled from the server's `UserConfiguration` (an arbitrary account
 * preference) with an arbitrary language tag pulled from stream metadata (arbitrary file
 * tagging), across the full ISO 639-1 / 639-2/B / 639-2/T space — not just the five languages
 * Shoumei's own settings dropdown offers.
 *
 * Every case here drives [TrackSelection.selectAudioIndex] end-to-end (the same entry point
 * `TrackController.prepare` calls when real playback starts) rather than asserting on an
 * internal constant, so each test fails against the pre-fix prefix-heuristic implementation if
 * the fix in `TrackSelection.kt` is reverted.
 */
class TrackSelectionLanguageMatchingTest {

    private fun audio(index: Int, language: String?, isDefault: Boolean = false) =
        PlaybackMediaStream(index = index, type = "Audio", language = language, isDefault = isDefault)

    /** Two audio tracks: the server default (index 0) and a language-tagged alternative (index 1). */
    private fun streamsFor(defaultLanguage: String, alternativeLanguage: String) = listOf(
        audio(0, defaultLanguage, isDefault = true),
        audio(1, alternativeLanguage),
    )

    private fun config(audioLanguagePreference: String) = PlaybackTrackPreferences(
        playDefaultAudioTrack = false,
        audioLanguagePreference = audioLanguagePreference,
    )

    private fun assertMatches(streamLanguage: String, preference: String) {
        val streams = streamsFor(defaultLanguage = "und", alternativeLanguage = streamLanguage)
        val index = TrackSelection.selectAudioIndex(
            streams = streams,
            configuration = config(preference),
            defaultAudioStreamIndex = 0,
        )
        assertEquals(
            "expected stream tagged '$streamLanguage' to match preference '$preference'",
            1,
            index,
        )
    }

    // --- cross-standard pairs (639-1 vs 639-2/B vs 639-2/T) -------------------

    @Test
    fun `spanish 639-1 matches 639-2 code`() = assertMatches(streamLanguage = "spa", preference = "es")

    @Test
    fun `chinese 639-1 matches both 639-2 variants`() {
        assertMatches(streamLanguage = "zho", preference = "zh")
        assertMatches(streamLanguage = "chi", preference = "zh")
        assertMatches(streamLanguage = "chi", preference = "zho")
    }

    @Test
    fun `dutch 639-1 matches both 639-2 variants`() {
        assertMatches(streamLanguage = "nld", preference = "nl")
        assertMatches(streamLanguage = "dut", preference = "nl")
        assertMatches(streamLanguage = "dut", preference = "nld")
    }

    @Test
    fun `german 639-1 matches both 639-2 variants`() {
        assertMatches(streamLanguage = "deu", preference = "de")
        assertMatches(streamLanguage = "ger", preference = "de")
        assertMatches(streamLanguage = "ger", preference = "deu")
    }

    @Test
    fun `additional 639-2B codes resolve to their 639-1 preference`() {
        // sv/swe and pl/pol have no B/T split (Locale alone resolves them); cs/cze/ces,
        // el/gre/ell, ro/rum/ron and is/ice/isl do, exercising the explicit B-code table.
        assertMatches(streamLanguage = "swe", preference = "sv")
        assertMatches(streamLanguage = "pol", preference = "pl")
        assertMatches(streamLanguage = "cze", preference = "cs")
        assertMatches(streamLanguage = "ces", preference = "cs")
        assertMatches(streamLanguage = "gre", preference = "el")
        assertMatches(streamLanguage = "ell", preference = "el")
        assertMatches(streamLanguage = "rum", preference = "ro")
        assertMatches(streamLanguage = "ron", preference = "ro")
        assertMatches(streamLanguage = "ice", preference = "is")
        assertMatches(streamLanguage = "isl", preference = "is")
    }

    // --- region-tagged variants (the previous run's regression) ---------------

    @Test
    fun `region-tagged stream tags still match a bare language preference`() {
        assertMatches(streamLanguage = "de-DE", preference = "de")
        assertMatches(streamLanguage = "en-US", preference = "en")
        assertMatches(streamLanguage = "fr-CA", preference = "fr")
        assertMatches(streamLanguage = "ja-JP", preference = "ja")
        assertMatches(streamLanguage = "vi-VN", preference = "vi")
    }

    @Test
    fun `region-tagged stream still matches a cross-standard preference`() {
        // Combines both defects: a region subtag AND a different ISO 639 standard.
        assertMatches(streamLanguage = "de-DE", preference = "ger")
        assertMatches(streamLanguage = "es-419", preference = "spa")
    }

    // --- persisted English display names ---------------------------------------

    @Test
    fun `english display name preference matches a tagged stream`() {
        val streams = streamsFor(defaultLanguage = "und", alternativeLanguage = "de")
        val index = TrackSelection.selectAudioIndex(
            streams = streams,
            configuration = config("German"),
            defaultAudioStreamIndex = 0,
        )
        assertEquals(1, index)
    }

    @Test
    fun `japanese display name preference matches a 639-2 tagged stream`() {
        val streams = streamsFor(defaultLanguage = "und", alternativeLanguage = "jpn")
        val index = TrackSelection.selectAudioIndex(
            streams = streams,
            configuration = config("Japanese"),
            defaultAudioStreamIndex = 0,
        )
        assertEquals(1, index)
    }

    // --- genuine non-match -------------------------------------------------------

    @Test
    fun `unrelated languages do not match and selection falls back to the server default`() {
        val streams = streamsFor(defaultLanguage = "jpn", alternativeLanguage = "spa")
        val index = TrackSelection.selectAudioIndex(
            streams = streams,
            configuration = config("fre"),
            defaultAudioStreamIndex = 0,
        )
        assertEquals(0, index)
    }

    // --- direct assertions on the matcher itself --------------------------------
    // (internal, exercised the same way selectAudioIndex/selectSubtitleIndex call it, but
    // asserted directly for a clear true/false signal on the boundary cases above)

    @Test
    fun `languageMatches is true for every cross-standard pair under test`() {
        assertTrue(TrackSelection.languageMatches("spa", "es"))
        assertTrue(TrackSelection.languageMatches("zh", "chi"))
        assertTrue(TrackSelection.languageMatches("nl", "dut"))
        assertTrue(TrackSelection.languageMatches("de", "ger"))
        assertTrue(TrackSelection.languageMatches("de-DE", "de"))
        assertTrue(TrackSelection.languageMatches("en-US", "en"))
        assertTrue(TrackSelection.languageMatches("German", "de"))
    }

    @Test
    fun `languageMatches is false for genuinely different languages`() {
        assertFalse(TrackSelection.languageMatches("jpn", "fre"))
        assertFalse(TrackSelection.languageMatches("spa", "deu"))
    }
}
