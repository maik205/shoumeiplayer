package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions

object TelevisionFocusScale {
    const val Navigation = 1.06f
    const val Action = 1.055f
    const val Landscape = 1.055f
    const val Poster = 1.055f
    const val Square = 1.055f
}

/**
 * A remote-native focus target with no panel, border, glow, or elevation. Focus is carried by
 * contrast and a restrained scale so artwork remains the visual surface.
 */
@Composable
fun TelevisionFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    scaleTo: Float = TelevisionFocusScale.Action,
    restingAlpha: Float = 0.64f,
    focusedAlpha: Float = 1f,
    focusedTranslationY: Dp = 0.dp,
    focusAnimationMillis: Int = 120,
    onFocusChanged: (Boolean) -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) scaleTo else 1f,
        animationSpec = tween(durationMillis = focusAnimationMillis),
        label = "televisionFocusScale",
    )
    val translationY by animateFloatAsState(
        targetValue = if (focused && enabled) {
            with(density) { focusedTranslationY.toPx() }
        } else {
            0f
        },
        animationSpec = tween(durationMillis = focusAnimationMillis),
        label = "televisionFocusTranslation",
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> TelevisionColors.PaperDisabled.alpha
            focused -> focusedAlpha
            else -> restingAlpha
        },
        animationSpec = tween(durationMillis = if (focused) 100 else 80),
        label = "televisionFocusAlpha",
    )

    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(requesterModifier)
            .focusProperties { canFocus = enabled }
            .onFocusChanged { state ->
                val hasFocus = state.isFocused || state.hasFocus
                if (focused != hasFocus) {
                    focused = hasFocus
                    onFocusChanged(hasFocus)
                }
            }
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                transformOrigin = TransformOrigin.Center
                scaleX = scale
                scaleY = scale
                this.translationY = translationY
                this.alpha = alpha
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(TelevisionDimensions.FocusRadius)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TelevisionColors.Paper,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = TelevisionColors.Paper,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = TelevisionColors.Paper,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TelevisionColors.PaperDisabled,
        ),
        scale = ClickableSurfaceDefaults.scale(
            scale = 1f,
            focusedScale = 1f,
            pressedScale = 1f,
            disabledScale = 1f,
            focusedDisabledScale = 1f,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
            disabledBorder = Border.None,
            focusedDisabledBorder = Border.None,
        ),
        glow = ClickableSurfaceDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow.None,
            pressedGlow = Glow.None,
        ),
    ) {
        content(focused)
    }
}
