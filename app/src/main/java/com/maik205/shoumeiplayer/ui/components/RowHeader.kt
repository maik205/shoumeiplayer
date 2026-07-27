package com.maik205.shoumeiplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.focusTween

/** §5 - the row-header band is 32dp tall. */
val RowHeaderHeight = 32.dp

/**
 * §5 - **one `Text`, nothing else.** The shared heading for Home rows, Detail sections, Library
 * groups and Settings groups: sentence-case `titleMedium`, `@0.55` at rest and `@1.0` when the row
 * owns focus, so the heading is what tells you which row your D-pad is in.
 *
 * A row is titled the way a person would say it. No uppercase, no tracking, no rule, no count, no
 * folio: hierarchy comes from size and weight, and the content below already says how much of it
 * there is.
 */
@Composable
fun RowHeader(
    title: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    startPadding: Dp = Dimens.OverscanHorizontal,
    endPadding: Dp = Dimens.OverscanHorizontal,
) {
    val titleAlpha by animateFloatAsState(
        targetValue = if (active) 1f else Alpha.TextTertiary,
        animationSpec = focusTween(active),
        label = "rowHeaderTitle",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeaderHeight)
            .padding(start = startPadding, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Paper.copy(alpha = titleAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
