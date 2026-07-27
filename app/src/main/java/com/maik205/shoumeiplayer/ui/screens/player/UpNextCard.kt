package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.components.FocusScale
import com.maik205.shoumeiplayer.ui.components.FocusSurface
import com.maik205.shoumeiplayer.ui.components.PosterImage
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink150
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.ShoumeiType
import com.maik205.shoumeiplayer.ui.theme.Tungsten

/** §6 amendment — the Up Next card's thumb takes the shelf's 8dp radius. */
private val CardRadius = 8.dp
private val ThumbWidth = 160.dp
private val CardWidth = 360.dp

/** The card's own fill: it is chrome over video, so it needs a plate the plate gradient cannot give. */
private const val CARD_FILL_ALPHA = 0.92f

/**
 * docs/osd-v3.md §5 — the Up Next card: episodes only, last 30 seconds, bottom-right.
 *
 * Next thumbnail, its title, and — only when the user's Jellyfin config has
 * `EnableNextEpisodeAutoPlay` ([UpNextUi.autoPlay]) — a mono countdown. Without that setting the
 * card *offers* and never counts down, because a countdown the app will not act on is a lie about
 * what is going to happen.
 *
 * CENTER plays now; BACK dismisses it permanently for this item. Both are arbitrated by
 * [PlayerScreen] (§1: the card takes DOWN priority from row 1 before the shelf), which is also why
 * [focusRequester] is exposed: the card is composed and dismissed mid-playback, so the screen needs
 * a stable handle rather than a directional search that may find nothing.
 */
@Composable
internal fun UpNextCard(
    upNext: UpNextUi,
    remainingMs: Long,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Dur.OsdIn, easing = Ease.Decel)) +
            slideInVertically(tween(Dur.OsdIn, easing = Ease.Decel)) { it / 4 },
        exit = fadeOut(tween(Dur.OsdOut, easing = Ease.Accel)) +
            slideOutVertically(tween(Dur.OsdOut, easing = Ease.Accel)) { it / 4 },
        modifier = modifier,
    ) {
        FocusSurface(
            onClick = onPlay,
            focused = focused,
            onFocusChanged = {
                focused = it
                if (it) onFocused()
            },
            modifier = Modifier
                .width(CardWidth)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            scaleTo = FocusScale.Slab,
            shape = RoundedCornerShape(CardRadius),
            innerHairlineShape = RoundedCornerShape(CardRadius - 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .background(Ink150.copy(alpha = CARD_FILL_ALPHA))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosterImage(
                    url = upNext.thumbUrl,
                    contentDescription = upNext.title,
                    blurHash = upNext.blurHash,
                    aspect = 16f / 9f,
                    modifier = Modifier
                        .width(ThumbWidth)
                        .clip(RoundedCornerShape(CardRadius - 4.dp)),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Up next",
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.copy(alpha = Alpha.TextTertiary),
                        maxLines = 1,
                    )
                    Text(
                        text = upNext.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Paper,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (upNext.subtitle != null) {
                        Text(
                            text = upNext.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Paper.copy(alpha = Alpha.TextTertiary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (upNext.autoPlay) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            // A countdown is clock-like, so it keeps the mono face (§3).
                            text = "Playing in ${countdownSeconds(remainingMs)}s",
                            style = ShoumeiType.Duration,
                            color = Tungsten,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Whole seconds left, floored at zero and rounded *up* so the card never shows `0s` while the
 * episode is still running: `1200ms` remaining is "2s" the way a person counts down out loud.
 */
internal fun countdownSeconds(remainingMs: Long): Int =
    ((remainingMs + 999L) / 1000L).coerceAtLeast(0L).toInt()
