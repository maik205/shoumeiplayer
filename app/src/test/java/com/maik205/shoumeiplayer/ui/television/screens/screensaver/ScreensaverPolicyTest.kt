package com.maik205.shoumeiplayer.ui.television.screens.screensaver

import com.maik205.shoumeiplayer.domain.model.LibraryDestination
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screensaver's policy (#87): when it may start, what it may draw, and in what order.
 *
 * Everything here goes through the shipped functions, so reverting any of the four behaviours --
 * "never" meaning never, playback suppression, content filtering, avoid-repeats -- fails a test
 * rather than merely changing an unasserted default.
 */
class ScreensaverPolicyTest {

    // --- timeout selection -------------------------------------------------------------------

    @Test
    fun `a timeout of zero minutes means never, not immediately`() {
        assertNull(screensaverIdleTimeoutMillis(0))
        // The dangerous reading of 0 is "0 ms of idle is enough", which would blank the screen the
        // instant the watchdog ticks.
        assertFalse(shouldStartScreensaver(timeoutMinutes = 0, idleMillis = 0L, playbackHoldsScreenOn = false))
        assertFalse(
            shouldStartScreensaver(
                timeoutMinutes = 0,
                idleMillis = 24L * 60L * 60L * 1_000L,
                playbackHoldsScreenOn = false,
            ),
        )
    }

    @Test
    fun `a corrupt negative timeout degrades to never rather than to always`() {
        assertNull(screensaverIdleTimeoutMillis(-5))
        assertFalse(shouldStartScreensaver(timeoutMinutes = -5, idleMillis = 999_999L, playbackHoldsScreenOn = false))
    }

    @Test
    fun `each offered timeout is that many minutes of idle`() {
        assertEquals(5L * 60_000L, screensaverIdleTimeoutMillis(5))
        assertEquals(30L * 60_000L, screensaverIdleTimeoutMillis(30))

        assertFalse(shouldStartScreensaver(timeoutMinutes = 5, idleMillis = 299_999L, playbackHoldsScreenOn = false))
        assertTrue(shouldStartScreensaver(timeoutMinutes = 5, idleMillis = 300_000L, playbackHoldsScreenOn = false))
    }

    @Test
    fun `a zero or negative image duration falls back to the shipped default`() {
        assertEquals(20_000L, screensaverImageDurationMillis(0))
        assertEquals(20_000L, screensaverImageDurationMillis(-1))
        assertEquals(45_000L, screensaverImageDurationMillis(45))
    }

    // --- playback suppression ----------------------------------------------------------------

    @Test
    fun `video playback suppresses the screensaver however long the remote has been idle`() {
        assertFalse(
            shouldStartScreensaver(
                timeoutMinutes = 5,
                idleMillis = 3L * 60L * 60L * 1_000L,
                playbackHoldsScreenOn = true,
            ),
        )
    }

    @Test
    fun `playback resets the idle count instead of only postponing it`() {
        // Two hours into a film the remote has been idle the whole time. If playback merely
        // postponed the screensaver, it would appear the moment the credits stop the engine.
        val duringPlayback = screensaverIdleAfterTick(
            idleMillis = 2L * 60L * 60L * 1_000L,
            tickMillis = SCREENSAVER_IDLE_TICK_MILLIS,
            playbackHoldsScreenOn = true,
        )
        assertEquals(0L, duringPlayback)
        assertFalse(shouldStartScreensaver(timeoutMinutes = 5, idleMillis = duringPlayback, playbackHoldsScreenOn = false))
    }

    @Test
    fun `idle accumulates once nothing holds the screen on`() {
        var idle = 0L
        repeat(60) {
            idle = screensaverIdleAfterTick(idle, SCREENSAVER_IDLE_TICK_MILLIS, playbackHoldsScreenOn = false)
        }
        assertEquals(300_000L, idle)
        assertTrue(shouldStartScreensaver(timeoutMinutes = 5, idleMillis = idle, playbackHoldsScreenOn = false))
    }

    // --- content filtering -------------------------------------------------------------------

    private val libraries = listOf(
        LibraryDestination(id = "lib-movies", title = "Movies", collectionType = "movies"),
        LibraryDestination(id = "lib-shows", title = "Shows", collectionType = "tvshows"),
        LibraryDestination(id = "lib-music", title = "Music", collectionType = "music"),
        LibraryDestination(id = "lib-live", title = "Live TV", collectionType = "livetv"),
    )

    @Test
    fun `each content source selects only its own libraries`() {
        assertEquals(
            listOf("lib-movies"),
            screensaverLibraries(libraries, ScreensaverContent.Movies).map { it.id },
        )
        assertEquals(
            listOf("lib-shows"),
            screensaverLibraries(libraries, ScreensaverContent.Shows).map { it.id },
        )
        assertEquals(
            listOf("lib-music"),
            screensaverLibraries(libraries, ScreensaverContent.Music).map { it.id },
        )
    }

