package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Ink150
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Tungsten

/** §5.3 — the panel is right-anchored and 360dp wide. */
private val PanelWidth = 360.dp

/** §6 amendment — panel list items are 42dp pills on a 21dp radius, matching the chip that opened them. */
private val PanelRowHeight = 42.dp
private val PanelRowRadius = 21.dp
private val PanelRowSpacing = 8.dp

/** §5.3 — the selected row is prefixed with a 6dp tungsten square, not a check. */
private val MarkerSquare = 6.dp

/** Inset from the pill's leading edge to its label. */
private val RowStartPadding = 18.dp

/** Horizontal margin between the pills and the panel's own edges. */
private val PanelInset = 20.dp

/** §5.3 — the panel fill. */
private const val PANEL_FILL_ALPHA = 0.96f

/**
 * One row of an OSD panel: subtitle track, audio track, speed rung or quality rung.
 *
 * [detail] is the trailing, quieter half of a row — `1080p` next to `4K`, `20 Mbps` next to a rung —
 * and is omitted when a row has nothing to add.
 */
@Immutable
data class OsdPanelItem(
    /** Stable identity for the list, and what [OsdPanelSheet] hands back on select. */
    val key: String,
    val label: String,
    val detail: String? = null,
    val selected: Boolean = false,
)

/**
 * docs/osd-v3.md §2 / §5 — the right-side panel a track chip opens, in the pill idiom.
 *
 * This is the v2 track panel restyled, not replaced: still right-anchored, 360dp, full height minus
 * overscan, [Ink150] at 96% with a `1.dp #FFFFFF @8%` left hairline, [RectangleShape] because it is
 * flush to the screen edge, entering with the one motivated slide (220ms, from the edge it lives
 * on). What changed is the row: a 56dp full-width row with an edge-light bar became a 42dp **pill**
 * (§6 amendment), because the panel is now the chip's own surface and a chip that expanded into a
 * white pill must not open a list of square rows. Focus is therefore the chip's treatment too —
 * white fill, [Ink000] content — rather than §4.3's edge light, which belongs to full-width rows.
 *
 * Selection stays the 6dp tungsten square: it is real playback state, and ui-design §1 reserves the
 * accent for exactly that.
 *
 * The sheet is *inline*, not a `Dialog`: [PlayerOsd] hosts it so the OSD keeps one focus tree and
 * BACK is arbitrated by [PlayerScreen]'s row state machine (§1). [TrackDialog] is the dialog-hosted
 * wrapper for callers that still want the platform scrim and system Back.
 */
@Composable
fun OsdPanelSheet(
    title: String,
    items: List<OsdPanelItem>,
    onSelect: (OsdPanelItem) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    // The selected row owns focus on arrival, so the D-pad starts from where the user already is.
    val selectedFocus = remember { FocusRequester() }
    val selectedKey = items.firstOrNull { it.selected }?.key
    LaunchedEffect(title, selectedKey) {
        if (visible) runCatching { selectedFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(Dur.PanelIn, easing = Ease.Decel)) { it },
        exit = slideOutHorizontally(tween(Dur.OsdOut, easing = Ease.Accel)) { it },
        modifier = modifier,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .width(PanelWidth)
                    .fillMaxHeight()
                    .background(Ink150.copy(alpha = PANEL_FILL_ALPHA), RectangleShape)
                    .padding(vertical = Dimens.OverscanVertical),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = PanelInset + RowStartPadding,
                        end = PanelInset,
                        bottom = 16.dp,
                    ),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRestorer()
                        .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                    contentPadding = PaddingValues(
                        horizontal = PanelInset,
                        vertical = 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(PanelRowSpacing),
                ) {
                    items(items, key = { it.key }) { item ->
                        OsdPanelRow(
                            item = item,
                            onClick = { onSelect(item) },
                            modifier = if (item.key == selectedKey) {
                                Modifier.focusRequester(selectedFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
            // §4.3 — the panel's left hairline, drawn over the fill so it stays exactly 1dp.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = Alpha.Hairline)),
            )
        }
    }
}

@Composable
private fun OsdPanelRow(
    item: OsdPanelItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val container = if (focused) Color.White else Lit.copy(alpha = Alpha.Hairline)
    val content = if (focused) Ink000 else Paper

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PanelRowHeight)
            .clip(RoundedCornerShape(PanelRowRadius))
            .background(container)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = RowStartPadding, end = RowStartPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.selected) {
            Box(modifier = Modifier.size(MarkerSquare).background(Tungsten))
        } else {
            // Reserve the marker's width so selecting a row never shifts the labels.
            Spacer(modifier = Modifier.size(MarkerSquare))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (item.detail != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (focused) Ink000.copy(alpha = 0.7f) else Paper.copy(alpha = Alpha.TextTertiary),
                maxLines = 1,
            )
        }
    }
}

/**
 * The dialog-hosted track panel, kept for callers outside the OSD's own focus tree.
 *
 * Identical surface to [OsdPanelSheet] — same pills, same tungsten square — wrapped in a [Dialog] so
 * the platform scrim and system Back keep working. Inside the OSD, prefer [OsdPanelSheet]: hosting
 * the panel in the same tree is what lets §1's BACK ladder (panel → shelf → scrub → OSD → exit) be
 * one state machine instead of two.
 */
@Composable
fun TrackDialog(
    title: String,
    tracks: List<PlayerTrack>,
    onSelect: (PlayerTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    // The panel is composed already-open, so the slide needs a transition state that starts false
    // and flips on the first frame; a plain `visible = true` would snap it into place.
    val slideIn = remember { MutableTransitionState(false) }
    slideIn.targetState = true

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OsdPanelSheet(
                title = title,
                items = tracks.map { it.toPanelItem() },
                onSelect = { item ->
                    tracks.firstOrNull { it.panelKey == item.key }?.let(onSelect)
                    onDismiss()
                },
                visible = slideIn.currentState || slideIn.targetState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Panel identity for a track: the engine's own id, which is unique within a track list. */
internal val PlayerTrack.panelKey: String get() = "${type.name}:$id"

internal fun PlayerTrack.toPanelItem(): OsdPanelItem =
    OsdPanelItem(key = panelKey, label = label, selected = selected)

/**
 * Ensures a subtitle list always carries an explicit "Off" row (`id = -1`), so subtitles can be
 * turned off from the panel even when the engine's track list does not supply one.
 *
 * Named for what it is rather than reusing [PlayerScreen]'s private helper: the panel now lives in
 * the OSD, and the screen's copy can go once it stops opening dialogs itself.
 */
internal fun subtitleTracksWithOff(tracks: List<PlayerTrack>, offLabel: String): List<PlayerTrack> {
    if (tracks.any { it.id == -1 }) return tracks
    val noneSelected = tracks.none { it.selected }
    return tracks + PlayerTrack(
        id = -1,
        type = TrackType.SUBTITLE,
        label = offLabel,
        selected = noneSelected,
    )
}
