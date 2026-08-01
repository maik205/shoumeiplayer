package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.domain.result.*

import com.maik205.shoumeiplayer.data.api.dto.TrickplayInfoDto
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.player.VideoQuality
import com.maik205.shoumeiplayer.player.DEFAULT_MAX_STREAMING_BITRATE
import com.maik205.shoumeiplayer.player.PLAY_METHOD_DIRECT
import com.maik205.shoumeiplayer.player.PLAY_METHOD_TRANSCODE
import com.maik205.shoumeiplayer.player.ResolvedPlayback
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRepositoryTest {

    private val session = Session(
        serverUrl = "http://myserver",
        accessToken = "tok123",
        userId = "user-1",
        userName = "alice",
        deviceId = "dev-1",
    )

    @Test
    fun `resolve picks the direct-play source and builds a static stream url`() = runTest {
        val client = FakeJellyfin.client(
            routes = mapOf("/Items/item-1/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json"))),
            sessions = StaticSessionProvider(session),
        )
        val repo = PlaybackRepository(client)

        val result = repo.resolve("item-1")

        assertTrue(result is ApiResult.Success)
        val resolved = (result as ApiResult.Success).data
        assertEquals("item-1", resolved.itemId)
        assertEquals("media-1", resolved.mediaSourceId)
        assertEquals("play-session-1", resolved.playSessionId)
        assertEquals("DirectPlay", resolved.playMethod)
        assertTrue(resolved.streamUrl.contains("/Videos/item-1/stream"))
        assertTrue(resolved.streamUrl.contains("static=true"))
        assertTrue(resolved.streamUrl.contains("mediaSourceId=media-1"))
        assertTrue(resolved.streamUrl.contains("api_key=tok123"))
        assertEquals(1, resolved.audioTracks.size)
        assertEquals(1, resolved.subtitleTracks.size)
        assertEquals(1, resolved.defaultAudioIndex)
    }

    @Test
    fun `audio resolve advertises native audio support and gives mpv the original file`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Items/song-1/PlaybackInfo" to
                    fakeRoute(FakeJellyfin.fixture("playback_info_audio.json")),
            ),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = PlaybackRepository(client).resolve("song-1")

        val resolved = (result as ApiResult.Success).data
        assertEquals(PLAY_METHOD_DIRECT, resolved.playMethod)
        assertTrue(resolved.streamUrl.contains("/Audio/song-1/stream"))
        assertTrue(resolved.streamUrl.contains("static=true"))
        assertEquals("flac", resolved.audioTracks.single().label)
        assertTrue(recorder.body().contains(""""Type":"Audio""""))
        assertTrue(recorder.body().contains(""""EnableDirectPlay":true"""))
    }

    // --- §5 quality re-resolve -----------------------------------------------------------------

    @Test
    fun `a default resolve keeps the uncapped ceiling and leaves direct play on the table`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Items/item-1/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json"))),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val resolved = (PlaybackRepository(client).resolve("item-1") as ApiResult.Success).data

        val body = recorder.body()
        assertTrue(body.contains(""""MaxStreamingBitrate":$DEFAULT_MAX_STREAMING_BITRATE"""))
        assertTrue(body.contains(""""EnableDirectPlay":true"""))
        assertTrue(body.contains(""""AllowVideoStreamCopy":true"""))
        assertEquals(PLAY_METHOD_DIRECT, resolved.playMethod)
        assertEquals(DEFAULT_MAX_STREAMING_BITRATE, resolved.maxStreamingBitrate)
    }

    @Test
    fun `a quality re-resolve sends the cap and takes direct play off the table`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Items/item-1/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info_transcode.json")),
            ),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = PlaybackRepository(client).resolve(
            itemId = "item-1",
            startPositionTicks = 300_000_000L,
            mediaSourceId = "media-1",
            audioStreamIndex = 1,
            subtitleStreamIndex = 2,
            maxStreamingBitrate = VideoQuality.HD.maxStreamingBitrate!!,
            forceTranscode = true,
        )

        // A cap the server is still free to direct-play around is not a cap at all.
        val body = recorder.body()
        assertTrue(body.contains(""""MaxStreamingBitrate":8000000"""))
        assertTrue(body.contains(""""EnableDirectPlay":false"""))
        assertTrue(body.contains(""""EnableDirectStream":false"""))
        assertTrue(body.contains(""""AllowVideoStreamCopy":false"""))
        // Audio copy stays allowed: the ceiling is about the video, and re-encoding audio for
        // nothing costs the server CPU on every quality change.
        assertTrue(body.contains(""""AllowAudioStreamCopy":true"""))
        assertTrue(body.contains(""""StartTimeTicks":300000000"""))

        val resolved = (result as ApiResult.Success).data
        assertEquals(PLAY_METHOD_TRANSCODE, resolved.playMethod)
        assertEquals("play-session-2", resolved.playSessionId)
        assertEquals(8_000_000L, resolved.maxStreamingBitrate)
        assertTrue(resolved.streamUrl.contains("/videos/item-1/main.m3u8"))
        // The transcoding URL is unauthenticated on its own, so it has to carry the token.
        assertTrue(resolved.streamUrl.contains("api_key=tok123"))
    }

    @Test
    fun `a forced transcode fails cleanly when the server offers no transcoding url`() = runTest {
        val client = FakeJellyfin.client(
            // The direct-play fixture has no TranscodingUrl at all.
            routes = mapOf("/Items/item-1/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json"))),
            sessions = StaticSessionProvider(session),
        )

        val result = PlaybackRepository(client).resolve(
            itemId = "item-1",
            maxStreamingBitrate = VideoQuality.SD.maxStreamingBitrate!!,
            forceTranscode = true,
        )

        // Silently falling back to the uncapped original would tell the user 480p is playing when
        // the full-rate file is.
        assertTrue(result is ApiResult.Failure)
        assertEquals(
            ApiError.Unknown("Server offered no transcode for this quality"),
            (result as ApiResult.Failure).error,
        )
    }

    // --- §3 trickplay ---------------------------------------------------------------------------

    @Test
    fun `trickplaySource picks a band and builds an authenticated tile url`() = runTest {
        val client = FakeJellyfin.client(routes = emptyMap(), sessions = StaticSessionProvider(session))
        val bands = mapOf(
            "320" to TrickplayInfoDto(
                width = 320,
                height = 180,
                tileWidth = 10,
                tileHeight = 10,
                thumbnailCount = 414,
                interval = 10_000,
            ),
        )

        val source = PlaybackRepository(client).trickplaySource("ep-1", "media-1", bands, targetWidth = 320)

        assertEquals(320, source!!.info.width)
        assertEquals(
            "http://myserver/Videos/ep-1/Trickplay/320/0.jpg?api_key=tok123&mediaSourceId=media-1",
            source.tileUrl(0),
        )
    }

    @Test
    fun `trickplaySource is null when the item has no usable band`() = runTest {
        val client = FakeJellyfin.client(routes = emptyMap(), sessions = StaticSessionProvider(session))

        assertEquals(null, PlaybackRepository(client).trickplaySource("ep-1", "media-1", emptyMap(), 320))
    }

    @Test
    fun `resolve fails with Unauthorized when there is no session`() = runTest {
        val client = FakeJellyfin.client(routes = emptyMap(), sessions = StaticSessionProvider(null))
        val repo = PlaybackRepository(client)

        val result = repo.resolve("item-1")

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiError.Unauthorized, (result as ApiResult.Failure).error)
    }

    @Test
    fun `playback reporting calls never throw and succeed on a healthy server`() = runTest {
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Items/item-1/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json")),
                "/Sessions/Playing" to FakeRoute(HttpStatusCode.NoContent, ""),
                "/Sessions/Playing/Progress" to FakeRoute(HttpStatusCode.NoContent, ""),
                "/Sessions/Playing/Stopped" to FakeRoute(HttpStatusCode.NoContent, ""),
            ),
            sessions = StaticSessionProvider(session),
        )
        val repo = PlaybackRepository(client)
        val resolved = (repo.resolve("item-1") as ApiResult.Success).data

        assertTrue(repo.reportStart(resolved, 0, 1, null) is ApiResult.Success)
        assertTrue(repo.reportProgress(resolved, 5_000, false, 1, null) is ApiResult.Success)
        assertTrue(repo.reportStopped(resolved, 10_000, false) is ApiResult.Success)
    }

    @Test
    fun `resolve exposes the raw media streams and forwards preferred track indices`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Items/item-1/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json"))),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = PlaybackRepository(client).resolve(
            itemId = "item-1",
            audioStreamIndex = 1,
            subtitleStreamIndex = 2,
        )

        val resolved = (result as ApiResult.Success).data
        // MediaStreams survive verbatim so TrackSelection can read Language/IsForced/IsDefault.
        assertEquals(listOf(0, 1, 2), resolved.mediaStreams.map { it.index })
        assertEquals("eng", resolved.mediaStreams.first { it.type == "Audio" }.language)
        assertEquals("/Items/item-1/PlaybackInfo", recorder.path())
    }

    private fun resolved(playMethod: String, playSessionId: String = "play-session-1") = ResolvedPlayback(
        itemId = "item-1",
        mediaSourceId = "media-1",
        playSessionId = playSessionId,
        streamUrl = "http://myserver/stream",
        playMethod = playMethod,
        runTimeTicks = null,
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        defaultAudioIndex = null,
        defaultSubtitleIndex = null,
        headers = emptyMap(),
    )

    @Test
    fun `stopTranscode deletes the active encoding with deviceId and playSessionId`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Videos/ActiveEncodings" to FakeRoute(HttpStatusCode.NoContent, "")),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = PlaybackRepository(client).stopTranscode(resolved(PLAY_METHOD_TRANSCODE))

        assertTrue(result is ApiResult.Success)
        assertEquals("DELETE", recorder.methodAt(0))
        assertEquals("/Videos/ActiveEncodings", recorder.path())
        assertEquals("dev-1", recorder.query("deviceId"))
        assertEquals("play-session-1", recorder.query("playSessionId"))
    }

    @Test
    fun `stopTranscode makes no request for a direct play session`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Videos/ActiveEncodings" to FakeRoute(HttpStatusCode.NoContent, "")),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = PlaybackRepository(client).stopTranscode(resolved(PLAY_METHOD_DIRECT))

        // Direct play has no encoder to stop; hitting the endpoint anyway would be a wasted round
        // trip on every single exit.
        assertTrue(result is ApiResult.Success)
        assertEquals(0, recorder.count())
    }

    @Test
    fun `stopTranscode makes no request without a play session id`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Videos/ActiveEncodings" to FakeRoute(HttpStatusCode.NoContent, "")),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = PlaybackRepository(client).stopTranscode(resolved(PLAY_METHOD_TRANSCODE, playSessionId = ""))

        assertTrue(result is ApiResult.Success)
        assertEquals(0, recorder.count())
    }

    @Test
    fun `ping posts the play session id to keep the session alive`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = mapOf("/Sessions/Playing/Ping" to FakeRoute(HttpStatusCode.NoContent, "")),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        val result = PlaybackRepository(client).ping("play-session-1")

        assertTrue(result is ApiResult.Success)
        assertEquals("POST", recorder.methodAt(0))
        assertEquals("/Sessions/Playing/Ping", recorder.path())
        assertEquals("play-session-1", recorder.query("playSessionId"))
    }

    @Test
    fun `ping is skipped when the server returned no play session id`() = runTest {
        val recorder = RequestRecorder()
        val client = FakeJellyfin.client(
            routes = emptyMap(),
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )

        assertTrue(PlaybackRepository(client).ping("") is ApiResult.Success)
        assertEquals(0, recorder.count())
    }
}
