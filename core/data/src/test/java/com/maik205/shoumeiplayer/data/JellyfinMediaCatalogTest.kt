package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.data.repo.JellyfinMediaCatalog
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.domain.model.MediaPageRequest
import com.maik205.shoumeiplayer.domain.model.MediaSort
import com.maik205.shoumeiplayer.domain.model.MediaView
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinMediaCatalogTest {
    private val session = Session(
        serverUrl = "http://server",
        accessToken = "token",
        userId = "user-1",
        userName = "viewer",
        deviceId = "device-1",
    )

    @Test
    fun `libraries hides unsupported photo views and returns domain models`() = runTest {
        val response = """
            {
              "Items": [
                {"Id":"movies","Name":"Movies","CollectionType":"movies"},
                {"Id":"photos","Name":"Photos","CollectionType":"photos"}
              ],
              "TotalRecordCount": 2
            }
        """.trimIndent()
        val catalog = catalog(routes = mapOf("/UserViews" to fakeRoute(response)))

        val result = catalog.libraries()

        assertTrue(result is ApiResult.Success)
        assertEquals(listOf("movies"), (result as ApiResult.Success).data.map { it.id })
        assertEquals("Movies", result.data.single().title)
    }

    @Test
    fun `page translates domain intent at the Jellyfin boundary`() = runTest {
        val recorder = RequestRecorder()
        val catalog = catalog(
            routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
            recorder = recorder,
        )

        val result = catalog.page(
            MediaPageRequest(
                libraryId = "library-1",
                collectionType = "movies",
                sort = MediaSort.CommunityRating,
                view = MediaView.Favorites,
                startIndex = 60,
                limit = 60,
            ),
        )

        assertTrue(result is ApiResult.Success)
        val page = (result as ApiResult.Success).data
        assertEquals(1, page.totalCount)
        assertEquals("item-1", page.items.single().id)
        assertEquals("library-1", recorder.query("parentId"))
        assertEquals("Movie", recorder.query("includeItemTypes"))
        assertEquals("CommunityRating", recorder.query("sortBy"))
        assertEquals("Descending", recorder.query("sortOrder"))
        assertEquals("IsFavorite", recorder.query("filters"))
        assertEquals("60", recorder.query("startIndex"))
        assertEquals("60", recorder.query("limit"))
    }

    @Test
    fun `new view owns its date filter instead of leaking query construction to UI`() = runTest {
        val recorder = RequestRecorder()
        val catalog = catalog(
            routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
            recorder = recorder,
        )

        catalog.page(
            MediaPageRequest(
                libraryId = "library-1",
                collectionType = "tvshows",
                sort = MediaSort.Recent,
                view = MediaView.New,
                startIndex = 0,
                limit = 60,
            ),
        )

        assertEquals("Series", recorder.query("includeItemTypes"))
        assertTrue(recorder.query("minDateLastSavedForUser").orEmpty().isNotBlank())
    }

    private fun catalog(
        routes: Map<String, FakeRoute>,
        recorder: RequestRecorder? = null,
    ): JellyfinMediaCatalog {
        val client = FakeJellyfin.client(
            routes = routes,
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )
        return JellyfinMediaCatalog(
            repository = LibraryRepository(client),
            images = ImageUrlBuilder { session.serverUrl },
        )
    }
}
