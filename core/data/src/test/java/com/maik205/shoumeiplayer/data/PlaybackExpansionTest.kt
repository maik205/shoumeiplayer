package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.player.PLAY_METHOD_DIRECT
import com.maik205.shoumeiplayer.player.PLAY_METHOD_DIRECT_STREAM
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.data.session.Session
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackExpansionTest {
    private val session = Session(
        serverUrl = "http://server",
        accessToken = "token",
        userId = "user-1",
        userName = "Alice",
        deviceId = "device-1",
    )

    @Test
    fun `direct stream uses the server remux url and reports DirectStream`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Items/item-1/PlaybackInfo" to fakeRoute(
                    """
                    {
                      "PlaySessionId":"play-1",
                      "MediaSources":[{
                        "Id":"source-1",
                        "SupportsDirectPlay":false,
                        "SupportsDirectStream":true,
                        "SupportsTranscoding":true,
                        "TranscodingUrl":"/Videos/item-1/stream.m3u8?mediaSourceId=source-1",
                        "RequiredHttpHeaders":{"X-Tuner-Token":"abc"}
                      }]
                    }
                    """.trimIndent(),
                ),
                "/Videos/ActiveEncodings" to FakeRoute(HttpStatusCode.NoContent, ""),
            ),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )
        val repo = PlaybackRepository(client)

        val resolved = (repo.resolve("item-1") as ApiResult.Success).data

        assertEquals(PLAY_METHOD_DIRECT_STREAM, resolved.playMethod)
        assertTrue(resolved.streamUrl.contains("/Videos/item-1/stream.m3u8"))
        assertTrue(resolved.streamUrl.contains("api_key=token"))
        assertFalse(resolved.streamUrl.contains("static=true"))
        assertEquals("abc", resolved.headers["X-Tuner-Token"])

        assertTrue(repo.stopTranscode(resolved) is ApiResult.Success)
        assertEquals("DELETE", recorder.methodAt(1))
        assertEquals("/Videos/ActiveEncodings", recorder.paths()[1])
    }

    @Test
    fun `audio-only direct play uses the Jellyfin audio stream endpoint`() = runTest {
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Items/song-1/PlaybackInfo" to fakeRoute(
                    """
                    {
                      "PlaySessionId":"play-song",
                      "MediaSources":[{
                        "Id":"audio-source",
                        "SupportsDirectPlay":true,
                        "MediaStreams":[{"Index":0,"Type":"Audio","Codec":"flac"}]
                      }]
                    }
                    """.trimIndent(),
                ),
            ),
            sessions = StaticSessionProvider(session),
        )

        val resolved = (PlaybackRepository(client).resolve("song-1") as ApiResult.Success).data

        assertEquals(PLAY_METHOD_DIRECT, resolved.playMethod)
        assertTrue(resolved.streamUrl.contains("/Audio/song-1/stream"))
        assertFalse(resolved.streamUrl.contains("/Videos/song-1/stream"))
    }

    @Test
    fun `live source is opened explicitly and carries close lifecycle metadata`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Items/channel-1/PlaybackInfo" to fakeRoute(
                    """
                    {
                      "PlaySessionId":"play-live",
                      "MediaSources":[{
                        "Id":"tuner-source",
                        "RequiresOpening":true,
                        "OpenToken":"open-token",
                        "SupportsDirectPlay":true
                      }]
                    }
                    """.trimIndent(),
                ),
                "/LiveStreams/Open" to fakeRoute(
                    """
                    {
                      "MediaSource":{
                        "Id":"opened-source",
                        "SupportsDirectPlay":true,
                        "RequiresClosing":true,
                        "LiveStreamId":"live-1",
                        "RequiredHttpHeaders":{"X-Live":"yes"}
                      }
                    }
                    """.trimIndent(),
                ),
                "/LiveStreams/Close" to FakeRoute(HttpStatusCode.NoContent, ""),
                "/Sessions/Playing" to FakeRoute(HttpStatusCode.NoContent, ""),
            ),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )
        val repo = PlaybackRepository(client)

        val resolved = (repo.resolve("channel-1") as ApiResult.Success).data

        assertEquals(PLAY_METHOD_DIRECT, resolved.playMethod)
        assertEquals("live-1", resolved.liveStreamId)
        assertTrue(resolved.requiresLiveStreamClose)
        assertEquals("yes", resolved.headers["X-Live"])
        assertEquals("/LiveStreams/Open", recorder.paths()[1])
        assertTrue(recorder.bodyAt(1).contains(""""OpenToken":"open-token""""))
        assertTrue(recorder.bodyAt(1).contains(""""ItemId":"channel-1""""))

        assertTrue(repo.reportStart(resolved, 0, null, null) is ApiResult.Success)
        assertTrue(recorder.bodyAt(2).contains(""""LiveStreamId":"live-1""""))
        assertTrue(repo.closeLiveStream(resolved) is ApiResult.Success)
        assertEquals("live-1", recorder.queryAt(3, "liveStreamId"))
    }
}
