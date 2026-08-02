package com.maik205.shoumeiplayer.ui.television.screens.settings

import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.ColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #101: the palette is only user-selectable if Settings actually offers it and the choice actually
 * reaches [ClientSettings]. Both halves are asserted -- a row that renders but writes nothing is the
 * shape this feature would fail in.
 */
class ColorPaletteRowTest {

    @Test
    fun `the interface section offers every palette and applies the chosen one`() {
        var updated = ClientSettings()
        val row = interfaceRows { transform -> updated = transform(updated) }
            .single { it.key == "color-palette" }

        assertEquals(ColorPalette.entries.size, row.choices.size)
        assertEquals(1, row.choices.count(SettingChoiceOption::selected))
        assertEquals(R.string.tv_settings_palette_midnight.toString(), row.value)

        row.choices.single { it.label == R.string.tv_settings_palette_sunrise.toString() }.onSelect()

        assertEquals(ColorPalette.Sunrise, updated.colorPalette)
    }

    @Test
    fun `the row reflects the palette this profile already chose`() {
        val row = interfaceRows(ClientSettings(colorPalette = ColorPalette.Ember))
            .single { it.key == "color-palette" }

        assertEquals(R.string.tv_settings_palette_ember.toString(), row.value)
        assertTrue(
            row.choices.single { it.selected }.label == R.string.tv_settings_palette_ember.toString(),
        )
    }

    private fun interfaceRows(
        settings: ClientSettings = ClientSettings(),
        update: (((ClientSettings) -> ClientSettings) -> Unit) = {},
    ): List<SettingRowModel> = settingsRowsForStrings(
        section = SettingsSection.Interface,
        state = TelevisionSettingsState(settings = settings),
        update = update,
        onSignOut = {},
        onChangeServer = {},
        onForgetServer = {},
        onSwitchProfile = {},
        onTestConnection = {},
        onRefreshLibraries = {},
        onQuickConnect = {},
        onClearArtworkCache = {},
        resolve = { id, _ -> id.toString() },
        resolvePlural = { id, _, _ -> id.toString() },
    )
}
