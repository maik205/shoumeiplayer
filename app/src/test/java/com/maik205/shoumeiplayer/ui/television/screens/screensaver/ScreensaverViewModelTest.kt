package com.maik205.shoumeiplayer.ui.television.screens.screensaver

import com.maik205.shoumeiplayer.data.InMemoryPreferencesDataStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.domain.model.LibraryDestination
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.domain.model.MediaPage
import com.maik205.shoumeiplayer.domain.model.MediaPageRequest
import com.maik205.shoumeiplayer.domain.repository.MediaCatalog
import com.maik205.shoumeiplayer.domain.result.ApiError
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The screensaver over a real [SettingsStore] and a recording [MediaCatalog] (#87).
 *
 * [ScreensaverPolicyTest] proves the decisions in isolation; this proves they are actually *wired*
 * -- that the six persisted preferences reach the running screensaver, that the chosen content
 * source decides which libraries get fetched, and that the host's idle loop, driven here exactly as
 * `ScreensaverHost` drives it, cannot put a screensaver over a playing film. Reverting any of those
 * connections fails a test here rather than merely leaving a default unasserted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreensaverViewModelTest {

    /**
     * One dispatcher for the class, whose scheduler every `runTest` borrows: `viewModelScope` work
     * is otherwise never drained by `advanceUntilIdle`, and `resetMain` then pulls Dispatchers.Main
     * out from under coroutines that are still running.
     */
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- the settings actually reach the screensaver ---------------------------------------------

    @Test
    fun `the persisted timeout, dwell time and clock choice reach the running screensaver`() =
        runTest(mainDispatcher.scheduler) {
            val viewModel = viewModel(
                ClientSettings(
                    screensaverTimeoutMinutes = 30,
                    screensaverImageDurationSeconds = 45,
                    screensaverClock = false,
                ),
            )
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(30, state.timeoutMinutes)
            assertEquals(45_000L, state.imageDurationMillis)
            assertFalse(state.showClock)
        }

    @Test
    fun `choosing Never leaves the host with no timeout to arm`() = runTest(mainDispatcher.scheduler) {
        val viewModel = viewModel(ClientSettings(screensaverTimeoutMinutes = 0))
        advanceUntilIdle()

        // This is the exact expression ScreensaverHost gates its watchdog on.
        assertNull(screensaverIdleTimeoutMillis(viewModel.state.value.timeoutMinutes))

        driveIdleWatchdog(viewModel.state.value.timeoutMinutes, hours = 8) { viewModel.start() }
        advanceUntilIdle()
        assertFalse(viewModel.state.value.visible)
    }

    // --- content filtering reaches the server ----------------------------------------------------

    @Test
    fun `the chosen content source decides which libraries are fetched at all`() =
        runTest(mainDispatcher.scheduler) {
            val catalog = RecordingCatalog()
            val viewModel = viewModel(
                ClientSettings(screensaverContent = ScreensaverContent.Shows, screensaverShuffle = false),
                catalog = catalog,
            )
            advanceUntilIdle()

            viewModel.start()
            advanceUntilIdle()

            // Not "fetched everything, then filtered": the movie and music libraries are never
            // asked for at all, which is the difference the setting is supposed to make.
            assertEquals(listOf("lib-shows"), catalog.requestedLibraries)
            assertEquals("SHOW-1", viewModel.state.value.slide?.title)
        }

    @Test
    fun `all libraries draws from every browsable library in one pool`() =
        runTest(mainDispatcher.scheduler) {
            val catalog = RecordingCatalog()
            val viewModel = viewModel(
                ClientSettings(
                    screensaverContent = ScreensaverContent.AllLibraries,
                    screensaverShuffle = false,
                ),
                catalog = catalog,
            )
            advanceUntilIdle()

            viewModel.start()
            advanceUntilIdle()
            val shown = generateSequence { viewModel.state.value.slide?.title.also { viewModel.advance() } }
                .take(5)
                .toList()

            assertEquals(listOf("lib-movies", "lib-shows", "lib-music"), catalog.requestedLibraries)
            assertEquals(listOf("MOVIE-1", "MOVIE-2", "SHOW-1", "MUSIC-1", "MOVIE-1"), shown)
        }

    @Test
    fun `an item with no artwork never becomes a slide`() = runTest(mainDispatcher.scheduler) {
        val catalog = RecordingCatalog(
            itemsByLibrary = mapOf(
                "lib-movies" to listOf(
                    item("art-less", backdrop = null, poster = null),
                    item("movie-1"),
                ),
            ),
        )
        val viewModel = viewModel(
            ClientSettings(screensaverContent = ScreensaverContent.Movies, screensaverShuffle = false),
            catalog = catalog,
        )
        advanceUntilIdle()

        viewModel.start()
        advanceUntilIdle()
        viewModel.advance()

        // Both draws land on the only item that has artwork; the art-less one is not in the pool.
        assertEquals("MOVIE-1", viewModel.state.value.slide?.title)
    }

    @Test
    fun `an unreachable server leaves the viewer's screen alone rather than blacking it out`() =
        runTest(mainDispatcher.scheduler) {
            val viewModel = viewModel(ClientSettings(), catalog = RecordingCatalog(librariesFail = true))
            advanceUntilIdle()

            driveIdleWatchdog(timeoutMinutes = 5, hours = 1) { viewModel.start() }
            advanceUntilIdle()

            assertFalse(viewModel.state.value.visible)
            assertNull(viewModel.state.value.slide)
        }

    // --- playback suppression, driven the way the host drives it ---------------------------------

    @Test
    fun `video playback keeps the screensaver off however long the remote has been idle`() =
        runTest(mainDispatcher.scheduler) {
            val viewModel = viewModel(ClientSettings(screensaverTimeoutMinutes = 5))
            advanceUntilIdle()

            // Three hours of watchdog ticks while the player holds FLAG_KEEP_SCREEN_ON.
            driveIdleWatchdog(timeoutMinutes = 5, hours = 3, playbackHoldsScreenOn = true) { viewModel.start() }
            advanceUntilIdle()
            assertFalse(viewModel.state.value.visible)

            // The same idle time with nothing holding the screen on does arm it -- so the assertion
            // above is about playback, not about a screensaver that never works at all.
            driveIdleWatchdog(timeoutMinutes = 5, hours = 3, playbackHoldsScreenOn = false) { viewModel.start() }
            advanceUntilIdle()
            assertTrue(viewModel.state.value.visible)
        }

    // --- rotation, end to end --------------------------------------------------------------------

    @Test
    fun `avoid repeats shows every item once per cycle before any comes round again`() =
        runTest(mainDispatcher.scheduler) {
            val catalog = RecordingCatalog(
                itemsByLibrary = mapOf("lib-movies" to (1..4).map { item("movie-$it") }),
            )
            val viewModel = viewModel(
                ClientSettings(
                    screensaverContent = ScreensaverContent.Movies,
                    screensaverShuffle = true,
                    screensaverAvoidRepeats = true,
                ),
                catalog = catalog,
            )
            advanceUntilIdle()

            viewModel.start()
            advanceUntilIdle()
            val shown = generateSequence { viewModel.state.value.slide?.title.also { viewModel.advance() } }
                .take(8)
                .toList()

            assertEquals(4, shown.take(4).toSet().size)
            assertEquals(4, shown.drop(4).toSet().size)
        }

    @Test
    fun `dismissing resumes the cycle rather than restarting it`() = runTest(mainDispatcher.scheduler) {
        val catalog = RecordingCatalog(
            itemsByLibrary = mapOf("lib-movies" to (1..4).map { item("movie-$it") }),
        )
        val viewModel = viewModel(
            ClientSettings(screensaverContent = ScreensaverContent.Movies, screensaverShuffle = false),
            catalog = catalog,
        )
        advanceUntilIdle()

        viewModel.start()
        advanceUntilIdle()
        assertEquals("MOVIE-1", viewModel.state.value.slide?.title)
        viewModel.advance()
        assertEquals("MOVIE-2", viewModel.state.value.slide?.title)

        viewModel.dismiss()
        assertFalse(viewModel.state.value.visible)

        // Somebody walking past the TV twice in an evening should not see the same two posters
        // again, and the pool is not re-fetched to find that out.
        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.visible)
        assertEquals("MOVIE-3", viewModel.state.value.slide?.title)
        assertEquals(listOf("lib-movies"), catalog.requestedLibraries)
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private suspend fun viewModel(
        settings: ClientSettings,
        catalog: MediaCatalog = RecordingCatalog(),
    ): ScreensaverViewModel {
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())
        settingsStore.save(settings)
        return ScreensaverViewModel(
            catalog = catalog,
            settingsStore = settingsStore,
            random = ZeroRandom(),
        )
    }

    /**
     * The idle watchdog `ScreensaverHost` runs, minus the real clock: the same two shipped policy
     * functions in the same order, so a test that arms the screensaver here arms it there.
     */
    private fun driveIdleWatchdog(
        timeoutMinutes: Int,
        hours: Int,
        playbackHoldsScreenOn: Boolean = false,
        onArm: () -> Unit,
    ) {
        var idleMillis = 0L
        repeat((hours * 60 * 60 * 1_000L / SCREENSAVER_IDLE_TICK_MILLIS).toInt()) {
            idleMillis = screensaverIdleAfterTick(
                idleMillis = idleMillis,
                tickMillis = SCREENSAVER_IDLE_TICK_MILLIS,
                playbackHoldsScreenOn = playbackHoldsScreenOn,
            )
            if (shouldStartScreensaver(timeoutMinutes, idleMillis, playbackHoldsScreenOn)) {
                onArm()
                idleMillis = 0L
            }
        }
    }
}

