package com.maik205.shoumeiplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Scrims

/** §5.4 — `TopVignette` covers the top 72dp so content dissolves under a pinned header. */
private val TopVignetteHeight = 72.dp

/** Gap under the screen heading. */
private val TitleGap = 24.dp

/**
 * §1 / §5 — the common screen root: a true-black [Ink000] plane with overscan-safe padding
 * (48 × 27dp, the 864 × 486 safe area at origin (48, 27)).
 *
 * [title] is rendered exactly as authored: a plain sentence-case `displaySmall` heading,
 * flush-left. No uppercase transform, no tracked eyebrow, no rule under it - the name of the
 * screen says what it is (§3.1).
 *
 * Policy note for callers: interactive elements inside a scaffold use `indication = null`
 * (§1 anti-goals — no ripples); focus is carried by the white rim / edge-light, never by a wash.
 */
@Composable
fun ScreenScaffold(
    title: String? = null,
    modifier: Modifier = Modifier,
    topVignette: Boolean = false,
    horizontalPadding: Dp = Dimens.OverscanHorizontal,
    verticalPadding: Dp = Dimens.OverscanVertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink000),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Paper,
                )
                Spacer(modifier = Modifier.height(TitleGap))
            }
            content()
        }
        if (topVignette) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopVignetteHeight)
                    .background(Scrims.TopVignette),
            )
        }
    }
}
