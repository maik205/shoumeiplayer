package com.maik205.shoumeiplayer.ui.screens.player

/**
 * The player's D-pad input model — docs/osd-v3.md §1 (layers & focus) and §3 (scrub mode) —
 * expressed as a **pure reducer** so the whole interaction can be unit-tested without Compose, a
 * device, or a running engine. [PlayerScreen] owns only the wiring: it maps key events onto
 * [PlayerKey], feeds the reducer, and executes the [PlayerEffect]s it hands back.
 *
 * The two layers of §1:
 *  - **Layer 0** ([PlayerInputState.osdVisible] = false): the root surface owns focus. LEFT/RIGHT
 *    seek live ±10s and raise the slim mini strip for [MiniStripFadeMs]; a fast second press in the
 *    same direction is a chapter jump; CENTER plays/pauses and reveals; UP/DOWN/MENU reveal; BACK
 *    exits.
 *  - **Layer 1** ([PlayerInputState.osdVisible] = true): focus lives inside the OSD, arranged as the
 *    four rows of [OsdRow]. UP/DOWN walk the rows (and are consumed here, because a directional
 *    focus search cannot cross the overlay boundary on its own); LEFT/RIGHT belong to the focused
 *    row's own traversal and are deliberately *not* consumed — except on [OsdRow.Seek], where they
 *    drive the virtual playhead.
 */

// --- vocabulary ---------------------------------------------------------------------------------

/** The only keys the input model has an opinion about; everything else arrives as [Other]. */
enum class PlayerKey { Left, Right, Up, Down, Center, Back, Menu, Other }

/** §1 — the vertical focus geography of the OSD. Ordinals are the row numbers in the spec. */
enum class OsdRow {
    /** row 0 — the seek bar. Focusing it *is* scrub mode. */
    Seek,

    /** row 1 — THE control track (transport cluster + track/speed/quality chips). */
    Controls,

    /** row 2 — the "More like this" shelf. */
    MoreLikeThis,

    /** row 3 — the "Cast" shelf. */
    Cast,
}

/** §2 — the right-side panels a control-track chip can open. */
enum class PlayerPanel { Subtitles, Audio, Speed, Quality }

/** Where the screen should hand focus after a reduction. */
enum class FocusTarget { Root, PlayPause, SeekBar, UpNext, MoreLikeThisRow, CastRow }

/**
 * Something the reducer wants done to the world. Pure data: no engine, no navigation, no Compose,
 * so a test can assert on intent rather than on side effects.
 */
sealed interface PlayerEffect {
    data object TogglePlayPause : PlayerEffect

    /**
     * A *live* seek by [deltaMs] — layer 0 only. Scrub mode never touches the engine until CENTER
     * commits, which is the whole point of the virtual playhead (§3).
     */
    data class SeekBy(val deltaMs: Long) : PlayerEffect

    /** An absolute seek: a chapter jump on layer 0, or the commit of a scrub. */
    data class SeekTo(val positionMs: Long) : PlayerEffect

    data class Focus(val target: FocusTarget) : PlayerEffect

    /** §5 — the shelf rows are lazily loaded, on the DOWN that first opens them. */
    data object LoadShelves : PlayerEffect

    /** §5 — BACK on the Up Next card retires it for this item. */
    data object DismissUpNext : PlayerEffect

    data object Exit : PlayerEffect
}

// --- constants ----------------------------------------------------------------------------------

/** §1 — LEFT/RIGHT on the surface, and the resting step of a scrub. */
internal const val SeekStepMs = 10_000L

/** §3 — the accelerated scrub steps and the repeat counts that unlock them. */
internal const val ScrubStepMediumMs = 30_000L
internal const val ScrubStepFastMs = 60_000L
internal const val RepeatsToMediumStep = 10
internal const val RepeatsToFastStep = 20

/** Two LEFT/RIGHT presses in the same direction within this window jump a chapter instead of seeking. */
internal const val ChapterDoublePressWindowMs = 400L

/** §1 — the layer-0 mini strip fades this long after the last seek key. */
internal const val MiniStripFadeMs = 1_500L

/** §1 — the OSD auto-hides after this much inactivity, unless [PlayerInputState.autoHideSuspended]. */
internal const val OsdAutoHideMs = 5_000L

/**
 * How far past a chapter boundary the playhead may be and still count as "in" it, so a backwards
 * jump goes back to the previous boundary rather than re-landing on the current one.
 */
