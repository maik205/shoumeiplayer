package com.maik205.shoumeiplayer.data

import androidx.datastore.preferences.core.stringPreferencesKey
import com.maik205.shoumeiplayer.data.session.LibraryPresentation
import com.maik205.shoumeiplayer.data.session.MAX_PREFERENCE_ROWS_PER_USER
import com.maik205.shoumeiplayer.data.session.PreferenceStore
import com.maik205.shoumeiplayer.data.session.TrackDelays
import com.maik205.shoumeiplayer.data.session.UserScope
import com.maik205.shoumeiplayer.domain.settings.PreferredQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-item store that #88/#90/#95 build on.
 *
 * The two properties worth defending are isolation (one profile's playback trivia is not another's)
 * and bounded growth (a 5,000-episode library must not leave 5,000 permanent rows). Both are tested
 * by driving the public API, not by asserting the constants.
 */
class PreferenceStoreTest {

    private val alice = UserScope(serverUrl = "https://media.example", userId = "alice")
    private val bob = UserScope(serverUrl = "https://media.example", userId = "bob")

    @Test
    fun `every stored preference round-trips`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore())

        store.setLastLibraryId(alice, "library-7")
        store.setLibraryPresentation(alice, "library-7", LibraryPresentation("date_added", "grid"))
        store.setSeriesAudioTrack(alice, "series-1", "3")
        store.setPlaybackSpeed(alice, "episode-1", 1.25f)
        store.setTrackDelays(alice, "episode-1", TrackDelays(audioDelayMs = -80, subtitleDelayMs = 40))
        store.setQualityCap(alice, "episode-1", PreferredQuality.FullHd)

        assertEquals("library-7", store.lastLibraryId(alice))
        assertEquals(
            LibraryPresentation("date_added", "grid"),
            store.libraryPresentation(alice, "library-7"),
        )
        assertEquals("3", store.seriesAudioTrack(alice, "series-1"))
        assertEquals(1.25f, store.playbackSpeed(alice, "episode-1"))
        assertEquals(
            TrackDelays(audioDelayMs = -80, subtitleDelayMs = 40),
            store.trackDelays(alice, "episode-1"),
        )
        assertEquals(PreferredQuality.FullHd, store.qualityCap(alice, "episode-1"))
    }

    @Test
    fun `nothing is stored until it is set`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore())

        assertNull(store.lastLibraryId(alice))
        assertNull(store.libraryPresentation(alice, "library-7"))
        assertNull(store.seriesAudioTrack(alice, "series-1"))
        assertNull(store.playbackSpeed(alice, "episode-1"))
        assertNull(store.trackDelays(alice, "episode-1"))
        assertNull(store.qualityCap(alice, "episode-1"))
    }

    @Test
    fun `one profile cannot read or overwrite another profile's state`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore())

        store.setSeriesAudioTrack(alice, "series-1", "3")
        store.setLastLibraryId(alice, "library-7")
        store.setSeriesAudioTrack(bob, "series-1", "1")

        assertEquals("3", store.seriesAudioTrack(alice, "series-1"))
        assertEquals("1", store.seriesAudioTrack(bob, "series-1"))
        assertNull(store.lastLibraryId(bob))
    }

    @Test
    fun `clearing one field leaves its neighbours on the same item alone`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore())
        store.setQualityCap(alice, "episode-1", PreferredQuality.Hd)
        store.setPlaybackSpeed(alice, "episode-1", 1.5f)

        store.setPlaybackSpeed(alice, "episode-1", null)

        assertNull(store.playbackSpeed(alice, "episode-1"))
        assertEquals(PreferredQuality.Hd, store.qualityCap(alice, "episode-1"))
    }

    /**
     * A value written by a build that knows more than this one must survive being read and must
     * survive a write to the field next to it. Seeded as raw storage because that is the only way
     * to stand in for the foreign build.
     */
    @Test
    fun `a value this build does not understand is neither reported nor destroyed`() = runTest {
        val backing = InMemoryPreferencesDataStore()
        val document = stringPreferencesKey("item_preferences_v1")
        backing.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[document] = """
                    {"sequence":1,"rows":{"${alice.storagePrefix}item/episode-1":
                    {"values":{"quality_cap":"8k","playback_speed":"1.5"},"usedAt":1}}}
                """.trimIndent().replace("\n", "")
            }
        }
        val store = PreferenceStore(backing)

        // Unrecognised: reported as "no opinion" rather than coerced onto a known value.
        assertNull(store.qualityCap(alice, "episode-1"))
        assertEquals(1.5f, store.playbackSpeed(alice, "episode-1"))

        store.setPlaybackSpeed(alice, "episode-1", 2.0f)

        assertTrue(backing.data.first()[document]!!.contains("\"8k\""))
        assertEquals(2.0f, store.playbackSpeed(alice, "episode-1"))
    }

    @Test
    fun `the least recently used row is the one evicted`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore(), maxRowsPerUser = 2)
        store.setPlaybackSpeed(alice, "episode-1", 1.5f)
        store.setPlaybackSpeed(alice, "episode-2", 1.5f)

        // Reading counts as use, so episode-1 is now newer than episode-2.
        store.playbackSpeed(alice, "episode-1")
        store.setPlaybackSpeed(alice, "episode-3", 1.5f)

        assertNotNull(store.playbackSpeed(alice, "episode-1"))
        assertNull(store.playbackSpeed(alice, "episode-2"))
        assertNotNull(store.playbackSpeed(alice, "episode-3"))
    }

    @Test
    fun `eviction never reaches across profiles`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore(), maxRowsPerUser = 1)
        store.setPlaybackSpeed(bob, "episode-1", 1.5f)

        store.setPlaybackSpeed(alice, "episode-1", 1.5f)
        store.setPlaybackSpeed(alice, "episode-2", 1.5f)

        assertNull(store.playbackSpeed(alice, "episode-1"))
        assertNotNull(store.playbackSpeed(alice, "episode-2"))
        assertNotNull(store.playbackSpeed(bob, "episode-1"))
    }

    @Test
    fun `the last library is not evicted by episode churn`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore(), maxRowsPerUser = 1)
        store.setLastLibraryId(alice, "library-7")

        store.setPlaybackSpeed(alice, "episode-1", 1.5f)
        store.setPlaybackSpeed(alice, "episode-2", 1.5f)

        assertEquals("library-7", store.lastLibraryId(alice))
    }

    /** The shipped cap, not an injected one: a real library is what has to stay bounded. */
    @Test
    fun `a library far larger than the cap does not grow the store without bound`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore())
        val overflow = 10
        repeat(MAX_PREFERENCE_ROWS_PER_USER + overflow) { index ->
            store.setPlaybackSpeed(alice, "episode-$index", 1.5f)
        }

        assertNull(store.playbackSpeed(alice, "episode-0"))
        assertNull(store.playbackSpeed(alice, "episode-${overflow - 1}"))
        assertNotNull(store.playbackSpeed(alice, "episode-$overflow"))
        assertNotNull(
            store.playbackSpeed(alice, "episode-${MAX_PREFERENCE_ROWS_PER_USER + overflow - 1}"),
        )
    }

    @Test
    fun `forgetting a profile leaves the other profiles intact`() = runTest {
        val store = PreferenceStore(InMemoryPreferencesDataStore())
        store.setSeriesAudioTrack(alice, "series-1", "3")
        store.setLastLibraryId(alice, "library-7")
        store.setSeriesAudioTrack(bob, "series-1", "1")

        store.forget(alice)

        assertNull(store.seriesAudioTrack(alice, "series-1"))
        assertNull(store.lastLibraryId(alice))
        assertEquals("1", store.seriesAudioTrack(bob, "series-1"))
    }
}
