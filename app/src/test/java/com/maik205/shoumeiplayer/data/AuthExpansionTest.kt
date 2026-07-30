package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.repo.AuthRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `changing servers clears credentials but retains the stable device id`() = runTest {
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
    }
}
