package com.maik205.shoumeiplayer.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The docs/osd-v3.md §1/§3 input model. Because [reducePlayerKey] is pure, every rule in the spec —
 * the layers, the row geography, scrub acceleration, the BACK cascade, the auto-hide suspensions —
 * can be asserted here rather than driven through a device.
 */
class PlayerInputTest {

    private val chapters = listOf(
        ChapterMark(0, "Cold Open"),
        ChapterMark(60_000, "Titles"),
        ChapterMark(120_000, "Act One"),
    )

    private fun ctx(
        positionMs: Long = 0,
        durationMs: Long? = 600_000,
        chapters: List<ChapterMark> = emptyList(),
        upNextVisible: Boolean = false,
        hasCast: Boolean = false,
        hasMoreLikeThis: Boolean = true,
    ) = PlayerInputContext(
        positionMs = positionMs,
        durationMs = durationMs,
        chapters = chapters,
        upNextVisible = upNextVisible,
        hasMoreLikeThis = hasMoreLikeThis,
        hasCast = hasCast,
    )

    private fun press(
        state: PlayerInputState,
        key: PlayerKey,
        ctx: PlayerInputContext = ctx(),
        atMs: Long = 0,
        repeat: Boolean = false,
    ) = reducePlayerKey(state, key, ctx, atMs, repeat)

    /** The OSD, focus on row 1 — the state every reveal lands in. */
    private val onControls = PlayerInputState(osdVisible = true, row = OsdRow.Controls)

    // --- §3 hold-repeat acceleration -------------------------------------------------------------

    @Test
    fun `stepForRepeat climbs 10s to 30s to 60s at the spec thresholds`() {
        assertEquals(10_000L, stepForRepeat(0))
        assertEquals(10_000L, stepForRepeat(9))
        // "30s after 10 repeats": the eleventh event of the hold is the first accelerated one.
        assertEquals(30_000L, stepForRepeat(10))
        assertEquals(30_000L, stepForRepeat(19))
        assertEquals(60_000L, stepForRepeat(20))
        assertEquals(60_000L, stepForRepeat(500))
    }

    @Test
    fun `stepForRepeat treats a nonsensical repeat count as the resting step`() {
        assertEquals(10_000L, stepForRepeat(-1))
    }

    @Test
    fun `a held scrub key accelerates through the ladder`() {
        var state = PlayerInputState(osdVisible = true, row = OsdRow.Seek, virtualPositionMs = 600_000)
        val context = ctx(positionMs = 600_000, durationMs = 3_600_000)
        // The initial press is not a repeat: 10s.
        state = press(state, PlayerKey.Right, context).state
        assertEquals(610_000L, state.virtualPositionMs)
        // Nine more repeats stay at 10s (repeat counts 1..9).
        repeat(9) { state = press(state, PlayerKey.Right, context, repeat = true).state }
        assertEquals(700_000L, state.virtualPositionMs)
        assertEquals(9, state.repeatCount)
        // The tenth repeat crosses into 30s.
        state = press(state, PlayerKey.Right, context, repeat = true).state
        assertEquals(730_000L, state.virtualPositionMs)
        // Ten more repeats (11..20) — the last of them is the first 60s step.
        repeat(10) { state = press(state, PlayerKey.Right, context, repeat = true).state }
        assertEquals(20, state.repeatCount)
        assertEquals(730_000L + 9 * 30_000L + 60_000L, state.virtualPositionMs)
    }

    @Test
    fun `changing direction mid-hold resets the acceleration`() {
        var state = PlayerInputState(osdVisible = true, row = OsdRow.Seek, virtualPositionMs = 3_000_000)
        val context = ctx(positionMs = 3_000_000, durationMs = 7_200_000)
        state = press(state, PlayerKey.Right, context).state
        repeat(25) { state = press(state, PlayerKey.Right, context, repeat = true).state }
        assertTrue(state.repeatCount >= RepeatsToFastStep)
        val before = state.virtualPositionMs
        // The other direction is a fresh hold: back to 10s, not 60s.
        state = press(state, PlayerKey.Left, context, repeat = true).state
        assertEquals(0, state.repeatCount)
        assertEquals(before - 10_000L, state.virtualPositionMs)
    }

