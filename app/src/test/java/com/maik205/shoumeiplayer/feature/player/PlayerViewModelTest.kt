package com.maik205.shoumeiplayer.feature.player

import com.maik205.shoumeiplayer.di.player.JellyfinPlaybackMetadataLoader

import com.maik205.shoumeiplayer.data.FakeJellyfin
import com.maik205.shoumeiplayer.data.InMemoryPreferencesDataStore
import com.maik205.shoumeiplayer.data.FakeRoute
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.RequestRecorder
import com.maik205.shoumeiplayer.data.StaticSessionProvider
import com.maik205.shoumeiplayer.data.fakeRoute
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.data.session.UserConfigurationStore
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.PlayerSettingsRepository
import com.maik205.shoumeiplayer.domain.settings.ResumeBehavior
import com.maik205.shoumeiplayer.player.PlaybackProgressReporter
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.SimulatedPlayerEngine
import com.maik205.shoumeiplayer.player.TrackType
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
    fun `timelineState carries the engine buffered position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine)

        // uiState is WhileSubscribed, so it only runs while something collects it.
        backgroundScope.launch { viewModel.uiState.collect { } }
        backgroundScope.launch { viewModel.timelineState.collect { } }

        // Let resolve + configuration + engine.load settle, then run the ticker a few seconds.
        advanceTimeBy(5_000)
        runCurrent()

        val uiState = viewModel.uiState.value
        val timeline = viewModel.timelineState.value
        assertEquals(false, uiState.loading)
        assertEquals(null, uiState.error)
        val buffered = timeline.bufferedMs
        assertNotNull("bufferedMs should reach the UI once the engine reports it", buffered)
        // SimulatedPlayerEngine buffers a fixed window ahead of the playhead.
        assertEquals(timeline.positionMs + 30_000L, buffered)

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
        backgroundScope.launch { viewModel.timelineState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()
        val positionBefore = viewModel.timelineState.value.positionMs

        viewModel.setQuality(VideoQuality.SD)
        advanceTimeBy(1_000)
        runCurrent()

        val state = viewModel.uiState.value
        assertNotNull("a refused swap has to say so", state.notice)
        // Nothing was thrown away: same quality rung, still playing, no stop report.
        assertEquals(VideoQuality.AUTO, state.quality)
        assertEquals(null, state.error)
        assertEquals(false, state.swapping)
        assertTrue(viewModel.timelineState.value.positionMs >= positionBefore)
        assertEquals(0, recorder.paths().count { it == "/Sessions/Playing/Stopped" })

        finish(viewModel, engine)
    }

    // --- §95 in-player overrides stay session-scoped ----------------------------------------------

    @Test
    fun `an in-player audio delay does not persist and does not leak into the next playback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsStore = FakePlayerSettingsRepository()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, settingsStore = settingsStore)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.setAudioDelayMs(250L)
        viewModel.setSubtitleDelayMs(-75L)
        runCurrent()

        // The correction is visible for this session...
        assertEquals(250L, viewModel.uiState.value.audioDelayMs)
        assertEquals(-75L, viewModel.uiState.value.subtitleDelayMs)
        // ...but never wrote through to the global store a Settings-screen change would use.
        assertEquals(0, settingsStore.audioDelayMs)
        assertEquals(0, settingsStore.subtitleDelayMs)

        finish(viewModel, engine)

        // A fresh playback session reads the (untouched) persisted defaults, not the prior
        // session's per-item nudge.
        val engine2 = SimulatedPlayerEngine(scope = this)
        val viewModel2 = viewModel(engine = engine2, settingsStore = settingsStore)
        backgroundScope.launch { viewModel2.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(0L, viewModel2.uiState.value.audioDelayMs)
        assertEquals(0L, viewModel2.uiState.value.subtitleDelayMs)

        finish(viewModel2, engine2)
    }

    @Test
    fun `an in-player quality change does not persist the new default`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsStore = FakePlayerSettingsRepository()
        val recorder = RequestRecorder()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            playbackInfo = "playback_info_transcode.json",
            settingsStore = settingsStore,
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.setQuality(VideoQuality.HD)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(VideoQuality.HD, viewModel.uiState.value.quality)
        assertTrue("a per-item quality cap must not persist", settingsStore.preferredQualityWrites.isEmpty())

        finish(viewModel, engine)
    }

    // --- §91 resume behavior -----------------------------------------------------------------------

    @Test
    fun `Restart always starts over even with a saved position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsStore = FakePlayerSettingsRepository(resumeBehavior = ResumeBehavior.Restart)
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, startPositionTicks = 50_000_000L, settingsStore = settingsStore)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(0L, engine.positionMs.value)
        assertEquals(null, viewModel.uiState.value.resumePrompt)

        finish(viewModel, engine)
    }

    @Test
    fun `Resume always honours the saved position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsStore = FakePlayerSettingsRepository(resumeBehavior = ResumeBehavior.Resume)
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, startPositionTicks = 50_000_000L, settingsStore = settingsStore)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(5_000L, engine.positionMs.value)
        assertEquals(null, viewModel.uiState.value.resumePrompt)

        finish(viewModel, engine)
    }

    @Test
    fun `Ask resumes immediately and surfaces a prompt the host screen can act on`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsStore = FakePlayerSettingsRepository(resumeBehavior = ResumeBehavior.Ask)
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, startPositionTicks = 50_000_000L, settingsStore = settingsStore)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(1_000)
        runCurrent()

        // No blocking dialog owned here — Ask still resumes so playback is never stuck waiting.
        assertEquals(5_000L, engine.positionMs.value)
        assertEquals(5_000L, viewModel.uiState.value.resumePrompt?.positionMs)

        // The host screen can offer "start over" without a re-resolve.
        viewModel.confirmResumePrompt(restart = true)
        runCurrent()

        assertEquals(0L, engine.positionMs.value)
        assertEquals(null, viewModel.uiState.value.resumePrompt)

        finish(viewModel, engine)
    }

    @Test
    fun `Ask does not surface a prompt when there is nothing to resume`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsStore = FakePlayerSettingsRepository(resumeBehavior = ResumeBehavior.Ask)
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, startPositionTicks = 0L, settingsStore = settingsStore)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(0L, engine.positionMs.value)
        assertEquals(null, viewModel.uiState.value.resumePrompt)

        finish(viewModel, engine)
    }

    // --- §5 episode adjacency / switchTo ---------------------------------------------------------

    @Test
    fun `episode adjacency and Up Next come from the series episode list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, extraRoutes = mapOf("/Shows/series-1/Episodes" to fakeRoute(EPISODES)))
        backgroundScope.launch { viewModel.uiState.collect { } }
        backgroundScope.launch { viewModel.timelineState.collect { } }
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
        assertEquals(false, viewModel.timelineState.value.upNextVisible)

        viewModel.dismissUpNext()
        assertEquals(false, viewModel.timelineState.value.upNextVisible)

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

    @Test
    fun `Restart also applies when switchTo resolves the target, not just the initial entry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val settingsStore = FakePlayerSettingsRepository(resumeBehavior = ResumeBehavior.Restart)
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            settingsStore = settingsStore,
            extraRoutes = mapOf(
                "/Shows/series-1/Episodes" to fakeRoute(EPISODES),
                // ep-2's own item_detail.json carries a non-zero saved position (17595000000
                // ticks); Restart must discard it exactly as it does from Detail.
                "/Items/ep-2/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json")),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        // playNextEpisode() drives switchTo() — the Up Next / episode-picker path, not
        // startInitialPlayback. Before the §91 fix this landed at ~1,759,500ms regardless.
        viewModel.playNextEpisode()
        // Stay under the simulated engine's ~1,300ms warm-up-plus-first-tick window (see the
        // Restart tests above, which all advance by 1_000ms for the same reason) so this assertion
        // observes the freshly-reset position rather than one tick of simulated playback on ep-2.
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(0L, engine.positionMs.value)
        assertEquals(null, viewModel.uiState.value.resumePrompt)

        finish(viewModel, engine)
    }

    @Test
    fun `a stale resume prompt from the previous item does not survive switchTo`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val settingsStore = FakePlayerSettingsRepository(resumeBehavior = ResumeBehavior.Ask)
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            startPositionTicks = 50_000_000L,
            settingsStore = settingsStore,
            extraRoutes = mapOf(
                "/Shows/series-1/Episodes" to fakeRoute(EPISODES),
                "/Items/ep-2/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json")),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        // Entering item-1 under Ask with a saved position records a prompt for item-1.
        assertEquals(5_000L, viewModel.uiState.value.resumePrompt?.positionMs)

        viewModel.playNextEpisode()
        advanceTimeBy(2_000)
        runCurrent()

        // ep-2 also has a saved position, so Ask still records a prompt — but it must describe
        // ep-2's resume point, not the stale 5,000ms carried over from item-1.
        val prompt = viewModel.uiState.value.resumePrompt
        assertNotNull("Ask should still record a prompt for the new item", prompt)
        assertTrue(
            "expected the prompt to reflect ep-2's own resume position, not item-1's stale 5,000ms",
            prompt!!.positionMs != 5_000L,
        )

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

    @Test
    fun `detail playback selections are applied to the initial prepare`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            initialAudioStreamIndex = 1,
            initialSubtitleStreamIndex = -1,
            initialQualityLabel = "720p",
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(1_000)
        runCurrent()

        val prepareIndex = recorder.paths().indexOf("/Items/item-1/PlaybackInfo")
        assertTrue("PlaybackInfo prepare was not recorded", prepareIndex >= 0)
        val body = recorder.bodyAt(prepareIndex)
        assertTrue(body.contains("\"AudioStreamIndex\":1"))
        assertTrue(body.contains("\"SubtitleStreamIndex\":-1"))
        assertTrue(body.contains("\"MaxStreamingBitrate\":8000000"))
        assertEquals(VideoQuality.HD, viewModel.uiState.value.quality)

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

    /**
     * §88 — the [TrackController] instance used by the most recently built [viewModel], so tests can
     * inspect [TrackController.selectedAudioIndex] directly: the simulated engine's own `tracks`
     * list is a fixed fixture that ignores `PlayRequest.preferredAudioTrackId`, so it cannot be used
     * to observe which track the ViewModel actually asked for on a fresh attach.
     */
    private var lastTrackController: TrackController? = null

    private fun TestScope.viewModel(
        engine: SimulatedPlayerEngine,
        recorder: RequestRecorder? = null,
        playbackInfo: String = "playback_info.json",
        extraRoutes: Map<String, FakeRoute> = emptyMap(),
        initialAudioStreamIndex: Int? = null,
        initialSubtitleStreamIndex: Int? = null,
        initialQualityLabel: String? = null,
        startPositionTicks: Long = 0,
        settingsStore: PlayerSettingsRepository? = null,
        preferenceMemory: PlaybackPreferenceMemory? = null,
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
        val libraryRepository = LibraryRepository(client)
        val authRepository = AuthRepository(
            client,
            FakeJellyfin.newSessionStore(),
            UserConfigurationStore(InMemoryPreferencesDataStore()),
        )
        val imageUrlBuilder = ImageUrlBuilder { session.serverUrl }
        val trackController = TrackController(
            preferenceProvider = authRepository,
            initialAudioStreamIndex = initialAudioStreamIndex,
            initialSubtitleStreamIndex = initialSubtitleStreamIndex,
        )
        lastTrackController = trackController
        return PlayerViewModel(
            engine = engine,
            playbackResolver = playbackRepository,
            trackController = trackController,
            metadataLoader = JellyfinPlaybackMetadataLoader(
                libraryRepository = libraryRepository,
                authRepository = authRepository,
                imageUrlBuilder = imageUrlBuilder,
                settingsStore = null,
                playbackRepository = playbackRepository,
            ),
            reporter = PlaybackProgressReporter(playbackRepository),
            itemId = "item-1",
            startPositionTicks = startPositionTicks,
            teardownScope = backgroundScope,
            settingsStore = settingsStore,
            initialQualityLabel = initialQualityLabel,
            preferenceMemory = preferenceMemory,
        )
    }

    /**
     * Records what was persisted so §95's tests can assert in-player overrides never reach it, and
     * carries settable [resumeBehavior] / [rememberSeriesAudio] / [rememberPlaybackSpeed] so §88/§91's
     * tests can drive each branch independently of `ClientSettings`' real defaults.
     */
    private class FakePlayerSettingsRepository(
        var resumeBehavior: ResumeBehavior = ResumeBehavior.Ask,
        var rememberSeriesAudio: Boolean = true,
        var rememberPlaybackSpeed: Boolean = false,
    ) : PlayerSettingsRepository {
        var audioDelayMs: Int = 0
            private set
        var subtitleDelayMs: Int = 0
            private set
        val preferredQualityWrites = mutableListOf<String>()

        override suspend fun current(): ClientSettings = ClientSettings(
            resumeBehavior = resumeBehavior,
            rememberSeriesAudio = rememberSeriesAudio,
            rememberPlaybackSpeed = rememberPlaybackSpeed,
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs,
        )

        override suspend fun setPreferredQuality(value: String) {
            preferredQualityWrites += value
        }

        override suspend fun setAudioDelayMs(value: Int) {
            audioDelayMs = value
        }

        override suspend fun setSubtitleDelayMs(value: Int) {
            subtitleDelayMs = value
        }
    }

    /**
     * In-memory stand-in for the real `PreferenceStore`-backed [PlaybackPreferenceMemory] the DI
     * layer supplies in production. Backing maps are exposed directly (not just through the
     * interface) so tests can assert on exactly what was — or was not — written, the same way
     * [FakePlayerSettingsRepository] exposes its writes.
     */
    private class FakePlaybackPreferenceMemory : PlaybackPreferenceMemory {
        val seriesAudio = mutableMapOf<String, Int>()
        val speeds = mutableMapOf<String, Float>()
        val delays = mutableMapOf<String, ItemTrackDelays>()
        val qualityCaps = mutableMapOf<String, String>()

        override suspend fun seriesAudioTrack(seriesId: String): Int? = seriesAudio[seriesId]

        override suspend fun setSeriesAudioTrack(seriesId: String, audioIndex: Int) {
            seriesAudio[seriesId] = audioIndex
        }

        override suspend fun playbackSpeed(itemId: String): Float? = speeds[itemId]

        override suspend fun setPlaybackSpeed(itemId: String, speed: Float) {
            speeds[itemId] = speed
        }

        override suspend fun trackDelays(itemId: String): ItemTrackDelays? = delays[itemId]

        override suspend fun setTrackDelays(itemId: String, delays: ItemTrackDelays?) {
            if (delays == null) this.delays.remove(itemId) else this.delays[itemId] = delays
        }

        override suspend fun qualityCapLabel(itemId: String): String? = qualityCaps[itemId]

        override suspend fun setQualityCapLabel(itemId: String, label: String?) {
            if (label == null) qualityCaps.remove(itemId) else qualityCaps[itemId] = label
        }
    }

    /**
     * Same shape as `core/data`'s `playback_info.json` fixture but with two extra audio streams —
     * Japanese (index 2) and French (index 3) — alongside the English default (index 1).
     *
     * `user_me.json` (shared by every test in this file) carries `PlayDefaultAudioTrack: false` and
     * `AudioLanguagePreference: "jpn"`, so [TrackSelection]'s own server-preference default already
     * lands on the *Japanese* track, not the English one `IsDefault`/`DefaultAudioStreamIndex` name.
     * §88's tests deliberately pick the *French* track as the "explicit" choice — a third option
     * that neither the default flag nor the language preference would ever land on — so a test
     * asserting the remembered index took effect cannot pass merely because it coincides with the
     * ordinary default.
     */
    private val PLAYBACK_INFO_THREE_AUDIO_TRACKS = """
        {
          "MediaSources": [
            {
              "Id": "media-1",
              "Container": "mkv",
              "Protocol": "File",
              "RunTimeTicks": 72000000000,
              "SupportsDirectPlay": true,
              "SupportsDirectStream": true,
              "SupportsTranscoding": true,
              "MediaStreams": [
                {"Index": 0, "Type": "Video", "Codec": "h264"},
                {"Index": 1, "Type": "Audio", "Codec": "aac", "Language": "eng", "DisplayTitle": "English (AAC 5.1)", "IsDefault": true},
                {"Index": 2, "Type": "Audio", "Codec": "aac", "Language": "jpn", "DisplayTitle": "Japanese (AAC)"},
                {"Index": 3, "Type": "Audio", "Codec": "flac", "Language": "fre", "DisplayTitle": "French (FLAC)"},
                {"Index": 4, "Type": "Subtitle", "Codec": "subrip", "Language": "eng", "DisplayTitle": "English (SRT)"}
              ],
              "DefaultAudioStreamIndex": 1
            }
          ],
          "PlaySessionId": "play-session-1"
        }
    """.trimIndent()

    // --- §88 rememberSeriesAudio ---------------------------------------------------------------

    @Test
    fun `an explicit audio pick is remembered and adopted by the next episode of the same series`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        // rememberSeriesAudio defaults to true (see ClientSettings), but only the presence of a
        // ClientSettings instance activates it -- a null settingsStore reads as "no opinion" and
        // would leave the feature dormant regardless of that default.
        val settingsStore = FakePlayerSettingsRepository()
        val memory = FakePlaybackPreferenceMemory()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            settingsStore = settingsStore,
            preferenceMemory = memory,
            extraRoutes = mapOf(
                "/Items/item-1/PlaybackInfo" to fakeRoute(PLAYBACK_INFO_THREE_AUDIO_TRACKS),
                "/Items/ep-2/PlaybackInfo" to fakeRoute(PLAYBACK_INFO_THREE_AUDIO_TRACKS),
                "/Shows/series-1/Episodes" to fakeRoute(EPISODES),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        // Without a remembered pick, episode 1 lands on the account's language preference (index 2,
        // Japanese) — see PLAYBACK_INFO_THREE_AUDIO_TRACKS's KDoc for why that, not the "default"
        // flag on index 1, is what TrackSelection actually picks here.
        assertEquals(2, lastTrackController?.selectedAudioIndex)

        // The viewer explicitly switches to French (index 3). A direct-play source applies the pick
        // immediately, with no stream swap.
        viewModel.selectTrack(PlayerTrack(id = 3, type = TrackType.AUDIO, label = "French (FLAC)"))
        runCurrent()
        assertEquals(3, lastTrackController?.selectedAudioIndex)
        // Keyed by series *name* (see PlayerViewModel.rememberSeriesAudioChoice): item_detail.json's
        // fixture is shared by every item this fake server serves, and carries "Game of Thrones" as
        // SeriesName for both item-1 and ep-2.
        assertEquals(3, memory.seriesAudio["Game of Thrones"])

        viewModel.playNextEpisode()
        advanceTimeBy(2_000)
        runCurrent()

        // ep-2 carries no explicit request of its own — without the remembered pick it would land
        // back on index 2 (the language preference), exactly like episode 1 did before the viewer
        // touched anything.
        assertEquals(3, lastTrackController?.selectedAudioIndex)

        finish(viewModel, engine)
    }

    @Test
    fun `a track TrackController merely defaulted to is never written back as a remembered pick`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val memory = FakePlaybackPreferenceMemory()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            preferenceMemory = memory,
            extraRoutes = mapOf(
                "/Items/item-1/PlaybackInfo" to fakeRoute(PLAYBACK_INFO_THREE_AUDIO_TRACKS),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        // Playing the item through to a default selection, with no explicit pick, must not plant a
        // memory entry — a series nobody has touched keeps following TrackSelection's own logic.
        assertEquals(2, lastTrackController?.selectedAudioIndex)
        assertTrue(memory.seriesAudio.isEmpty())

        finish(viewModel, engine)
    }

    @Test
    fun `an explicit audio pick is not remembered when rememberSeriesAudio is off`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsStore = FakePlayerSettingsRepository(rememberSeriesAudio = false)
        val memory = FakePlaybackPreferenceMemory()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            settingsStore = settingsStore,
            preferenceMemory = memory,
            extraRoutes = mapOf(
                "/Items/item-1/PlaybackInfo" to fakeRoute(PLAYBACK_INFO_THREE_AUDIO_TRACKS),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.selectTrack(PlayerTrack(id = 3, type = TrackType.AUDIO, label = "French (FLAC)"))
        runCurrent()

        assertTrue(memory.seriesAudio.isEmpty())

        finish(viewModel, engine)
    }

    // --- §88 rememberPlaybackSpeed ----------------------------------------------------------------

    @Test
    fun `a remembered playback speed reapplies per item and does not leak to a different item`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val settingsStore = FakePlayerSettingsRepository(rememberPlaybackSpeed = true)
        val memory = FakePlaybackPreferenceMemory()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            settingsStore = settingsStore,
            preferenceMemory = memory,
            extraRoutes = mapOf(
                "/Shows/series-1/Episodes" to fakeRoute(EPISODES),
                "/Items/ep-2/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json")),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.setSpeed(1.5f)
        runCurrent()
        assertEquals(1.5f, engine.speed.value)
        assertEquals(1.5f, memory.speeds["item-1"])

        viewModel.playNextEpisode()
        advanceTimeBy(2_000)
        runCurrent()
        // ep-2 has no memory of its own — item-1's rate must not leak onto it.
        assertEquals(1.0f, engine.speed.value)

        finish(viewModel, engine)

        // A fresh session re-entering item-1 restores exactly the rate it was left at.
        val engine2 = SimulatedPlayerEngine(scope = this)
        val viewModel2 = viewModel(engine = engine2, settingsStore = settingsStore, preferenceMemory = memory)
        backgroundScope.launch { viewModel2.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1.5f, engine2.speed.value)

        finish(viewModel2, engine2)
    }

    @Test
    fun `playback speed is not remembered when rememberPlaybackSpeed is off`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsStore = FakePlayerSettingsRepository(rememberPlaybackSpeed = false)
        val memory = FakePlaybackPreferenceMemory()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, settingsStore = settingsStore, preferenceMemory = memory)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.setSpeed(1.5f)
        runCurrent()

        assertTrue(memory.speeds.isEmpty())

        finish(viewModel, engine)
    }

    // --- §95 per-item delay / quality overrides ---------------------------------------------------

    @Test
    fun `an in-player audio delay persists per item, reapplies on reopen, and does not leak to a different item`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val recorder = RequestRecorder()
            val memory = FakePlaybackPreferenceMemory()
            val engine = SimulatedPlayerEngine(scope = this)
            val viewModel = viewModel(
                engine = engine,
                recorder = recorder,
                preferenceMemory = memory,
                extraRoutes = mapOf(
                    "/Shows/series-1/Episodes" to fakeRoute(EPISODES),
                    "/Items/ep-2/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info.json")),
                ),
            )
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceTimeBy(5_000)
            runCurrent()

            viewModel.setAudioDelayMs(250L)
            runCurrent()
            assertEquals(250L, viewModel.uiState.value.audioDelayMs)
            assertEquals(250L, memory.delays["item-1"]?.audioDelayMs)

            viewModel.playNextEpisode()
            advanceTimeBy(2_000)
            runCurrent()
            // ep-2 has no override of its own — item-1's nudge must not leak onto it.
            assertEquals(0L, viewModel.uiState.value.audioDelayMs)

            finish(viewModel, engine)

            // A fresh session re-entering item-1 restores exactly the delay it was left with.
            val engine2 = SimulatedPlayerEngine(scope = this)
            val viewModel2 = viewModel(engine = engine2, preferenceMemory = memory)
            backgroundScope.launch { viewModel2.uiState.collect { } }
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(250L, viewModel2.uiState.value.audioDelayMs)

            finish(viewModel2, engine2)
        }

    @Test
    fun `resetPlaybackDelays clears the persisted per-item override, not just the session state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val memory = FakePlaybackPreferenceMemory()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(engine = engine, preferenceMemory = memory)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.setAudioDelayMs(300L)
        viewModel.setSubtitleDelayMs(-50L)
        runCurrent()
        assertEquals(ItemTrackDelays(300L, -50L), memory.delays["item-1"])

        viewModel.resetPlaybackDelays()
        runCurrent()
        assertEquals(null, memory.delays["item-1"])

        finish(viewModel, engine)

        // A fresh session re-entering item-1 sees no override — not a persisted (0, 0) pair.
        val engine2 = SimulatedPlayerEngine(scope = this)
        val viewModel2 = viewModel(engine = engine2, preferenceMemory = memory)
        backgroundScope.launch { viewModel2.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0L, viewModel2.uiState.value.audioDelayMs)
        assertEquals(0L, viewModel2.uiState.value.subtitleDelayMs)

        finish(viewModel2, engine2)
    }

    @Test
    fun `an in-player quality cap persists per item and does not leak to a different item`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = RequestRecorder()
        val memory = FakePlaybackPreferenceMemory()
        val engine = SimulatedPlayerEngine(scope = this)
        val viewModel = viewModel(
            engine = engine,
            recorder = recorder,
            playbackInfo = "playback_info_transcode.json",
            preferenceMemory = memory,
            extraRoutes = mapOf(
                "/Shows/series-1/Episodes" to fakeRoute(EPISODES),
                "/Items/ep-2/PlaybackInfo" to fakeRoute(FakeJellyfin.fixture("playback_info_transcode.json")),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()

        viewModel.setQuality(VideoQuality.HD)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(VideoQuality.HD, viewModel.uiState.value.quality)
        assertEquals("720p", memory.qualityCaps["item-1"])

        viewModel.playNextEpisode()
        advanceTimeBy(2_000)
        runCurrent()
        // ep-2 has no cap of its own — falls back to Auto (the global default), not item-1's pin.
        assertEquals(VideoQuality.AUTO, viewModel.uiState.value.quality)

        finish(viewModel, engine)

        // A fresh session re-entering item-1 restores exactly the cap it was left at.
        val engine2 = SimulatedPlayerEngine(scope = this)
        val viewModel2 = viewModel(
            engine = engine2,
            playbackInfo = "playback_info_transcode.json",
            preferenceMemory = memory,
        )
        backgroundScope.launch { viewModel2.uiState.collect { } }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(VideoQuality.HD, viewModel2.uiState.value.quality)

        finish(viewModel2, engine2)
    }
}