    @Test
    fun `all libraries means every browsable library but not Live TV`() {
        assertEquals(
            listOf("lib-movies", "lib-shows", "lib-music"),
            screensaverLibraries(libraries, ScreensaverContent.AllLibraries).map { it.id },
        )
    }

    @Test
    fun `collection types are matched case-insensitively`() {
        val shouty = listOf(LibraryDestination(id = "lib", title = "Films", collectionType = "Movies"))
        assertEquals(1, screensaverLibraries(shouty, ScreensaverContent.Movies).size)
    }

    @Test
    fun `a library with an unknown or absent collection type only reaches the all-libraries pool`() {
        val odd = listOf(LibraryDestination(id = "lib-mixed", title = "Mixed", collectionType = null))
        assertEquals(1, screensaverLibraries(odd, ScreensaverContent.AllLibraries).size)
        assertTrue(screensaverLibraries(odd, ScreensaverContent.Movies).isEmpty())
    }

    // --- artwork -----------------------------------------------------------------------------

    @Test
    fun `the backdrop is preferred, the poster is the fallback, and artless items are skipped`() {
        assertEquals(
            "https://art/backdrop",
            screensaverArtworkUrl(item("a", backdrop = "https://art/backdrop", poster = "https://art/poster")),
        )
        assertEquals("https://art/poster", screensaverArtworkUrl(item("a", backdrop = null, poster = "https://art/poster")))
        assertNull(screensaverArtworkUrl(item("a", backdrop = "  ", poster = null)))
    }

    // --- rotation order ----------------------------------------------------------------------

    @Test
    fun `avoid repeats shows every item once before any comes round again`() {
        val pool = (1..5).map { item("item-$it") }
        val rotation = ScreensaverRotation(pool, shuffle = true, avoidRepeats = true, random = FixedRandom())

        val firstCycle = List(5) { rotation.next()!!.id }
        val secondCycle = List(5) { rotation.next()!!.id }

        assertEquals(5, firstCycle.toSet().size)
        assertEquals(5, secondCycle.toSet().size)
        assertEquals(pool.map { it.id }.toSet(), firstCycle.toSet())
    }

    @Test
    fun `turning avoid repeats off allows the same artwork twice`() {
        val pool = (1..5).map { item("item-$it") }
        val rotation = ScreensaverRotation(pool, shuffle = true, avoidRepeats = false, random = FixedRandom())

        val drawn = List(5) { rotation.next()!!.id }

        // The same draw five times over is only possible because avoid-repeats is off; with it on
        // the assertion above proves five distinct items come out of the identical pool.
        assertEquals(1, drawn.toSet().size)
    }

    @Test
    fun `a new shuffled cycle never opens with the item still on screen`() {
        // Scripted so the raw second permutation starts with the item the first cycle ended on --
        // the only case the seam swap exists for.
        val pool = listOf(item("a"), item("b"), item("c"))
        val rotation = ScreensaverRotation(
            items = pool,
            shuffle = true,
            avoidRepeats = true,
            random = ScriptedRandom(intArrayOf(2, 1, 0, 1)),
        )

        val drawn = List(4) { rotation.next()!!.id }

        assertEquals(listOf("a", "b", "c"), drawn.take(3))
        assertNotEquals("c", drawn[3])
    }

    @Test
    fun `with shuffle off the pool is walked in catalog order and loops`() {
        val pool = listOf(item("a"), item("b"), item("c"))
        val rotation = ScreensaverRotation(pool, shuffle = false, avoidRepeats = true, random = FixedRandom())

        assertEquals(listOf("a", "b", "c", "a", "b", "c"), List(6) { rotation.next()!!.id })
    }

    @Test
    fun `an empty pool yields no slide at all`() {
        val rotation = ScreensaverRotation(emptyList(), shuffle = true, avoidRepeats = true, random = FixedRandom())
        assertTrue(rotation.isEmpty)
        assertNull(rotation.next())
    }
}

internal fun item(
    id: String,
    backdrop: String? = "https://art/$id",
    poster: String? = null,
): MediaItem = MediaItem(
    id = id,
    title = id.uppercase(),
    type = "Movie",
    imageUrl = poster,
    backdropUrl = backdrop,
)

/** Always draws index 0 -- deterministic, and the worst case for "avoid repeats". */
private class FixedRandom : Random() {
    override fun nextBits(bitCount: Int): Int = 0
}

/**
 * Feeds `shuffled()` a scripted swap index per step, so a specific permutation can be forced.
 * `MutableList.shuffle` calls `nextInt(i + 1)` for `i` from the last index down to 1.
 */
private class ScriptedRandom(private val values: IntArray) : Random() {
    private var index = 0

    override fun nextBits(bitCount: Int): Int = 0

    override fun nextInt(until: Int): Int {
        val value = values.getOrElse(index) { 0 }
        index++
        return value.coerceIn(0, until - 1)
    }
}
