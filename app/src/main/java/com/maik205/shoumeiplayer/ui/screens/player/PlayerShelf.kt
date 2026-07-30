package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.components.FocusScale
import com.maik205.shoumeiplayer.ui.components.FocusSurface
import com.maik205.shoumeiplayer.ui.components.MediaCardUi
import com.maik205.shoumeiplayer.ui.components.PosterImage
import com.maik205.shoumeiplayer.ui.components.RowGlowBleed
import com.maik205.shoumeiplayer.ui.components.RowHeader
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Paper

/** §6 amendment — shelf card thumbs take an 8dp radius; person portraits are circular. */
private val ShelfCardRadius = 8.dp

/** Shelf card geometry: a 16:9 thumb on the wide-card width, with a fixed two-line title block. */
private val ShelfCardWidth = Dimens.WideCardWidth
private val ShelfTitleHeight = 44.dp

/** Cast portrait diameter and its own fixed label block. */
private val PortraitSize = 104.dp
private val PortraitLabelHeight = 44.dp

/** The sheet's fill: the video keeps playing behind it, so it is opaque enough to read type on. */
private const val SHEET_FILL_ALPHA = 0.94f

/**
 * Height of the sheet.
 *
 * Two full rows do not fit on the 540dp canvas and were never meant to: the sheet is a *window* onto
 * the rows, and the column inside it scrolls, so focusing the Cast row brings it into view the way a
 * down-shelf does everywhere else. Sizing the sheet to its content instead would leave the video a
 * 20dp letterbox, which defeats the whole "the video keeps playing" idea.
 */
internal val ShelfSheetHeight = 330.dp

/**
 * docs/osd-v3.md §5 — the shelf: "More like this" and "Cast" as a bottom sheet over the plate.
 *
 * It slides up over the OSD's bottom plate while the video keeps playing, which is the whole point
 * of the down-shelf idiom: browsing what is adjacent to this title never costs you the title. Rows
 * are the app's own [RowHeader] plus a `LazyRow`, each carrying [focusRestorer] so LEFT/RIGHT within
 * a row and UP/DOWN between rows both remember where you were (§5).
 *
 * Loading is lazy and is the *screen's* job: the reducer emits `PlayerEffect.LoadShelves` on the
 * DOWN that first opens the shelf, so a user who never presses DOWN never pays for the request.
 */
@Composable
internal fun PlayerShelf(
    similar: List<MediaCardUi>,
    cast: List<CastMemberUi>,
    loading: Boolean,
    onSimilarClick: (MediaCardUi) -> Unit,
    onCastClick: (CastMemberUi) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    similarRowFocus: FocusRequester? = null,
    castRowFocus: FocusRequester? = null,
    onFocusMoved: (FocusTarget) -> Unit = {},
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Dur.OsdIn, easing = Ease.Decel)) +
            slideInVertically(tween(Dur.PanelIn, easing = Ease.Decel)) { it },
        exit = fadeOut(tween(Dur.OsdOut, easing = Ease.Accel)) +
            slideOutVertically(tween(Dur.OsdOut, easing = Ease.Accel)) { it },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(ShelfSheetHeight)
                .background(Ink000.copy(alpha = SHEET_FILL_ALPHA))
                .padding(top = 20.dp, bottom = Dimens.OverscanVertical)
                // Focus inside a scrollable container is brought into view automatically, which is
                // exactly the behaviour rows 2 and 3 need from UP/DOWN.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShelfRow(
                title = "More like this",
                empty = similar.isEmpty(),
                emptyLabel = if (loading) "Looking for something similar" else "Nothing similar here",
                rowFocus = similarRowFocus,
                onFocused = { onFocusMoved(FocusTarget.MoreLikeThisRow) },
            ) {
                items(similar, key = { it.id }) { item ->
                    ShelfCard(item = item, onClick = { onSimilarClick(item) })
                }
            }
            ShelfRow(
                title = "Cast",
                empty = cast.isEmpty(),
                emptyLabel = "No cast listed",
                rowFocus = castRowFocus,
                onFocused = { onFocusMoved(FocusTarget.CastRow) },
            ) {
                items(cast, key = { it.id }) { person ->
                    CastPortrait(person = person, onClick = { onCastClick(person) })
                }
            }
        }
    }
}