private const val ChapterBackStepGraceMs = 3_000L

// --- state --------------------------------------------------------------------------------------

/**
 * Everything the input model remembers between key presses. Immutable, so Compose can hold it in a
 * single `mutableStateOf` and a test can assert on the whole thing at once.
 */
data class PlayerInputState(
    /** Layer 1 (the full OSD) is up. False means layer 0: the root surface owns focus. */
    val osdVisible: Boolean = false,
    /** Which row of the OSD focus sits in; meaningless while [osdVisible] is false. */
    val row: OsdRow = OsdRow.Controls,
    /** The open right-side panel, or null. A panel is modal: it owns every key but BACK. */
    val panel: PlayerPanel? = null,
    /** §5 — the Up Next card holds focus (it wins DOWN from row 1 before the shelf does). */
    val upNextFocused: Boolean = false,
    /** §3 — where the virtual playhead sits while scrubbing. */
    val virtualPositionMs: Long = 0L,
    /** False until the virtual playhead has actually been moved; until then it just follows live. */
    val virtualDirty: Boolean = false,
    /** Repeat events received in the current LEFT/RIGHT hold — the input to [stepForRepeat]. */
    val repeatCount: Int = 0,
    /** Direction of the current hold: -1 back, +1 forward, 0 none. */
    val holdDirection: Int = 0,
    /** Direction of the last discrete seek press, for the chapter double-press test. */
    val lastSeekDirection: Int = 0,
    /** Event time of the last discrete seek press, for the chapter double-press test. */
    val lastSeekAtMs: Long = 0L,
    /** Bumped every time the layer-0 mini strip should (re)appear; the screen restarts its fade on it. */
    val miniStripTick: Int = 0,
    /** Bumped by every key that reaches the OSD; the screen restarts the auto-hide timer on it. */
    val interactionTick: Int = 0,
) {
    /** §1 — focusing row 0 *is* scrub mode; there is no separate arming step. */
    val scrubbing: Boolean get() = osdVisible && row == OsdRow.Seek

    /** True while one of the two shelf rows is open. */
    val shelfOpen: Boolean get() = osdVisible && (row == OsdRow.MoreLikeThis || row == OsdRow.Cast)

    /**
     * §1 — "Auto-hide 5s; suspended while a panel is open, while scrubbing, and while the shelf is
     * open." All three are deliberate stops; dissolving the OSD out from under one would be a lie
     * about where focus is.
     */
    val autoHideSuspended: Boolean get() = panel != null || scrubbing || shelfOpen

    /**
     * The position the seek bar should draw: the virtual playhead once it has been moved, the live
     * playhead otherwise. §3's trickplay preview shows exactly while these two differ.
     */
    fun displayPositionMs(livePositionMs: Long): Long =
        if (scrubbing && virtualDirty) virtualPositionMs else livePositionMs

    internal fun noteInteraction(): PlayerInputState = copy(interactionTick = interactionTick + 1)
}

/**
 * The slice of the world the reducer needs to answer a key press. Pulled from `PlayerUiState` at the
 * call site so the reducer itself never touches a ViewModel.
 */
data class PlayerInputContext(
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val chapters: List<ChapterMark> = emptyList(),
    /** §5 — the Up Next card is on screen, so it takes DOWN priority from row 1. */
    val upNextVisible: Boolean = false,
    /**
     * The "More like this" row is offered. Defaults to true: the shelf is lazily loaded, so its
     * emptiness at DOWN time says nothing about whether it will have content.
     */
    val hasMoreLikeThis: Boolean = true,
    /** The Cast row is offered — this one *is* known up front, People rides on the item detail. */
    val hasCast: Boolean = false,
)

/**
 * The outcome of one key press: the next [state], the [effects] the screen must run, and whether the
 * key was [consumed]. An unconsumed key falls through to Compose so the focused chip, card, or panel
 * row can do its own thing.
 */
data class PlayerInputResult(
    val state: PlayerInputState,
    val effects: List<PlayerEffect> = emptyList(),
    val consumed: Boolean = true,
)

// --- pure helpers -------------------------------------------------------------------------------

/**
 * §3 — hold-repeat acceleration: "step 10s → 30s after 10 repeats → 60s after 20 (reset on release
 * or direction change)". [repeats] is how many auto-repeat events the current hold has produced, so
 * the initial press is `0` and the eleventh event is the first accelerated one.
 *
 * The reset lives in the reducer ([reducePlayerKey] on a direction change, [releaseSeekHold] on
 * key-up); this function is a pure ladder so the thresholds can be read off in one glance.
 */
