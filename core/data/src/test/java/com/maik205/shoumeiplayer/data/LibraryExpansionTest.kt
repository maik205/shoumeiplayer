package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.session.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryExpansionTest {
    private val session = Session(
        serverUrl = "http://server",
        accessToken = "token",
        userId = "user-1",
        userName = "Alice",
        deviceId = "device-1",
    )

    @Test
    fun `album tracks route sends album and audio filters`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Items" to fakeRoute("""{"Items":[{"Id":"song-1","Type":"Audio"}],"TotalRecordCount":1}"""),
            ),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = LibraryRepository(client).albumTracks("album-1")

        assertEquals("song-1", (result as ApiResult.Success).data.single().id)
        assertEquals("album-1", recorder.query("parentId"))
        assertEquals("album-1", recorder.query("albumIds"))
        assertEquals("Audio", recorder.query("includeItemTypes"))
    }

    @Test
    fun `broad search includes the replacement TV media families`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Items" to fakeRoute("""{"Items":[],"TotalRecordCount":0}""")),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        LibraryRepository(client).broadSearch("space")

        val types = recorder.query("includeItemTypes").orEmpty()
        assertTrue(types.contains("MusicAlbum"))
        assertTrue(types.contains("AudioBook"))
        assertTrue(types.contains("BoxSet"))
        assertTrue(types.contains("LiveTvChannel"))
        assertTrue(types.contains("Recording"))
    }

    @Test
    fun `live tv channels request current guide programs`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/LiveTv/Channels" to fakeRoute(
                    """{"Items":[{"Id":"channel-1","ChannelNumber":"5"}],"TotalRecordCount":1}""",
                ),
            ),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = LibraryRepository(client).liveTvChannels()

        assertEquals("5", (result as ApiResult.Success).data.items.single().channelNumber)
        assertEquals("true", recorder.query("addCurrentProgram"))
        assertEquals("user-1", recorder.query("userId"))
    }

    @Test
    fun `favorite and played mutations use Jellyfin user endpoints`() = runTest {
        val recorder = RequestRecorder()
        val response = """{"ItemId":"item-1","IsFavorite":true,"Played":true}"""
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/UserFavoriteItems/item-1" to fakeRoute(response),
                "/UserPlayedItems/item-1" to fakeRoute(response),
            ),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )
        val repo = LibraryRepository(client)

        assertTrue((repo.setFavorite("item-1", true) as ApiResult.Success).data.isFavorite)
        assertTrue((repo.setPlayed("item-1", false) as ApiResult.Success).data.played)
        assertEquals("POST", recorder.methodAt(0))
        assertEquals("DELETE", recorder.methodAt(1))
        assertEquals("user-1", recorder.queryAt(1, "userId"))
    }

    @Test
    fun `series resolver prefers server next-up and keeps its resume position`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Shows/NextUp" to fakeRoute(
                    """
                    {
                      "Items":[{
                        "Id":"episode-3",
                        "Type":"Episode",
                        "UserData":{"PlaybackPositionTicks":123456,"Played":false}
                      }],
                      "TotalRecordCount":1
                    }
                    """.trimIndent(),
                ),
            ),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = LibraryRepository(client).resolvePlayableTarget(
            BaseItemDto(id = "series-1", type = "Series"),
        )

        val target = (result as ApiResult.Success).data
        assertEquals("episode-3", target.itemId)
        assertEquals(123_456L, target.startPositionTicks)
        assertEquals("series-1", recorder.query("seriesId"))
        assertEquals("1", recorder.query("limit"))
    }
}
