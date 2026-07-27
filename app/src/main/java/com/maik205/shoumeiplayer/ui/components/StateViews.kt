package com.maik205.shoumeiplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Ink100
import com.maik205.shoumeiplayer.ui.theme.Paper

/**
 * §6 — the three states every screen ships. None of them is a centred spinner and none of them is
 * a single word.
 *
 * **Loading is a skeleton of the content**: [Ink100] blocks at the app's one 4dp radius, laid out
 * at the real content's dimensions so the swap does not jump. They are static: no shimmer, no
 * pulse, nothing looping (§7). **Empty is composed and actionable**: a sentence, an optional
 * second line, and at most one slab. **Error is inline and plain**: a sentence in ordinary
 * language and one `Try again` slab, never a dialog and never a toast.
 *
 * Positioning is the caller's job — every screen already knows where its states belong (§5.6 puts
 * them at x=48, y=220), so [startPadding] / [topPadding] default to zero and never double up on a
 * `Modifier.padding(...)` supplied by the screen.
 */
private val SkeletonShape = RoundedCornerShape(4.dp)

/** How many tiles a skeleton row and grid draw: enough to fill the visible band, no more. */
private const val SKELETON_ROW_TILES = 5
private const val SKELETON_GRID_TILES = 10
private const val SKELETON_GRID_COLUMNS = 5
private const val SKELETON_LEDGER_ROWS = 6

/** Settings row height (§5.7) and the ledger label width the skeleton stands in for. */
private val LedgerRowHeight = 72.dp
private val LedgerLabelWidth = 220.dp

/** The metadata bar runs half the copy column; the last overview line runs two thirds of it. */
private const val BODY_HALF = 0.5f
private const val BODY_SHORT = 0.66f

/** Detail copy column bars (§5.2): title, metadata line, three lines of overview. */
private val DetailTitleHeight = 56.dp
private val DetailMetaHeight = 20.dp
private val DetailBodyHeight = 16.dp
private const val DETAIL_BODY_LINES = 3

/** One skeleton block: flat [Ink100], 4dp radius, static. */
@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(modifier = modifier.background(Ink100, SkeletonShape))
}

/**
 * §6 — the loading state for a poster row: five tiles at the real card size plus the fixed label
 * block, so the row does not reflow when the content arrives.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    startPadding: Dp = Dimens.OverscanHorizontal,
    tiles: Int = SKELETON_ROW_TILES,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
    ) {
        repeat(tiles) {
            Column {
                SkeletonBlock(
                    modifier = Modifier.size(Dimens.CardWidth, Dimens.CardHeight),
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBlock(
                    modifier = Modifier
                        .width(Dimens.CardWidth)
                        .height(Dimens.PosterLabelHeight - 8.dp),
                )
            }
        }
    }
}

/** §6 — the loading state for the Library and Search grids: ten tiles on the `Fixed(5)` rhythm. */
@Composable
fun SkeletonGrid(
    modifier: Modifier = Modifier,
    startPadding: Dp = Dimens.OverscanHorizontal,
    tiles: Int = SKELETON_GRID_TILES,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GridVSpacing),
    ) {
        val rows = (tiles + SKELETON_GRID_COLUMNS - 1) / SKELETON_GRID_COLUMNS
        repeat(rows) { rowIndex ->
            val remaining = tiles - rowIndex * SKELETON_GRID_COLUMNS
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.GridHSpacing)) {
                repeat(minOf(SKELETON_GRID_COLUMNS, remaining)) {
                    Column {
                        SkeletonBlock(
                            modifier = Modifier.size(Dimens.CardWidth, Dimens.CardHeight),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SkeletonBlock(
                            modifier = Modifier
                                .width(Dimens.CardWidth)
                                .height(Dimens.PosterLabelHeight - 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** §6 — the loading state for the Settings list: six full-width rows at the real 72dp pitch. */
@Composable
fun SkeletonLedger(
    modifier: Modifier = Modifier,
    startPadding: Dp = Dimens.OverscanHorizontal,
    rows: Int = SKELETON_LEDGER_ROWS,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = Dimens.OverscanHorizontal),
    ) {
        repeat(rows) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LedgerRowHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(LedgerLabelWidth)
                        .height(DetailMetaHeight),
                )
            }
        }
    }
}

/** §6 — the loading state for Detail: the title bar, the metadata bar, three lines of overview. */
@Composable
fun SkeletonDetail(
    modifier: Modifier = Modifier,
    startPadding: Dp = Dimens.OverscanHorizontal,
) {
    Column(
        modifier = modifier.padding(start = startPadding),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .width(Dimens.BodyMaxWidth)
                .height(DetailTitleHeight),
        )
        Spacer(modifier = Modifier.height(24.dp))
        SkeletonBlock(
            modifier = Modifier
                .width(Dimens.BodyMaxWidth * BODY_HALF)
                .height(DetailMetaHeight),
        )
        Spacer(modifier = Modifier.height(32.dp))
        repeat(DETAIL_BODY_LINES) { line ->
            val lastLine = line == DETAIL_BODY_LINES - 1
            SkeletonBlock(
                modifier = Modifier
                    .width(if (lastLine) Dimens.BodyMaxWidth * BODY_SHORT else Dimens.BodyMaxWidth)
                    .height(DetailBodyHeight),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * §6 — the generic loading state: a skeleton of a content row. Screens that know their own shape
 * should call [SkeletonGrid], [SkeletonLedger] or [SkeletonDetail] instead.
 */
@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    startPadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
) {
    SkeletonRow(
        modifier = modifier.padding(top = topPadding),
        startPadding = startPadding,
    )
}

/** §6 — a plain sentence, then one white slab. No icon, no dialog, no toast. */
@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = stringResource(R.string.retry),
    detail: String? = null,
    startPadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
) {
    StateColumn(modifier = modifier, startPadding = startPadding, topPadding = topPadding) {
        Text(
            text = message,
            style = MaterialTheme.typography.displaySmall,
            color = Paper,
            modifier = Modifier.widthIn(max = Dimens.BodyMaxWidth),
        )
        if (!detail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Paper.copy(alpha = Alpha.TextTertiary),
                modifier = Modifier.widthIn(max = Dimens.BodyMaxWidth),
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        SlabButton(text = retryLabel, onClick = onRetry)
    }
}

/**
 * §6 — empty is composed and actionable: a sentence, an optional second line, and at most one
 * slab. Pass [actionLabel] and [onAction] together, or neither.
 */
@Composable
fun EmptyView(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    startPadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
) {
    StateColumn(modifier = modifier, startPadding = startPadding, topPadding = topPadding) {
        Text(
            text = message,
            style = MaterialTheme.typography.displaySmall,
            color = Paper.copy(alpha = Alpha.TextTertiary),
            modifier = Modifier.widthIn(max = Dimens.BodyMaxWidth),
        )
        if (!detail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Paper.copy(alpha = Alpha.TextTertiary),
                modifier = Modifier.widthIn(max = Dimens.BodyMaxWidth),
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(28.dp))
            SlabButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun StateColumn(
    modifier: Modifier,
    startPadding: Dp,
    topPadding: Dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding, top = topPadding, end = Dimens.OverscanHorizontal),
        horizontalAlignment = Alignment.Start,
    ) {
        content()
    }
}
