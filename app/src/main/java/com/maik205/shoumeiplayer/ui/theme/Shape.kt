package com.maik205.shoumeiplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Shapes

/**
 * §4.1 - **one radius system: 4dp, everywhere.** Cards, chips, slab buttons, dialogs, badges and
 * skeleton blocks all take the same corner, so every slot below resolves to the same value; the
 * 2/6/8dp variants are gone.
 *
 * `RectangleShape` (0dp) is the only other shape in the app and is applied at the call site, where
 * an element is flush to an edge or is a bar: full-bleed imagery (Detail backdrop, Home ambient,
 * player surface), progress and seek bars, chapter ticks, the scrubber, edge-light focus bars, and
 * the right-anchored track panel. No pills, no circles, no asymmetric corner sets.
 */
private val Radius = RoundedCornerShape(4.dp)

val ShoumeiShapes = Shapes(
    extraSmall = Radius,
    small = Radius,
    medium = Radius,
    large = Radius,
    extraLarge = Radius,
)
