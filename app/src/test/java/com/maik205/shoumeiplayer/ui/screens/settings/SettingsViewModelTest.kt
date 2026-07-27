package com.maik205.shoumeiplayer.ui.screens.settings

import com.maik205.shoumeiplayer.data.FakeJellyfin
import com.maik205.shoumeiplayer.data.FakeRoute
import com.maik205.shoumeiplayer.data.InMemoryPreferencesDataStore
import com.maik205.shoumeiplayer.data.RequestRecorder
import com.maik205.shoumeiplayer.data.fakeRoute
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * docs/browse-v2-plan.md M-B13 — driven end-to-end over a mock Ktor engine and an in-memory
 * [SettingsStore], so this covers the real read-modify-write wiring, not a stub.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val sessionStore: SessionStore = FakeJellyfin.newSessionStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        routes: Map<String, FakeRoute>,
        recorder: RequestRecorder? = null,
        settingsStore: SettingsStore = SettingsStore(InMemoryPreferencesDataStore()),
    ): SettingsViewModel {
        val client = FakeJellyfin.client(routes = routes, sessions = sessionStore, recorder = recorder)
        val authRepository = AuthRepository(client, sessionStore)
        return SettingsViewModel(
            sessionStore = sessionStore,
            settingsStore = settingsStore,
            authRepository = authRepository,
            appVersion = "1.0-test",
            engineName = "SimulatedPlayerEngine",
        )
    }

    @Test
    fun `init loads session, server label and the current user configuration`() = runTest {
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "alice")
        val viewModel = viewModel(
            routes = mapOf(
                "/System/Info/Public" to fakeRoute(FakeJellyfin.fixture("system_info_public.json")),
                "/Users/Me" to fakeRoute(FakeJellyfin.fixture("user_me.json")),
            ),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("http://myserver", state.serverUrl)
        assertEquals("alice", state.userName)
        assertEquals("Test Server", state.serverName)
        assertEquals("10.9.0", state.serverVersion)
        assertEquals("Smart", state.configuration?.subtitleMode)
        assertEquals(SettingsGroup.Playback, state.group)
    }

    @Test
    fun `selectGroup switches the active group`() = runTest {
        val viewModel = viewModel(routes = emptyMap())
        advanceUntilIdle()

        viewModel.selectGroup(SettingsGroup.Appearance)

        assertEquals(SettingsGroup.Appearance, viewModel.uiState.value.group)
    }

    @Test
    fun `setSubtitleMode success replaces configuration and clears saving without an error`() = runTest {
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "alice")
        val recorder = RequestRecorder()
        val viewModel = viewModel(
            routes = mapOf(
                "/Users/Me" to fakeRoute(FakeJellyfin.fixture("user_me.json")),
                "/Users/Configuration" to fakeRoute("", status = HttpStatusCode.NoContent),
            ),
            recorder = recorder,
        )
        advanceUntilIdle()

        viewModel.setSubtitleMode("Always")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Always", state.configuration?.subtitleMode)
        assertFalse(state.saving)
        assertNull(state.errorMessage)
    }

    @Test
    fun `a failed write keeps the previous configuration value and surfaces the inline error`() = runTest {
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "alice")
        val viewModel = viewModel(
            routes = mapOf(
                "/Users/Me" to fakeRoute(FakeJellyfin.fixture("user_me.json")),
            ),
        )
        advanceUntilIdle()
        val before = viewModel.uiState.value.configuration

        // No /Users/Configuration route registered: the refresh inside updateUserConfiguration
        // succeeds (re-reads /Users/Me) but the POST 404s, so the write fails.
        viewModel.setSubtitleMode("Always")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Optimistic UI is banned: the row never showed "Always" while the POST was in flight,
        // and on failure it is still exactly the value the last successful read produced.
        assertEquals(before, state.configuration)
        assertFalse(state.saving)
        assertTrue(state.errorMessage!!.isNotBlank())
    }

    @Test
    fun `a second write while one is already in flight is ignored`() = runTest {
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "alice")
        val recorder = RequestRecorder()
        val viewModel = viewModel(
            routes = mapOf(
                "/Users/Me" to fakeRoute(FakeJellyfin.fixture("user_me.json")),
                "/Users/Configuration" to fakeRoute("", status = HttpStatusCode.NoContent),
            ),
            recorder = recorder,
        )
        advanceUntilIdle()

        viewModel.setSubtitleMode("Always")
        viewModel.setSubtitleMode("None")
        advanceUntilIdle()

        // The second call landed while `saving` was still true, so it was a no-op: the first
        // write's value is the one that stuck.
        assertEquals("Always", viewModel.uiState.value.configuration?.subtitleMode)
    }

    @Test
    fun `client-only fields write through SettingsStore and round-trip into uiState`() = runTest {
        val store = SettingsStore(InMemoryPreferencesDataStore())
        val viewModel = viewModel(routes = emptyMap(), settingsStore = store)
        advanceUntilIdle()

        viewModel.setPreferredQuality("1080p")
        viewModel.setFocusScaleEnabled(false)
        viewModel.setClockInOsd(false)
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertEquals("1080p", settings.preferredQuality)
        assertFalse(settings.focusScaleEnabled)
        assertFalse(settings.clockInOsd)
    }

    @Test
    fun `signOut clears the session and emits signedOut once`() = runTest {
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "alice")
        val viewModel = viewModel(routes = emptyMap())
        advanceUntilIdle()

        var signedOutCount = 0
        // Foreground `launch`, not `backgroundScope`: advanceUntilIdle only drains foreground
        // tasks, so a background collector would not have subscribed to the replay-less
        // `signedOut` SharedFlow before signOut() emits, and the event would be dropped.
        val job = launch { viewModel.signedOut.collect { signedOutCount++ } }

        viewModel.signOut()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.signingOut)
        assertEquals(1, signedOutCount)
        assertNull(sessionStore.session.first())
        assertEquals("http://myserver", sessionStore.serverUrl.first())
        job.cancel()
    }
}
