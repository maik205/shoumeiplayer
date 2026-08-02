package com.maik205.shoumeiplayer.ui.television.screens.settings

import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import com.maik205.shoumeiplayer.domain.settings.DisplayLanguage
import com.maik205.shoumeiplayer.domain.settings.ServerLanguage
import com.maik205.shoumeiplayer.domain.settings.SubtitleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelevisionSettingsCatalogTest {
    @Test
    fun `every settings section has stable unique row keys`() {
        SettingsSection.entries.forEach { section ->
            val rows = rows(section)

            assertFalse("$section should not be empty", rows.isEmpty())
            assertEquals(
                "$section contains duplicate row keys",
                rows.size,
                rows.map(SettingRowModel::key).distinct().size,
            )
        }
    }

    @Test
    fun `toggle rows update the current settings snapshot`() {
        val initial = ClientSettings()
        var updated = initial
        val rows = rows(
            section = SettingsSection.Playback,
            state = TelevisionSettingsState(settings = initial),
            update = { transform -> updated = transform(updated) },
        )

        rows.single { it.key == "skip-intro" }.onClick?.invoke()

        assertEquals(!initial.skipIntroPrompt, updated.skipIntroPrompt)
    }

    @Test
    fun `choice rows expose one selected option and apply the chosen value`() {
        val initial = ClientSettings()
        var updated = initial
        val rows = rows(
            section = SettingsSection.Playback,
            state = TelevisionSettingsState(settings = initial),
            update = { transform -> updated = transform(updated) },
        )
        val seek = rows.single { it.key == "seek" }
        val replacement = seek.choices.first { !it.selected }

        assertEquals(1, seek.choices.count(SettingChoiceOption::selected))
        replacement.onSelect()

        assertEquals(replacement.label, "${updated.seekIntervalSeconds} seconds")
    }

    @Test
    fun `display language defaults to system default and offers it as a choice`() {
        val initial = ClientSettings()
        var updated = initial
        val rows = rows(
            section = SettingsSection.Interface,
            state = TelevisionSettingsState(settings = initial),
            update = { transform -> updated = transform(updated) },
        )
        val displayLanguage = rows.single { it.key == "display-language" }

        assertEquals(DisplayLanguage.SystemDefault, initial.displayLanguage)
        assertTrue(displayLanguage.choices.any { it.selected })
        val english = displayLanguage.choices.single { it.label == R.string.tv_settings_english.toString() }
        english.onSelect()

        assertEquals(DisplayLanguage.English, updated.displayLanguage)
    }

    /**
     * #86: Kids Mode gated nothing -- no rating cap, no library restriction, no PIN. A caregiver
     * who switches it on and believes it works is worse off than one who was never offered it, so
     * the row must not come back anywhere in the catalog until enforcement exists.
     */
    @Test
    fun `no section offers a kids mode toggle`() {
        val offending = SettingsSection.entries.filter { section ->
            rows(section).any { it.key == "kids-mode" }
        }

        assertEquals(emptyList<SettingsSection>(), offending)
    }

    @Test
    fun `server actions remain wired to their supplied callbacks`() {
        var tested = false
        val rows = rows(
            section = SettingsSection.Server,
            onTestConnection = { tested = true },
        )

        rows.single { it.key == "test-connection" }.onClick?.invoke()

        assertTrue(tested)
    }

    @Test
    fun `unsupported device controls are omitted from their sections`() {
        val state = TelevisionSettingsState(
            capabilities = DevicePlaybackCapabilities(
                supportsRefreshRateSwitching = false,
                supportsDolbyDigitalPassthrough = false,
                supportsDolbyDigitalPlusPassthrough = false,
                supportsDtsPassthrough = false,
            ),
        )

        assertFalse(rows(SettingsSection.Playback, state).any { it.key == "refresh-rate" })
        assertEquals(
            emptyList<String>(),
            rows(SettingsSection.Audio, state)
                .filter { it.key in setOf("ac3", "eac3", "dts") }
                .map(SettingRowModel::key),
        )
    }

    // --- server-owned rows (#93, #94, #100) -----------------------------------
    //
    // These rows must read the account preference and write through the server editor. Routing
    // either side back through ClientSettings is exactly the bug being fixed, so both assertions
    // matter: the value shown, and which callback the row invokes.

    @Test
    fun `audio language row renders the account value and edits the account`() {
        val edits = ServerPreferenceRecorder(ServerPreferences(audioLanguage = "eng"))
        var clientUpdates = 0
        val rows = rows(
            section = SettingsSection.Audio,
            state = TelevisionSettingsState(
                serverPreferences = edits.current,
                editServerPreferences = edits,
            ),
            update = { clientUpdates++ },
        )
        val row = rows.single { it.key == "audio-language" }

        assertEquals(R.string.tv_settings_english.toString(), row.value)
        row.choices.single { it.label == R.string.tv_settings_japanese.toString() }.onSelect()

        assertEquals("jpn", edits.current.audioLanguage)
        assertEquals(0, clientUpdates)
    }

    @Test
    fun `subtitle language row offers no preference and clears the account value`() {
        val edits = ServerPreferenceRecorder(ServerPreferences(subtitleLanguage = "ger"))
        val rows = rows(
            section = SettingsSection.Subtitles,
            state = TelevisionSettingsState(
                serverPreferences = edits.current,
                editServerPreferences = edits,
            ),
        )
        val row = rows.single { it.key == "subtitle-language" }

        row.choices.single { it.label == R.string.settings_language_system_default.toString() }.onSelect()

        assertNull(edits.current.subtitleLanguage)
    }

    @Test
    fun `subtitle mode row renders the account mode and edits the account`() {
        val edits = ServerPreferenceRecorder(ServerPreferences(subtitleMode = SubtitleMode.Smart))
        var clientUpdates = 0
        val rows = rows(
            section = SettingsSection.Subtitles,
            state = TelevisionSettingsState(
                serverPreferences = edits.current,
                editServerPreferences = edits,
            ),
            update = { clientUpdates++ },
        )
        val row = rows.single { it.key == "subtitle-mode" }

        assertEquals(R.string.tv_settings_smart.toString(), row.value)
        row.choices.single { it.label == R.string.tv_settings_only_forced.toString() }.onSelect()

        assertEquals(SubtitleMode.OnlyForced, edits.current.subtitleMode)
        assertEquals(0, clientUpdates)
    }

    @Test
    fun `autoplay row toggles the account preference, not a client setting`() {
        val edits = ServerPreferenceRecorder(ServerPreferences(autoplayNextEpisode = true))
        var clientUpdates = 0
        val rows = rows(
            section = SettingsSection.Playback,
            state = TelevisionSettingsState(
                serverPreferences = edits.current,
                editServerPreferences = edits,
            ),
            update = { clientUpdates++ },
        )
        val row = rows.single { it.key == "autoplay" }

        assertTrue(row.checked)
        row.onClick?.invoke()

        assertFalse(edits.current.autoplayNextEpisode)
        assertEquals(0, clientUpdates)
    }

    private class ServerPreferenceRecorder(
        var current: ServerPreferences,
    ) : ServerPreferenceEditor {
        override fun edit(transform: (ServerPreferences) -> ServerPreferences) {
            current = transform(current)
        }
    }

    private fun rows(
        section: SettingsSection,
        state: TelevisionSettingsState = TelevisionSettingsState(),
        update: (((ClientSettings) -> ClientSettings) -> Unit) = {},
        onTestConnection: () -> Unit = {},
    ): List<SettingRowModel> = settingsRowsForStrings(
        section = section,
        state = state,
        update = update,
        onSignOut = {},
        onChangeServer = {},
        onForgetServer = {},
        onSwitchProfile = {},
        onTestConnection = onTestConnection,
        onRefreshLibraries = {},
        onQuickConnect = {},
        onClearArtworkCache = {},
        resolve = { id, _ -> id.toString() },
        resolvePlural = { id, quantity, args ->
            when (id) {
                R.plurals.tv_value_seconds -> "${args.single()} ${if (quantity == 1) "second" else "seconds"}"
                else -> id.toString()
            }
        },
    )
}
