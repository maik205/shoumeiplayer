package com.maik205.shoumeiplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * §2.1 — "dark room, lit evidence". True-black OLED-first ramp; one accent (Tungsten) that means
 * playback state and nothing else. Never invent a new hex: compose the [Alpha] constants over black.
 */

/** Background — OLED-first true black. */
val Ink000 = Color(0xFF000000)

/** Surface: sheets, focused rows. */
val Ink050 = Color(0xFF08080A)

/** surfaceVariant: placeholders, resting chips. */
val Ink100 = Color(0xFF101014)

/** Dialogs, track panel. */
val Ink150 = Color(0xFF17171C)

/** Pressed / raised. */
val Ink200 = Color(0xFF1F1F26)

/** Resting hairline border. */
val Ink300 = Color(0xFF2E2E36)

/** Disabled. */
val Ash400 = Color(0xFF6E6E76)

/** onSurfaceVariant metadata. */
val Ash600 = Color(0xFFA8A8AF)

/** Primary text, filled-slab fill (faintly warm white). */
val Paper = Color(0xFFF5F5F2)

/** Focus border and edge light ONLY. */
val Lit = Color(0xFFFFFFFF)

/** Playback state ONLY — resume, progress, now-playing, selected track. */
val Tungsten = Color(0xFFE8B472)
val TungstenPale = Color(0xFFF6DFC0)

/** Error — desaturated, never fire-engine red. */
val Signal = Color(0xFFE06C6C)

/** Inset hairline on flat image placeholders (§5, `#212125`). */
val PlaceholderHairline = Color(0xFF212125)

/**
 * §2.1 — the only sanctioned alpha values. Compose these over black rather than adding new hexes.
 */
object Alpha {
    /** Veil over unfocused art. */
    const val VeilUnfocused = 0.24f

    /** [Lit] @ 8% — every divider / hairline in the app. */
    const val Hairline = 0.08f

    const val TrackInactive = 0.16f
    const val TrackBuffered = 0.28f

    /** Tertiary text; also the resting icon alpha. */
    const val TextTertiary = 0.55f

    const val TextDisabled = 0.38f

    /** Watched artwork dim. */
    const val Watched = 0.55f
}
