package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val PrototypeEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val PrototypeEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val RevealButtonSize = 22.dp
private val RevealIconSize = 13.dp
private val RevealGap = 4.dp

/**
 * The prototype's compact reveal control: a right-anchored 44 px target whose label grows toward
 * the leading edge. At the TV emulator's 2x density those measurements map to 22/13/4 dp for the
 * target, icon, and focused gap. The label owns its 220 ms width reveal while scale, gap, and
 * opacity use the prototype's shorter transitions, keeping the trailing edge stationary.
 */
@Composable
fun TelevisionFocusRevealButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    expandWhenSelected: Boolean = true,
    focusRequester: FocusRequester? = null,
    expandedWidth: Dp = 66.dp,
    collapsedWidth: Dp = RevealButtonSize,
    buttonHeight: Dp = RevealButtonSize,
    iconSize: Dp = RevealIconSize,
    focusedScale: Float = TelevisionFocusScale.Navigation,
    contentDescription: String = label,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || (selected && expandWhenSelected)
    val emphasized = focused || selected
    val gap by animateDpAsState(
        targetValue = if (expanded) RevealGap else 0.dp,
        animationSpec = tween(durationMillis = 180, easing = PrototypeEaseOut),
        label = "focusRevealGap",
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded) focusedScale else 1f,
        animationSpec = tween(durationMillis = 180, easing = PrototypeEaseOut),
        label = "focusRevealScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (emphasized) 1f else 0.5f,
        animationSpec = tween(durationMillis = 180, easing = PrototypeEase),
        label = "focusRevealAlpha",
    )
    val maxWidth = if (expandedWidth < collapsedWidth) collapsedWidth else expandedWidth

    TelevisionFocusSurface(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        modifier = modifier
            .widthIn(min = collapsedWidth, max = maxWidth)
            .height(buttonHeight)
            .graphicsLayer {
                transformOrigin = TransformOrigin.Center
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        scaleTo = 1f,
        restingAlpha = 1f,
        focusedAlpha = 1f,
        onFocusChanged = {
            focused = it
            onFocusChanged(it)
        },
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(gap))
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally(
                    animationSpec = tween(durationMillis = 220, easing = PrototypeEaseOut),
                    expandFrom = Alignment.Start,
                    clip = true,
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 140, easing = PrototypeEase),
                ),
                exit = shrinkHorizontally(
                    animationSpec = tween(durationMillis = 220, easing = PrototypeEaseOut),
                    shrinkTowards = Alignment.Start,
                    clip = true,
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 140, easing = PrototypeEase),
                ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}
