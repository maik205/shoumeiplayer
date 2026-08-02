package com.maik205.shoumeiplayer.player

import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.SubtitleColor
import com.maik205.shoumeiplayer.domain.settings.SubtitleStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #97: mpv's core is process-scoped, so the set of subtitle options written when system
 * captions take over and the set written when they are handed back must be identical -- anything
 * only one side ever touches stays on that side's value for the rest of the process.
 */
class SubtitleOptionResolutionTest {

    private val inApp = ClientSettings(
        subtitleSizePercent = 130,
        subtitleColor = SubtitleColor.Yellow,
        subtitleStroke = SubtitleStroke.Heavy,
    ).toSubtitleAppearance()

    private val systemCaptions = SystemCaptionStyle(
        enabled = true,
        localeTag = null,
        fontScale = 2.0f,
        foregroundColor = 0xFF00FF00.toInt(),
        backgroundColor = 0xCC101010.toInt(),
        edgeType = 2,
        edgeColor = 0xFF0000FF.toInt(),
        typefaceName = null,
    )

    @Test
    fun `enabling and disabling system captions touch exactly the same options`() {
        val withCaptions = resolveSubtitleOptions(inApp, systemCaptions, "eng,en")
        val withoutCaptions = resolveSubtitleOptions(inApp, systemCaptions.copy(enabled = false), "eng,en")

        assertEquals(withCaptions.keys, withoutCaptions.keys)
    }

    @Test
    fun `turning system captions off restores every option they overwrote`() {
        val withCaptions = resolveSubtitleOptions(inApp, systemCaptions, "eng,en")
        val restored = resolveSubtitleOptions(inApp, null, "eng,en")

        // The two the previous fix left stuck on the accessibility value forever.
        assertNotEquals(withCaptions["sub-back-color"], restored["sub-back-color"])
        assertEquals("#00000000", restored["sub-back-color"])
        assertNotEquals(withCaptions["sub-border-color"], restored["sub-border-color"])
        assertEquals("#FF000000", restored["sub-border-color"])
        // ...and the rest of the in-app appearance comes back with them.
        assertEquals("1.3", restored["sub-scale"])
        assertEquals("#FFF176", restored["sub-color"])
        assertEquals("4", restored["sub-border-size"])
        assertEquals("sans-serif", restored["sub-font"])
    }

    @Test
    fun `an enabled system style still wins for appearance`() {
        val resolved = resolveSubtitleOptions(inApp, systemCaptions, "eng,en")

        assertEquals("2.0", resolved["sub-scale"])
        assertEquals("#FF00FF00", resolved["sub-color"])
        assertEquals("#CC101010", resolved["sub-back-color"])
        assertEquals("#FF0000FF", resolved["sub-border-color"])
        assertEquals("1", resolved["sub-border-size"])
    }

    @Test
    fun `a caption style reported while captions are off does not take over`() {
        val resolved = resolveSubtitleOptions(inApp, systemCaptions.copy(enabled = false), "eng,en")

        assertEquals("1.3", resolved["sub-scale"])
        assertEquals("#FFF176", resolved["sub-color"])
    }

    // --- slang: exactly one writer -------------------------------------------

    @Test
    fun `the account subtitle language is honoured with system captions on and no caption locale`() {
        // The common case: captions enabled, but the user never picked a caption language, so
        // CaptioningManager.locale is null. This is where slang stopped being written at all.
        val resolved = resolveSubtitleOptions(inApp, systemCaptions, "jpn,ja")

        assertEquals("jpn,ja", resolved["slang"])
    }

    @Test
    fun `the account subtitle language is honoured with system captions off`() {
        assertEquals("jpn,ja", resolveSubtitleOptions(inApp, null, "jpn,ja")["slang"])
    }

    @Test
    fun `an explicitly chosen caption locale wins over the account language`() {
        val resolved = resolveSubtitleOptions(inApp, systemCaptions.copy(localeTag = "fr-FR"), "jpn,ja")

        assertEquals("fr-FR", resolved["slang"])
    }

    @Test
    fun `slang is always resolved, whatever the combination of inputs`() {
        val captionStates = listOf(
            null,
            systemCaptions,
            systemCaptions.copy(enabled = false),
            systemCaptions.copy(localeTag = "de-DE"),
            systemCaptions.copy(enabled = false, localeTag = "de-DE"),
        )

        captionStates.forEach { captions ->
            val resolved = resolveSubtitleOptions(inApp, captions, "vie,vi")

            assertTrue("$captions did not resolve slang", resolved.containsKey("slang"))
        }
    }

    @Test
    fun `an account with no subtitle language leaves mpv's own ordering alone`() {
        assertEquals("", resolveSubtitleOptions(inApp, null, "")["slang"])
    }

    // --- the account language mapping mpv is handed ---------------------------

    @Test
    fun `account language codes map onto every alias a stream may be tagged with`() {
        assertEquals("jpn,ja", "jpn".toMpvLanguagePreference())
        assertEquals("jpn,ja", "ja".toMpvLanguagePreference())
        assertEquals("deu,ger,de", "ger".toMpvLanguagePreference())
        assertEquals("fra,fre,fr", "fre".toMpvLanguagePreference())
        assertEquals("vie,vi", "vie".toMpvLanguagePreference())
    }

    @Test
    fun `English display names written by older builds still map`() {
        assertEquals("eng,en", "English".toMpvLanguagePreference())
        assertEquals("jpn,ja", "Japanese".toMpvLanguagePreference())
    }

    @Test
    fun `an unknown or absent preference leaves mpv's own ordering alone`() {
        assertEquals("", null.toMpvLanguagePreference())
        assertEquals("", "klingon".toMpvLanguagePreference())
    }
}
