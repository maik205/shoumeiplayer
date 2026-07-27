package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.theme.ShoumeiType
import kotlin.math.cos
import kotlin.math.sin

/**
 * docs/osd-v3.md §2 — the control-track glyphs.
 *
 * **Why these are hand-drawn (the note §2 asks for).** The project depends on
 * `androidx.compose.material:material-icons-core`, whose entire set is 49 glyphs: `AccountBox,
 * AccountCircle, Add, AddCircle, ArrowBack, ArrowDropDown, ArrowForward, Build, Call, Check,
 * CheckCircle, Clear, Close, Create, DateRange, Delete, Done, Edit, Email, ExitToApp, Face,
 * Favorite, FavoriteBorder, Home, Info, KeyboardArrowDown/Left/Right/Up, List, LocationOn, Lock,
 * MailOutline, Menu, MoreVert, Notifications, Person, Phone, Place, PlayArrow, Refresh, Search,
 * Send, Settings, Share, ShoppingCart, Star, ThumbUp, Warning` (verified against the 1.7.0 sources
 * jar on the Gradle cache). Of the ten controls the chip track needs, exactly one — play — has a
 * glyph there ([androidx.compose.material.icons.Icons.Filled.PlayArrow], which the track uses). The
 * media set (`Pause`, `SkipNext`, `SkipPrevious`, `Replay10`, `Forward10`, `ClosedCaption`,
 * `VolumeUp`, `Speed`, `HighQuality`) lives only in `material-icons-extended`, a ~40k-method
 * artifact that is not a dependency and would be a poor trade for nine glyphs on one screen.
 *
 * So the rest are drawn here at Material's own 24×24 viewport with Material's own geometry where a
 * canonical shape exists (`Pause` is literally two bars; `SkipPrevious`/`SkipNext` a triangle and a
 * bar), and as deliberate substitutes where none does — see each glyph's note. They compose through
 * [androidx.tv.material3.Icon] exactly like the core ones, so the track is visually one set.
 */
internal object PlayerIcons {

    /** Two bars — Material's `Pause` geometry (`M6 5h4v14H6z  M14 5h4v14h-4z`). */
    val Pause: ImageVector = icon("Pause") {
        bar(6f, 10f)
        bar(14f, 18f)
    }

    /** A bar and a left-pointing triangle — Material's `SkipPrevious` geometry. */
    val PreviousEpisode: ImageVector = icon("PreviousEpisode") {
        bar(6f, 8f)
        triangle(tipX = 9.5f, baseX = 18f)
    }

    /** A right-pointing triangle and a bar — Material's `SkipNext` geometry. */
    val NextEpisode: ImageVector = icon("NextEpisode") {
        triangle(tipX = 14.5f, baseX = 6f)
        bar(16f, 18f)
    }

    /**
     * Subtitles: a caption frame with two caption bars along its lower edge.
     *
     * A substitute for `ClosedCaption`, which spells "CC" in letterforms. At the 24dp the chip draws
     * it, a pair of letterforms turns to mush ten feet away, while the frame-plus-bars silhouette
     * survives — and the focused pill spells the state out in words anyway (`Subtitles · English`).
     */
    val Subtitles: ImageVector = icon("Subtitles") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 5f)
            lineTo(21f, 5f)
            lineTo(21f, 19f)
            lineTo(3f, 19f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 13.6f)
            lineTo(12f, 13.6f)
            lineTo(12f, 16f)
            lineTo(6f, 16f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(13.6f, 13.6f)
            lineTo(18f, 13.6f)
            lineTo(18f, 16f)
            lineTo(13.6f, 16f)
            close()
        }
    }

    /** Audio: a speaker cone and two emission arcs — the `VolumeUp` silhouette. */
    val Audio: ImageVector = icon("Audio") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 9f)
            lineTo(6.5f, 9f)
            lineTo(11f, 4.8f)
            lineTo(11f, 19.2f)
            lineTo(6.5f, 15f)
            lineTo(3f, 15f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(14.4f, 9f)
            curveTo(15.9f, 10f, 15.9f, 14f, 14.4f, 15f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(17.4f, 6.4f)
            curveTo(20.6f, 8.6f, 20.6f, 15.4f, 17.4f, 17.6f)
        }
    }

    /**
     * Speed: a dial arc with a needle — the `Speed` silhouette, drawn rather than imported.
     *
     * The needle points up-right (the "faster" half of a dial) at every rate; the *value* is real
     * state and belongs to the focused pill (`Speed · 1.5×`) and the §2 tungsten dot, not to a
     * resting icon that would then have six variants.
     */
    val Speed: ImageVector = icon("Speed") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(3.6f, 16.5f)
            arcTo(
                horizontalEllipseRadius = 8.4f,
                verticalEllipseRadius = 8.4f,
                theta = 0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                x1 = 20.4f,
                y1 = 16.5f,
            )
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 16.5f)
            lineTo(16.4f, 10.6f)
        }
    }

    /**
     * Quality: three ascending bars.
     *
     * A substitute for `HighQuality`, which — like `ClosedCaption` — is a two-letter plate ("HQ")
     * that does not survive 24dp at ten feet. Ascending bars are the "more of it" mark the ladder
     * actually means (a bitrate cap, per [com.maik205.shoumeiplayer.player.VideoQuality]), and the
     * focused pill names the rung (`Quality · 1080p`).
     */
    val Quality: ImageVector = icon("Quality") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 14f); lineTo(7.6f, 14f); lineTo(7.6f, 19.5f); lineTo(4f, 19.5f); close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(10.2f, 9f); lineTo(13.8f, 9f); lineTo(13.8f, 19.5f); lineTo(10.2f, 19.5f); close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.4f, 4.5f); lineTo(20f, 4.5f); lineTo(20f, 19.5f); lineTo(16.4f, 19.5f); close()
        }
    }
}