/** Deterministic draws, so the shuffled paths are reproducible. */
private class ZeroRandom : Random() {
    override fun nextBits(bitCount: Int): Int = 0
}

/**
 * A catalog that answers with fixed items and remembers which libraries were asked for -- the only
 * way to tell "the screensaver filtered by content source" apart from "the screensaver fetched
 * everything and filtered afterwards".
 */
private class RecordingCatalog(
    private val libraries: List<LibraryDestination> = listOf(
        LibraryDestination(id = "lib-movies", title = "Movies", collectionType = "movies"),
        LibraryDestination(id = "lib-shows", title = "Shows", collectionType = "tvshows"),
        LibraryDestination(id = "lib-music", title = "Music", collectionType = "music"),
        LibraryDestination(id = "lib-live", title = "Live TV", collectionType = "livetv"),
    ),
    private val itemsByLibrary: Map<String, List<MediaItem>> = mapOf(
        "lib-movies" to listOf(item("movie-1"), item("movie-2")),
        "lib-shows" to listOf(item("show-1")),
        "lib-music" to listOf(item("music-1")),
        "lib-live" to listOf(item("channel-1")),
    ),
    private val librariesFail: Boolean = false,
) : MediaCatalog {

    val requestedLibraries = mutableListOf<String>()

    override suspend fun libraries(): ApiResult<List<LibraryDestination>> =
        if (librariesFail) {
            ApiResult.Failure(ApiError.Network("unreachable"))
        } else {
            ApiResult.Success(libraries)
        }

    override suspend fun latest(libraryId: String, limit: Int): ApiResult<List<MediaItem>> {
        requestedLibraries += libraryId
        return ApiResult.Success(itemsByLibrary[libraryId].orEmpty().take(limit))
    }

    override suspend fun resumeItems(limit: Int): ApiResult<List<MediaItem>> = ApiResult.Success(emptyList())
    override suspend fun nextUp(limit: Int): ApiResult<List<MediaItem>> = ApiResult.Success(emptyList())
    override suspend fun favoriteItems(limit: Int): ApiResult<List<MediaItem>> = ApiResult.Success(emptyList())
    override suspend fun page(request: MediaPageRequest): ApiResult<MediaPage> =
        ApiResult.Success(MediaPage(items = emptyList(), totalCount = 0))

    override suspend fun search(term: String, limit: Int): ApiResult<List<MediaItem>> =
        ApiResult.Success(emptyList())

    override suspend fun setFavorite(itemId: String, favorite: Boolean): ApiResult<Boolean> =
        ApiResult.Success(true)
}
