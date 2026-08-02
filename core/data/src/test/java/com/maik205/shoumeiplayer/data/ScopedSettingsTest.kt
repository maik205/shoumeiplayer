package com.maik205.shoumeiplayer.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.data.session.UserScope
import com.maik205.shoumeiplayer.domain.settings.AppTheme
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.PlaybackBackend
import com.maik205.shoumeiplayer.domain.settings.PreferredQuality
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import com.maik205.shoumeiplayer.domain.settings.SubtitleColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #85: settings used to be one global file, so the profile you switched *to* inherited the taste of
 * the profile you switched *from*.
 *
 * Every test drives the real load/write path — a [SettingsStore] over a DataStore, with the scope
 * supplied the way `AppContainer` supplies it — rather than asserting on defaults, because the
 * defect class is "the split was declared and the read path ignored it".
 */
class ScopedSettingsTest {

    private val alice = UserScope(serverUrl = "https://media.example", userId = "alice")
    private val bob = UserScope(serverUrl = "https://media.example", userId = "bob")
    private val aliceElsewhere = UserScope(serverUrl = "https://other.example", userId = "alice")

    /** The populated settings file an install carried before scoping existed. */
    private val preScoping = ClientSettings(
        theme = AppTheme.System,
        subtitleSizePercent = 150,
        subtitleColor = SubtitleColor.Yellow,
        screensaverContent = ScreensaverContent.Movies,
        screensaverShuffle = false,
        preferredQuality = PreferredQuality.Hd,
        maxStreamingBitrateMbps = 12,
        seekIntervalSeconds = 30,
        playbackBackend = PlaybackBackend.System,
        forwardCacheMiB = 256,
    )

    private suspend fun populatedPreScopingStore(): InMemoryPreferencesDataStore {
        val backing = InMemoryPreferencesDataStore()
        // No scope: exactly the layout every existing install has on disk today.
        SettingsStore(backing).save(preScoping)
        return backing
    }

    @Test
    fun `the signed-in user inherits the settings a pre-scoping install already had`() = runTest {
        val backing = populatedPreScopingStore()

        val migrated = SettingsStore(backing, flowOf(alice)).current()

        assertEquals(AppTheme.System, migrated.theme)
        assertEquals(150, migrated.subtitleSizePercent)
        assertEquals(SubtitleColor.Yellow, migrated.subtitleColor)
        assertEquals(ScreensaverContent.Movies, migrated.screensaverContent)
        assertFalse(migrated.screensaverShuffle)
        assertEquals(PreferredQuality.Hd, migrated.preferredQuality)
        assertEquals(12, migrated.maxStreamingBitrateMbps)
        assertEquals(30, migrated.seekIntervalSeconds)
    }

    @Test
    fun `adopted settings survive a restart of the store`() = runTest {
        val backing = populatedPreScopingStore()
        SettingsStore(backing, flowOf(alice)).current()

        // A second store over the same file is the next process launch: the migration has already
        // run and moved the bytes, so nothing may fall back to defaults here.
        val reloaded = SettingsStore(backing, flowOf(alice)).current()

        assertEquals(AppTheme.System, reloaded.theme)
        assertEquals(150, reloaded.subtitleSizePercent)
    }

    @Test
    fun `a second profile does not inherit the first profile's personal settings`() = runTest {
        val backing = populatedPreScopingStore()
        val scope = MutableStateFlow<UserScope?>(alice)
        val store = SettingsStore(backing, scope)
        assertEquals(150, store.current().subtitleSizePercent)

        scope.value = bob

        val bobSettings = store.current()
        assertEquals(ClientSettings().subtitleSizePercent, bobSettings.subtitleSizePercent)
        assertEquals(ClientSettings().theme, bobSettings.theme)
        assertEquals(ClientSettings().preferredQuality, bobSettings.preferredQuality)
        assertTrue(bobSettings.screensaverShuffle)
    }

    @Test
    fun `the same user on a different server is a different profile`() = runTest {
        val backing = populatedPreScopingStore()
        val scope = MutableStateFlow<UserScope?>(alice)
        val store = SettingsStore(backing, scope)
        store.current()

        scope.value = aliceElsewhere

        assertEquals(ClientSettings().subtitleSizePercent, store.current().subtitleSizePercent)
    }