    @Test
    fun `releasing the key resets the acceleration`() {
        val held = PlayerInputState(repeatCount = 17, holdDirection = 1)
        val released = releaseSeekHold(held)
        assertEquals(0, released.repeatCount)
        assertEquals(0, released.holdDirection)
        // Idle state is returned untouched, so a stray key-up cannot churn Compose state.
        val idle = PlayerInputState()
        assertTrue(idle === releaseSeekHold(idle))
    }

    // --- §1 layer 0 -------------------------------------------------------------------------------

    @Test
    fun `layer zero seeks ten seconds and raises the mini strip only`() {
        val result = press(PlayerInputState(), PlayerKey.Right, ctx(positionMs = 30_000), atMs = 1_000)
        assertTrue(result.consumed)
        assertEquals(listOf(PlayerEffect.SeekBy(10_000L)), result.effects)
        // The full OSD stays down: §1 is explicit that this is "NOT the full OSD".
        assertFalse(result.state.osdVisible)
        assertEquals(1, result.state.miniStripTick)
    }

    @Test
    fun `layer zero LEFT seeks backwards`() {
        val result = press(PlayerInputState(), PlayerKey.Left, ctx(positionMs = 30_000))
        assertEquals(listOf(PlayerEffect.SeekBy(-10_000L)), result.effects)
    }

    @Test
    fun `layer zero never accelerates on a held key`() {
        var state = PlayerInputState()
        val context = ctx(positionMs = 600_000)
        var last: PlayerInputResult = press(state, PlayerKey.Right, context)
        state = last.state
        // A live seek re-buffers, so §3's acceleration is deliberately scrub-only: every repeat
        // here is still a flat 10s.
        repeat(30) {
            last = press(state, PlayerKey.Right, context, atMs = 5_000L + it, repeat = true)
            state = last.state
        }
        assertEquals(listOf(PlayerEffect.SeekBy(10_000L)), last.effects)
    }

    @Test
    fun `a fast second press in the same direction jumps a chapter on layer zero`() {
        val context = ctx(positionMs = 30_000, chapters = chapters)
        val first = press(PlayerInputState(), PlayerKey.Right, context, atMs = 1_000)
        val second = press(first.state, PlayerKey.Right, context, atMs = 1_200)
        assertEquals(listOf(PlayerEffect.SeekTo(60_000L)), second.effects)
    }

    @Test
    fun `a slow second press stays a plain seek`() {
        val context = ctx(positionMs = 30_000, chapters = chapters)
        val first = press(PlayerInputState(), PlayerKey.Right, context, atMs = 1_000)
        val second = press(first.state, PlayerKey.Right, context, atMs = 1_000 + ChapterDoublePressWindowMs + 1)
        assertEquals(listOf(PlayerEffect.SeekBy(10_000L)), second.effects)
    }

    @Test
    fun `a chapter double press at the last boundary is consumed and does nothing`() {
        val context = ctx(positionMs = 500_000, chapters = chapters)
        val first = press(PlayerInputState(), PlayerKey.Right, context, atMs = 1_000)
        val second = press(first.state, PlayerKey.Right, context, atMs = 1_100)
        // Consumed anyway: a double press at the end must not leak out as a focus move.
        assertTrue(second.consumed)
        assertTrue(second.effects.isEmpty())
    }

    @Test
    fun `a held key never counts as the second half of a double press`() {
        val context = ctx(positionMs = 30_000, chapters = chapters)
        val first = press(PlayerInputState(), PlayerKey.Right, context, atMs = 1_000)
        val second = press(first.state, PlayerKey.Right, context, atMs = 1_100, repeat = true)
        // Hold-repeat and double-press read different signals on purpose; sharing one would make
        // holding the key unreachable on media with chapters.
        assertEquals(listOf(PlayerEffect.SeekBy(10_000L)), second.effects)
    }

