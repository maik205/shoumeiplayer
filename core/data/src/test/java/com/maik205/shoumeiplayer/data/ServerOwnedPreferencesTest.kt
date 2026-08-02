package com.maik205.shoumeiplayer.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.data.session.UserConfigurationStore
import com.maik205.shoumeiplayer.domain.settings.DisplayLanguage
import com.maik205.shoumeiplayer.player.ServerTrackPreferences
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server-authority slice: what a *fresh install* loads, what the settings rows read and write,
 * and what happens to an edit made while the server is unreachable.
 *
 * Every test here drives the real load/write path (SettingsStore over a DataStore, AuthRepository
 * over a mocked Jellyfin) rather than asserting on a default constant, because the defect class
 * being fixed is precisely "the constant was right and the load path ignored it".
 */
class ServerOwnedPreferencesTest {

    @After
    fun tearDown() {
        ServerTrackPreferences.clear()
    }

    // --- #98 -------------------------------------------------------------------

    @Test
    fun `a fresh install with no persisted preferences follows the system locale`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        val store = SettingsStore(backing)

        // Precondition: this is genuinely the fresh-install state, not a stored SystemDefault.
        assertTrue(backing.data.first().asMap().isEmpty())
        assertEquals(DisplayLanguage.SystemDefault, store.current().displayLanguage)
    }

    @Test
    fun `an explicitly chosen display language still survives a reload`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        SettingsStore(backing).update { it.copy(displayLanguage = DisplayLanguage.Japanese) }

        assertEquals(DisplayLanguage.Japanese, SettingsStore(backing).current().displayLanguage)
    }

    /**
     * The retired keys must SURVIVE an ordinary save. They are the only record of what the viewer
     * had chosen before these preferences moved to the account, and the migration that seeds the
     * account from them may not have run yet -- it needs a reachable server. Purging them on any
     * unrelated write (as an earlier version did) destroyed them before they could be migrated,
     * and they are unrecoverable once gone.
     */
    @Test
    fun `an ordinary save keeps the retired copies available for migration`() = runTest {
        val backing = retiredInstall()
        val store = SettingsStore(backing)

        // An upgrading install must not crash or lose the settings it still owns...
        assertEquals(130, store.current().subtitleSizePercent)
        store.update { it }

        // ...and the values the account migration still needs are intact.
        val retired = requireNotNull(store.retiredAccountPreferences())
        assertEquals("jpn", retired.audioLanguage)
        assertEquals("always", retired.subtitleMode)
        assertEquals(false, retired.autoplayNextEpisode)
        assertEquals(130, backing.data.first()[intPreferencesKey("subtitle_size_percent")])
    }

    @Test
    fun `clearing the retired copies is what finally removes them`() = runTest {
        val backing = retiredInstall()
        val store = SettingsStore(backing)

        store.clearRetiredAccountPreferences()

        assertNull(store.retiredAccountPreferences())
        val persisted = backing.data.first()
        assertNull(persisted[stringPreferencesKey("subtitle_mode")])
        assertNull(persisted[stringPreferencesKey("preferred_audio_language")])
        assertNull(persisted[booleanPreferencesKey("autoplay_next_episode")])
        // A setting this store still owns is untouched by the purge.
        assertEquals(130, persisted[intPreferencesKey("subtitle_size_percent")])
    }

    @Test
    fun `a clean install has nothing to migrate`() = runTest {
        assertNull(SettingsStore(InMemoryPreferencesDataStore()).retiredAccountPreferences())
    }

    private suspend fun retiredInstall() = InMemoryPreferencesDataStore().apply {
        updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[stringPreferencesKey("subtitle_mode")] = "always"
                this[stringPreferencesKey("preferred_audio_language")] = "jpn"
                this[booleanPreferencesKey("autoplay_next_episode")] = false
                this[intPreferencesKey("subtitle_size_percent")] = 130
            }
        }
    }

    // --- #93 / #100 ------------------------------------------------------------

    @Test
    fun `resolving track preferences publishes the account languages for the engine fallback`() = runTest {
        val fixture = signedInRepository()

        val preferences = fixture.repository.preferences()

        assertEquals("jpn", preferences?.audioLanguagePreference)
        assertEquals("eng", preferences?.subtitleLanguagePreference)
        // MpvEngine reads exactly this at load() to set alang/slang, so the fallback mpv would use
        // agrees with the stream index TrackSelection just picked from the same document.
        assertEquals(preferences, ServerTrackPreferences.latestOrNull())
    }

    @Test
    fun `editing a preference writes it back to the account`() = runTest {
        val fixture = signedInRepository()

        val result = fixture.repository.editUserConfiguration { it.copy(subtitleMode = "Always") }

        assertTrue(result is ApiResult.Success)
        // A read-modify-write against the live document, never a fresh one: the untouched
        // properties have to come back from the server, not from a local guess.
        assertEquals("/Users/Me", fixture.recorder.paths().first())
        assertTrue(fixture.lastConfigurationWrite().contains("\"SubtitleMode\":\"Always\""))
        assertTrue(fixture.lastConfigurationWrite().contains("\"RememberSubtitleSelections\":false"))
    }

    @Test
    fun `an audio language change reaches the account and the engine fallback`() = runTest {
        val fixture = signedInRepository()

        fixture.repository.editUserConfiguration { it.copy(audioLanguagePreference = "vie") }

        assertTrue(fixture.lastConfigurationWrite().contains("\"AudioLanguagePreference\":\"vie\""))
        assertEquals("vie", ServerTrackPreferences.latestOrNull()?.audioLanguagePreference)
    }

    // --- offline behaviour ------------------------------------------------------

    @Test
    fun `an offline edit is kept locally and replayed when the server returns`() = runTest {
        val fixture = signedInRepository()
        // Seed the local mirror the way opening Settings on a healthy connection does.
        fixture.repository.userConfiguration()
        fixture.goOffline()

        val rejected = fixture.repository.editUserConfiguration { it.copy(subtitleMode = "Always") }

        assertTrue(rejected is ApiResult.Failure)
        // Optimistic: the row the user just changed shows the new value even though nothing landed.
        assertEquals("Always", fixture.repository.cachedUserConfiguration.first()?.subtitleMode)

        fixture.goOnline()
        assertTrue(fixture.repository.retryPendingUserConfiguration())

        assertTrue(fixture.lastConfigurationWrite().contains("\"SubtitleMode\":\"Always\""))
    }

    /**
     * #103. The flaky-network case, which is NOT the offline case: the account reads fine and only
     * the write fails. `updateUserConfiguration` reads `/Users/Me` before posting, and while that
     * read was also adopted as local truth it overwrote the optimistic value with the server's
     * pre-edit document -- so the row snapped back to the old language while the banner claimed the
     * change was merely waiting to be sent.
     */
    @Test
    fun `an edit survives a write that fails after the read succeeded`() = runTest {
        val fixture = signedInRepository()
        fixture.repository.userConfiguration()
        // GET /Users/Me keeps working; only the write is refused.
        fixture.routes["/Users/Configuration"] = FakeRoute(HttpStatusCode.InternalServerError, "")

        val rejected = fixture.repository.editUserConfiguration { it.copy(subtitleMode = "Always") }

        assertTrue(rejected is ApiResult.Failure)
        assertEquals("Always", fixture.repository.cachedUserConfiguration.first()?.subtitleMode)
        assertEquals("Always", fixture.repository.userConfiguration()?.subtitleMode)

        // ...and the queued write still reaches the account once the server accepts writes again.
        fixture.routes["/Users/Configuration"] = fakeRoute("", status = HttpStatusCode.NoContent)
        assertTrue(fixture.repository.retryPendingUserConfiguration())
        assertTrue(fixture.lastConfigurationWrite().contains("\"SubtitleMode\":\"Always\""))
    }

    @Test
    fun `a settled write-back is not replayed again`() = runTest {
        val fixture = signedInRepository()
        fixture.repository.userConfiguration()
        fixture.goOffline()
        fixture.repository.editUserConfiguration { it.copy(subtitleMode = "None") }
        fixture.goOnline()
        assertTrue(fixture.repository.retryPendingUserConfiguration())
        val writesAfterFirstRetry = fixture.recorder.paths().count { it == "/Users/Configuration" }

        assertTrue(fixture.repository.retryPendingUserConfiguration())

        assertEquals(
            writesAfterFirstRetry,
            fixture.recorder.paths().count { it == "/Users/Configuration" },
        )
    }

    @Test
    fun `a replayed write-back keeps fields another client changed in the meantime`() = runTest {
        val fixture = signedInRepository()
        fixture.repository.userConfiguration()
        fixture.goOffline()
        fixture.repository.editUserConfiguration { it.copy(subtitleMode = "Always") }

        // While this TV was offline the account picked up unrelated changes elsewhere.
        fixture.routes["/Users/Me"] = fakeRoute(FakeJellyfin.fixture("user_me_full_config.json"))
        assertTrue(fixture.repository.retryPendingUserConfiguration())

        val body = fixture.lastConfigurationWrite()
        assertTrue(body.contains("\"SubtitleMode\":\"Always\""))
        assertTrue(body.contains("\"OrderedViews\":[\"view-1\",\"view-2\",\"view-3\"]"))
        assertTrue(body.contains("\"CastReceiverId\":\"cast-receiver-1\""))
    }

    @Test
    fun `an offline launch reads the cached account preferences instead of nothing`() = runTest {
        val fixture = signedInRepository()
        fixture.repository.userConfiguration()

        fixture.goOffline()
        // A fresh repository over the same store is the process-restart case: no in-memory cache.
        val restarted = AuthRepository(fixture.client, fixture.sessionStore, fixture.configurationStore)

        val offline = restarted.userConfiguration()

        assertEquals("jpn", offline?.audioLanguagePreference)
        assertEquals("Smart", offline?.subtitleMode)
    }

    @Test
    fun `an account never seen on this device has no cached preferences`() = runTest {
        val fixture = signedInRepository()
        fixture.goOffline()

        assertNull(fixture.repository.userConfiguration())
    }

    @Test
    fun `signing out drops the account preferences and the engine fallback`() = runTest {
        val fixture = signedInRepository()
        fixture.repository.preferences()
        fixture.routes["/Sessions/Logout"] = fakeRoute("", status = HttpStatusCode.NoContent)

        fixture.repository.logout()

        assertNull(ServerTrackPreferences.latestOrNull())
        assertNull(fixture.configurationStore.cachedOrNull("user-1"))
    }

    @Test
    fun `queued write-backs are not replayed once nobody is signed in`() = runTest {
        val fixture = signedInRepository()
        fixture.repository.userConfiguration()
        fixture.goOffline()
        fixture.repository.editUserConfiguration { it.copy(subtitleMode = "Always") }
        fixture.sessionStore.clearAuth()
        fixture.goOnline()
        val before = fixture.recorder.count()

        assertTrue(fixture.repository.retryPendingUserConfiguration())

        assertEquals(before, fixture.recorder.count())
    }

    @Test
    fun `an edit without a session is refused rather than queued for the wrong account`() = runTest {
        val fixture = signedInRepository()
        fixture.sessionStore.clearAuth()

        val result = fixture.repository.editUserConfiguration { it.copy(subtitleMode = "Always") }

        assertTrue(result is ApiResult.Failure)
        assertFalse(fixture.recorder.paths().contains("/Users/Configuration"))
    }

    private class Fixture(
        val repository: AuthRepository,
        val client: com.maik205.shoumeiplayer.data.api.JellyfinClient,
        val sessionStore: com.maik205.shoumeiplayer.data.session.SessionStore,
        val configurationStore: UserConfigurationStore,
        val recorder: RequestRecorder,
        val routes: MutableMap<String, FakeRoute>,
    ) {
        fun goOffline() {
            routes["/Users/Me"] = FakeRoute(HttpStatusCode.ServiceUnavailable, "")
        }

        fun goOnline() {
            routes["/Users/Me"] = fakeRoute(FakeJellyfin.fixture("user_me.json"))
        }

        /** Body of the most recent `POST /Users/Configuration`; fails loudly if none was sent. */
        fun lastConfigurationWrite(): String {
            val index = recorder.paths().indexOfLast { it == "/Users/Configuration" }
            check(index >= 0) { "no /Users/Configuration write was made" }
            return recorder.bodyAt(index)
        }
    }

    private suspend fun signedInRepository(): Fixture {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "testuser")
        val routes = mutableMapOf(
            "/Users/Me" to fakeRoute(FakeJellyfin.fixture("user_me.json")),
            "/Users/Configuration" to fakeRoute("", status = HttpStatusCode.NoContent),
        )
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(routes = routes, sessions = sessionStore, recorder = recorder)
        val configurationStore = UserConfigurationStore(InMemoryPreferencesDataStore())
        return Fixture(
            repository = AuthRepository(client, sessionStore, configurationStore),
            client = client,
            sessionStore = sessionStore,
            configurationStore = configurationStore,
            recorder = recorder,
            routes = routes,
        )
    }
}
