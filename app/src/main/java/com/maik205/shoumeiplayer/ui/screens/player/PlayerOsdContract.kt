package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusRequester
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.VideoQuality

/**
 * The seam between [PlayerScreen] (which owns the docs/osd-v3.md §1/§3 input model) and `PlayerOsd`
 * (which owns the §2/§4/§5 presentation).
 *
 * Two bundles rather than thirty parameters, for one reason: the screen decides *where focus goes*
 * and *what a control means*, the OSD decides what it all looks like. Keeping that division in two
 * named types means neither side can quietly grow a dependency on the other's internals.
 */

/**
 * The focus requesters the screen hands to the OSD, one per §1 focus target. The OSD attaches each
 * to the corresponding composable; the screen drives them, because a directional focus search
 * launched from the full-screen root can never find a control inside the root's own bounds — every
 * entry into the overlay has to be an explicit handoff.
 */
@Stable
class PlayerOsdFocus(
    /** Row 1's play/pause chip — the landing point of every reveal and of every BACK step out. */
    val playPause: FocusRequester = FocusRequester(),
    /** Row 0, the seek bar. Focusing it *is* scrub mode. */
    val seekBar: FocusRequester = FocusRequester(),
    /** §5 — the Up Next card, which takes DOWN priority from row 1 while it is on screen. */
    val upNext: FocusRequester = FocusRequester(),
    /** Row 2 — the "More like this" shelf row. */
    val moreLikeThis: FocusRequester = FocusRequester(),
    /** Row 3 — the "Cast" shelf row. */
    val cast: FocusRequester = FocusRequester(),
)

/**
 * Everything the OSD can ask for. All of these are already debounced against the auto-hide timer by
 * the screen: the OSD never has to think about visibility, only about meaning.
 */
@Stable
class PlayerOsdCallbacks(
    val onPlayPause: () -> Unit,
    /** Transport chips only (∓10s). The seek bar's own LEFT/RIGHT is the screen's business (§3). */
    val onSeekBy: (Long) -> Unit,
    val onPreviousEpisode: () -> Unit,
    val onNextEpisode: () -> Unit,
    /**
     * A chip opening its right-side panel (§2).
     *
     * There is deliberately no matching `onClosePanel`: the panel is inline in the OSD's own focus
     * tree, so it never dismisses itself. Closing it is either a selection (which reports through the
     * `onSelect*` callbacks below) or BACK, and BACK is step one of §1's cascade — owned by the
     * reducer in PlayerInput.kt, which clears the panel itself.
     */
    val onOpenPanel: (PlayerPanel) -> Unit,
    /** A panel row committing: audio/subtitle stream, playback rate, quality rung. */
    val onSelectTrack: (PlayerTrack) -> Unit,
    val onSelectSpeed: (Float) -> Unit,
    val onSelectQuality: (VideoQuality) -> Unit,
    /** §5 — Up Next: CENTER plays the next episode now. */
    val onPlayUpNext: () -> Unit,
    /** §5 — "More like this": stop playback cleanly and open the item's detail screen. */
    val onOpenItem: (itemId: String) -> Unit,
    /** §5 — "Cast": leave the player for a person-filtered library grid. */
    val onOpenPerson: (person: CastMemberUi) -> Unit,
    /**
     * Focus landed on one of the §1 targets without the screen having asked for it — a
     * `focusRestorer` reviving a shelf row, a chip taking focus on its own. Wire this to each
     * control's `onFocused` hook and the screen's row model follows real focus instead of drifting
     * out of step with it.
     */
    val onFocusMoved: (FocusTarget) -> Unit,
    /** Any OSD-side interaction that should restart the 5s auto-hide timer. */
    val onInteraction: () -> Unit,
)
