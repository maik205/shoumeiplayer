package com.maik205.shoumeiplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Ink300
import com.maik205.shoumeiplayer.ui.theme.LocalShoumeiMotion
import com.maik205.shoumeiplayer.ui.theme.Paper

/** §4.2 — slab geometry. No pills: a button is a 4dp-radius slab, 52dp tall, 28dp h-padding. */
private val SlabHeight = 52.dp
private val SlabHorizontalPadding = 28.dp

/** Secondary focused fill — `#FFFFFF @0.06` (§4.2). */
private const val SECONDARY_FOCUSED_FILL_ALPHA = 0.06f

/**
 * §4.2 — the only button shape in the app.
 *
 * Primary: [Paper] fill, black label. Secondary: transparent with a `1.dp` [Ink300] hairline.
 * Both take a 2dp white border on focus. The label is sentence-case `titleSmall`, rendered exactly
 * as authored - no uppercase transform, no tracking (§3.1). Scale is 1.03 and is gated behind
 * `LocalShoumeiMotion` (§7): a slab is not a card and must not lift as far.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SlabButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    val motion = LocalShoumeiMotion.current
    val shape = MaterialTheme.shapes.small
    val focusedScale = if (motion.focusScaleEnabled) FocusScale.Slab else 1f

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(SlabHeight)
            .defaultMinSize(minWidth = 120.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Paper else Color.Transparent,
            contentColor = if (primary) Ink000 else Paper,
            focusedContainerColor = if (primary) {
                Color.White
            } else {
                Color.White.copy(alpha = SECONDARY_FOCUSED_FILL_ALPHA)
            },
            focusedContentColor = if (primary) Ink000 else Color.White,
            pressedContainerColor = if (primary) Color.White else Color.Transparent,
            pressedContentColor = if (primary) Ink000 else Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (primary) {
                Border.None
            } else {
                Border(BorderStroke(1.dp, Ink300), shape = shape)
            },
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = focusedScale),
        // §7 flag 2 - a white glow on black is near invisible; the rim is the real signal.
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = SlabHorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
