package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.data.repo.AuthRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/Users/Configuration` write-back — regression coverage for the overwrite hazard called out in
 * docs/browse-v2-plan.md §1.3: `POST /Users/Configuration` replaces the whole 16-property object,
 * so `updateUserConfiguration` must read-modify-write rather than ever constructing a fresh DTO.
 */
class UserConfigurationTest {

    @Test
    fun `updateUserConfiguration reads once then posts all 16 keys with untouched fields echoed back`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "alice")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/Me" to fakeRoute(FakeJellyfin.fixture("user_me_full_config.json")),
                "/Users/Configuration" to fakeRoute("", status = HttpStatusCode.NoContent),
            ),
            sessions = sessionStore,
            recorder = recorder,
        )
        val repo = AuthRepository(client, sessionStore)

        val result = repo.updateUserConfiguration { it.copy(subtitleMode = "Always") }

        assertTrue(result is ApiResult.Success)
        assertEquals("Always", (result as ApiResult.Success).data.subtitleMode)

        // Exactly one GET /Users/Me then one POST /Users/Configuration — no extra round-trips.
        assertEquals(listOf("/Users/Me", "/Users/Configuration"), recorder.paths())
        assertEquals("GET", recorder.methodAt(0))
        assertEquals("POST", recorder.methodAt(1))
        assertEquals("user-1", recorder.queryAt(1, "userId"))

        val body = recorder.bodyAt(1)
        val expectedKeys = listOf(
            "AudioLanguagePreference",
            "PlayDefaultAudioTrack",
            "SubtitleLanguagePreference",
            "SubtitleMode",
            "RememberAudioSelections",
            "RememberSubtitleSelections",
            "EnableNextEpisodeAutoPlay",
            "DisplayMissingEpisodes",
            "GroupedFolders",
            "DisplayCollectionsView",
            "EnableLocalPassword",
            "OrderedViews",
            "LatestItemsExcludes",
            "MyMediaExcludes",
            "HidePlayedInLatest",
            "CastReceiverId",
        )
        expectedKeys.forEach { key ->
            assertTrue("expected body to contain \"$key\": $body", body.contains("\"$key\""))
        }

        // The regression case: fields untouched by the transform round-trip byte-identical.
        assertTrue(body.contains("\"OrderedViews\":[\"view-1\",\"view-2\",\"view-3\"]"))
        assertTrue(body.contains("\"MyMediaExcludes\":[\"exclude-2\",\"exclude-3\"]"))
        assertTrue(body.contains("\"SubtitleMode\":\"Always\""))
    }

    @Test
    fun `updateUserConfiguration issues no POST when the GET fails`() = runTest {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "alice")
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/Me" to FakeRoute(HttpStatusCode.InternalServerError, ""),
            ),
            sessions = sessionStore,
            recorder = recorder,
        )
        val repo = AuthRepository(client, sessionStore)

        val result = repo.updateUserConfiguration { it.copy(subtitleMode = "Always") }

        assertTrue(result is ApiResult.Failure)
        assertEquals(listOf("/Users/Me"), recorder.paths())
    }
}