    @Test
    fun `editing one profile leaves the other profile alone`() = runTest {
        val backing = populatedPreScopingStore()
        val scope = MutableStateFlow<UserScope?>(alice)
        val store = SettingsStore(backing, scope)
        store.current()

        scope.value = bob
        store.update { it.copy(subtitleSizePercent = 75, screensaverTimeoutMinutes = 20) }
        scope.value = alice

        assertEquals(150, store.current().subtitleSizePercent)
        assertEquals(ClientSettings().screensaverTimeoutMinutes, store.current().screensaverTimeoutMinutes)
        scope.value = bob
        assertEquals(75, store.current().subtitleSizePercent)
    }

    @Test
    fun `device settings stay shared across profiles`() = runTest {
        val backing = populatedPreScopingStore()
        val scope = MutableStateFlow<UserScope?>(alice)
        val store = SettingsStore(backing, scope)
        store.current()

        scope.value = bob

        // The TV's decoder and cache budget are facts about the box, not about who is watching.
        assertEquals(PlaybackBackend.System, store.current().playbackBackend)
        assertEquals(256, store.current().forwardCacheMiB)

        store.update { it.copy(forwardCacheMiB = 512) }
        scope.value = alice
        assertEquals(512, store.current().forwardCacheMiB)
    }

    @Test
    fun `per-user setters write to the signed-in profile only`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        val scope = MutableStateFlow<UserScope?>(alice)
        val store = SettingsStore(backing, scope)

        store.setPreferredQuality(PreferredQuality.Sd.storageId)
        store.setAudioDelayMs(-120)
        store.setSubtitleDelayMs(80)
        store.setFocusScaleEnabled(false)
        store.setClockInOsd(false)

        scope.value = bob
        val bobSettings = store.current()
        assertEquals(PreferredQuality.Auto, bobSettings.preferredQuality)
        assertEquals(0, bobSettings.audioDelayMs)
        assertEquals(0, bobSettings.subtitleDelayMs)
        assertTrue(bobSettings.focusScaleEnabled)
        assertTrue(bobSettings.clockInOsd)

        scope.value = alice
        val aliceSettings = store.current()
        assertEquals(PreferredQuality.Sd, aliceSettings.preferredQuality)
        assertEquals(-120, aliceSettings.audioDelayMs)
        assertEquals(80, aliceSettings.subtitleDelayMs)
        assertFalse(aliceSettings.focusScaleEnabled)
        assertFalse(aliceSettings.clockInOsd)
    }

    @Test
    fun `the migration leaves the account preferences it does not own alone`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        backing.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[stringPreferencesKey("preferred_audio_language")] = "Japanese"
                this[stringPreferencesKey("subtitle_mode")] = "Always"
                this[booleanPreferencesKey("autoplay_next_episode")] = false
            }
        }

        SettingsStore(backing, flowOf(alice)).current()

        // These are still owed to the Jellyfin account; moving or deleting them here would lose
        // choices the viewer made before those preferences became server-owned.
        val retired = SettingsStore(backing, flowOf(alice)).retiredAccountPreferences()
        assertNotNull(retired)
        assertEquals("Japanese", retired?.audioLanguage)
        assertEquals("Always", retired?.subtitleMode)
        assertEquals(false, retired?.autoplayNextEpisode)
    }

    @Test
    fun `an unrecognised pre-scoping key moves with the user instead of being dropped`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        backing.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[intPreferencesKey("a_setting_this_build_never_heard_of")] = 7
            }
        }

        SettingsStore(backing, flowOf(alice)).current()

        // A value we failed to understand is still a value the viewer chose. It must end up in
        // their namespace, not in the bin.
        val stored = backing.data.first().asMap()
            .filterKeys { it.name.endsWith("a_setting_this_build_never_heard_of") }
        assertEquals(1, stored.size)
        assertEquals(7, stored.values.single())
        assertTrue(stored.keys.single().name.startsWith(alice.storagePrefix))
    }

    /** #86: Kids Mode gated nothing, so there is no value to carry into anybody's profile. */
    @Test
    fun `kids mode is dropped rather than copied into the profile`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        backing.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[booleanPreferencesKey("kids_mode")] = true
            }
        }

        SettingsStore(backing, flowOf(alice)).current()

        assertNull(backing.data.first().asMap().keys.firstOrNull { it.name.endsWith("kids_mode") })
    }

    @Test
    fun `signing out falls back to the device slot without touching a profile`() = runTest {
        val backing = populatedPreScopingStore()
        val scope = MutableStateFlow<UserScope?>(alice)
        val store = SettingsStore(backing, scope)
        store.current()

        scope.value = null
        store.update { it.copy(subtitleSizePercent = 90) }

        assertEquals(90, store.current().subtitleSizePercent)
        scope.value = alice
        assertEquals(150, store.current().subtitleSizePercent)
    }
}
