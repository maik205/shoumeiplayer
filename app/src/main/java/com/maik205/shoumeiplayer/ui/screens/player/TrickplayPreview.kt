package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maik205.shoumeiplayer.data.TrickplayMath
import com.maik205.shoumeiplayer.data.TrickplaySource
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Ink100
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.ShoumeiType
import com.maik205.shoumeiplayer.util.Ticks

/** §3 — the preview frame's width; the height follows the sheet's own thumbnail aspect. */
internal val TrickplayPreviewWidth = 200.dp

/** §6 amendment — the trickplay frame takes an 8dp radius. */
private val PreviewRadius = 8.dp

/** Fallback aspect when there is no manifest to ask: the caption still needs a stable anchor. */
private const val FALLBACK_ASPECT = 16f / 9f

/** Gap between the frame and its caption, and the caption block's own height. */
private val CaptionGap = 8.dp
private val CaptionHeight = 22.dp

/**
 * The height [TrickplayPreview] will occupy for [source], caption included.
 *
 * Callers need this *before* composing it — the preview floats above the seek lane on a negative
 * offset, so its own measured height is not available in time. Pure, so the offset and the drawing
 * can never disagree.
 */
internal fun trickplayPreviewHeight(source: TrickplaySource?): Dp =
    trickplayFrameHeight(source) + CaptionGap + CaptionHeight

private fun trickplayFrameHeight(source: TrickplaySource?): Dp {
    val info = source?.info
    val aspect = if (info != null && info.width > 0 && info.height > 0) {
        info.width.toFloat() / info.height.toFloat()
    } else {
        FALLBACK_ASPECT
    }
    return TrickplayPreviewWidth / aspect
}

/**
 * docs/osd-v3.md §3 — the scrub preview: one trickplay thumbnail plus a chapter/timecode caption.
 *
 * Jellyfin packs `tileWidth × tileHeight` thumbnails into every sheet
 * (`/Videos/{itemId}/Trickplay/{width}/{index}.jpg`), so drawing one is a sprite crop, not an image
 * load: [TrickplayMath.tileAt] says which sheet and which cell, the whole sheet is laid out at
 * `columns × rows` preview-sized tiles, and the wanted cell is slid under a clipped window. That
 * keeps every frame of a scrub on the *same* Coil entry — a sheet already in cache costs nothing to
 * re-crop, which is the entire reason the format exists.
 *
 * With no manifest (or an unusable one — zero interval, empty grid) the frame is dropped and the
 * caption stands alone, exactly as §3 asks. The caption is never dropped: a chapter name and a
 * timecode are the part of the preview that always exists.
 */
@Composable
internal fun TrickplayPreview(
    source: TrickplaySource?,
    positionMs: Long,
    chapterName: String?,
    modifier: Modifier = Modifier,
) {
    val located = remember(source, positionMs) { source?.tileAt(positionMs) }

    Column(
        modifier = modifier.widthIn(min = TrickplayPreviewWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val info = source?.info
        if (located != null && info != null) {
            val (url, tile) = located
            val frameHeight = trickplayFrameHeight(source)
            val platformContext = LocalPlatformContext.current

            Box(
                modifier = Modifier
                    .width(TrickplayPreviewWidth)
                    .height(frameHeight)
                    .clipToBounds()
                    .background(Ink100, RoundedCornerShape(PreviewRadius))
                    // §4.3 — the one hairline this frame is allowed: it separates a video still from
                    // the video behind it, which is exactly "two different kinds of content".
                    .border(1.dp, Lit.copy(alpha = Alpha.Hairline), RoundedCornerShape(PreviewRadius)),
            ) {
                AsyncImage(
                    model = remember(url, platformContext, info) {
                        ImageRequest.Builder(platformContext)
                            .data(url)
                            // Pin the decode to the sheet's *own* pixel size. Without this Coil
                            // sizes the request from the composable, which here is the sheet laid
                            // out at `columns × preview width` — several thousand dp, and an
                            // upscaled decode of a file that was small on purpose.
                            .size(info.width * info.tileWidth, info.height * info.tileHeight)
                            // A scrub crosses cells inside one sheet constantly; a crossfade on
                            // every step would strobe. The sheet is either cached or it is not.
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        // The sheet at preview scale: one cell is exactly one preview frame…
                        .width(TrickplayPreviewWidth * info.tileWidth)
                        .height(frameHeight * info.tileHeight)
                        // …and the wanted cell is slid into the clipped window.
                        .offset(
                            x = -TrickplayPreviewWidth * tile.column,
                            y = -frameHeight * tile.row,
                        ),
                )
            }
            Box(modifier = Modifier.height(CaptionGap))
        }

        Row(
            modifier = Modifier
                .height(CaptionHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!chapterName.isNullOrBlank()) {
                Text(
                    text = chapterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Paper.copy(alpha = Alpha.TextTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = Ticks.formatDuration(positionMs),
                style = ShoumeiType.Timecode,
                color = Paper,
                maxLines = 1,
            )
        }
    }
}
