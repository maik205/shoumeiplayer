package com.maik205.shoumeiplayer.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.blurHash
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Ink100
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.LocalShoumeiMotion
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Tungsten
import com.maik205.shoumeiplayer.ui.theme.focusTween
import com.maik205.shoumeiplayer.ui.components.PosterImage

/**
 * §5.4 / §5.6 — the single `Fixed(5)` poster grid shared by Library and Search, plus the small
 * typographic primitives (text chips, hairlines, check marks) those two screens and Detail draw
 * from.
 *
 * Focus here is **1.04**, not 1.08: §4.2 — 1.08 collides visually in a grid.
 *
 * §5.4 — no periodic rules and no position markers in the grid: the content conveys scroll
 * position, so the v1 folio rule every 25 tiles is gone.
 */

/** §5.4 — the intended rhythm; `GridCells.Adaptive(160.dp)` is the sanctioned fallback. */
const val GRID_COLUMNS = 5

/** §4.2 (d) — grid tiles scale 1.04. */
private const val GRID_FOCUS_SCALE = 1.04f

/** Poster maxWidth px for Coil (§5): poster 320. */
private const val POSTER_IMAGE_WIDTH = 320

/** §5.6 — an episode hit in Search is drawn from its thumb; same 320 budget as the poster. */
private const val EPISODE_THUMB_IMAGE_WIDTH = 320

/** §5.4 — the tile box is fixed, so a server-reported ratio outside this band is not honoured. */
private const val MIN_ASPECT = 0.5f
private const val MAX_ASPECT = 2.0f

/** The grid's own frame: 160×240 = 2:3. */
private val DEFAULT_TILE_ASPECT = Dimens.CardWidth / Dimens.CardHeight

/** A single tile in the Library / Search contact sheet. */
data class GridTileUi(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val progressFraction: Float?,
    val watched: Boolean,
    /** §5.6 — `Film` / `Series` / `Episode` above the title; mixed-type results are otherwise ambiguous. */
    val typeLabel: String? = null,
    /** §5 — `ImageBlurHashes` for whichever art link won; the art fades up from colour, not black. */
    val blurHash: String? = null,
    /** `PrimaryImageAspectRatio`, clamped. `null` keeps the 2:3 frame. */
    val aspect: Float? = null,
)

/**
 * Maps a Jellyfin item to a grid tile. [typeLabel] is set by Search only.
 *
 * [preferEpisodeThumb] is §5.6: an `Episode` hit is unrecognisable as a series poster, so Search
 * asks for its thumb instead. The tile frame stays 2:3 either way — the thumb crops into it.
 */
fun BaseItemDto.toGridTile(
    images: ImageUrlBuilder,
    typeLabel: String? = null,
    preferEpisodeThumb: Boolean = false,
): GridTileUi {
    val thumbUrl = if (preferEpisodeThumb && type == "Episode") {
        images.thumbWithSeriesFallback(this, EPISODE_THUMB_IMAGE_WIDTH)
    } else {
        null
    }
    val posterUrl = images.primaryWithParentFallback(this, POSTER_IMAGE_WIDTH)
    return GridTileUi(
        id = id,
        title = name.orEmpty(),
        subtitle = when (type) {
            "Episode" -> "S${parentIndexNumber ?: 0}:E${indexNumber ?: 0}"
            else -> productionYear?.toString()
        },
        imageUrl = thumbUrl ?: posterUrl,
        progressFraction = userData?.playedPercentage
            ?.toFloat()
            ?.takeIf { it > 0f && it < 100f }
            ?.div(100f),
        watched = userData?.played == true,
        typeLabel = typeLabel,
        // The hash is looked up under the type that won, keyed by *our own* tag. When the chain fell
        // through to a series/parent link our own tag is null, and `blurHash(type, null)` takes the
        // sole entry the server published for that type — which is the parent's hash, because
        // `imageTypeLimit=1` on every grid call means these maps hold exactly one tag per type.
        blurHash = if (thumbUrl != null) {
            imageBlurHashes.blurHash("Thumb", imageTags["Thumb"])
        } else {
            imageBlurHashes.blurHash("Primary", imageTags["Primary"])
        },
        // §5.4 — `PrimaryImageAspectRatio` describes *this item's own* Primary image, so it is only
        // honoured when that image is what actually won the chain. Three links must not honour it:
        //   · a thumb (16:9) — it crops into the 2:3 frame per §5.6;
        //   · a series/parent poster — the ratio belongs to the child, not the art being drawn;
        //   · an Episode's own Primary, which is a 16:9 still, not a poster. Letterboxing that
        //     inside the 160×240 tile leaves a 160×90 strip floating in black; Search wants it
        //     cropped into the frame like a thumb (L4).
        aspect = primaryImageAspectRatio
            ?.toFloat()
            ?.takeIf { thumbUrl == null && type != "Episode" && imageTags["Primary"] != null }
            ?.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_ASPECT, MAX_ASPECT),
    )
}

/**
 * §5.4 — `LazyVerticalGrid(Fixed(5))`, 16/28 spacing, `focusRestorer()`. One uninterrupted sheet of
 * tiles: no periodic rules, no position markers.
 *
 * [firstTileFocus] is attached to tile 0 and [rowZeroUp] is wired as `focusProperties { up = … }`
 * on the whole first row — §5.6 requires the field↔grid boundary to be explicit rather than
 * trusting default focus search.
 *
 * M-B11 — [onTileFocused] reports the tile that just took D-pad focus, so a caller (Library's
 * metadata strip) can mirror it without every tile carrying its own per-item lambda.
 */
