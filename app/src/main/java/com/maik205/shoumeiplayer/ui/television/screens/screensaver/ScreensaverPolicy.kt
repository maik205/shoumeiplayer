package com.maik205.shoumeiplayer.ui.television.screens.screensaver

import com.maik205.shoumeiplayer.domain.model.LibraryDestination
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import kotlin.random.Random

/**
 * The screensaver's decisions, as plain functions (#87).
 *
 * Everything here is deliberately free of Compose, Android and coroutines so the four questions the
 * feature actually gets wrong -- *when* may it start, *what* may it draw, *in which order*, and
 * *never during a film* -- are answerable in a JVM unit test. `ScreensaverHost` and
 * `ScreensaverViewModel` hold no policy of their own; they only feed these functions.
 */

/** How often the idle watchdog wakes up. Coarse on purpose: the shortest timeout is five minutes. */
internal const val SCREENSAVER_IDLE_TICK_MILLIS: Long = 5_000L

/** Fallback slide duration when a stored value is nonsense; matches `ClientSettings`' default. */
private const val DEFAULT_IMAGE_DURATION_SECONDS = 20

/** How many items are pulled per selected library to build the rotation pool. */
internal const val SCREENSAVER_ITEMS_PER_LIBRARY = 40

/**
 * The idle time after which the screensaver may appear, or `null` for "never".
 *
 * `0` is the settings screen's *Never* choice (`SettingsChoices.screensaverTimeoutMinutes`), and a
 * negative value can only come from a corrupt store -- both mean the same thing: do not arm.
 */
internal fun screensaverIdleTimeoutMillis(timeoutMinutes: Int): Long? =
    if (timeoutMinutes <= 0) null else timeoutMinutes.toLong() * 60_000L

/** Per-slide dwell time, guarded against a zero/negative stored value that would busy-loop. */
internal fun screensaverImageDurationMillis(seconds: Int): Long =
    (if (seconds <= 0) DEFAULT_IMAGE_DURATION_SECONDS else seconds).toLong() * 1_000L

/**
 * The idle accumulator after one watchdog tick.
 *
 * Playback does not merely *postpone* the screensaver, it resets the count: the moment a film ends
 * the viewer has been "idle" for its entire runtime, and without the reset the screensaver would
 * appear instantly over the credits.
 */
internal fun screensaverIdleAfterTick(
    idleMillis: Long,
    tickMillis: Long,
    playbackHoldsScreenOn: Boolean,
): Long = if (playbackHoldsScreenOn) 0L else idleMillis + tickMillis

/**
 * Whether the screensaver may take the screen now.
 *
 * [playbackHoldsScreenOn] is the *same* signal the player already uses to suppress Ambient Mode --
 * the `FLAG_KEEP_SCREEN_ON` window flag it sets exactly when
 * `TelevisionPlayerScreen.shouldKeepScreenOn` is true -- rather than a second, drifting notion of
 * "is playing". Two consequences follow from reusing it, both intended:
 *
 * - **Video never gets blanked.** A playing or buffering video holds the flag, so an idle timer can
 *   never cut into a film.
 * - **Audio-only playback is not protected, and should not be.** `shouldKeepScreenOn` returns false
 *   for audio precisely because there is nothing to look at; the panel is showing one static
 *   now-playing frame, which is the burn-in case a screensaver exists for. The screensaver is drawn
 *   *over* the now-playing screen and never touches the engine, so the music keeps playing and any
 *   key puts the viewer back exactly where they were.
 */
internal fun shouldStartScreensaver(
    timeoutMinutes: Int,
    idleMillis: Long,
    playbackHoldsScreenOn: Boolean,
): Boolean {
    val timeout = screensaverIdleTimeoutMillis(timeoutMinutes) ?: return false
    return !playbackHoldsScreenOn && idleMillis >= timeout
}

/**
 * The libraries the chosen [content] source draws from.
 *
 * Live TV is excluded from [ScreensaverContent.AllLibraries] because its "latest" is a recording
 * list with channel logos rather than artwork -- it would quietly poison the "all libraries" pool
 * that everybody gets by default.
 */
internal fun screensaverLibraries(
    libraries: List<LibraryDestination>,
    content: ScreensaverContent,
): List<LibraryDestination> = libraries.filter { library ->
    val type = library.collectionType?.lowercase()
    when (content) {
        ScreensaverContent.AllLibraries -> type != "livetv"
        ScreensaverContent.Movies -> type == "movies"
        ScreensaverContent.Shows -> type == "tvshows"
        ScreensaverContent.Music -> type == "music"
    }
}

/** An item is only worth a slide if there is actually artwork to show. */
internal fun screensaverArtworkUrl(item: MediaItem): String? =
    item.backdropUrl?.takeIf(String::isNotBlank) ?: item.imageUrl?.takeIf(String::isNotBlank)

/**
 * The order slides come out in, honouring *shuffle* and *avoid repeats* together.
 *
 * The two settings interact, and the interaction is the whole point:
 *
 * - **shuffle off** -- catalog order, walked in a loop. Nothing repeats until the pool is
 *   exhausted, so *avoid repeats* has nothing left to do and is a no-op here by construction.
 * - **shuffle on, avoid repeats on** -- a fresh permutation per cycle, played to the end before any
 *   item can come round again. The seam between cycles is swapped when it would otherwise show the
 *   same item twice in a row, because "avoid repeats" is judged by the viewer at the seam, not over
 *   the cycle.
 * - **shuffle on, avoid repeats off** -- an independent draw each time, so the same poster may well
 *   come up twice. That is what turning the setting *off* asks for.
 *
 * [random] is injected so the shuffled paths are deterministic under test.
 */
internal class ScreensaverRotation(
    private val items: List<MediaItem>,
    private val shuffle: Boolean,
    private val avoidRepeats: Boolean,
    private val random: Random = Random.Default,
) {
    private var cycle: List<MediaItem> = emptyList()
    private var index: Int = 0
    private var lastShown: MediaItem? = null

    val isEmpty: Boolean get() = items.isEmpty()

    fun next(): MediaItem? {
        if (items.isEmpty()) return null
        val chosen = if (shuffle && !avoidRepeats) {
            items[random.nextInt(items.size)]
        } else {
            if (index >= cycle.size) {
                cycle = if (shuffle) nextCycle() else items
                index = 0
            }
            cycle[index++]
        }
        lastShown = chosen
        return chosen
    }

    /** A new permutation whose first item is not the one still on screen. */
    private fun nextCycle(): List<MediaItem> {
        val shuffled = items.shuffled(random)
        val previous = lastShown ?: return shuffled
        if (shuffled.size < 2 || shuffled.first().id != previous.id) return shuffled
        val swapped = shuffled.toMutableList()
        val first = swapped[0]
        swapped[0] = swapped[swapped.lastIndex]
        swapped[swapped.lastIndex] = first
        return swapped
    }
}
