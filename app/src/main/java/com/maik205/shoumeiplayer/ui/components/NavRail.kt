package com.maik205.shoumeiplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Ink050
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.focusTween

/** §3.1 - the four top-level destinations, in rail order. */
enum class NavRailDestination(val labelRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Filled.Home),
    Search(R.string.nav_search, Icons.Filled.Search),
    Libraries(R.string.nav_libraries, Icons.Filled.VideoLibrary),
    Settings(R.string.nav_settings, Icons.Filled.Settings),
}

/** §3.1 - the rail's icon size, fixed across collapsed and expanded states. */
private val RailIconSize = 24.dp

/** §3.1 - fixed left inset for the icon, so it never shifts as the rail expands. */
private val RailIconInset = 16.dp

/** §4.3 - the edge-light bar width for a focused full-width row. */
private val EdgeLightWidth = 3.dp

/**
 * §3.1 - collapsed 56dp / expanded 220dp left rail. Collapsed: icons only, `@0.55`, and **no
 * painted background at all** — §3.1 asks for "no background beyond [Ink000]", which over the
 * app's [Ink000] surface is visually identical to painting nothing, and painting nothing is what
 * keeps the collapsed rail from occluding content (see below). Expanded (any rail item focused):
 * animates to [Dimens.RailExpandedWidth] over [Dur.RailExpand], fading an opaque [Ink000] panel in
 * over the same spec so the widened rail occludes the content it slides across, with `titleMedium`
 * labels — [Paper] for the selected item, [Ash600] for the rest. A focused item takes the §4.3
 * full-width-row treatment ([Ink050] background plus a [Lit] edge-light bar) and **never scales**.
 *
 * Overlap: [Dimens.RailCollapsedWidth] (56dp) is pinned by §3.1 and exceeds the 48dp
 * [Dimens.OverscanHorizontal] content start by 8dp. The rail is a `zIndex(1f)` overlay, so an
 * opaque collapsed surface would clip that leading 8dp of every screen. The width is contract, and
 * screen anchoring is not this file's to move, so the collapsed surface is transparent and
 * scrim-free instead: only the 24dp icon at a 16dp inset is drawn, spanning 16dp–40dp and clearing
 * the 48dp content edge entirely.
 *
 * Anti-trap: every item routes `right` focus to [contentFocusRequester], the rail itself never
 * takes initial focus (content does — see [NavRailScaffold]), and BACK moves focus to content via
 * a [BackHandler] enabled only while the rail owns focus, so a viewer can never get stuck here.
 * BACK is handled through [BackHandler] rather than `onPreviewKeyEvent { key == Key.Back }`
 * because predictive back on targetSdk 36 dispatches through `OnBackPressedDispatcher` and never
 * delivers a BACK key event to the Compose key pipeline — same reason PlayerScreen.kt:219 refuses
 * BACK in its preview handler and leaves it to its own [BackHandler].
 */
@Composable
fun NavRail(
    selected: NavRailDestination,
    onSelect: (NavRailDestination) -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    railFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    var railHasFocus by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (railHasFocus) Dimens.RailExpandedWidth else Dimens.RailCollapsedWidth,
        animationSpec = tween<Dp>(durationMillis = Dur.RailExpand, easing = Ease.Standard),
        label = "railWidth",
    )
    // §3.1 - transparent while collapsed so the 56dp overlay never paints over the leading 8dp of
    // the 48dp-anchored content; opaque only once the rail widens over that content.
    val railBackgroundAlpha by animateFloatAsState(
        targetValue = if (railHasFocus) 1f else 0f,
        animationSpec = tween(durationMillis = Dur.RailExpand, easing = Ease.Standard),
        label = "railBackgroundAlpha",
    )

    // Anti-trap: BACK hands focus back to content, consumed only while the rail owns focus.
    // Must be a BackHandler — predictive back never reaches onPreviewKeyEvent (see KDoc).
    BackHandler(enabled = railHasFocus) { contentFocusRequester.requestFocus() }

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(Ink000.copy(alpha = railBackgroundAlpha))
            .focusRequester(railFocusRequester)
            .focusGroup()
            .focusRestorer()
            .onFocusChanged { railHasFocus = it.hasFocus },
    ) {
        Spacer(Modifier.height(Dimens.OverscanVertical))
        for (destination in NavRailDestination.entries) {
            NavRailItem(
                destination = destination,
                selected = destination == selected,
                expanded = railHasFocus,
                contentFocusRequester = contentFocusRequester,
                onSelect = onSelect,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavRailItem(
    destination: NavRailDestination,
    selected: Boolean,
    expanded: Boolean,
    contentFocusRequester: FocusRequester,
    onSelect: (NavRailDestination) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    // §7 - the one focus tween; `focusTween` is Float-typed (pinned signature), so the colour
    // animation re-uses its duration and easing rather than a second, divergent spec.
    val focusSpec = focusTween(focused)
    val background by animateColorAsState(
        targetValue = if (focused) Ink050 else Color.Transparent,
        animationSpec = tween(
            durationMillis = focusSpec.durationMillis,
            easing = focusSpec.easing,
        ),
        label = "railItemBackground",
    )
    val edgeLightAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = focusTween(focused),
        label = "railEdgeLight",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else Alpha.TextTertiary,
        animationSpec = tween(durationMillis = Dur.RailExpand, easing = Ease.Standard),
        label = "railItemContentAlpha",
    )
    val contentColor = when {
        focused -> Lit
        selected -> Paper
        else -> Ash600
    }

    Surface(
        onClick = {
            onSelect(destination)
            contentFocusRequester.requestFocus()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.RailItemHeight)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .focusProperties { right = contentFocusRequester },
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = background,
            pressedContainerColor = background,
            contentColor = contentColor,
            focusedContentColor = contentColor,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(EdgeLightWidth)
                    .fillMaxHeight()
                    .background(Lit.copy(alpha = edgeLightAlpha))
                    .zIndex(1f),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = RailIconInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = stringResource(destination.labelRes),
                    tint = contentColor.copy(alpha = contentAlpha),
                    modifier = Modifier.size(RailIconSize),
                )
                if (expanded) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Rail + content frame. [content] gets both requesters so a screen can wire
 * `focusProperties { left = railFocusRequester }` on its leading column and take initial focus on
 * [contentFocusRequester]. The rail overlays the leading edge of [content] rather than displacing
 * it. Screens anchor their leading content at [Dimens.OverscanHorizontal] (48dp), which is 8dp
 * *narrower* than the pinned 56dp [Dimens.RailCollapsedWidth] — so the collapsed rail's footprint
 * does cross into content. [NavRail] resolves that by drawing no background while collapsed: only
 * its 24dp icons paint, and they end at 40dp, clear of the content edge. The opaque panel appears
 * only when the rail expands to 220dp, where occluding the content underneath is intended.
 */
@Composable
fun NavRailScaffold(
    selected: NavRailDestination,
    onSelect: (NavRailDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (contentFocusRequester: FocusRequester, railFocusRequester: FocusRequester) -> Unit,
) {
    val contentFocusRequester = remember { FocusRequester() }
    val railFocusRequester = remember { FocusRequester() }

    Box(modifier = modifier.fillMaxSize()) {
        content(contentFocusRequester, railFocusRequester)
        NavRail(
            selected = selected,
            onSelect = onSelect,
            contentFocusRequester = contentFocusRequester,
            railFocusRequester = railFocusRequester,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .zIndex(1f),
        )
    }
}