fun stepForRepeat(repeats: Int): Long = when {
    repeats >= RepeatsToFastStep -> ScrubStepFastMs
    repeats >= RepeatsToMediumStep -> ScrubStepMediumMs
    else -> SeekStepMs
}

/**
 * Whether a LEFT/RIGHT press counts as the second half of a chapter-jump double press:
 * same direction as the previous press, inside [ChapterDoublePressWindowMs], and there
 * are chapters to jump between. Without chapters, rapid presses stay plain repeated
 * seeks, so hammering the seek key is never hijacked on chapterless media.
 */
internal fun isChapterDoublePress(sameDirection: Boolean, elapsedMs: Long, chapterCount: Int): Boolean =
    sameDirection && elapsedMs in 1..ChapterDoublePressWindowMs && chapterCount >= 1

/** The next chapter boundary strictly ahead of [positionMs], or null when already in the last one. */
internal fun nextChapterMs(chapters: List<ChapterMark>, positionMs: Long): Long? =
    chapters.firstOrNull { it.positionMs > positionMs }?.positionMs

/**
 * The start of the previous chapter — or the start of the current one when the playhead has run
 * more than [ChapterBackStepGraceMs] into it (the familiar "restart this chapter first" behaviour).
 */
internal fun previousChapterMs(chapters: List<ChapterMark>, positionMs: Long): Long? =
    chapters.lastOrNull { it.positionMs < positionMs - ChapterBackStepGraceMs }?.positionMs
        ?: chapters.firstOrNull()?.positionMs?.takeIf { it < positionMs }

/** Key-up on LEFT/RIGHT ends the hold, so the next one starts back at the 10s step (§3). */
fun releaseSeekHold(state: PlayerInputState): PlayerInputState =
    if (state.repeatCount == 0 && state.holdDirection == 0) state
    else state.copy(repeatCount = 0, holdDirection = 0)

/**
 * Realigns the row model when focus moves *without* a key press — a `focusRestorer` bringing a shelf
 * row back, a click, a chip that took focus on its own. The reducer's UP/DOWN are relative moves, so
 * a model that has drifted from where focus actually is would send the next press to the wrong row.
 *
 * [FocusTarget.Root] is deliberately inert: the OSD hiding is what moves focus to the root, and that
 * transition is already owned by the auto-hide timer and the BACK cascade.
 */
fun syncFocus(state: PlayerInputState, target: FocusTarget): PlayerInputState = when (target) {
    FocusTarget.Root -> state
    FocusTarget.PlayPause -> state.copy(row = OsdRow.Controls, upNextFocused = false)
    // Focus landing on the bar arms scrub mode, but not a scrub: until a key moves it, the virtual
    // playhead is just the live one.
    FocusTarget.SeekBar -> state.copy(row = OsdRow.Seek, upNextFocused = false, virtualDirty = false)
    FocusTarget.UpNext -> state.copy(row = OsdRow.Controls, upNextFocused = true)
    FocusTarget.MoreLikeThisRow -> state.copy(row = OsdRow.MoreLikeThis, upNextFocused = false)
    FocusTarget.CastRow -> state.copy(row = OsdRow.Cast, upNextFocused = false)
}

// --- the reducer --------------------------------------------------------------------------------

/**
 * Folds one key press into the input model.
 *
 * @param repeat true for an auto-repeat event (the key is being *held*). Hold-repeat acceleration
 *   and the chapter double-press are deliberately disjoint: a held key accelerates, discrete taps
 *   pair up into chapter jumps. Reading them off the same signal would make one of the two
 *   unreachable.
 * @param eventTimeMs the key event's own timestamp, so double-press timing never depends on when
 *   the reducer happened to run.
 */