@Composable
fun PosterGrid(
    tiles: List<GridTileUi>,
    onTileClick: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    firstTileFocus: FocusRequester? = null,
    rowZeroUp: FocusRequester? = null,
    onTileFocused: (GridTileUi) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Dimens.GridHSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.GridVSpacing),
    ) {
        itemsIndexed(tiles, key = { _, tile -> tile.id }, contentType = { _, _ -> "tile" }) { index, tile ->
            var tileModifier: Modifier = Modifier
            if (index == 0 && firstTileFocus != null) {
                tileModifier = tileModifier.focusRequester(firstTileFocus)
            }
            if (index < GRID_COLUMNS && rowZeroUp != null) {
                tileModifier = tileModifier.focusProperties { up = rowZeroUp }
            }
            PosterGridTile(
                tile = tile,
                onClick = { onTileClick(tile.id) },
                modifier = tileModifier,
                onFocused = onTileFocused,
            )
        }
    }
}

/**
 * §4.1 — resting → focused runs four simultaneous signals on **separate** tweens: veil, 2dp white
 * rim, inner dark hairline, scale. Glow is deliberately absent (§7 flag 2: a white spot-shadow on
 * black is near-invisible on many panels; the rim is the real signal).
 */
@Composable
fun PosterGridTile(
    tile: GridTileUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (GridTileUi) -> Unit = {},
) {
    val motion = LocalShoumeiMotion.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)

    val scale by animateFloatAsState(
        targetValue = motion.scale(if (focused) GRID_FOCUS_SCALE else 1f),
        animationSpec = focusTween(focused),
        label = "gridTileScale",
    )
    val veil by animateFloatAsState(
        targetValue = if (focused) 0f else Alpha.VeilUnfocused,
        animationSpec = focusTween(focused),
        label = "gridTileVeil",
    )
    val rim by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = focusTween(focused),
        label = "gridTileRim",
    )
    // §5.4 / §5.2 — watched art sits at 0.55; focus restores it over 180ms.
    val artAlpha by animateFloatAsState(
        targetValue = if (tile.watched && !focused) Alpha.Watched else 1f,
        animationSpec = tween(180),
        label = "gridTileArt",
    )

    Column(
        modifier = modifier
            .width(Dimens.CardWidth)
            // else the neighbour clips the scale
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(tile)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // §1 — no ripples
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0.62f) // grows upward, feet aligned
                }
                .fillMaxWidth()
                .height(Dimens.CardHeight)
                .clip(shape)
                .background(Ink100),
        ) {
            // §5.4 — the tile box is always CardWidth × CardHeight. A poster whose reported ratio
            // is not 2:3 letterboxes (or crops) inside that box; the grid never reflows.
            PosterImage(
                url = tile.imageUrl,
                contentDescription = tile.title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = artAlpha },
                aspect = tile.aspect ?: DEFAULT_TILE_ASPECT,
                blurHash = tile.blurHash,
            )
            // (a) veil
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = veil)),
            )
            // §5.1 — progress is a light leak flush to the art's bottom edge, never a widget.
            if (tile.progressFraction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Lit.copy(alpha = Alpha.TrackInactive)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(tile.progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Tungsten),
                    )
                }
            }
            if (tile.watched) {
                CheckMark(
                    dimension = 14.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            // (b) edge light
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, Color.White.copy(alpha = rim), shape),
            )
            // (c) inner hairline — load-bearing: keeps the rim readable against pale artwork
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .border(1.dp, Color.Black.copy(alpha = 0.55f * rim), shape),
            )
        }

        // §5.1 — fixed label block so the row/grid never reflows.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (tile.typeLabel != null) LABEL_HEIGHT_WITH_TYPE else Dimens.PosterLabelHeight)
                .padding(top = 8.dp),
        ) {
            // §5.6 — a sentence-case type label, not an eyebrow: mixed-type Search results are
            // otherwise ambiguous, but the label is plain language at the same case as everything
            // else on screen.
            if (tile.typeLabel != null) {
                Text(
                    text = tile.typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Paper.copy(alpha = Alpha.TextTertiary),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall,
                color = Paper.copy(alpha = if (focused) 1f else Alpha.TextTertiary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            if (tile.subtitle != null) {
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Search tiles carry a type label, so their label block is taller than the Library 52dp. */
private val LABEL_HEIGHT_WITH_TYPE = 70.dp

/**
 * §4.3 — text-only chip: zero container. Inactive `@0.55`; selected `#FFFFFF` + 2dp Tungsten
 * underline; focused `#FFFFFF` + 2dp White underline; selected+focused = white underline with a
 * 4dp tungsten cap at the leading end.
 *
 * §3.1 — the label renders as authored, sentence case: chips are not eyebrows.
 */
@Composable
fun TextChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var chipModifier = modifier
        .onFocusChanged { focused = it.isFocused }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    if (focusRequester != null) chipModifier = chipModifier.focusRequester(focusRequester)

    // IntrinsicSize.Max so the underline is exactly as wide as the label, not the parent row.
    Column(modifier = chipModifier.width(IntrinsicSize.Max)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused || selected) Color.White else Paper.copy(alpha = Alpha.TextTertiary),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(6.dp))
        when {
            selected && focused -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            ) {
                Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(Tungsten))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
            }

            focused -> Box(
                modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White),
            )

            selected -> Box(
                modifier = Modifier.fillMaxWidth().height(2.dp).background(Tungsten),
            )

            else -> Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

/**
 * §5.4 / §5.2 — the watched marker: a white check, never a coloured dot. Hand-drawn so it does not
 * depend on a glyph being present in the TV build's font.
 */
@Composable
fun CheckMark(
    dimension: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Canvas(modifier = modifier.size(dimension)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.16f
        drawLine(
            color = color,
            start = Offset(w * 0.14f, h * 0.52f),
            end = Offset(w * 0.40f, h * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.40f, h * 0.80f),
            end = Offset(w * 0.88f, h * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
