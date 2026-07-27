package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.data.TrickplaySource
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.ShoumeiType
import com.maik205.shoumeiplayer.ui.theme.Tungsten
import com.maik205.shoumeiplayer.util.Ticks
import kotlin.math.abs

/** §3 — a slim 3dp line, 5dp while the bar owns focus. */
private val TrackRestHeight = 3.dp
private val TrackFocusedHeight = 5.dp

/** §3 — the scrubber: `3 × 14dp`, rounded (the §6 amendment; §4.1's rectangle is the v2 shape). */
private val ScrubberWidth = 3.dp
private val ScrubberHeight = 14.dp
private val ScrubberRadius = 1.5.dp

/** Lane tall enough to hold the scrubber without the bar's own height jumping. */
private val SeekLaneHeight = 18.dp

/** §3 — chapter ticks: hairlines in the lane, never labelled. */
private val ChapterTickWidth = 1.dp
private val ChapterTickHeight = 9.dp

/** Gap between a timecode and the lane it flanks. */
private val TimecodeGap = 14.dp

/** §3 — the trickplay preview floats above the scrubber; this is the gap to the lane's top edge. */
private val PreviewGap = 12.dp

/** §5.3 — beyond the virtual playhead the fill ghosts, so the bar shows where you *were*. */
private const val GHOST_FILL_ALPHA = 0.45f

/**
 * docs/osd-v3.md §3 — row 0 of the OSD: the seek bar, with its timecodes at the ends.
 *
 * The bar itself is hand-drawn (ui-design §7 flag 4 — material3's `LinearProgressIndicator` draws a
 * track gap and a stop indicator, and a `Slider` is a circle-thumb phone idiom). Track `#FFFFFF @16%`
 * growing `3dp → 5dp` on focus, buffered `#FFFFFF @28%`, played fill [Tungsten], chapter ticks under
 * the fill so what is still marked is what is still ahead, and a `3 × 14dp` white scrubber that
 * exists only while the bar has focus.
 *
 * **Scrub mode.** [headMs] is what the bar *draws*; [liveMs] is where playback actually is. While
 * they differ the fill past the head ghosts, the leading timecode switches to `TimecodeLarge` in
 * [Tungsten] with a signed delta beside it, and the [TrickplayPreview] floats above the scrubber.
 *
 * The bar is [focusable] but never `clickable`: it has no action of its own. Focusing it *is* scrub
 * mode, LEFT/RIGHT move the virtual playhead and CENTER commits it — all of which the screen's
 * reducer consumes before any click could fire (§1, §3). A `clickable` here would be a second,
 * invisible meaning for the same key.
 */
