package com.maik205.shoumeiplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Ink100
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.Paper

/** §3.5 — a filter/sort chip. `hasMenu` chips (Genres, Sort) open a dialog rather than toggling. */
@Immutable
data class GenreUi(val id: String, val name: String)

@Immutable
data class ChipUi(val id: String, val label: String, val selected: Boolean, val hasMenu: Boolean = false)

/** §4.1 osd-v3 pill amendment / §3.5 — 36dp tall, 18dp radius, 20dp h-padding, 12dp gap. */
private val PillHeight = 36.dp
private val PillRadius = 18.dp
private val PillHPadding = 20.dp
private val PillShape: Shape = RoundedCornerShape(PillRadius)

/**
 * §3.5 — `LazyRow` of [PillChip]s. `focusRestorer()` returns D-pad focus to the chip you left; the
 * top/bottom content padding keeps the 2dp focus rim from being clipped by the row viewport.
 */
@Composable
fun ChipRow(
    chips: List<ChipUi>,
    onChipClick: (ChipUi) -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = Dimens.OverscanHorizontal,
) {
    val railFocusRequesters = remember(chips.map(ChipUi::id)) {
        List(chips.size) { FocusRequester() }
    }
    val railState = rememberLazyListState()
    LazyRow(
        state = railState,
        modifier = modifier.focusRestorer(),
        contentPadding = PaddingValues(start = startPadding, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(chips, key = { _, chip -> chip.id }) { index, chip ->
            PillChip(
                label = chip.label,
                selected = chip.selected,
                onClick = { onChipClick(chip) },
                trailingCaret = chip.hasMenu,
                modifier = Modifier
                    .horizontalFocusWrap(index, railFocusRequesters, railState)
                    .focusRequester(railFocusRequesters[index]),
            )
        }
    }
}

/**
 * §3.5 / §4.1 — osd-v3 pill amendment. A chip is a full-height control (§4.3 row rule): no scale,
 * no `FocusScale` / `shoumeiFocus`. Focus is carried by the 2dp `Lit` border alone, animated on the
 * §7 focus tween's own duration/easing pair (never the deprecated `borderTween`) — typed for
 * `Color` here since `focusTween` itself returns `TweenSpec<Float>`.
 *
 * States (no new colours, no second accent): resting `Ink100` fill / `Ash600` label; selected
 * `Paper` fill / `Ink000` label; focused `Paper` fill / `Ink000` label + 2dp `Lit` border (selected
 * and focused is identical — the border is the only addition). `Tungsten` never appears here.
 */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingCaret: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }

    // §7 - the one focus tween (Dur.FocusIn/Out, Ease.Decel/Accel), typed for Color: `focusTween`
    // itself is pinned to TweenSpec<Float> for the scale/veil/rim case.
    val colorSpec = tween<Color>(
        durationMillis = if (focused) Dur.FocusIn else Dur.FocusOut,
        easing = if (focused) Ease.Decel else Ease.Accel,
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected || focused) Paper else Ink100,
        animationSpec = colorSpec,
        label = "pillChipContainer",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected || focused) Ink000 else Ash600,
        animationSpec = colorSpec,
        label = "pillChipLabel",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Lit else Color.Transparent,
        animationSpec = colorSpec,
        label = "pillChipBorder",
    )

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .background(color = containerColor, shape = PillShape)
            .border(BorderStroke(2.dp, borderColor), shape = PillShape)
            .heightIn(min = PillHeight)
            .padding(horizontal = PillHPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingCaret) {
            TrailingCaret(color = labelColor)
        }
    }
}

/** §3.5 — 12dp chevron for menu-opening chips (Genres, Sort). */
@Composable
private fun TrailingCaret(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.3f)
            lineTo(w * 0.5f, h * 0.7f)
            lineTo(w * 0.8f, h * 0.3f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}
