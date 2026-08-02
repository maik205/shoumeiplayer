package com.maik205.shoumeiplayer.di.player

import com.maik205.shoumeiplayer.data.FakeJellyfin
import com.maik205.shoumeiplayer.data.FakeRoute
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.InMemoryPreferencesDataStore
import com.maik205.shoumeiplayer.data.fakeRoute
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.data.session.UserConfigurationStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #94: next-episode autoplay is the Jellyfin account setting, so turning it off in the web
 * client turns it off here.
 *
 * Driven through the real loader the player screen uses -- not through the flag it reads -- because
 * the previous bug was exactly that the account value was never consulted: a non-null client
 * Boolean always short-circuited the elvis chain in front of it.
 */
class NextEpisodeAutoPlayTest {

    @Test
    fun `the Up Next card follows the account setting when it is off`() = runTest {
        val loader = loader(enableNextEpisodeAutoPlay = false)

        val context = loader.loadEpisodeContext("episode-1")

        assertNotNull(context?.upNext)
        assertEquals("episode-2", context?.upNext?.itemId)
        assertFalse(context!!.upNext!!.autoPlay)
        assertTrue(context.postPlayEpisodes.none { it.autoPlay })
    }

    @Test
    fun `the Up Next card follows the account setting when it is on`() = runTest {
        val loader = loader(enableNextEpisodeAutoPlay = true)

        val context = loader.loadEpisodeContext("episode-1")

        assertTrue(context!!.upNext!!.autoPlay)
    }

    @Test
    fun `an unreachable server leaves autoplay on rather than silently disabling it`() = runTest {
        val loader = loader(enableNextEpisodeAutoPlay = false, serverReachable = false)

        val context = loader.loadEpisodeContext("episode-1")

        assertTrue(context!!.upNext!!.autoPlay)
    }

    private suspend fun loader(
        enableNextEpisodeAutoPlay: Boolean,
        serverReachable: Boolean = true,
    ): JellyfinPlaybackMetadataLoader {
        val sessionStore = FakeJellyfin.newSessionStore()
        sessionStore.setServerUrl("http://myserver")
        sessionStore.saveAuth(accessToken = "tok", userId = "user-1", userName = "testuser")
        val userMe = if (serverReachable) {
            fakeRoute(
                """
                {
                  "Id": "user-1",
                  "Name": "testuser",
                  "Configuration": { "EnableNextEpisodeAutoPlay": $enableNextEpisodeAutoPlay }
                }
                """.trimIndent(),
            )
        } else {
            FakeRoute(HttpStatusCode.ServiceUnavailable, "")
        }
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Users/Me" to userMe,
                "/Items" to fakeRoute(EPISODE_ITEM),
                "/Shows/series-1/Episodes" to fakeRoute(SERIES_EPISODES),
            ),
            sessions = sessionStore,
        )
        return JellyfinPlaybackMetadataLoader(
            libraryRepository = LibraryRepository(client),
            authRepository = AuthRepository(
                client,
                sessionStore,
                UserConfigurationStore(InMemoryPreferencesDataStore()),
            ),
            imageUrlBuilder = ImageUrlBuilder({ "http://myserver" }, { "tok" }),
            settingsStore = SettingsStore(InMemoryPreferencesDataStore()),
            playbackRepository = PlaybackRepository(client),
        )
    }

    private companion object {
        const val EPISODE_ITEM = """
            {
              "Items": [
                {
                  "Id": "episode-1",
                  "Name": "First",
                  "Type": "Episode",
                  "SeriesId": "series-1"
                }
              ],
              "TotalRecordCount": 1
            }
        """

        const val SERIES_EPISODES = """
            {
              "Items": [
                { "Id": "episode-1", "Name": "First", "Type": "Episode", "SeriesId": "series-1" },
                { "Id": "episode-2", "Name": "Second", "Type": "Episode", "SeriesId": "series-1" },
                { "Id": "episode-3", "Name": "Third", "Type": "Episode", "SeriesId": "series-1" }
              ],
              "TotalRecordCount": 3
            }
        """
    }
}
