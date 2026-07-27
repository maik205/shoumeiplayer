package com.maik205.shoumeiplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maik205.shoumeiplayer.ui.theme.Dimens

/**
 * §4.2 — the cross-axis bleed a focus-scaled card needs inside the row's own viewport. A `LazyRow`
 * clips to its bounds, so without this the 1.08 scale and its 2dp rim are sliced top and bottom.
 */
val RowGlowBleed = 14.dp

/**
 * §5.1 — the fifth poster deliberately clips at the trailing edge to signal scrollability without
 * a chevron, so the end padding is *less* than overscan by design.
 */
private val RowEndPadding = 24.dp

/**
 * §5.1 — a titled horizontal row of [MediaCard]s under the shared plain-sentence [RowHeader].
 *
 * Focus plumbing (M6.3): `focusRestorer()` returns D-pad focus to the card you left, the
 * `contentPadding` gives the scale and glow room to bleed, and the row `Column` is deliberately
 * **not** clipped. §7 flag 3 — `BringIntoViewSpec` is experimental foundation API that has moved
 * between versions, so the §6 row snap uses the sanctioned `animateScrollToItem(index, 0)`
 * fallback: the focused card lands at the row's leading edge (x=48), never centred.
 */
@Composable
fun MediaRow(
    title: String,
    items: List<MediaCardUi>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onItemFocused: (MediaCardUi) -> Unit = {},
    startPadding: Dp = Dimens.OverscanHorizontal,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val listState = rememberLazyListState()
    var rowActive by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            listState.animateScrollToItem(index = focusedIndex, scrollOffset = 0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { rowActive = it.hasFocus },
    ) {
        RowHeader(
            title = title,
            active = rowActive,
            startPadding = startPadding,
        )
        // RowTitleGap is measured to the art; the LazyRow already contributes RowGlowBleed on top.
        Spacer(modifier = Modifier.height((Dimens.RowTitleGap - RowGlowBleed).coerceAtLeast(0.dp)))
        LazyRow(
            state = listState,
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(
                start = startPadding,
                end = RowEndPadding,
                top = RowGlowBleed,
                bottom = RowGlowBleed,
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                MediaCard(
                    item = item,
                    onClick = { onItemClick(item.id) },
                    focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                    onFocusChanged = { focused ->
                        if (focused) {
                            focusedIndex = index
                            onItemFocused(item)
                        }
                    },
                )
            }
        }
    }
}
