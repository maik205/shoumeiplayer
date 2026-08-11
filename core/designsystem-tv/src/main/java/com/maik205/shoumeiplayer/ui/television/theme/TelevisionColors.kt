package com.maik205.shoumeiplayer.ui.television.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.maik205.shoumeiplayer.domain.settings.AppTheme
import com.maik205.shoumeiplayer.domain.settings.ColorPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The eleven colour roles the television UI paints with -- as a *value*, so they can differ per
 * viewer (#101).
 *
 * This reverses the position the old `object TelevisionColors` documented ("the television UI is
 * intentionally achromatic"). Colour is now a personal setting: [TelevisionPalettes.Midnight] is
 * still the achromatic black/paper look and is still the default, but a viewer may choose an
 * accented or a light palette instead, and nothing in the tree may assume which one is mounted.
 *
 * The names are historical and describe **roles**, not hues -- in a light palette [Black] is the
 * near-white base surface and [Paper] is near-black ink:
 *
 * - [Black] base surface, [BlackRaised] raised surface (sheets, drawers), [LibraryBackground] the
 *   library canvas.
 * - [Paper] primary foreground, with [PaperMuted]/[PaperSoft]/[PaperDisabled] the translucent
 *   secondary/tertiary/disabled steps of the same ramp. They are kept *translucent* rather than
 *   pre-composited so they keep reading correctly over artwork, which is what they mostly sit on.
 * - [Ember] the accent: the one chromatic role, reaching progress fills and focus rings through the
 *   colour schemes `ShoumeiTelevisionTheme` builds from this palette.
 * - [ImagePlaceholder] poster fill before artwork loads, [ImageVeil] the wash over artwork,
 *   [ProgressTrack] the unfilled part of a progress rail.
 */
@Immutable
data class TelevisionPalette(
    val Black: Color,
    val BlackRaised: Color,
    val LibraryBackground: Color,
    val Paper: Color,
    val Ember: Color,
    val PaperMuted: Color,
    val PaperSoft: Color,
    val PaperDisabled: Color,
    val ImagePlaceholder: Color,
    val ImageVeil: Color,
    val ProgressTrack: Color,
) {
    /**
     * Content colour for anything filled with [Ember].
     *
     * Derived rather than authored, because [Ember] is a user choice: a hardcoded "on accent"
     * colour is legible only for the accent it was picked against, and the next palette added would
     * silently ship unreadable labels on selected chips. Picking whichever end of this palette's own
     * ramp contrasts more keeps that impossible by construction.
     */
    val OnEmber: Color =
        if (contrastRatio(Black, Ember) >= contrastRatio(Paper, Ember)) Black else Paper

    /** True when surfaces are lighter than foregrounds, i.e. this is a light palette. */
    val isLight: Boolean = relativeLuminance(Black) > relativeLuminance(Paper)
}

/**
 * The palettes a viewer can choose between, keyed by [ColorPalette].
 *
 * Each is a complete token set rather than a hue applied to a shared base: a light palette needs a
 * different foreground ramp, a different placeholder and a different progress track, and deriving
 * those from one knob is how "light mode" ends up unreadable. Every palette here is contrast-checked
 * by `TelevisionPaletteContrastTest`.
 */
object TelevisionPalettes {

    /**
     * Today's look, byte-for-byte, and the default -- an upgrade must not shift anybody's TV. Its
     * accent is the original Ember (#DF754F), which already tints the Compose progress indicators.
     */
    val Midnight: TelevisionPalette = run {
        val black = Color(0xFF08090A)
        val paper = Color(0xFFF7F6F2)
        TelevisionPalette(
            Black = black,
            BlackRaised = Color(0xFF111315),
            LibraryBackground = Color(0xFF0E1012),
            Paper = paper,
            Ember = Color(0xFFDF754F),
            PaperMuted = paper.copy(alpha = 0.68f),
            PaperSoft = paper.copy(alpha = 0.48f),
            PaperDisabled = paper.copy(alpha = 0.28f),
            ImagePlaceholder = Color(0xFF1B1D20),
            ImageVeil = black.copy(alpha = 0.06f),
            ProgressTrack = paper.copy(alpha = 0.24f),
        )
    }

    /**
     * Midnight's surfaces with the accent turned up: the same base, but chrome that can carry
     * colour does. [TelevisionPalette.ProgressTrack] is a step lighter than Midnight's so the ember
     * fill still clears 3:1 against its own rail.
     */
    val Ember: TelevisionPalette = Midnight.copy(
        ProgressTrack = Midnight.Paper.copy(alpha = 0.18f),
    )

    /**
     * A genuinely light palette. [TelevisionPalette.Black] is a warm near-white base and
     * [TelevisionPalette.Paper] is ink; the ramp alphas are heavier than the dark palettes' because
     * translucent ink over white loses contrast faster than translucent white over black does.
     */
    val Daylight: TelevisionPalette = run {
        val base = Color(0xFFF6F4EF)
        val ink = Color(0xFF14161A)
        TelevisionPalette(
            Black = base,
            BlackRaised = Color(0xFFFFFFFF),
            LibraryBackground = Color(0xFFEDEAE3),
            Paper = ink,
            Ember = ink,
            PaperMuted = ink.copy(alpha = 0.76f),
            PaperSoft = ink.copy(alpha = 0.62f),
            PaperDisabled = ink.copy(alpha = 0.42f),
            ImagePlaceholder = Color(0xFFDCD8D0),
            ImageVeil = base.copy(alpha = 0.06f),
            ProgressTrack = ink.copy(alpha = 0.20f),
        )
    }

    /**
     * Daylight with an accent. The original #DF754F only reaches 2.8:1 on a white surface, which is
     * under the 3:1 floor for interface colour, so the light palette uses a deeper ember instead of
     * reusing the dark one.
     */
    val Sunrise: TelevisionPalette = Daylight.copy(Ember = Color(0xFFA8442A))

    fun of(choice: ColorPalette): TelevisionPalette = when (choice) {
        ColorPalette.Midnight -> Midnight
        ColorPalette.Ember -> Ember
        ColorPalette.Daylight -> Daylight
        ColorPalette.Sunrise -> Sunrise
    }

    /**
     * The palette to mount for [choice] under [theme].
     *
     * [AppTheme] has selected nothing since it was added, because every palette was dark. Now each
     * choice has a counterpart in the other mode ([ColorPalette.counterpart]), so
     * [AppTheme.System] can follow the television into light mode without discarding the viewer's
     * choice of *which* palette; [AppTheme.Dark] pins the light/dark mode the viewer picked
     * explicitly and ignores the system.
     */
    fun resolve(
        choice: ColorPalette,
        theme: AppTheme,
        systemInDarkTheme: Boolean,
    ): TelevisionPalette {
        val wanted = when (theme) {
            AppTheme.Dark -> choice
            AppTheme.System -> if (systemInDarkTheme == of(choice).isLight) choice.counterpart else choice
        }
        return of(wanted)
    }
}

/** The light/dark twin of this palette, i.e. the same accent in the other mode. */
val ColorPalette.counterpart: ColorPalette
    get() = when (this) {
        ColorPalette.Midnight -> ColorPalette.Daylight
        ColorPalette.Daylight -> ColorPalette.Midnight
        ColorPalette.Ember -> ColorPalette.Sunrise
        ColorPalette.Sunrise -> ColorPalette.Ember
    }

/**
 * The palette the surrounding UI is painted with.
 *
 * `static` because a palette change repaints everything anyway, so tracking individual readers would
 * only add bookkeeping. The default is [TelevisionPalettes.Midnight] so a composable previewed or
 * tested outside `ShoumeiTelevisionTheme` still renders the shipped look instead of throwing.
 */
val LocalTelevisionPalette = staticCompositionLocalOf { TelevisionPalettes.Midnight }

/** Entry point for reading theme values, mirroring `MaterialTheme.colorScheme`. */
object TelevisionTheme {
    val colors: TelevisionPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalTelevisionPalette.current
}

/**
 * WCAG 2.1 relative luminance of an opaque colour.
 *
 * Written out rather than taken from `Color.luminance()` so the contrast floors the palettes are
 * held to are checkable in a plain JVM unit test against the published formula.
 */
fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

/**
 * WCAG 2.1 contrast ratio, 1..21. Translucent colours are composited over [background] first, so a
 * ramp step such as `PaperSoft` is measured as it is actually seen rather than as authored.
 */
fun contrastRatio(foreground: Color, background: Color): Float {
    val front = if (foreground.alpha < 1f) foreground.compositeOver(background) else foreground
    val a = relativeLuminance(front)
    val b = relativeLuminance(background)
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}
