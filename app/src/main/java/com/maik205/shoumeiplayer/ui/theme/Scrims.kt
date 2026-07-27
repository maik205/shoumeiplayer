package com.maik205.shoumeiplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * §2.3 — every gradient in the app, declared exactly once. All stops are αRRGGBB on black.
 * **Never hand-roll a gradient in a screen**; if a new one is needed, add it here.
 */
object Scrims {
    /** Detail: wipe from the LEFT, not the bottom — type on solid black, art uncropped on the right. */
    val DetailWipe = Brush.horizontalGradient(
        0f to Color(0xFF000000),
        .30f to Color(0xF2000000),
        .58f to Color(0x99000000),
        .86f to Color(0x1F000000),
        1f to Color(0x00000000),
    )

    /** Detail: settles the lower 200dp into black. */
    val BottomSettle = Brush.verticalGradient(
        0f to Color(0x00000000),
        .45f to Color(0x4D000000),
        .78f to Color(0xD9000000),
        1f to Color(0xFF000000),
    )

    /** Home ambient: damps the focused item's backdrop vertically. */
    val AmbientDamp = Brush.verticalGradient(
        0f to Color(0xB3000000),
        .38f to Color(0xD9000000),
        .72f to Color(0xFA000000),
        1f to Color(0xFF000000),
    )

    /** Home ambient: clears the left column for type. */
    val AmbientLeft = Brush.horizontalGradient(
        0f to Color(0xFF000000),
        .34f to Color(0x99000000),
        .70f to Color(0x00000000),
    )

    /** Player OSD bottom plate (236dp). */
    val OsdBottom = Brush.verticalGradient(
        0f to Color(0x00000000),
        .40f to Color(0x73000000),
        .75f to Color(0xCC000000),
        1f to Color(0xF2000000),
    )

    /** Player OSD top plate (112dp). */
    val OsdTop = Brush.verticalGradient(
        0f to Color(0xC2000000),
        1f to Color(0x00000000),
    )

    /** Under pinned headers (Library, Search) so tiles dissolve upward. */
    val TopVignette = Brush.verticalGradient(
        0f to Color(0xB3000000),
        1f to Color(0x00000000),
    )

    /** Wide-card text set inside the art at bottom-left. */
    val CardFoot = Brush.verticalGradient(
        .45f to Color(0x00000000),
        1f to Color(0xE6000000),
    )
}