// --- glyph construction helpers ---------------------------------------------------------------

private fun icon(
    name: String,
    paths: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(paths).build()

/** A full-height transport bar between [startX] and [endX], on Material's 5..19 vertical band. */
private fun ImageVector.Builder.bar(startX: Float, endX: Float) {
    path(fill = SolidColor(Color.Black)) {
        moveTo(startX, 5f)
        lineTo(endX, 5f)
        lineTo(endX, 19f)
        lineTo(startX, 19f)
        close()
    }
}

/** A horizontal triangle with its apex at [tipX] and its vertical base at [baseX]. */
private fun ImageVector.Builder.triangle(tipX: Float, baseX: Float) {
    path(fill = SolidColor(Color.Black)) {
        moveTo(baseX, 5f)
        lineTo(tipX, 12f)
        lineTo(baseX, 19f)
        close()
    }
}

// --- the ∓10s glyph ----------------------------------------------------------------------------

/** The step the seek chips carry, printed inside the arc. */
internal const val SEEK_CHIP_SECONDS = 10

private const val ARC_RADIUS_FRACTION = 0.40f
private const val ARC_START_DEGREES = 10f
private const val ARC_SWEEP_DEGREES = 290f
private const val ARROW_LENGTH = 3.4f
private const val ARROW_HALF_WIDTH = 2.7f
private val ArcStrokeWidth = 1.8.dp
private val SeekDigitsSize = 9.sp

/**
 * docs/osd-v3.md §2 — the `-10s` / `+10s` chip glyph: a broken circular arrow with `10` inside it.
 *
 * `Replay10` / `Forward10` are `material-icons-extended`-only (see [PlayerIcons]), and no core glyph
 * carries a number, so this one is a [Canvas] rather than an [ImageVector]: the digits are real text
 * on the app's mono face, which keeps them legible at the 24dp the chip draws and lets them inherit
 * the chip's animated content colour. The backward variant is the forward one mirrored about the
 * centre, so the two are guaranteed to be the same drawing.
 */
@Composable
internal fun SeekChipGlyph(
    forward: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(ChipIconSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(ChipIconSize)) {
            if (forward) {
                drawSeekArc(tint)
            } else {
                // The mirror is the whole point: one arc drawing, two directions.
                scale(scaleX = -1f, scaleY = 1f, pivot = center) { drawSeekArc(tint) }
            }
        }
        Text(
            text = SEEK_CHIP_SECONDS.toString(),
            style = ShoumeiType.Timecode.copy(fontSize = SeekDigitsSize, letterSpacing = 0.sp),
            color = tint,
            maxLines = 1,
        )
    }
}

/** Clockwise arc with a filled head at its end, drawn into the top-right gap. */
private fun DrawScope.drawSeekArc(tint: Color) {
    val radius = size.minDimension * ARC_RADIUS_FRACTION
    val topLeft = Offset(center.x - radius, center.y - radius)
    drawArc(
        color = tint,
        startAngle = ARC_START_DEGREES,
        sweepAngle = ARC_SWEEP_DEGREES,
        useCenter = false,
        topLeft = topLeft,
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = ArcStrokeWidth.toPx(), cap = StrokeCap.Round),
    )

    // Unit vectors at the arc's end: `along` is the clockwise tangent, `across` its normal.
    val endRadians = Math.toRadians((ARC_START_DEGREES + ARC_SWEEP_DEGREES).toDouble())
    val end = Offset(
        x = center.x + radius * cos(endRadians).toFloat(),
        y = center.y + radius * sin(endRadians).toFloat(),
    )
    val along = Offset(-sin(endRadians).toFloat(), cos(endRadians).toFloat())
    val across = Offset(-along.y, along.x)
    // The scale factor turns the 24-unit viewport constants into pixels of whatever size we got.
    val unit = size.minDimension / 24f

    val head = Path().apply {
        moveTo(end.x + along.x * ARROW_LENGTH * unit, end.y + along.y * ARROW_LENGTH * unit)
        lineTo(end.x + across.x * ARROW_HALF_WIDTH * unit, end.y + across.y * ARROW_HALF_WIDTH * unit)
        lineTo(end.x - across.x * ARROW_HALF_WIDTH * unit, end.y - across.y * ARROW_HALF_WIDTH * unit)
        close()
    }
    drawPath(head, tint)
}
