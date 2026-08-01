package com.maik205.shoumeiplayer.ui.television.screens.settings

import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        rows.single { it.key == "autoplay" }.onClick?.invoke()

        assertEquals(!initial.autoplayNextEpisode, updated.autoplayNextEpisode)
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

    private fun rows(
        section: SettingsSection,
        state: TelevisionSettingsState = TelevisionSettingsState(),
        update: (((ClientSettings) -> ClientSettings) -> Unit) = {},
        onTestConnection: () -> Unit = {},
    ): List<SettingRowModel> = settingsRows(
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
    )
}
