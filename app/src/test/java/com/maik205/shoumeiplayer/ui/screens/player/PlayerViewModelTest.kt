package com.maik205.shoumeiplayer.ui.screens.player

import com.maik205.shoumeiplayer.data.FakeJellyfin
import com.maik205.shoumeiplayer.data.FakeRoute
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.RequestRecorder
import com.maik205.shoumeiplayer.data.StaticSessionProvider
import com.maik205.shoumeiplayer.data.fakeRoute
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.player.PlaybackProgressReporter
import com.maik205.shoumeiplayer.player.SimulatedPlayerEngine
import com.maik205.shoumeiplayer.player.VideoQuality
import androidx.lifecycle.viewModelScope
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The ViewModel is driven end-to-end over a mock Ktor engine and the simulated player, so this
 * covers the real wiring (resolve → user configuration → engine.load) rather than a stub.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val session = Session(
        serverUrl = "http://myserver",
        accessToken = "tok123",
        userId = "user-1",
        userName = "alice",
        deviceId = "dev-1",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState carries the engine buffered position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine)

        // uiState is WhileSubscribed, so it only runs while something collects it.
        backgroundScope.launch { viewModel.uiState.collect { } }

        // Let resolve + configuration + engine.load settle, then run the ticker a few seconds.
        advanceTimeBy(5_000)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(false, state.loading)
        assertEquals(null, state.error)
        val buffered = state.bufferedMs
        assertNotNull("bufferedMs should reach the UI once the engine reports it", buffered)
        // SimulatedPlayerEngine buffers a fixed window ahead of the playhead.
        assertEquals(state.positionMs + 30_000L, buffered)

        finish(viewModel, engine)
    }

    @Test
    fun `stop is reported exactly once no matter how often teardown fires`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, recorder = recorder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        // onDispose and onCleared both route through the same guarded teardown; firing it repeatedly
        // must not produce duplicate stop reports on the server.
        viewModel.onStopped()
        viewModel.onStopped()
        viewModel.onStopped()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, recorder.paths().count { it == "/Sessions/Playing/Stopped" })
        // playback_info.json is a direct-play source, so no encoder should be torn down.
        assertEquals(0, recorder.paths().count { it == "/Videos/ActiveEncodings" })

        finish(viewModel, engine)
    }

    // --- §5 quality swap -------------------------------------------------------------------------

    /**
     * The whole point of the swap path: the outgoing session is closed out *and* the playback keeps
     * running, so the final teardown still owes the server one more stop. A swap that consumed the
     * teardown latch would leave the new session open on the server forever.
     */
    @Test
    fun `a quality change retires the old session without ending the playback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            // A transcoding source both starts and re-resolves cleanly, and leaves an encoder to
            // tear down, which is exactly what the swap has to clean up.
            playbackInfo = "playback_info_transcode.json",
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(VideoQuality.AUTO, viewModel.uiState.value.quality)
        val resolvesBefore = recorder.paths().count { it == "/Items/item-1/PlaybackInfo" }

        viewModel.setQuality(VideoQuality.HD)
        advanceTimeBy(1_000)
        runCurrent()

        // Re-resolved, and the chip now reflects the stream that actually won.
        assertEquals(resolvesBefore + 1, recorder.paths().count { it == "/Items/item-1/PlaybackInfo" })
        assertEquals(VideoQuality.HD, viewModel.uiState.value.quality)
        assertEquals(null, viewModel.uiState.value.notice)
        assertEquals(false, viewModel.uiState.value.swapping)
        // The outgoing stream was closed out, encoder and all.
        assertEquals(1, recorder.paths().count { it == "/Sessions/Playing/Stopped" })
        assertEquals(1, recorder.paths().count { it == "/Videos/ActiveEncodings" })

        // …and the replacement still owes its own stop when the screen finally goes.
        viewModel.onStopped()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, recorder.paths().count { it == "/Sessions/Playing/Stopped" })
        assertEquals(2, recorder.paths().count { it == "/Videos/ActiveEncodings" })

        finish(viewModel, engine)
    }

    @Test
    fun `a refused quality change keeps the current stream and surfaces a notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val engine = SimulatedPlayerEngine(scope = this)
        // The direct-play fixture offers no transcoding url, so a capped re-resolve cannot be served.
        val viewModel = viewModel(engine = engine, recorder = recorder)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()
        val positionBefore = viewModel.uiState.value.positionMs

        viewModel.setQuality(VideoQuality.SD)
        advanceTimeBy(1_000)
        runCurrent()

        val state = viewModel.uiState.value
        assertNotNull("a refused swap has to say so", state.notice)
        // Nothing was thrown away: same quality rung, still playing, no stop report.
        assertEquals(VideoQuality.AUTO, state.quality)
        assertEquals(null, state.error)
        assertEquals(false, state.swapping)
        assertTrue(state.positionMs >= positionBefore)
        assertEquals(0, recorder.paths().count { it == "/Sessions/Playing/Stopped" })

        finish(viewModel, engine)
    }

    // --- §5 episode adjacency / switchTo ---------------------------------------------------------

    @Test
    fun `episode adjacency and Up Next come from the series episode list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, extraRoutes = mapOf("/Shows/series-1/Episodes" to fakeRoute(EPISODES)))
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("ep-0", state.previousEpisodeId)
        assertEquals("ep-2", state.nextEpisodeId)
        assertEquals("ep-2", state.upNext?.itemId)
        assertEquals("The Dragon and the Wolf", state.upNext?.title)
        // user_me.json omits EnableNextEpisodeAutoPlay, whose server default is on.
        assertEquals(true, state.upNext?.autoPlay)
        // Far from the end, so the card is data-only until the last 30s.
        assertEquals(false, state.upNextVisible)

        viewModel.dismissUpNext()
        assertEquals(false, viewModel.uiState.value.upNextVisible)

        finish(viewModel, engine)
    }

    @Test
    fun `switchTo swaps the item in place at the target's resume position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            extraRoutes = mapOf(
                "/Shows/series-1/Episodes" to fakeRoute(EPISODES),
                "/Items/ep-2/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json")),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.playNextEpisode()
        advanceTimeBy(2_000)
        runCurrent()

        // The next episode was resolved and the outgoing session closed out — exactly one stop, so
        // the swap did not double-report or swallow the final one.
        assertTrue(recorder.paths().contains("/Items/ep-2/PlaybackInfo"))
        assertEquals(1, recorder.paths().count { it == "/Sessions/Playing/Stopped" })
        // The target resumes where it was left (item_detail.json: 17595000000 ticks = 1759.5s),
        // give or take the simulated ticker's advance since the load.
        assertTrue(
            "expected a resume near 1759500ms, got ${engine.positionMs.value}",
            engine.positionMs.value - 1_759_500L in 0..2_000L,
        )
        assertEquals(false, viewModel.uiState.value.swapping)

        viewModel.onStopped()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, recorder.paths().count { it == "/Sessions/Playing/Stopped" })

        finish(viewModel, engine)
    }

    // --- §5 More like this -----------------------------------------------------------------------

    @Test
    fun `the shelves load similar items once and cast rides on the item detail`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            extraRoutes = mapOf("/Items/item-1/Similar" to fakeRoute(FakeJellyfin.fixture("items_query.json"))),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        // Cast needs no request of its own: People rode in on the item detail.
        val cast = viewModel.uiState.value.cast
        assertEquals(listOf("Peter Dinklage", "Miguel Sapochnik"), cast.map { it.name })
        assertEquals("Tyrion Lannister", cast.first().role)
        assertNotNull(cast.first().imageUrl)
        // No portrait tag on the second person, so no URL is invented for them.
        assertEquals(null, cast[1].imageUrl)

        // The shelf is lazy — nothing was fetched until it opened.
        assertEquals(0, recorder.paths().count { it == "/Items/item-1/Similar" })
        viewModel.loadShelves()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("Test Movie"), viewModel.uiState.value.similar.map { it.title })

        // Re-opening the shelf does not re-fetch it.
        viewModel.loadShelves()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, recorder.paths().count { it == "/Items/item-1/Similar" })

        finish(viewModel, engine)
    }

    // --- §3 trickplay ------------------------------------------------------------------------------

    @Test
    fun `the trickplay manifest reaches the ui state from the item query`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        val trickplay = viewModel.uiState.value.trickplay
        assertNotNull("item_detail.json carries a 320px band for media-1", trickplay)
        assertEquals(320, trickplay!!.info.width)
        assertEquals(
            "http://myserver/Videos/item-1/Trickplay/320/0.jpg?api_key=tok123&mediaSourceId=media-1",
            trickplay.tileUrl(0),
        )

        finish(viewModel, engine)
    }

    // --- helpers -----------------------------------------------------------------------------------

    /**
     * Three episodes of `series-1`, with the fixture's own item under test in the middle so
     * adjacency has a neighbour on each side.
     */
    private val EPISODES = """
        {
          "Items": [
            {"Id":"ep-0","Name":"Beyond the Wall","Type":"Episode","IndexNumber":6,"ParentIndexNumber":7,"SeriesId":"series-1"},
            {"Id":"item-1","Name":"The Winds of Winter","Type":"Episode","IndexNumber":7,"ParentIndexNumber":7,"SeriesId":"series-1"},
            {"Id":"ep-2","Name":"The Dragon and the Wolf","Type":"Episode","IndexNumber":8,"ParentIndexNumber":7,"SeriesId":"series-1","ImageTags":{"Primary":"ep-2-tag"}}
          ],
          "TotalRecordCount": 3,
          "StartIndex": 0
        }
    """.trimIndent()

    /**
     * Ends a test cleanly.
     *
     * The ViewModel's own coroutines (the reporter loop, the `stateIn` sharing job) live in
     * `viewModelScope` on the test's Main dispatcher and nothing in production cancels them until
     * the ViewModel is cleared. Left running, they are still dispatching when JUnit's `@After`
     * calls `resetMain()`, which throws "Dispatchers.Main is used concurrently with setting it" —
     * and lands the failure on whichever test happened to be unlucky. Cancelling here, while the
     * scheduler can still drain, keeps each test self-contained.
     */
    private fun TestScope.finish(viewModel: PlayerViewModel, engine: SimulatedPlayerEngine) {
        viewModel.viewModelScope.cancel()
        engine.release()
        runCurrent()
    }

    private fun TestScope.viewModel(
        engine: SimulatedPlayerEngine,
        recorder: RequestRecorder? = null,
        playbackInfo: String = "playback_info.json",
        extraRoutes: Map<String, FakeRoute> = emptyMap(),
    ): PlayerViewModel {
        val client = FakeJellyfin.client(
            routes = mapOf(
                "/Items" to fakeRoute(FakeJellyfin.fixture("item_detail.json")),
                "/Items/item-1/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture(playbackInfo)),
                "/Users/Me" to fakeRoute(FakeJellyfin.fixture("user_me.json")),
                "/Sessions/Playing" to fakeRoute("{}"),
                "/Sessions/Playing/Progress" to fakeRoute("{}"),
                "/Sessions/Playing/Ping" to FakeRoute(HttpStatusCode.NoContent, ""),
                "/Sessions/Playing/Stopped" to fakeRoute("{}"),
                "/Videos/ActiveEncodings" to FakeRoute(HttpStatusCode.NoContent, ""),
            ) + extraRoutes,
            sessions = StaticSessionProvider(session),
            recorder = recorder,
        )
        val playbackRepository = PlaybackRepository(client)
        return PlayerViewModel(
            engine = engine,
            playbackRepository = playbackRepository,
            libraryRepository = LibraryRepository(client),
            authRepository = AuthRepository(client, FakeJellyfin.newSessionStore()),
            imageUrlBuilder = ImageUrlBuilder { session.serverUrl },
            reporter = PlaybackProgressReporter(playbackRepository),
            itemId = "item-1",
            startPositionTicks = 0,
            teardownScope = backgroundScope,
        )
    }
}
