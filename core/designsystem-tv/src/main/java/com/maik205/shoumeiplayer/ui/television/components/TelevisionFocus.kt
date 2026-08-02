package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object TelevisionFocusScale {
    const val Navigation = 1.06f
    const val Action = 1.055f
    const val Landscape = 1.055f
    const val Poster = 1.055f
    const val Square = 1.055f
}

/**
 * Serializes the scroll-and-focus coroutines that D-pad wrapping launches.
 *
 * Holding Left or Right on a remote delivers a KeyDown every few tens of milliseconds. Launching
 * one uncancelled coroutine per event let several scroll-then-focus sequences interleave, and
 * whichever finished last won -- so a held key could land somewhere the viewer never asked for.
 * Cancelling the outstanding move keeps the most recent key press authoritative.
 */
@Stable
class TelevisionFocusMoves internal constructor(private val scope: CoroutineScope) {
    private var move: Job? = null

    internal fun dispatch(block: suspend CoroutineScope.() -> Unit) {
        move?.cancel()
        move = scope.launch(block = block)
    }
}

@Composable
fun rememberTelevisionFocusMoves(): TelevisionFocusMoves {
    val scope = rememberCoroutineScope()
    return remember(scope) { TelevisionFocusMoves(scope) }
}

/**
 * Hands focus to a successor when a transient control disappears.
 *
 * Async screens routinely remove the control the viewer is standing on: a Retry button vanishes
 * the moment the retry starts, a rail is rebuilt by a refresh, an action is disabled while it
 * runs. Compose does not assign a replacement, so the remote goes dead until the viewer guesses
 * a direction that happens to find something.
 *
 * [present] is whether the control is still composed, and [focused] whether it owns focus.
 * Because the removal itself clears [focused], the last value seen while the control was still
 * present is what decides whether a handoff is owed -- a viewer who had already moved elsewhere
 * is left alone. Successors are tried in order and the first one attached to a live node wins.
 */
@Composable
fun TelevisionFocusHandoff(
    present: Boolean,
    focused: Boolean,
    vararg successors: FocusRequester?,
) {
    val heldFocus = remember { mutableStateOf(false) }
    if (present) {
        SideEffect { heldFocus.value = focused }
    }
    val targets = successors.toList()
    LaunchedEffect(present, targets) {
        if (present || !heldFocus.value) return@LaunchedEffect
        heldFocus.value = false
        // The replacement content is composed in the same pass that removed the old control, so
        // wait a frame for its requesters to attach before asking one of them for focus.
        withFrameNanos { }
        targets.firstOrNull { it != null && runCatching { it.requestFocus() }.isSuccess }
    }
}

/**
 * Keeps horizontal D-pad navigation inside its rail. Compose's spatial fallback can otherwise
 * leave the row at either edge and select an unrelated nearby control.
 */
@Composable
fun Modifier.televisionHorizontalWrap(
    index: Int,
    focusRequesters: List<FocusRequester>,
    listState: LazyListState,
): Modifier {
    if (focusRequesters.size < 2 || index !in focusRequesters.indices) return this
    val moves = rememberTelevisionFocusMoves()
    return onPreviewKeyEvent { event ->
        val target = when {
            event.type != KeyEventType.KeyDown -> null
            index == 0 && event.key == Key.DirectionLeft -> focusRequesters.lastIndex
            index == focusRequesters.lastIndex && event.key == Key.DirectionRight -> 0
            else -> null
        } ?: return@onPreviewKeyEvent false

        moves.dispatch {
            listState.scrollToItem(target)
            withFrameNanos { }
            focusRequesters[target].requestFocus()
        }
        true
    }
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
    val colors = TelevisionTheme.colors
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
            !enabled -> colors.PaperDisabled.alpha
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
            contentColor = colors.Paper,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = colors.Paper,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = colors.Paper,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.PaperDisabled,
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
