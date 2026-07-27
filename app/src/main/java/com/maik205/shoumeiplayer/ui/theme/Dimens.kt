package com.maik205.shoumeiplayer.ui.theme

import androidx.compose.ui.unit.dp

/**
 * §5 — shared TV layout dimensions. Canvas 960×540dp, safe area 864×486dp at origin (48, 27);
 * every value below assumes that box.
 */
object Dimens {
    /** Overscan / safe-area origin. */
    val OverscanHorizontal = 48.dp
    val OverscanVertical = 27.dp

    /** §5.5 - the sign-in column (ServerEntry / Login) is anchored here, not at overscan. */
    val Gutter = 96.dp

    // Poster card (Home rows, Library grid, Search grid).
    val CardWidth = 160.dp
    val CardHeight = 240.dp

    /** Fixed label block under a poster so the row never reflows. */
    val PosterLabelHeight = 52.dp

    // Wide card (My Media, Next Up).
    val WideCardWidth = 280.dp
    val WideCardHeight = 158.dp
    val WideLabelHeight = 56.dp

    // Episode thumb (Detail).
    val EpisodeCardWidth = 320.dp
    val EpisodeCardHeight = 180.dp

    /** Horizontal gap between cards in a row. */
    val ItemSpacing = 16.dp

    /** Vertical gap between rows. */
    val RowSpacing = 40.dp

    /** Gap between a row header and its cards. */
    val RowTitleGap = 24.dp

    // Library / Search grid rhythm.
    val GridHSpacing = 16.dp
    val GridVSpacing = 28.dp

    /** Copy caps at 420dp (Detail); a paragraph across the full 864dp is a defect. */
    val BodyMaxWidth = 420.dp

    // Nav rail (§3.1).
    val RailCollapsedWidth = 56.dp
    val RailExpandedWidth = 220.dp
    val RailItemHeight = 56.dp
}