    @Test
    fun `CENTER on layer zero toggles playback and reveals the OSD on play slash pause`() {
        val result = press(PlayerInputState(), PlayerKey.Center)
        assertTrue(result.consumed)
        assertTrue(result.state.osdVisible)
        assertEquals(OsdRow.Controls, result.state.row)
        assertEquals(
            listOf(PlayerEffect.TogglePlayPause, PlayerEffect.Focus(FocusTarget.PlayPause)),
            result.effects,
        )
    }

    @Test
    fun `UP DOWN and MENU reveal the OSD and hand focus to play slash pause`() {
        for (key in listOf(PlayerKey.Up, PlayerKey.Down, PlayerKey.Menu)) {
            val result = press(PlayerInputState(), key)
            assertTrue("$key should be consumed", result.consumed)
            assertTrue("$key should reveal", result.state.osdVisible)
            // The press that reveals does nothing else — no row move, no transport action.
            assertEquals(listOf(PlayerEffect.Focus(FocusTarget.PlayPause)), result.effects)
            assertEquals(OsdRow.Controls, result.state.row)
        }
    }

    @Test
    fun `BACK on layer zero exits playback`() {
        val result = press(PlayerInputState(), PlayerKey.Back)
        assertEquals(listOf(PlayerEffect.Exit), result.effects)
    }

    @Test
    fun `unknown keys on layer zero fall through untouched`() {
        val result = press(PlayerInputState(), PlayerKey.Other)
        assertFalse(result.consumed)
        assertEquals(PlayerInputState(), result.state)
    }

    // --- §1 row state machine ---------------------------------------------------------------------

    @Test
    fun `UP from the control track enters scrub mode parked on the live playhead`() {
        val result = press(onControls, PlayerKey.Up, ctx(positionMs = 123_000))
        assertEquals(OsdRow.Seek, result.state.row)
        assertTrue(result.state.scrubbing)
        assertEquals(123_000L, result.state.virtualPositionMs)
        // Not "dirty" yet, so the bar still tracks live and no trickplay preview is claimed.
        assertFalse(result.state.virtualDirty)
        assertEquals(listOf(PlayerEffect.Focus(FocusTarget.SeekBar)), result.effects)
    }

