package com.maik205.shoumeiplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.maik205.shoumeiplayer.ui.theme.LocalShoumeiMotion
import com.maik205.shoumeiplayer.ui.theme.Tungsten
import com.maik205.shoumeiplayer.ui.theme.focusTween
import kotlinx.coroutines.launch

/**
 * §4.2 — the focus scale per surface class. A grid tile at 1.08 collides with its neighbours, a
 * full-width row never scales at all (§4.2), so the value is always chosen by the call site.
 */
object FocusScale {
    /** Poster card in a row. */
    const val Poster = 1.08f

    /** Wide card (My Media, Next Up) and episode thumbs. */
    const val Wide = 1.06f

    /** Library / Search grid tile. */
    const val GridTile = 1.04f

    /** Slab button. */
    const val Slab = 1.03f
}

@Composable
fun Modifier.horizontalFocusWrap(
    index: Int,
    focusRequesters: List<FocusRequester>,
    listState: LazyListState,
): Modifier {
    if (focusRequesters.size < 2 || index !in focusRequesters.indices) return this
    val scope = rememberCoroutineScope()
    return onPreviewKeyEvent { event ->
        val target = when {
            event.type != KeyEventType.KeyDown -> null
            index == 0 && event.key == Key.DirectionLeft -> focusRequesters.lastIndex
            index == focusRequesters.lastIndex && event.key == Key.DirectionRight -> 0
            else -> null
        } ?: return@onPreviewKeyEvent false

        scope.launch {
            listState.scrollToItem(target)
            withFrameNanos { }
            focusRequesters[target].requestFocus()
        }
        true
    }
}

/** §4.2 (c) — the inner hairline that keeps the white rim readable against pale artwork. */
private const val INNER_HAIRLINE_ALPHA = 0.55f

/**
 * §4.2 — the geometry half of the focus treatment, shared by every focusable card.
 *
 * `zIndex(1f)` while focused, or the *next* card in the row paints over the scaled edge; the scale
 * runs from [TransformOrigin] `(0.5, 0.62)` so the card grows **upward** with its feet roughly
 * aligned to the resting baseline instead of pushing into the row below.
 *
 * The scale is gated behind `LocalShoumeiMotion` (§7) — with it off the 2dp white rim alone must
 * still satisfy "focus is always visible".
 */
@Composable
fun Modifier.shoumeiFocus(
    focused: Boolean,
    scaleTo: Float = FocusScale.Poster,
    label: String = "shoumeiFocusScale",
): Modifier {
    val motion = LocalShoumeiMotion.current
    val target = if (focused) motion.scale(scaleTo) else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = focusTween(focused),
        label = label,
    )
    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0.62f)
            scaleX = scale
            scaleY = scale
        }
}

/**
 * §4.2 — the canonical card container: transparent surface, **2dp `#FFFFFF` rim** on focus (the
 * non-negotiable signal) and a 1dp `Black @0.55` inner hairline just inside it, which is what keeps
 * that rim readable against pale artwork. There is no glow: a white spot shadow on black is near
 * invisible on most panels and cost a fourth tween (§7 flag 2).
 *
 * The surface's own `scale` is pinned to 1f: tv-material3 animates it in an internal
 * `graphicsLayer` whose transform origin we cannot reach, so [shoumeiFocus] does the scaling
 * instead. Veil and content live in [content], and scale, veil and rim all ride the one
 * [focusTween] so the whole signal lands at once (§7).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scaleTo: Float = FocusScale.Poster,
    shape: Shape = MaterialTheme.shapes.small,
    // Not a second radius (§4.1): the hairline sits 2dp inside the 4dp rim, so 2dp is the corner
    // that stays concentric with it.
    innerHairlineShape: Shape = RoundedCornerShape(2.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val hairline by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = focusTween(focused),
        label = "innerHairline",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .shoumeiFocus(focused = focused, scaleTo = scaleTo)
            .onFocusChanged { onFocusChanged(it.isFocused || it.hasFocus) },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            // Literal white — focus must never inherit the muted `colorScheme.border` token (§2.2).
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
            pressedBorder = Border(BorderStroke(2.dp, Tungsten), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        // §7 flag 2 — no glow anywhere: the rim plus the inner hairline is the real signal.
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
    ) {
        content()
        if (hairline > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = INNER_HAIRLINE_ALPHA * hairline),
                        shape = innerHairlineShape,
                    ),
            )
        }
    }
}
