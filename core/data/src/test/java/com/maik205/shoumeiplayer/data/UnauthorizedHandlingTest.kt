package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.session.SessionEvent
import com.maik205.shoumeiplayer.data.session.SessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A 401 on an authenticated call clears the session and signals reauthentication. */
class UnauthorizedHandlingTest {

    private suspend fun signedInStore() = FakeJellyfin.newSessionStore().apply {
        setServerUrl("http://myserver:8096")
        saveAuth(accessToken = "token-abc", userId = "user-1", userName = "maik")
    }

    @Test
    fun `401 on an authenticated call clears the stored auth`() = runTest {
        val sessionStore = signedInStore()
        assertNotNull(sessionStore.session.first())

        val client = FakeJellyfin.client(
            routes = mapOf("/Users/user-1/Items/Latest" to FakeRoute(HttpStatusCode.Unauthorized, "")),
            sessions = sessionStore,
        )

        val result = client.postEmpty("/Users/user-1/Items/Latest")

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiError.Unauthorized, (result as ApiResult.Failure).error)
        // token/userId/userName gone; server url survives so the user lands on Login, not ServerEntry.
        assertNull(sessionStore.session.first())
        assertEquals("http://myserver:8096", sessionStore.serverUrl.first())
    }

    @Test
    fun `401 on an authenticated call emits the unauthorized event`() = runTest {
        val sessionStore = signedInStore()
        val sessionManager = SessionManager(sessionStore)
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized) }
        val client = JellyfinClient(
            sessions = sessionStore,
            appName = "Shoumei Player Test",
            appVersion = "1.0-test",
            engine = engine,
            onUnauthorized = sessionManager::expireSession,
        )

        // UNDISPATCHED so the collector is subscribed before the request runs;
        // SessionManager does not require navigation or another global singleton.
        val event = async(start = CoroutineStart.UNDISPATCHED) { sessionManager.events.first() }

        client.postEmpty("/Sessions/Playing")

        assertEquals(SessionEvent.Expired, event.await())
    }

    @Test
    fun `401 without a stored token does not clear or signal`() = runTest {
        // Wrong-password login: already logged out, so no bounce-to-Login event.
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver:8096")
        var handlerRan = false
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized) }
        val client = JellyfinClient(
            sessions = sessionStore,
            appName = "Shoumei Player Test",
            appVersion = "1.0-test",
            engine = engine,
            onUnauthorized = { handlerRan = true },
        )

        val result = client.postEmpty("/Users/AuthenticateByName")

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiError.Unauthorized, (result as ApiResult.Failure).error)
        assertEquals("http://myserver:8096", sessionStore.serverUrl.first())
        assertFalse(handlerRan)
    }
}
