package com.maik205.shoumeiplayer.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.data.session.UserScope
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.ColorPalette
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #101 stores the palette on the **personal** side of the split #85 introduced: which colours the UI
 * wears is taste, not a fact about the panel, so it must not follow one viewer into another
 * viewer's profile. Every test drives the real store rather than asserting on defaults, because the
 * failure being guarded against is "the setting was declared and never persisted".
 */
class ColorPaletteSettingTest {

    private val alice = UserScope(serverUrl = "https://media.example", userId = "alice")
    private val bob = UserScope(serverUrl = "https://media.example", userId = "bob")

    @Test
    fun `a chosen palette survives a restart of the store`() = runTest {
        val backing = InMemoryPreferencesDataStore()

        SettingsStore(backing, flowOf(alice)).update { it.copy(colorPalette = ColorPalette.Sunrise) }

        assertEquals(ColorPalette.Sunrise, SettingsStore(backing, flowOf(alice)).current().colorPalette)
    }

    @Test
    fun `one profile's palette does not follow the viewer into another profile`() = runTest {
        val backing = InMemoryPreferencesDataStore()

        SettingsStore(backing, flowOf(alice)).update { it.copy(colorPalette = ColorPalette.Sunrise) }
        SettingsStore(backing, flowOf(bob)).update { it.copy(colorPalette = ColorPalette.Ember) }

        assertEquals(ColorPalette.Sunrise, SettingsStore(backing, flowOf(alice)).current().colorPalette)
        assertEquals(ColorPalette.Ember, SettingsStore(backing, flowOf(bob)).current().colorPalette)
    }

    @Test
    fun `an install that never chose a palette keeps the shipped one`() = runTest {
        val backing = InMemoryPreferencesDataStore()

        assertEquals(ColorPalette.Midnight, SettingsStore(backing, flowOf(alice)).current().colorPalette)
    }

    /**
     * A value written by a build that shipped a palette this one does not have must not make the UI
     * unreadable; falling back to the default is the only safe reading.
     */
    @Test
    fun `an unrecognised stored palette falls back to the default`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        backing.edit { it[stringPreferencesKey("color_palette")] = "aurora" }

        assertEquals(ColorPalette.Midnight, SettingsStore(backing).current().colorPalette)
    }

    @Test
    fun `saving an unrelated setting does not drop the palette`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        val store = SettingsStore(backing, flowOf(alice))
        store.update { it.copy(colorPalette = ColorPalette.Daylight) }

        store.save(store.current().copy(subtitleSizePercent = 150))

        val reloaded: ClientSettings = SettingsStore(backing, flowOf(alice)).current()
        assertEquals(ColorPalette.Daylight, reloaded.colorPalette)
        assertEquals(150, reloaded.subtitleSizePercent)
    }
}
