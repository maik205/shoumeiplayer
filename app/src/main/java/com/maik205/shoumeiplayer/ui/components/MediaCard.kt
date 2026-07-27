package com.maik205.shoumeiplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Scrims
import com.maik205.shoumeiplayer.ui.theme.Tungsten
import com.maik205.shoumeiplayer.ui.theme.focusTween

/** UI-ready data for a single media card. */
data class MediaCardUi(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val progressFraction: Float?,
    val wide: Boolean = false,
    /** §5.1 — the focused card's own backdrop washes the Home screen. */
    val backdropUrl: String? = null,
    /** §5.1 — My Media sets the library name *inside* the art over [Scrims.CardFoot]. */
    val labelInsideArt: Boolean = false,
    /** §5.4 — watched art dims to `0.55` and takes a white check. */
    val watched: Boolean = false,
    /** §5 — `ImageBlurHashes` for whichever tag won [imageUrl]'s fallback chain; art fades up from colour. */
    val blurHash: String? = null,
    /** `PrimaryImageAspectRatio`. Poster cards honour it (clamped); wide cards keep 280×158. */
    val aspect: Float? = null,
    /** §5.1 — the first `Taglines` entry, shown in the Home ambient header when the server has one. */
    val tagline: String? = null,
)

/**
 * §5.1 — a server may report any `PrimaryImageAspectRatio`; clamping keeps one rogue value from
 * collapsing or stretching the whole row, whose height is the tallest card in it.
 */
private const val MIN_POSTER_ASPECT = 0.5f
private const val MAX_POSTER_ASPECT = 2.0f

/** §5.1 — the progress overlay is a light leak, not a widget. */
private val ProgressBarHeight = 3.dp

/** §5.1 — gap between the art and the title below it. */
private val LabelGap = 8.dp

/**
 * §5.1 / §4.1 — a poster (160×240) or wide (280×158) card.
 *
 * Four simultaneous focus signals, each on its own tween: the veil lifts (160ms), the 2dp white rim
 * and its inner dark hairline fade in (120ms), the card scales (140/100ms) upward from
 * `TransformOrigin(0.5, 0.62)`, and the title goes `@0.55 → @1.0`. The title lives **below** the
 * art in a fixed-height label block so the row never reflows when a subtitle is missing.
 *
 * §7 flag 4 — the progress bar is a hand-drawn [Box], never material3's `LinearProgressIndicator`
 * (which draws a track gap and a stop indicator by default).
 */
@Composable
fun MediaCard(
    item: MediaCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    focusScale: Float = if (item.wide) FocusScale.Wide else FocusScale.Poster,
    /** M6.3 — lets a screen park initial focus on a specific card. */
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }

    val width = if (item.wide) Dimens.WideCardWidth else Dimens.CardWidth
    val labelHeight = if (item.wide) Dimens.WideLabelHeight else Dimens.PosterLabelHeight
    val aspect = if (item.wide) {
        Dimens.WideCardWidth.value / Dimens.WideCardHeight.value
    } else {
        item.aspect?.coerceIn(MIN_POSTER_ASPECT, MAX_POSTER_ASPECT)
            ?: (Dimens.CardWidth.value / Dimens.CardHeight.value)
    }

    val veil by animateFloatAsState(
        targetValue = if (focused) 0f else Alpha.VeilUnfocused,
        animationSpec = focusTween(focused),
        label = "cardVeil",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else Alpha.TextTertiary,
        animationSpec = focusTween(focused),
        label = "cardTitleAlpha",
    )
    // §5.2 — watched art restores full alpha on focus over the veil duration (180ms band).
    val artAlpha by animateFloatAsState(
        targetValue = if (item.watched && !focused) Alpha.Watched else 1f,
        animationSpec = focusTween(focused),
        label = "cardArtAlpha",
    )
    // §6 — linear, because eased playback progress reads as a lie.
    val progress by animateFloatAsState(
        targetValue = item.progressFraction?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(Dur.ProgressValue, easing = LinearEasing),
        label = "cardProgress",
    )

    Column(modifier = modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            focused = focused,
            onFocusChanged = {
                focused = it
                onFocusChanged(it)
            },
            modifier = Modifier
                .width(width)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            scaleTo = focusScale,
        ) {
            PosterImage(
                url = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth(),
                aspect = aspect,
                veilAlpha = veil,
                contentAlpha = artAlpha,
                blurHash = item.blurHash,
            )

            if (item.labelInsideArt) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Scrims.CardFoot),
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }

            if (item.watched) {
                // §5.4 — a white check, never a coloured dot.
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }

            if (item.progressFraction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(ProgressBarHeight)
                        .background(Color.White.copy(alpha = Alpha.TrackInactive)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Tungsten),
                    )
                }
            }
        }

        if (!item.labelInsideArt) {
            Column(
                modifier = Modifier
                    .padding(top = LabelGap)
                    .height(labelHeight)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Paper.copy(alpha = titleAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
