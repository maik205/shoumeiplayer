package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.data.repo.AuthRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthExpansionTest {

    @Test
    fun `wrong credentials are local and preserve an existing stored session`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        sessions.saveAuth("existing-token", "existing-user", "Existing")
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/AuthenticateByName" to FakeRoute(HttpStatusCode.Unauthorized, ""),
            ),
            sessions = sessions,
        )

        val result = AuthRepository(client, sessions).login("alice", "wrong")

        assertEquals(ApiError.InvalidCredentials, (result as ApiResult.Failure).error)
        assertEquals("existing-token", sessions.current()?.accessToken)
    }

    @Test
    fun `public profiles and quick connect authenticate and persist the session`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/Public" to fakeRoute(
                    """[{"Id":"public-1","Name":"Alice","PrimaryImageTag":"face","HasPassword":true}]""",
                ),
                "/QuickConnect/Enabled" to fakeRoute("true"),
                "/QuickConnect/Initiate" to fakeRoute(
                    """{"Authenticated":false,"Secret":"secret-1","Code":"ABCD12"}""",
                ),
                "/QuickConnect/Connect" to fakeRoute(
                    """{"Authenticated":true,"Secret":"secret-1","Code":"ABCD12"}""",
                ),
                "/Users/AuthenticateWithQuickConnect" to fakeRoute(
                    """
                    {
                      "User":{"Id":"user-qc","Name":"Quick User"},
                      "AccessToken":"quick-token",
                      "ServerId":"server-1"
                    }
                    """.trimIndent(),
                ),
            ),
            sessions = sessions,
            recorder = recorder,
        )
        val repo = AuthRepository(client, sessions)

        val users = (repo.publicUsers() as ApiResult.Success).data
        assertEquals("face", users.single().primaryImageTag)
        assertTrue(users.single().hasPassword)
        assertEquals(true, (repo.quickConnectEnabled() as ApiResult.Success).data)
        assertEquals("ABCD12", (repo.initiateQuickConnect() as ApiResult.Success).data.code)
        assertTrue((repo.pollQuickConnect("secret-1") as ApiResult.Success).data.authenticated)
        val session = (repo.authenticateWithQuickConnect("secret-1") as ApiResult.Success).data

        assertEquals("quick-token", session.accessToken)
        assertEquals(session, sessions.current())
        assertEquals("secret-1", recorder.queryAt(3, "secret"))
        assertTrue(recorder.bodyAt(4).contains(""""Secret":"secret-1""""))
    }

    @Test
    fun `account switcher includes hidden active user before public profiles`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        sessions.saveAuth("active-token", "hidden-1", "Hidden User")
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/Public" to fakeRoute(
                    """[{"Id":"public-1","Name":"Public User","HasPassword":true}]""",
                ),
                "/Users/Me" to fakeRoute(
                    """{"Id":"hidden-1","Name":"Hidden User","PrimaryImageTag":"hidden-face"}""",
                ),
            ),
            sessions = sessions,
        )

        val users = (
            AuthRepository(client, sessions).accountSwitcherUsers() as ApiResult.Success
        ).data

        assertEquals(listOf("hidden-1", "public-1"), users.map { it.id })
        assertEquals("hidden-face", users.first().primaryImageTag)
    }

    @Test
    fun `account switcher falls back to persisted active user while offline`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        sessions.saveAuth("active-token", "active-1", "Active User")
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/Public" to FakeRoute(HttpStatusCode.InternalServerError, ""),
                "/Users/Me" to FakeRoute(HttpStatusCode.InternalServerError, ""),
            ),
            sessions = sessions,
        )

        val users = (
            AuthRepository(client, sessions).accountSwitcherUsers() as ApiResult.Success
        ).data

        assertEquals("active-1", users.single().id)
        assertEquals("Active User", users.single().name)
    }

    @Test
    fun `logout calls the server and clears local auth while retaining server selection`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        sessions.saveAuth("token", "user", "Alice")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Sessions/Logout" to FakeRoute(HttpStatusCode.NoContent, "")),
            sessions = sessions,
            recorder = recorder,
        )

        val result = AuthRepository(client, sessions).logout()

        assertTrue(result is ApiResult.Success)
        assertEquals("/Sessions/Logout", recorder.path())
        assertNull(sessions.current())
        assertEquals("http://server", sessions.serverUrl.first())
    }

    @Test
    fun `changing servers preserves the previous token but activates the new server`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://old-server")
        sessions.saveAuth("token", "user", "Alice")
        val deviceId = sessions.deviceId()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/System/Info/Public" to fakeRoute("""{"ServerName":"New","Version":"10.11.11","Id":"new"}"""),
            ),
            sessions = sessions,
        )

        val result = AuthRepository(client, sessions).changeServer("new-server:8096")

        assertTrue(result is ApiResult.Success)
        assertNull(sessions.current())
        assertEquals("http://new-server:8096", sessions.serverUrl.first())
        assertEquals(deviceId, sessions.deviceId())
        val previous = sessions.rememberedServers.first().single { it.url == "http://old-server" }
        assertEquals("token", previous.accessToken)
    }

    @Test
    fun `quick connect replaces the session then revokes the old token`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        sessions.saveAuth("old-token", "user-a", "Alice")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/AuthenticateWithQuickConnect" to fakeRoute(
                    """
                    {
                      "User":{"Id":"user-a","Name":"Alice"},
                      "AccessToken":"new-token",
                      "ServerId":"server-1"
                    }
                    """.trimIndent(),
                ),
                "/Users/Me" to fakeRoute("""{"Id":"user-a","Name":"Alice"}"""),
                "/Sessions/Logout" to FakeRoute(HttpStatusCode.NoContent, ""),
            ),
            sessions = sessions,
            recorder = recorder,
        )

        val result = AuthRepository(client, sessions)
            .replaceSessionWithQuickConnect("secret-1") as ApiResult.Success

        assertTrue(result.data.tokenChanged)
        assertTrue(result.data.oldTokenRevoked)
        assertEquals("new-token", sessions.current()?.accessToken)
        assertNull(sessions.pendingRevocationTokenOrNull())
        assertEquals(
            listOf(
                "/Users/AuthenticateWithQuickConnect",
                "/Users/Me",
                "/Sessions/Logout",
            ),
            recorder.paths(),
        )
        assertTrue(recorder.authorizationAt(1).orEmpty().contains("""Token="new-token""""))
        assertTrue(recorder.authorizationAt(2).orEmpty().contains("""Token="old-token""""))
    }

    @Test
    fun `quick connect with the same token verifies without logging out`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        sessions.saveAuth("same-token", "user-a", "Alice")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/AuthenticateWithQuickConnect" to fakeRoute(
                    """
                    {
                      "User":{"Id":"user-a","Name":"Alice"},
                      "AccessToken":"same-token",
                      "ServerId":"server-1"
                    }
                    """.trimIndent(),
                ),
                "/Users/Me" to fakeRoute("""{"Id":"user-a","Name":"Alice"}"""),
            ),
            sessions = sessions,
            recorder = recorder,
        )

        val result = AuthRepository(client, sessions)
            .replaceSessionWithQuickConnect("secret-1") as ApiResult.Success

        assertFalse(result.data.tokenChanged)
        assertTrue(result.data.oldTokenRevoked)
        assertEquals("same-token", sessions.current()?.accessToken)
        assertEquals(
            listOf("/Users/AuthenticateWithQuickConnect", "/Users/Me"),
            recorder.paths(),
        )
    }

    @Test
    fun `failed replacement verification preserves the old session`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        sessions.saveAuth("old-token", "user-a", "Alice")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/AuthenticateWithQuickConnect" to fakeRoute(
                    """
                    {
                      "User":{"Id":"user-a","Name":"Alice"},
                      "AccessToken":"unverified-token",
                      "ServerId":"server-1"
                    }
                    """.trimIndent(),
                ),
                "/Users/Me" to FakeRoute(HttpStatusCode.Unauthorized, ""),
            ),
            sessions = sessions,
            recorder = recorder,
        )

        val result = AuthRepository(client, sessions)
            .replaceSessionWithQuickConnect("secret-1")

        assertTrue(result is ApiResult.Failure)
        assertEquals("old-token", sessions.current()?.accessToken)
        assertNull(sessions.pendingRevocationTokenOrNull())
        assertEquals(
            listOf("/Users/AuthenticateWithQuickConnect", "/Users/Me"),
            recorder.paths(),
        )
    }

    @Test
    fun `quick connect approved by another user preserves the old session`() = runTest {
        val sessions = FakeJellyfin.newSessionStore()
        sessions.setServerUrl("http://server")
        sessions.saveAuth("old-token", "user-a", "Alice")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/AuthenticateWithQuickConnect" to fakeRoute(
                    """
                    {
                      "User":{"Id":"user-b","Name":"Bob"},
                      "AccessToken":"other-token",
                      "ServerId":"server-1"
                    }
                    """.trimIndent(),
                ),
            ),
            sessions = sessions,
            recorder = recorder,
        )

        val result = AuthRepository(client, sessions)
            .replaceSessionWithQuickConnect("secret-1")

        assertTrue(result is ApiResult.Failure)
        assertEquals("old-token", sessions.current()?.accessToken)
        assertEquals(listOf("/Users/AuthenticateWithQuickConnect"), recorder.paths())
    }
}
