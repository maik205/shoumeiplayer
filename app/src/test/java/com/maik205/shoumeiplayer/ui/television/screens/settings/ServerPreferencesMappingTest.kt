package com.maik205.shoumeiplayer.ui.television.screens.settings

import com.maik205.shoumeiplayer.data.api.dto.UserConfigurationDto
import com.maik205.shoumeiplayer.domain.settings.ServerLanguage
import com.maik205.shoumeiplayer.domain.settings.SubtitleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The translation the settings screen performs between Jellyfin's `UserConfiguration` and the rows
 * the user sees. `POST /Users/Configuration` replaces the whole document, so a mistake here silently
 * rewrites preferences the user never touched.
 */
class ServerPreferencesMappingTest {

    private val account = UserConfigurationDto(
        audioLanguagePreference = "jpn",
        playDefaultAudioTrack = false,
        subtitleLanguagePreference = "eng",
        subtitleMode = "Smart",
        enableNextEpisodeAutoPlay = false,
        rememberAudioSelections = false,
        displayMissingEpisodes = true,
        orderedViews = listOf("view-1", "view-2"),
        castReceiverId = "cast-1",
    )

    @Test
    fun `account values become the rows the user sees`() {
        val shown = account.toServerPreferences()

        assertEquals("jpn", shown.audioLanguage)
        assertEquals("eng", shown.subtitleLanguage)
        assertEquals(SubtitleMode.Smart, shown.subtitleMode)
        assertFalse(shown.autoplayNextEpisode)
    }

    /**
     * Jellyfin accepts any ISO 639 code and other clients write ones this app has no label for.
     * Since every server-owned row folds the whole [ServerPreferences] back onto the document, a
     * language parsed through a five-entry enum came back as null and was POSTed as null -- so
     * toggling an unrelated setting erased the viewer's language from their account, on every
     * client. Unrecognised codes must survive untouched.
     */
    @Test
    fun `a language this app has no label for survives an unrelated edit`() {
        val spanish = account.copy(audioLanguagePreference = "spa", subtitleLanguagePreference = "kor")

        val afterTogglingAutoplay = spanish.toServerPreferences()
            .copy(autoplayNextEpisode = true)
            .applyTo(spanish)

        assertEquals("spa", afterTogglingAutoplay.audioLanguagePreference)
        assertEquals("kor", afterTogglingAutoplay.subtitleLanguagePreference)
        assertTrue(afterTogglingAutoplay.enableNextEpisodeAutoPlay)
    }

    @Test
    fun `a device that has never reached the server shows Jellyfin's own defaults`() {
        val shown = (null as UserConfigurationDto?).toServerPreferences()

        assertNull(shown.audioLanguage)
        assertNull(shown.subtitleLanguage)
        assertEquals(SubtitleMode.Default, shown.subtitleMode)
        assertTrue(shown.autoplayNextEpisode)
    }

    /**
     * An older build wrote English display names, and other clients write two-letter codes. The
     * mapping keeps whatever the account holds verbatim -- resolving it to a [ServerLanguage] is a
     * display concern the row handles, so normalising here would rewrite the account on the next
     * unrelated edit rather than only when the viewer actually picks a language.
     */
    @Test
    fun `legacy language spellings are carried through rather than rewritten`() {
        val legacy = account.copy(audioLanguagePreference = "Japanese", subtitleLanguagePreference = "de")

        val shown = legacy.toServerPreferences()
        assertEquals("Japanese", shown.audioLanguage)
        assertEquals("de", shown.subtitleLanguage)

        // ...and the account still reads back unchanged when a different row is edited.
        assertEquals("Japanese", shown.copy(autoplayNextEpisode = true).applyTo(legacy).audioLanguagePreference)

        // The row can still label them, which is why normalising in the mapping was unnecessary.
        assertEquals(ServerLanguage.Japanese, ServerLanguage.fromStored("Japanese"))
        assertEquals(ServerLanguage.German, ServerLanguage.fromStored("de"))
    }

    @Test
    fun `editing one row leaves every property the screen does not own untouched`() {
        val edited = account.toServerPreferences()
            .copy(subtitleMode = SubtitleMode.Always)
            .applyTo(account)

        assertEquals("Always", edited.subtitleMode)
        assertEquals(account.copy(subtitleMode = "Always"), edited)
    }

    @Test
    fun `choosing an audio language stops the server default track from overriding it`() {
        val edited = account.toServerPreferences()
            .copy(audioLanguage = "vie")
            .applyTo(account.copy(playDefaultAudioTrack = true))

        // TrackSelection.selectAudioIndex returns the server default outright when this is true,
        // so leaving it on would make the language the user just picked do nothing.
        assertEquals("vie", edited.audioLanguagePreference)
        assertFalse(edited.playDefaultAudioTrack)
    }

    @Test
    fun `clearing the audio language leaves the account's default-track flag alone`() {
        val edited = account.toServerPreferences()
            .copy(audioLanguage = null)
            .applyTo(account.copy(playDefaultAudioTrack = true))

        assertNull(edited.audioLanguagePreference)
        assertTrue(edited.playDefaultAudioTrack)
    }

    @Test
    fun `subtitle mode round-trips through the wire value the server understands`() {
        SubtitleMode.entries.forEach { mode ->
            val written = account.toServerPreferences().copy(subtitleMode = mode).applyTo(account)

            assertEquals(mode.storageId, written.subtitleMode)
            assertEquals(mode, written.toServerPreferences().subtitleMode)
        }
    }

    @Test
    fun `autoplay round-trips as the account's next-episode flag`() {
        val enabled = account.toServerPreferences().copy(autoplayNextEpisode = true).applyTo(account)

        assertTrue(enabled.enableNextEpisodeAutoPlay)
        assertTrue(enabled.toServerPreferences().autoplayNextEpisode)
    }
}