fun reducePlayerKey(
    state: PlayerInputState,
    key: PlayerKey,
    ctx: PlayerInputContext = PlayerInputContext(),
    eventTimeMs: Long = 0L,
    repeat: Boolean = false,
): PlayerInputResult {
    fun done(next: PlayerInputState, effects: List<PlayerEffect> = emptyList(), consumed: Boolean = true) =
        PlayerInputResult(
            // Any key that reaches a visible OSD restarts its auto-hide timer, consumed or not:
            // walking the chips with LEFT/RIGHT is exactly as much "still here" as anything else.
            state = if (next.osdVisible) next.noteInteraction() else next,
            effects = effects,
            consumed = consumed,
        )

    /** Reveal is always an explicit focus handoff to play/pause: §1's directional search can't enter. */
    fun reveal(effects: List<PlayerEffect> = emptyList()) = done(
        next = state.copy(
            osdVisible = true,
            row = OsdRow.Controls,
            upNextFocused = false,
            virtualDirty = false,
            repeatCount = 0,
            holdDirection = 0,
        ),
        effects = effects + PlayerEffect.Focus(FocusTarget.PlayPause),
    )

    fun toControls(extra: List<PlayerEffect> = emptyList()) = done(
        next = state.copy(row = OsdRow.Controls, upNextFocused = false, virtualDirty = false),
        effects = extra + PlayerEffect.Focus(FocusTarget.PlayPause),
    )

    // A panel is modal. Its list does its own UP/DOWN traversal and its own CENTER commit, so the
    // reducer takes only BACK off it (step one of the §1 cascade).
    if (state.panel != null) {
        return if (key == PlayerKey.Back) {
            done(state.copy(panel = null), listOf(PlayerEffect.Focus(FocusTarget.PlayPause)))
        } else {
            done(state, consumed = false)
        }
    }

    val seekDirection = when (key) {
        PlayerKey.Left -> -1
        PlayerKey.Right -> 1
        else -> 0
    }

    // --- layer 0: the root surface owns focus ----------------------------------------------------
    if (!state.osdVisible) {
        return when (key) {
            PlayerKey.Left, PlayerKey.Right -> {
                val sameDirection = state.lastSeekDirection == seekDirection
                val doublePress = !repeat && isChapterDoublePress(
                    sameDirection = sameDirection,
                    elapsedMs = eventTimeMs - state.lastSeekAtMs,
                    chapterCount = ctx.chapters.size,
                )
                // §1/§3 — acceleration belongs to scrub mode. On the surface every repeat is a live
                // seek, and a live seek re-buffers, so 60s-per-tick here would be violence.
                val effect = if (doublePress) {
                    val target = if (seekDirection > 0) {
                        nextChapterMs(ctx.chapters, ctx.positionMs)
                    } else {
                        previousChapterMs(ctx.chapters, ctx.positionMs)
                    }
                    // Consumed even at the first/last boundary, so a double press at the ends is a
                    // no-op rather than a stray focus move.
                    target?.let(PlayerEffect::SeekTo)
                } else {
                    PlayerEffect.SeekBy(seekDirection * SeekStepMs)
                }
                done(
                    next = state.copy(
                        lastSeekDirection = seekDirection,
                        lastSeekAtMs = eventTimeMs,
                        holdDirection = seekDirection,
                        repeatCount = if (repeat && state.holdDirection == seekDirection) state.repeatCount + 1 else 0,
                        miniStripTick = state.miniStripTick + 1,
                    ),
                    effects = listOfNotNull(effect),
                )
            }
            // CENTER on the surface both acts and reveals: the OSD that appears already shows the
            // state the press just produced.
            PlayerKey.Center -> reveal(listOf(PlayerEffect.TogglePlayPause))
            PlayerKey.Up, PlayerKey.Down, PlayerKey.Menu -> reveal()
            PlayerKey.Back -> done(state, listOf(PlayerEffect.Exit))
            PlayerKey.Other -> done(state, consumed = false)
        }
    }

    // --- layer 1: focus lives inside the OSD -----------------------------------------------------
    return when (key) {
        PlayerKey.Left, PlayerKey.Right -> {
            if (state.row != OsdRow.Seek) {
                // Row 1/2/3: LEFT/RIGHT is the row's own traversal (chips, cards). Not ours.
                done(state, consumed = false)
            } else {
                val base = state.displayPositionMs(ctx.positionMs)
                val sameDirection = state.lastSeekDirection == seekDirection
                val doublePress = !repeat && isChapterDoublePress(
                    sameDirection = sameDirection,
                    elapsedMs = eventTimeMs - state.lastSeekAtMs,
                    chapterCount = ctx.chapters.size,
                )
                val repeats = if (repeat && state.holdDirection == seekDirection) state.repeatCount + 1 else 0
                val target = if (doublePress) {
                    val chapter = if (seekDirection > 0) {
                        nextChapterMs(ctx.chapters, base)
                    } else {
                        previousChapterMs(ctx.chapters, base)
                    }
                    chapter ?: base
                } else {
                    base + seekDirection * stepForRepeat(repeats)
                }
                done(
                    next = state.copy(
                        // No live seek: the virtual playhead is the whole point of §3.
                        virtualPositionMs = target.coerceIn(0L, ctx.durationMs ?: Long.MAX_VALUE),
                        virtualDirty = true,
                        lastSeekDirection = seekDirection,
                        lastSeekAtMs = eventTimeMs,
                        holdDirection = seekDirection,
                        repeatCount = repeats,
                    ),
                )
            }
        }

        PlayerKey.Up -> when {
            state.row == OsdRow.Cast -> done(
                state.copy(row = OsdRow.MoreLikeThis),
                listOf(PlayerEffect.Focus(FocusTarget.MoreLikeThisRow)),
            )
            state.row == OsdRow.MoreLikeThis && ctx.upNextVisible -> done(
                state.copy(row = OsdRow.Controls, upNextFocused = true),
                listOf(PlayerEffect.Focus(FocusTarget.UpNext)),
            )
            state.row == OsdRow.MoreLikeThis -> toControls()
            state.upNextFocused -> toControls()
            // Row 1 → row 0: focusing the bar arms scrub mode with the virtual playhead parked on
            // the live one, so the first LEFT/RIGHT moves from where the picture actually is.
            state.row == OsdRow.Controls -> done(
                state.copy(
                    row = OsdRow.Seek,
                    virtualPositionMs = ctx.positionMs,
                    virtualDirty = false,
                    repeatCount = 0,
                    holdDirection = 0,
                ),
                listOf(PlayerEffect.Focus(FocusTarget.SeekBar)),
            )
            // Row 0 is the top of the geography: UP has nowhere to go, and must not escape the OSD.
            else -> done(state)
        }

        PlayerKey.Down -> when {
            state.row == OsdRow.Seek -> toControls()
            // §1 — "Up Next card (when visible) takes DOWN priority from row 1 before the shelf."
            state.row == OsdRow.Controls && !state.upNextFocused && ctx.upNextVisible -> done(
                state.copy(upNextFocused = true),
                listOf(PlayerEffect.Focus(FocusTarget.UpNext)),
            )
            state.row == OsdRow.Controls || state.upNextFocused -> when {
                ctx.hasMoreLikeThis -> done(
                    state.copy(row = OsdRow.MoreLikeThis, upNextFocused = false),
                    listOf(PlayerEffect.LoadShelves, PlayerEffect.Focus(FocusTarget.MoreLikeThisRow)),
                )
                ctx.hasCast -> done(
                    state.copy(row = OsdRow.Cast, upNextFocused = false),
                    listOf(PlayerEffect.Focus(FocusTarget.CastRow)),
                )
                else -> done(state)
            }
            state.row == OsdRow.MoreLikeThis && ctx.hasCast -> done(
                state.copy(row = OsdRow.Cast),
                listOf(PlayerEffect.Focus(FocusTarget.CastRow)),
            )
            // Bottom of the geography.
            else -> done(state)
        }

        // §3 — CENTER on row 0 commits the scrub; everywhere else the focused chip or card owns it.
        PlayerKey.Center -> if (state.row == OsdRow.Seek) {
            val target = state.displayPositionMs(ctx.positionMs)
            done(
                state.copy(virtualPositionMs = target, virtualDirty = false),
                listOf(PlayerEffect.SeekTo(target)),
            )
        } else {
            done(state, consumed = false)
        }

        // §1 — "BACK: closes panel → closes shelf → cancels scrub → hides OSD → exits", with §5's
        // Up Next dismissal sitting between panel and shelf because the card is the innermost thing
        // focus can be in.
        PlayerKey.Back -> when {
            state.upNextFocused -> toControls(listOf(PlayerEffect.DismissUpNext))
            state.shelfOpen -> toControls()
            state.scrubbing -> toControls()
            else -> PlayerInputResult(
                state = state.copy(osdVisible = false, row = OsdRow.Controls, virtualDirty = false),
                effects = listOf(PlayerEffect.Focus(FocusTarget.Root)),
            )
        }

        // The OSD is already up; MENU has nothing left to reveal.
        PlayerKey.Menu -> done(state)
        PlayerKey.Other -> done(state, consumed = false)
    }
}