@Suppress("LongParameterList")
@Composable
internal fun OsdSeekBar(
    headMs: Long,
    liveMs: Long,
    durationMs: Long,
    bufferedMs: Long?,
    chapters: List<ChapterMark>,
    focused: Boolean,
    scrubbing: Boolean,
    modifier: Modifier = Modifier,
    trickplay: TrickplaySource? = null,
    focusable: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    showPreview: Boolean = scrubbing,
) {
    var selfFocused by remember { mutableStateOf(false) }
    val lit = focused || selfFocused

    val trackHeight by animateDpAsState(
        targetValue = if (lit) TrackFocusedHeight else TrackRestHeight,
        animationSpec = tween(Dur.FocusIn, easing = Ease.Decel),
        label = "seekTrackHeight",
    )
    // §7 — linear, always: eased playback progress reads as a lie.
    val head by animateFloatAsState(
        targetValue = fractionOf(headMs, durationMs),
        animationSpec = tween(
            durationMillis = if (scrubbing) Dur.ScrubTick else Dur.ProgressValue,
            easing = LinearEasing,
        ),
        label = "seekHead",
    )
    val bufferedFraction = fractionOf(bufferedMs ?: 0L, durationMs)
    val deltaMs = headMs - liveMs

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                selfFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(if (focusable) Modifier.focusable() else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TimecodeGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Ticks.formatDuration(headMs),
                style = if (scrubbing) ShoumeiType.TimecodeLarge else ShoumeiType.Timecode,
                color = if (scrubbing) Tungsten else Paper,
                maxLines = 1,
            )
            if (scrubbing && deltaMs != 0L) {
                Text(
                    text = formatSeekDelta(deltaMs),
                    style = ShoumeiType.Timecode,
                    color = Tungsten,
                    maxLines = 1,
                    modifier = Modifier.offset(x = 10.dp),
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(SeekLaneHeight),
        ) {
            val laneWidth = maxWidth

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(trackHeight)
                    .background(Lit.copy(alpha = Alpha.TrackInactive), RectangleShape),
            )
            if (bufferedFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(laneWidth * bufferedFraction)
                        .height(trackHeight)
                        .background(Lit.copy(alpha = Alpha.TrackBuffered), RectangleShape),
                )
            }
            // Ticks sit above the track but below the fill: the tungsten swallows passed chapters,
            // so what is still marked is what is still ahead. Never labelled on the bar (§3).
            chapterFractions(chapters, durationMs).forEach { markFraction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = laneWidth * markFraction - ChapterTickWidth / 2)
                        .width(ChapterTickWidth)
                        .height(ChapterTickHeight)
                        .background(Lit.copy(alpha = Alpha.TextDisabled)),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(laneWidth * head)
                    .height(trackHeight)
                    .background(
                        color = if (scrubbing) Tungsten.copy(alpha = GHOST_FILL_ALPHA) else Tungsten,
                        shape = RectangleShape,
                    ),
            )

            LaneOverlay(
                visible = lit,
                enter = scaleIn(initialScale = 0.4f, animationSpec = tween(Dur.FocusIn, easing = Ease.Decel)),
                exit = scaleOut(targetScale = 0.4f, animationSpec = tween(Dur.FocusOut, easing = Ease.Accel)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = laneWidth * head - ScrubberWidth / 2),
            ) {
                Box(
                    modifier = Modifier
                        .width(ScrubberWidth)
                        .height(ScrubberHeight)
                        .clip(RoundedCornerShape(ScrubberRadius))
                        .background(Color.White),
                )
            }

            // §3 — the preview rides above the scrubber. It is deliberately allowed to overflow the
            // lane: the plate behind it does not clip, so no layout above has to reserve space and
            // the identity block never shifts when a scrub begins.
            LaneOverlay(
                visible = showPreview,
                enter = fadeIn(tween(Dur.FocusIn, easing = Ease.Decel)),
                exit = fadeOut(tween(Dur.FocusOut, easing = Ease.Accel)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (laneWidth * head - TrickplayPreviewWidth / 2)
                            .coerceIn(0.dp, (laneWidth - TrickplayPreviewWidth).coerceAtLeast(0.dp)),
                        y = -(PreviewGap + trickplayPreviewHeight(trickplay)),
                    ),
            ) {
                TrickplayPreview(
                    source = trickplay,
                    positionMs = headMs,
                    chapterName = currentChapter(chapters, headMs)?.name,
                )
            }
        }

        Text(
            text = Ticks.formatDuration(durationMs),
            style = ShoumeiType.Timecode,
            color = Ash600,
            maxLines = 1,
        )
    }
}

/**
 * `AnimatedVisibility` at the top level of the file, deliberately.
 *
 * The scrubber and the trickplay preview are composed inside a `BoxWithConstraints` that is itself
 * inside the bar's `Row`. `RowScope` declares its own `AnimatedVisibility` extension, so calling the
 * plain one from in there resolves against the wrong (non-innermost) receiver and fails to compile.
 * Hoisting the call out of every scope is the smallest honest fix.
 */
@Composable
private fun LaneOverlay(
    visible: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit) { content() }
}

/** A position as a fraction of the lane, clamped; an unknown duration reads as 0. */
internal fun fractionOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

/**
 * §3 — chapter tick positions as lane fractions. The opening chapter (position 0) is dropped: a tick
 * under the scrubber's home position is noise, not navigation.
 */
internal fun chapterFractions(chapters: List<ChapterMark>, durationMs: Long): List<Float> {
    if (durationMs <= 0L) return emptyList()
    return chapters
        .filter { it.positionMs > 0L && it.positionMs < durationMs }
        .map { (it.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
}

/**
 * The scrub delta beside the virtual playhead, e.g. `-00:30`. Mono, tabular, signed with an ASCII
 * hyphen: ui-design §1 bans long dashes in visible strings, and a hyphen is what the rest of the
 * app's ranges and negatives use.
 */
internal fun formatSeekDelta(deltaMs: Long): String {
    val sign = if (deltaMs < 0) "-" else "+"
    val totalSeconds = abs(deltaMs) / 1000
    return "%s%02d:%02d".format(sign, totalSeconds / 60, totalSeconds % 60)
}
