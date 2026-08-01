package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.data.repo.AuthRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun `validateServer success persists normalized server url and returns system info`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        val client = FakeJellyfin.client(
            routes = mapOf("/System/Info/Public" to fakeRoute(FakeJellyfin.fixture("system_info_public.json"))),
            sessions = sessionStore,
        )
        val repo = AuthRepository(client, sessionStore)

        val result = repo.validateServer("myserver.local:8096/")

        assertTrue(result is ApiResult.Success)
        assertEquals("Test Server", (result as ApiResult.Success).data.serverName)
        assertEquals("https://myserver.local:8096", sessionStore.serverUrl.first())
    }

    @Test
    fun `validateServer failure does not persist server url`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        val client = FakeJellyfin.client(
            routes = mapOf("/System/Info/Public" to FakeRoute(HttpStatusCode.Unauthorized, "")),
            sessions = sessionStore,
        )
        val repo = AuthRepository(client, sessionStore)

        val result = repo.validateServer("http://myserver")

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiError.Unauthorized, (result as ApiResult.Failure).error)
        assertNull(sessionStore.serverUrl.first())
    }

    @Test
    fun `login success persists auth and returns full session`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        val client = FakeJellyfin.client(
            routes = mapOf("/Users/AuthenticateByName" to fakeRoute(FakeJellyfin.fixture("auth_result.json"))),
            sessions = sessionStore,
        )
        val repo = AuthRepository(client, sessionStore)

        val result = repo.login("alice", "secret")

        assertTrue(result is ApiResult.Success)
        val session = (result as ApiResult.Success).data
        assertEquals("http://myserver", session.serverUrl)
        assertEquals("test-access-token", session.accessToken)
        assertEquals("user-1", session.userId)
        assertEquals("testuser", session.userName)
        assertTrue(session.deviceId.isNotBlank())

        val persisted = sessionStore.session.first()
        assertEquals(session, persisted)
    }

    @Test
    fun `login failure does not save auth`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        val client = FakeJellyfin.client(
            routes = mapOf("/Users/AuthenticateByName" to FakeRoute(HttpStatusCode.Unauthorized, "")),
            sessions = sessionStore,
        )
        val repo = AuthRepository(client, sessionStore)

        val result = repo.login("alice", "wrong")

        assertTrue(result is ApiResult.Failure)
        assertNull(sessionStore.session.first())
    }

    @Test
    fun `logout clears auth but keeps server url`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "u1", userName = "alice")
        val client = FakeJellyfin.client(routes = emptyMap(), sessions = sessionStore)
        val repo = AuthRepository(client, sessionStore)

        repo.logout()

        assertNull(sessionStore.session.first())
        assertEquals("http://myserver", sessionStore.serverUrl.first())
    }

    @Test
    fun `userConfiguration reads Users Me once and caches the result`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Users/Me" to fakeRoute(FakeJellyfin.fixture("user_me.json"))),
            sessions = sessionStore,
            recorder = recorder,
        )
        val repo = AuthRepository(client, sessionStore)

        val configuration = repo.userConfiguration()

        assertEquals("jpn", configuration?.audioLanguagePreference)
        assertEquals("eng", configuration?.subtitleLanguagePreference)
        assertEquals("Smart", configuration?.subtitleMode)
        assertEquals(false, configuration?.playDefaultAudioTrack)

        // Second read is served from the in-memory cache: still exactly one HTTP request.
        assertEquals(configuration, repo.userConfiguration())
        assertEquals("/Users/Me", recorder.path())
    }

    @Test
    fun `userConfiguration returns null when the server call fails`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        val client = FakeJellyfin.client(
            routes = mapOf("/Users/Me" to FakeRoute(HttpStatusCode.InternalServerError, "")),
            sessions = sessionStore,
        )

        assertNull(AuthRepository(client, sessionStore).userConfiguration())
    }
}