    @Test
    fun `UP from the seek bar stays put rather than escaping the OSD`() {
        val onSeek = onControls.copy(row = OsdRow.Seek)
        val result = press(onSeek, PlayerKey.Up)
        assertTrue(result.consumed)
        assertEquals(OsdRow.Seek, result.state.row)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `DOWN walks row 1 to the shelf and on to Cast`() {
        val toShelf = press(onControls, PlayerKey.Down, ctx(hasCast = true))
        assertEquals(OsdRow.MoreLikeThis, toShelf.state.row)
        // §5 — rows are lazily loaded, on the DOWN that opens them.
        assertEquals(
            listOf(PlayerEffect.LoadShelves, PlayerEffect.Focus(FocusTarget.MoreLikeThisRow)),
            toShelf.effects,
        )
        val toCast = press(toShelf.state, PlayerKey.Down, ctx(hasCast = true))
        assertEquals(OsdRow.Cast, toCast.state.row)
        assertEquals(listOf(PlayerEffect.Focus(FocusTarget.CastRow)), toCast.effects)
        // Row 3 is the floor.
        val past = press(toCast.state, PlayerKey.Down, ctx(hasCast = true))
        assertEquals(OsdRow.Cast, past.state.row)
        assertTrue(past.effects.isEmpty())
    }

    @Test
    fun `DOWN skips Cast when the item has no people`() {
        val toShelf = press(onControls, PlayerKey.Down, ctx(hasCast = false))
        val past = press(toShelf.state, PlayerKey.Down, ctx(hasCast = false))
        assertEquals(OsdRow.MoreLikeThis, past.state.row)
        assertTrue(past.effects.isEmpty())
    }

    @Test
    fun `UP walks the shelf back to the control track`() {
        val onCast = onControls.copy(row = OsdRow.Cast)
        val toShelf = press(onCast, PlayerKey.Up, ctx(hasCast = true))
        assertEquals(OsdRow.MoreLikeThis, toShelf.state.row)
        val toControls = press(toShelf.state, PlayerKey.Up, ctx(hasCast = true))
        assertEquals(OsdRow.Controls, toControls.state.row)
        assertEquals(listOf(PlayerEffect.Focus(FocusTarget.PlayPause)), toControls.effects)
    }

    @Test
    fun `LEFT and RIGHT belong to the row on the control track and the shelves`() {
        for (row in listOf(OsdRow.Controls, OsdRow.MoreLikeThis, OsdRow.Cast)) {
            val result = press(onControls.copy(row = row), PlayerKey.Right)
            assertFalse("row $row must leave LEFT/RIGHT to its own traversal", result.consumed)
        }
        // Row 0 is the exception: the bar is driven, not traversed.
        assertTrue(press(onControls.copy(row = OsdRow.Seek), PlayerKey.Right).consumed)
    }

    @Test
    fun `CENTER belongs to the focused chip or card everywhere but the seek bar`() {
        for (row in listOf(OsdRow.Controls, OsdRow.MoreLikeThis, OsdRow.Cast)) {
            assertFalse(press(onControls.copy(row = row), PlayerKey.Center).consumed)
        }
    }

    // --- §3 scrub mode ----------------------------------------------------------------------------

    @Test
    fun `scrubbing moves a virtual playhead and never seeks live`() {
        val onSeek = onControls.copy(row = OsdRow.Seek, virtualPositionMs = 100_000)
        val result = press(onSeek, PlayerKey.Right, ctx(positionMs = 100_000))
        assertEquals(110_000L, result.state.virtualPositionMs)
        assertTrue(result.state.virtualDirty)
        // Nothing reaches the engine until CENTER commits.
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `the virtual playhead is clamped to the media`() {
        val nearEnd = onControls.copy(row = OsdRow.Seek, virtualPositionMs = 595_000, virtualDirty = true)
        assertEquals(600_000L, press(nearEnd, PlayerKey.Right, ctx(durationMs = 600_000)).state.virtualPositionMs)
        val nearStart = onControls.copy(row = OsdRow.Seek, virtualPositionMs = 4_000, virtualDirty = true)
        assertEquals(0L, press(nearStart, PlayerKey.Left, ctx(durationMs = 600_000)).state.virtualPositionMs)
    }

    @Test
    fun `an unknown duration leaves the virtual playhead unclamped at the top`() {
        val onSeek = onControls.copy(row = OsdRow.Seek, virtualPositionMs = 100_000, virtualDirty = true)
        val result = press(onSeek, PlayerKey.Right, ctx(durationMs = null))
        assertEquals(110_000L, result.state.virtualPositionMs)
    }

    @Test
    fun `a double press jumps the virtual playhead by chapter`() {
        val context = ctx(positionMs = 30_000, chapters = chapters)
        val onSeek = onControls.copy(row = OsdRow.Seek, virtualPositionMs = 30_000)
        val first = press(onSeek, PlayerKey.Right, context, atMs = 1_000)
        val second = press(first.state, PlayerKey.Right, context, atMs = 1_100)
        assertEquals(60_000L, second.state.virtualPositionMs)
        // Still virtual: a chapter jump while scrubbing is a preview, not a seek.
        assertTrue(second.effects.isEmpty())
    }

    @Test
    fun `CENTER commits the scrub`() {
        val onSeek = onControls.copy(row = OsdRow.Seek, virtualPositionMs = 240_000, virtualDirty = true)
        val result = press(onSeek, PlayerKey.Center, ctx(positionMs = 100_000))
        assertEquals(listOf(PlayerEffect.SeekTo(240_000L)), result.effects)
        // The preview closes: virtual and live are the same thing again.
        assertFalse(result.state.virtualDirty)
        assertEquals(OsdRow.Seek, result.state.row)
    }

    @Test
    fun `displayPositionMs follows live until the playhead is actually moved`() {
        val onSeek = onControls.copy(row = OsdRow.Seek, virtualPositionMs = 240_000)
        assertEquals(100_000L, onSeek.displayPositionMs(100_000))
        assertEquals(240_000L, onSeek.copy(virtualDirty = true).displayPositionMs(100_000))
        // Off row 0 the virtual playhead is meaningless, dirty or not.
        assertEquals(100_000L, onControls.copy(virtualDirty = true).displayPositionMs(100_000))
    }

    // --- §5 Up Next arbitration ---------------------------------------------------------------------

    @Test
    fun `the Up Next card takes DOWN priority from row 1 before the shelf`() {
        val context = ctx(upNextVisible = true)
        val toUpNext = press(onControls, PlayerKey.Down, context)
        assertTrue(toUpNext.state.upNextFocused)
        assertEquals(OsdRow.Controls, toUpNext.state.row)
        assertEquals(listOf(PlayerEffect.Focus(FocusTarget.UpNext)), toUpNext.effects)
        // A second DOWN carries on to the shelf.
        val toShelf = press(toUpNext.state, PlayerKey.Down, context)
        assertFalse(toShelf.state.upNextFocused)
        assertEquals(OsdRow.MoreLikeThis, toShelf.state.row)
    }

    @Test
    fun `UP from the shelf returns through the Up Next card while it is on screen`() {
        val context = ctx(upNextVisible = true)
        val onShelf = onControls.copy(row = OsdRow.MoreLikeThis)
        val toUpNext = press(onShelf, PlayerKey.Up, context)
        assertTrue(toUpNext.state.upNextFocused)
        val toControls = press(toUpNext.state, PlayerKey.Up, context)
        assertFalse(toControls.state.upNextFocused)
        assertEquals(OsdRow.Controls, toControls.state.row)
    }

    @Test
    fun `BACK on the Up Next card dismisses it and returns to the control track`() {
        val focused = onControls.copy(upNextFocused = true)
        val result = press(focused, PlayerKey.Back, ctx(upNextVisible = true))
        assertEquals(
            listOf(PlayerEffect.DismissUpNext, PlayerEffect.Focus(FocusTarget.PlayPause)),
            result.effects,
        )
        assertFalse(result.state.upNextFocused)
        assertTrue(result.state.osdVisible)
    }

    // --- §1 BACK cascade ----------------------------------------------------------------------------

    @Test
    fun `BACK walks panel then shelf then scrub then the OSD then out`() {
        // A panel is modal: only BACK is taken off it.
        var state = onControls.copy(panel = PlayerPanel.Subtitles, row = OsdRow.MoreLikeThis)
        val closePanel = press(state, PlayerKey.Back)
        assertNull(closePanel.state.panel)
        // The shelf it was opened over is still there.
        assertEquals(OsdRow.MoreLikeThis, closePanel.state.row)

        val closeShelf = press(closePanel.state, PlayerKey.Back)
        assertEquals(OsdRow.Controls, closeShelf.state.row)
        assertTrue(closeShelf.state.osdVisible)

        state = onControls.copy(row = OsdRow.Seek, virtualPositionMs = 500_000, virtualDirty = true)
        val cancelScrub = press(state, PlayerKey.Back)
        assertEquals(OsdRow.Controls, cancelScrub.state.row)
        assertFalse(cancelScrub.state.virtualDirty)
        // Cancelled means cancelled: no seek reaches the engine.
        assertEquals(listOf(PlayerEffect.Focus(FocusTarget.PlayPause)), cancelScrub.effects)

        val hide = press(cancelScrub.state, PlayerKey.Back)
        assertFalse(hide.state.osdVisible)
        assertEquals(listOf(PlayerEffect.Focus(FocusTarget.Root)), hide.effects)

        val exit = press(hide.state, PlayerKey.Back)
        assertEquals(listOf(PlayerEffect.Exit), exit.effects)
    }

    @Test
    fun `an open panel keeps every key but BACK`() {
        val withPanel = onControls.copy(panel = PlayerPanel.Speed)
        for (key in listOf(PlayerKey.Up, PlayerKey.Down, PlayerKey.Left, PlayerKey.Right, PlayerKey.Center)) {
            val result = press(withPanel, key)
            assertFalse("$key belongs to the panel list", result.consumed)
            assertEquals(PlayerPanel.Speed, result.state.panel)
        }
    }

    // --- focus sync ---------------------------------------------------------------------------------

    @Test
    fun `syncFocus realigns the row model with where focus actually landed`() {
        assertEquals(OsdRow.MoreLikeThis, syncFocus(onControls, FocusTarget.MoreLikeThisRow).row)
        assertEquals(OsdRow.Cast, syncFocus(onControls, FocusTarget.CastRow).row)
        assertEquals(OsdRow.Controls, syncFocus(onControls.copy(row = OsdRow.Cast), FocusTarget.PlayPause).row)
        // The bar is armed, but a scrub only starts when a key moves the playhead.
        val onBar = syncFocus(onControls.copy(virtualDirty = true), FocusTarget.SeekBar)
        assertEquals(OsdRow.Seek, onBar.row)
        assertFalse(onBar.virtualDirty)
        // The Up Next card is an overlay on row 1, not a row of its own.
        val onCard = syncFocus(onControls.copy(row = OsdRow.MoreLikeThis), FocusTarget.UpNext)
        assertTrue(onCard.upNextFocused)
        assertEquals(OsdRow.Controls, onCard.row)
        // Root is inert: hiding the OSD is the only thing that sends focus back out.
        assertEquals(onControls, syncFocus(onControls, FocusTarget.Root))
    }

    @Test
    fun `focus sync leaves the next DOWN pointing at the right row`() {
        // A focusRestorer put focus back on the Cast row while the model still believed row 1.
        val synced = syncFocus(onControls, FocusTarget.CastRow)
        val down = press(synced, PlayerKey.Down, ctx(hasCast = true))
        // Row 3 is the floor, so DOWN is a no-op rather than a jump back down from row 1.
        assertEquals(OsdRow.Cast, down.state.row)
        assertTrue(down.effects.isEmpty())
    }

    // --- §1 auto-hide -------------------------------------------------------------------------------

    @Test
    fun `auto-hide is suspended by a panel, a scrub, and the shelf`() {
        assertFalse(onControls.autoHideSuspended)
        assertTrue(onControls.copy(panel = PlayerPanel.Quality).autoHideSuspended)
        assertTrue(onControls.copy(row = OsdRow.Seek).autoHideSuspended)
        assertTrue(onControls.copy(row = OsdRow.MoreLikeThis).autoHideSuspended)
        assertTrue(onControls.copy(row = OsdRow.Cast).autoHideSuspended)
        // A hidden OSD has nothing to suspend.
        assertFalse(PlayerInputState(row = OsdRow.Seek).autoHideSuspended)
    }

    @Test
    fun `every key that reaches a visible OSD restarts the auto-hide timer`() {
        // Including the ones it does not consume: walking the chips is as much "still here" as
        // anything else the user could do.
        val traversal = press(onControls, PlayerKey.Right)
        assertFalse(traversal.consumed)
        assertEquals(onControls.interactionTick + 1, traversal.state.interactionTick)
        // Layer 0 has no timer to restart.
        assertEquals(0, press(PlayerInputState(), PlayerKey.Right).state.interactionTick)
    }

    @Test
    fun `revealing resets the row, the Up Next focus, and any stale scrub`() {
        val stale = PlayerInputState(
            row = OsdRow.Cast,
            upNextFocused = true,
            virtualDirty = true,
            repeatCount = 12,
            holdDirection = -1,
        )
        val result = press(stale, PlayerKey.Up)
        assertEquals(OsdRow.Controls, result.state.row)
        assertFalse(result.state.upNextFocused)
        assertFalse(result.state.virtualDirty)
        assertEquals(0, result.state.repeatCount)
    }
}
