package com.maik205.shoumeiplayer.ui.television.screens.browse

import com.maik205.shoumeiplayer.data.InMemoryPreferencesDataStore
import com.maik205.shoumeiplayer.data.cache.LibraryCacheStore
import com.maik205.shoumeiplayer.data.session.LibraryPresentation
import com.maik205.shoumeiplayer.data.session.PreferenceStore
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.data.session.UserScope
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.domain.model.MediaPage
import com.maik205.shoumeiplayer.domain.model.MediaPageRequest
import com.maik205.shoumeiplayer.domain.repository.MediaCatalog
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Drives [TelevisionLibraryViewModel] and [TelevisionHomeViewModel] end-to-end over a real
 * [PreferenceStore]/[SettingsStore] (in-memory DataStore, no fakes of the persistence layer
 * itself), so these tests fail the moment #90/#88 persistence is reverted rather than merely
 * re-asserting a default that survives the revert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelsTest {

    private val serverUrl = "https://media.example"
    private val userId = "alice"
    private val scope = UserScope(serverUrl = serverUrl, userId = userId)

    /**
     * One dispatcher for the class, and every `runTest` below borrows its scheduler. The two must
     * be the same: with separate schedulers the ViewModels' `viewModelScope` work is never drained
     * by `advanceUntilIdle`, and `resetMain` in [tearDown] then pulls Dispatchers.Main out from
     * under coroutines that are still running.
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

    // --- #90: sort/view persisted per library, per user ------------------------------------------

    @Test
    fun `a sort chosen for one library is restored the next time that library opens`() = runTest(mainDispatcher.scheduler) {
        val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
        val vm1 = libraryViewModel(preferenceStore = preferenceStore, libraryId = "lib-1")
        advanceUntilIdle()

        vm1.setSort(BrowseSort.Name)
        advanceUntilIdle()
        assertEquals(BrowseSort.Name, vm1.state.value.sort)

        // A brand new ViewModel instance for the same library -- e.g. the viewer left and came
        // back -- must pick the persisted sort back up instead of resetting to Recent.
        val vm2 = libraryViewModel(preferenceStore = preferenceStore, libraryId = "lib-1")
        advanceUntilIdle()
        assertEquals(BrowseSort.Name, vm2.state.value.sort)
    }

    @Test
    fun `a view mode chosen for one library is restored the next time that library opens`() = runTest(mainDispatcher.scheduler) {
        val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
        val vm1 = libraryViewModel(preferenceStore = preferenceStore, libraryId = "lib-1")
        advanceUntilIdle()

        vm1.setView(LibraryViewMode.Favorites)
        advanceUntilIdle()

        val vm2 = libraryViewModel(preferenceStore = preferenceStore, libraryId = "lib-1")
        advanceUntilIdle()
        assertEquals(LibraryViewMode.Favorites, vm2.state.value.view)
    }

    @Test
    fun `sort is scoped per library -- a different library keeps its own default`() = runTest(mainDispatcher.scheduler) {
        val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
        val libraryOne = libraryViewModel(preferenceStore = preferenceStore, libraryId = "lib-1")
        advanceUntilIdle()
        libraryOne.setSort(BrowseSort.Name)
        advanceUntilIdle()

        val libraryTwo = libraryViewModel(preferenceStore = preferenceStore, libraryId = "lib-2")
        advanceUntilIdle()

        assertEquals(BrowseSort.Recent, libraryTwo.state.value.sort)
    }

    @Test
    fun `a retired sort or view value degrades to the default instead of crashing`() = runTest(mainDispatcher.scheduler) {
        val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
        // Simulates a value written by a build that understood a sort/view this build has since
        // retired -- e.g. a shipped-and-removed enum entry.
        preferenceStore.setLibraryPresentation(
            scope,
            "lib-1",
            LibraryPresentation(sort = "no-longer-shipped", view = "also-retired"),
        )

        val vm = libraryViewModel(preferenceStore = preferenceStore, libraryId = "lib-1")
        advanceUntilIdle()

        assertEquals(BrowseSort.Recent, vm.state.value.sort)
        assertEquals(LibraryViewMode.All, vm.state.value.view)
    }

    // --- #88: remember last library ---------------------------------------------------------------

    @Test
    fun `opening a library records it as the last-opened library`() = runTest(mainDispatcher.scheduler) {
        val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
        libraryViewModel(preferenceStore = preferenceStore, libraryId = "lib-9")
        advanceUntilIdle()

        assertEquals("lib-9", preferenceStore.lastLibraryId(scope))
    }

    @Test
    fun `nothing is remembered when the remember-last-library toggle is off`() = runTest(mainDispatcher.scheduler) {
        val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())
        settingsStore.save(ClientSettings(rememberLastLibrary = false))

        libraryViewModel(
            preferenceStore = preferenceStore,
            settingsStore = settingsStore,
            libraryId = "lib-9",
        )
        advanceUntilIdle()

        assertNull(preferenceStore.lastLibraryId(scope))
    }

    @Test
    fun `home offers to restore the last library once it is validated against the account's libraries`() =
        runTest {
            val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
            preferenceStore.setLastLibraryId(scope, "lib-1")
            val library = LibraryDestinationUi(id = "lib-1", title = "Movies", collectionType = "movies")

            val vm = homeViewModel(preferenceStore = preferenceStore, libraries = listOf(library))
            advanceUntilIdle()

            assertEquals(library, vm.state.value.restoreLibrary)
        }

    @Test
    fun `home offers nothing when the remember-last-library toggle is off`() = runTest(mainDispatcher.scheduler) {
        val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
        preferenceStore.setLastLibraryId(scope, "lib-1")
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())
        settingsStore.save(ClientSettings(rememberLastLibrary = false))
        val library = LibraryDestinationUi(id = "lib-1", title = "Movies", collectionType = "movies")

        val vm = homeViewModel(
            preferenceStore = preferenceStore,
            settingsStore = settingsStore,
            libraries = listOf(library),
        )
        advanceUntilIdle()

        assertNull(vm.state.value.restoreLibrary)
    }

    @Test
    fun `a remembered library removed from the server degrades to no restoration`() = runTest(mainDispatcher.scheduler) {
        val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
        preferenceStore.setLastLibraryId(scope, "lib-deleted")
        val library = LibraryDestinationUi(id = "lib-1", title = "Movies", collectionType = "movies")

        val vm = homeViewModel(preferenceStore = preferenceStore, libraries = listOf(library))
        advanceUntilIdle()

        assertNull(vm.state.value.restoreLibrary)
    }

    @Test
    fun `consuming the restore target clears it and it does not come back on a later refresh`() =
        runTest {
            val preferenceStore = PreferenceStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher)
            preferenceStore.setLastLibraryId(scope, "lib-1")
            val library = LibraryDestinationUi(id = "lib-1", title = "Movies", collectionType = "movies")

            val vm = homeViewModel(preferenceStore = preferenceStore, libraries = listOf(library))
            advanceUntilIdle()
            assertEquals(library, vm.state.value.restoreLibrary)

            vm.consumeRestoreLibrary()
            vm.refresh()
            advanceUntilIdle()

            assertNull(vm.state.value.restoreLibrary)
        }

    // --- test fixtures -----------------------------------------------------------------------------

    private suspend fun newSessionStore(): SessionStore {
        val sessionStore = SessionStore(InMemoryPreferencesDataStore())
        sessionStore.activateServer(url = serverUrl)
        sessionStore.saveAuth(accessToken = "token", userId = userId, userName = "Alice")
        return sessionStore
    }

    private suspend fun libraryViewModel(
        preferenceStore: PreferenceStore,
        settingsStore: SettingsStore = SettingsStore(InMemoryPreferencesDataStore()),
        libraryId: String,
    ): TelevisionLibraryViewModel = TelevisionLibraryViewModel(
        catalog = FakeMediaCatalog(),
        sessionStore = newSessionStore(),
        settingsStore = settingsStore,
        libraryCacheStore = LibraryCacheStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher),
        preferenceStore = preferenceStore,
        libraryId = libraryId,
        title = "Library",
        collectionType = null,
    )

    private suspend fun homeViewModel(
        preferenceStore: PreferenceStore,
        settingsStore: SettingsStore = SettingsStore(InMemoryPreferencesDataStore()),
        libraries: List<LibraryDestinationUi>,
    ): TelevisionHomeViewModel = TelevisionHomeViewModel(
        catalog = FakeMediaCatalog(libraries = libraries),
        sessionStore = newSessionStore(),
        settingsStore = settingsStore,
        libraryCacheStore = LibraryCacheStore(InMemoryPreferencesDataStore(), ioDispatcher = mainDispatcher),
        preferenceStore = preferenceStore,
    )
}

private class FakeMediaCatalog(
    private val libraries: List<LibraryDestinationUi> = emptyList(),
) : MediaCatalog {
    override suspend fun libraries(): ApiResult<List<LibraryDestinationUi>> = ApiResult.Success(libraries)
    override suspend fun resumeItems(limit: Int): ApiResult<List<MediaItemUi>> = ApiResult.Success(emptyList())
    override suspend fun nextUp(limit: Int): ApiResult<List<MediaItemUi>> = ApiResult.Success(emptyList())
    override suspend fun favoriteItems(limit: Int): ApiResult<List<MediaItemUi>> = ApiResult.Success(emptyList())
    override suspend fun latest(libraryId: String, limit: Int): ApiResult<List<MediaItemUi>> =
        ApiResult.Success(emptyList())

    override suspend fun page(request: MediaPageRequest): ApiResult<MediaPage> =
        ApiResult.Success(MediaPage(items = emptyList(), totalCount = 0))

    override suspend fun search(term: String, limit: Int): ApiResult<List<MediaItemUi>> =
        ApiResult.Success(emptyList())

    override suspend fun setFavorite(itemId: String, favorite: Boolean): ApiResult<Boolean> =
        ApiResult.Success(true)
}