@Composable
private fun ShelfRow(
    title: String,
    empty: Boolean,
    emptyLabel: String,
    rowFocus: FocusRequester?,
    onFocused: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    var active by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                active = it.hasFocus
                if (it.hasFocus) onFocused()
            },
    ) {
        RowHeader(title = title, active = active)
        if (empty) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Paper.copy(alpha = Alpha.TextDisabled),
                modifier = Modifier.padding(
                    start = Dimens.OverscanHorizontal,
                    top = 8.dp,
                ),
            )
        } else {
            Spacer(modifier = Modifier.height((Dimens.RowTitleGap - RowGlowBleed).coerceAtLeast(0.dp)))
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer()
                    .then(rowFocus?.let { Modifier.focusRequester(it) } ?: Modifier),
                contentPadding = PaddingValues(
                    start = Dimens.OverscanHorizontal,
                    end = Dimens.OverscanHorizontal,
                    top = RowGlowBleed,
                    bottom = RowGlowBleed,
                ),
                horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
                content = content,
            )
        }
    }
}

/**
 * §5 — a "More like this" card: 16:9 thumb on the §6 amendment's 8dp radius, two-line title below.
 *
 * Deliberately not [com.maik205.shoumeiplayer.ui.components.MediaCard]: that card is the app's 4dp
 * poster/wide card with a one-line title over a subtitle, and the shelf's amendment asks for the 8dp
 * thumb and the two-line title the spec names. Everything else — the [FocusSurface] rim, the veil,
 * the fixed label block so the row never reflows — is the shared treatment.
 */
@Composable
private fun ShelfCard(item: MediaCardUi, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.width(ShelfCardWidth)) {
        FocusSurface(
            onClick = onClick,
            focused = focused,
            onFocusChanged = { focused = it },
            modifier = Modifier.width(ShelfCardWidth),
            scaleTo = FocusScale.Wide,
            shape = RoundedCornerShape(ShelfCardRadius),
            innerHairlineShape = RoundedCornerShape(ShelfCardRadius - 2.dp),
        ) {
            PosterImage(
                url = item.imageUrl,
                contentDescription = item.title,
                blurHash = item.blurHash,
                aspect = 16f / 9f,
                veilAlpha = if (focused) 0f else Alpha.VeilUnfocused,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = Paper.copy(alpha = if (focused) 1f else Alpha.TextTertiary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(ShelfTitleHeight),
        )
    }
}

/**
 * §5 — a Cast portrait: circular (the §6 amendment), name and role below in a fixed label block.
 *
 * CENTER exits the player and hands the person up to the caller; [PlayerScreen] navigates to the
 * person-filtered results. The portrait deliberately carries no play affordance — a person is a
 * query, not something you can press play on.
 */
@Composable
private fun CastPortrait(person: CastMemberUi, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.width(PortraitSize),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FocusSurface(
            onClick = onClick,
            focused = focused,
            onFocusChanged = { focused = it },
            modifier = Modifier.size(PortraitSize),
            scaleTo = FocusScale.Poster,
            shape = CircleShape,
            innerHairlineShape = CircleShape,
        ) {
            PosterImage(
                url = person.imageUrl,
                contentDescription = person.name,
                blurHash = person.blurHash,
                aspect = 1f,
                veilAlpha = if (focused) 0f else Alpha.VeilUnfocused,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.height(PortraitLabelHeight)) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Paper.copy(alpha = if (focused) 1f else Alpha.TextTertiary),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (person.role != null) {
                Text(
                    text = person.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = Paper.copy(alpha = Alpha.TextDisabled),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
