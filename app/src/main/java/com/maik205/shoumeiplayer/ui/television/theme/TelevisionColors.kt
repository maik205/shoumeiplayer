package com.maik205.shoumeiplayer.ui.television.theme

import androidx.compose.ui.graphics.Color

/**
 * The television UI is intentionally achromatic. Hierarchy comes from type, image crop, and
 * opacity; semantic state is communicated with copy and icons rather than decorative colour.
 */
object TelevisionColors {
    val Black = Color(0xFF08090A)
    val BlackRaised = Color(0xFF111315)
    val LibraryBackground = Color(0xFF0E1012)
    val Paper = Color(0xFFF7F6F2)
    val Ember = Color(0xFFDF754F)
    val PaperMuted = Paper.copy(alpha = 0.68f)
    val PaperSoft = Paper.copy(alpha = 0.48f)
    val PaperDisabled = Paper.copy(alpha = 0.28f)
    val ImagePlaceholder = Color(0xFF1B1D20)
    val ImageVeil = Black.copy(alpha = 0.06f)
    val ProgressTrack = Paper.copy(alpha = 0.24f)
}
