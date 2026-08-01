package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.session.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryTest {

    private val session = Session(
        serverUrl = "http://myserver",
        accessToken = "tok123",
        userId = "user-1",
        userName = "alice",
        deviceId = "dev-1",
    )

    @Test
    fun `calls fail fast with Unauthorized when no session is present`() = runTest {
        val client = FakeJellyfin.client(routes = emptyMap(), sessions = StaticSessionProvider(null))
        val repo = LibraryRepository(client)

        val result = repo.userViews()

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiError.Unauthorized, (result as ApiResult.Failure).error)
    }

    @Test
    fun `items unwraps QueryResult of BaseItemDto`() = runTest {
        val client = FakeJellyfin.client(
            routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
            sessions = StaticSessionProvider(session),
        )
        val repo = LibraryRepository(client)

        val result = repo.items()

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(1, data.totalRecordCount)
        assertEquals("item-1", data.items.first().id)
    }

    @Test
    fun `latest parses a bare array response`() = runTest {
        val bareArrayJson = """[{"Id":"item-9","Name":"Bare Item","Type":"Movie"}]"""
        val client = FakeJellyfin.client(
            routes = mapOf("/Items/Latest" to fakeRoute(bareArrayJson)),
            sessions = StaticSessionProvider(session),
        )
        val repo = LibraryRepository(client)

        val result = repo.latest(parentId = "lib-1")

        assertTrue(result is ApiResult.Success)
        val items = (result as ApiResult.Success).data
        assertEquals(1, items.size)
        assertEquals("item-9", items.first().id)
        assertEquals("Bare Item", items.first().name)
    }

    @Test
    fun `userViews unwraps QueryResult items`() = runTest {
        val client = FakeJellyfin.client(
            routes = mapOf("/UserViews" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
            sessions = StaticSessionProvider(session),
        )
        val repo = LibraryRepository(client)

        val result = repo.userViews()

        assertTrue(result is ApiResult.Success)
        assertEquals(1, (result as ApiResult.Success).data.size)
    }

    // --- request shape (the `fields=` / image params the asset audit pins) -------------------

    @Test
    fun `item routes through Items with ids and the full detail field set`() = runTest {
        val recorder = RequestRecorder()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("item_detail.json"))),
                sessions = StaticSessionProvider(session),
                recorder = recorder,
            ),
        )

        val result = repo.item("ep-1")

        assertTrue(result is ApiResult.Success)
        assertEquals("ep-1", (result as ApiResult.Success).data.id)

        // `GET /Items/{id}` takes no `fields`, so the detail fetch must go through `/Items?ids=`.
        assertEquals("/Items", recorder.path())
        assertEquals("ep-1", recorder.query("ids"))
        val fields = recorder.query("fields").orEmpty().split(",")
        assertTrue(fields.containsAll(listOf("People", "Chapters", "MediaStreams", "Trickplay", "Taglines")))
        assertEquals("true", recorder.query("enableUserData"))
        assertEquals("3", recorder.query("imageTypeLimit"))
        assertEquals("Primary,Backdrop,Thumb,Logo,Banner,Art", recorder.query("enableImageTypes"))
    }

    @Test
    fun `item reports 404 when the query returns no items`() = runTest {
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Items" to fakeRoute("""{"Items":[],"TotalRecordCount":0,"StartIndex":0}""")),
                sessions = StaticSessionProvider(session),
            ),
        )

        val result = repo.item("missing")

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiError.Http(404, "Item missing not found"), (result as ApiResult.Failure).error)
    }

    @Test
    fun `search requests user data so watched and progress render on results`() = runTest {
        val recorder = RequestRecorder()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
                sessions = StaticSessionProvider(session),
                recorder = recorder,
            ),
        )

        repo.search("winter")

        assertEquals("true", recorder.query("enableUserData"))
        assertEquals("winter", recorder.query("searchTerm"))
        assertEquals("Overview,PrimaryImageAspectRatio", recorder.query("fields"))
        assertEquals("Primary,Thumb", recorder.query("enableImageTypes"))
        assertEquals("1", recorder.query("imageTypeLimit"))
    }

    @Test
    fun `card queries request the aspect ratio field and the card image types`() = runTest {
        val recorder = RequestRecorder()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Shows/NextUp" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
                sessions = StaticSessionProvider(session),
                recorder = recorder,
            ),
        )

        repo.nextUp()

        assertEquals("Overview,PrimaryImageAspectRatio", recorder.query("fields"))
        assertEquals("Primary,Backdrop,Thumb,Logo", recorder.query("enableImageTypes"))
        assertEquals("1", recorder.query("imageTypeLimit"))
    }

    // --- §5 "More like this" shelf ------------------------------------------------------------

    @Test
    fun `similar hits the Items Similar endpoint with only the params it accepts`() = runTest {
        val recorder = RequestRecorder()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Items/ep-1/Similar" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
                sessions = StaticSessionProvider(session),
                recorder = recorder,
            ),
        )

        val result = repo.similar("ep-1")

        assertTrue(result is ApiResult.Success)
        assertEquals("item-1", (result as ApiResult.Success).data.single().id)

        assertEquals("/Items/ep-1/Similar", recorder.path())
        assertEquals("user-1", recorder.query("userId"))
        assertEquals("12", recorder.query("limit"))
        assertEquals("Overview,PrimaryImageAspectRatio", recorder.query("fields"))
        // GetSimilarItems takes no enableImageTypes/imageTypeLimit/enableUserData — sending them
        // would be noise the server ignores.
        assertEquals(null, recorder.query("enableImageTypes"))
        assertEquals(null, recorder.query("imageTypeLimit"))
        assertEquals(null, recorder.query("enableUserData"))
    }

    @Test
    fun `similar fails fast without a session`() = runTest {
        val repo = LibraryRepository(
            FakeJellyfin.client(routes = emptyMap(), sessions = StaticSessionProvider(null)),
        )

        val result = repo.similar("ep-1")

        assertEquals(ApiError.Unauthorized, (result as ApiResult.Failure).error)
    }

    // --- §5 Cast shelf → person-filtered grid ---------------------------------------------------

    @Test
    fun `items forwards personIds and omits the param when no person is filtered`() = runTest {
        val recorder = RequestRecorder()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
                sessions = StaticSessionProvider(session),
                recorder = recorder,
            ),
        )

        repo.items(personIds = listOf("person-1"))
        assertEquals("person-1", recorder.queryAt(0, "personIds"))

        repo.items()
        assertEquals(null, recorder.queryAt(1, "personIds"))
    }

    @Test
    fun `items query keeps genres alongside the card fields`() = runTest {
        val recorder = RequestRecorder()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
                sessions = StaticSessionProvider(session),
                recorder = recorder,
            ),
        )

        repo.items(parentId = "lib-1")

        assertEquals("Overview,PrimaryImageAspectRatio,Genres", recorder.query("fields"))
        assertEquals("Primary,Backdrop,Thumb,Logo", recorder.query("enableImageTypes"))
    }

    // --- M-B6 case 1: genres() -----------------------------------------------------------------

    @Test
    fun `genres parses Items and surfaces Id and Name`() = runTest {
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Genres" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
                sessions = StaticSessionProvider(session),
            ),
        )

        val result = repo.genres()

        assertTrue(result is ApiResult.Success)
        val genres = (result as ApiResult.Success).data
        assertEquals(1, genres.size)
        assertEquals("item-1", genres.first().id)
        assertEquals("Test Movie", genres.first().name)
    }

    // --- M-B6 case 2: genreIds / personIds go on the URL, `genres=` never does -----------------

    @Test
    fun `items genreIds produces a comma-joined genreIds param and no genres param`() = runTest {
        val recorder = RequestRecorder()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
                sessions = StaticSessionProvider(session),
                recorder = recorder,
            ),
        )

        repo.items(genreIds = listOf("a", "b"))

        assertEquals("a,b", recorder.query("genreIds"))
        assertEquals(null, recorder.query("genres"))
    }

    @Test
    fun `items personIds produces a comma-joined personIds param and no genres param`() = runTest {
        val recorder = RequestRecorder()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Items" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
                sessions = StaticSessionProvider(session),
                recorder = recorder,
            ),
        )

        repo.items(personIds = listOf("a", "b"))

        assertEquals("a,b", recorder.query("personIds"))
        assertEquals(null, recorder.query("genres"))
    }

    // --- M-B6 case 3: searchHints() ------------------------------------------------------------

    @Test
    fun `searchHints parses SearchHints and tolerates a missing TotalRecordCount`() = runTest {
        val json = """
            {
              "SearchHints": [
                { "ItemId": "item-1", "Id": "item-1", "Name": "Winter Soldier", "Type": "Movie" }
              ]
            }
        """.trimIndent()
        val repo = LibraryRepository(
            FakeJellyfin.client(
                routes = mapOf("/Search/Hints" to fakeRoute(json)),
                sessions = StaticSessionProvider(session),
            ),
        )

        val result = repo.searchHints(term = "winter")

        assertTrue(result is ApiResult.Success)
        val hints = (result as ApiResult.Success).data
        assertEquals(1, hints.size)
        assertEquals("Winter Soldier", hints.first().name)
        assertEquals("item-1", hints.first().itemId)
    }
}
