package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Tungsten

/** §2 / §6 amendment — the resting chip is a 42dp disc; focused it grows into a pill of that height. */
internal val ChipSize = 42.dp

/** The glyph inside a chip, resting or focused. */
internal val ChipIconSize = 24.dp

/** Inside padding a chip takes once it is a labelled pill; zero at rest, where the disc centres. */
private val ChipPillPadding = 16.dp

/** Gap between the glyph and its inline label in the focused pill. */
private val ChipLabelGap = 10.dp

/** §2 — the 4dp tungsten dot that marks a chip whose feature is *on*. */
private val ActiveDotSize = 4.dp
private val ActiveDotGap = 6.dp

/** Height of the whole control track, dot lane included, so the row never reflows. */
internal val ChipTrackHeight = ChipSize + ActiveDotGap + ActiveDotSize

/** §2 — LEFT/RIGHT walks across this gap between the transport cluster and the options cluster. */
internal val ChipSpacing = 8.dp

/**
 * §7 — the one focus tween, typed for colour.
 * [com.maik205.shoumeiplayer.ui.theme.focusTween] is `TweenSpec<Float>`; a chip animates its fill
 * and its content colour, so it needs the same curve and the same two durations at `Color`.
 */
private fun colorFocusTween(focused: Boolean): TweenSpec<Color> = tween(
    durationMillis = if (focused) Dur.FocusIn else Dur.FocusOut,
    easing = if (focused) Ease.Decel else Ease.Accel,
)

/**
 * docs/osd-v3.md §2 — one control of the YouTube-TV chip track.
 *
 * Resting it is a [ChipSize] disc holding a [ChipIconSize] glyph at [Alpha.TextTertiary] over a
 * `#FFFFFF @8%` fill — the app's hairline alpha, so the disc reads over bright video without
 * becoming a plate. Focused it *is* the affordance: the fill goes to solid white, the content
 * inverts to [Ink000], and the disc grows into a pill with [label] set inline (tv-material inverse
 * focus). There is no scale and no rim — the shape change is unmistakable at ten feet and a scaled
 * chip in a horizontal track shoves its neighbours.
 *
 * [active] draws the §2 tungsten dot beneath the chip: subs on, speed ≠ 1×, quality ≠ Auto. The dot
 * lane is always laid out ([ChipTrackHeight]) so turning a feature on never nudges the track.
 */
@Composable
internal fun OsdChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    glyph: @Composable (Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        targetValue = if (focused) Color.White else Lit.copy(alpha = Alpha.Hairline),
        animationSpec = colorFocusTween(focused),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (focused) Ink000 else Paper.copy(alpha = Alpha.TextTertiary),
        animationSpec = colorFocusTween(focused),
        label = "chipContent",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Row(
            modifier = Modifier
                .height(ChipSize)
                // The clip and the fill sit *outside* animateContentSize so both are painted at the
                // animating width; inside it they would snap straight to the pill's target size.
                .clip(CircleShape)
                .background(container)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .animateContentSize(animationSpec = tween(Dur.FocusIn, easing = Ease.Decel))
                .widthIn(min = ChipSize)
                .padding(horizontal = if (focused) ChipPillPadding else 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(ChipIconSize), contentAlignment = Alignment.Center) {
                glyph(content)
            }
            if (focused) {
                Spacer(modifier = Modifier.width(ChipLabelGap))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Spacer(modifier = Modifier.height(ActiveDotGap))
        Box(
            modifier = Modifier
                .size(ActiveDotSize)
                .clip(CircleShape)
                .background(if (active) Tungsten else Color.Transparent),
        )
    }
}

/** Convenience for the chips whose glyph is a plain [ImageVector]. */
@Composable
internal fun OsdChip(
    label: String,
    imageVector: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    OsdChip(
        label = label,
        active = active,
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) { tint ->
        Icon(
            imageVector = imageVector,
            contentDescription = label,
            modifier = Modifier.size(ChipIconSize),
            tint = tint,
        )
    }
}
