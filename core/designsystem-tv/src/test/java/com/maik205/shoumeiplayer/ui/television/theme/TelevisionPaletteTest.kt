package com.maik205.shoumeiplayer.ui.television.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.maik205.shoumeiplayer.domain.settings.AppTheme
import com.maik205.shoumeiplayer.domain.settings.ColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #101 makes colour a user setting, which turns legibility from a matter of taste into a property
 * every shipped palette has to hold. These tests measure the ramp as it is actually composited over
 * each surface -- authoring `Paper.copy(alpha = 0.48f)` says nothing about whether the result can be
 * read -- and hold every palette to the same floors, so a palette added later cannot be the one that
 * is unreadable.
 *
 * Floors: WCAG 2.1 AA is 4.5:1 for body text and 3:1 for interface colour. Primary and secondary
 * foregrounds are held far above that because television viewing distance is not desk distance.
 */
class TelevisionPaletteTest {

    /** Backgrounds a foreground can legitimately land on. */
    private fun TelevisionPalette.backgrounds() =
        listOf("Black" to Black, "BlackRaised" to BlackRaised, "LibraryBackground" to LibraryBackground)

    private fun TelevisionPalette.surfaces() = backgrounds() + ("ImagePlaceholder" to ImagePlaceholder)

    private fun assertAtLeast(floor: Float, actual: Float, what: String) =
        assertTrue("$what was %.2f:1, below the %.2f:1 floor".format(actual, floor), actual >= floor)

    @Test
    fun `the contrast helper matches the published WCAG extremes`() {
        assertEquals(21.0f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1.0f, contrastRatio(Color.White, Color.White), 0.01f)
        // 50% white over black composites to #808080-ish, not to "half of 21".
        assertEquals(
            contrastRatio(Color.White.copy(alpha = 0.5f).compositeOver(Color.Black), Color.Black),
            contrastRatio(Color.White.copy(alpha = 0.5f), Color.Black),
            0.01f,
        )
    }

    @Test
    fun `every palette keeps its foreground ramp legible on every surface`() {
        ColorPalette.entries.forEach { choice ->
            val palette = TelevisionPalettes.of(choice)
            palette.surfaces().forEach { (name, surface) ->
                assertAtLeast(12f, contrastRatio(palette.Paper, surface), "$choice Paper on $name")
                assertAtLeast(6.5f, contrastRatio(palette.PaperMuted, surface), "$choice PaperMuted on $name")
                assertAtLeast(4.3f, contrastRatio(palette.PaperSoft, surface), "$choice PaperSoft on $name")
            }
        }
    }

    @Test
    fun `every palette accent clears the interface-colour floor and carries legible content`() {
        ColorPalette.entries.forEach { choice ->
            val palette = TelevisionPalettes.of(choice)
            palette.surfaces().forEach { (name, surface) ->
                assertAtLeast(3f, contrastRatio(palette.Ember, surface), "$choice Ember on $name")
            }
            assertAtLeast(4.5f, contrastRatio(palette.OnEmber, palette.Ember), "$choice OnEmber on Ember")
        }
    }

    /**
     * A progress rail communicates by the boundary between fill and track, so that edge is
     * interface colour and owes 3:1. The accent is measured over the base surface -- where the
     * player's rails and the Compose progress indicators are drawn -- while the paper fill, which
     * also appears on raised surfaces, is measured over all of them.
     */
    @Test
    fun `every palette separates a progress fill from its own track`() {
        ColorPalette.entries.forEach { choice ->
            val palette = TelevisionPalettes.of(choice)
            assertAtLeast(
                3f,
                contrastRatio(palette.Ember, palette.ProgressTrack.compositeOver(palette.Black)),
                "$choice Ember fill on its track",
            )
            palette.backgrounds().forEach { (name, surface) ->
                val track = palette.ProgressTrack.compositeOver(surface)
                assertAtLeast(3f, contrastRatio(palette.Paper, track), "$choice Paper fill on its track over $name")
            }
        }
    }

    @Test
    fun `the palettes offer both modes so AppTheme has something to select`() {
        val (light, dark) = ColorPalette.entries.partition { TelevisionPalettes.of(it).isLight }

        assertTrue("no light palette ships", light.isNotEmpty())
        assertTrue("no dark palette ships", dark.isNotEmpty())
        ColorPalette.entries.forEach { choice ->
            assertEquals(
                "$choice and its counterpart must sit in opposite modes",
                !TelevisionPalettes.of(choice).isLight,
                TelevisionPalettes.of(choice.counterpart).isLight,
            )
        }
    }

    @Test
    fun `AppTheme Dark pins the chosen palette and System follows the television`() {
        ColorPalette.entries.forEach { choice ->
            assertEquals(
                "AppTheme.Dark must not override an explicit palette choice",
                TelevisionPalettes.of(choice),
                TelevisionPalettes.resolve(choice, AppTheme.Dark, systemInDarkTheme = false),
            )
        }

        assertEquals(
            TelevisionPalettes.Daylight,
            TelevisionPalettes.resolve(ColorPalette.Midnight, AppTheme.System, systemInDarkTheme = false),
        )
        assertEquals(
            TelevisionPalettes.Sunrise,
            TelevisionPalettes.resolve(ColorPalette.Ember, AppTheme.System, systemInDarkTheme = false),
        )
        assertEquals(
            TelevisionPalettes.Midnight,
            TelevisionPalettes.resolve(ColorPalette.Daylight, AppTheme.System, systemInDarkTheme = true),
        )
        assertEquals(
            TelevisionPalettes.Ember,
            TelevisionPalettes.resolve(ColorPalette.Sunrise, AppTheme.System, systemInDarkTheme = true),
        )
    }

    /**
     * The default is what an existing install wakes up to after the upgrade, so Midnight must stay
     * byte-for-byte the pre-#101 palette or the migration ships a repaint nobody asked for. (The
     * `TelevisionColors` compatibility object these assertions also covered is gone: every call site
     * now reads the palette through `TelevisionTheme.colors`, and leaving a constant holder pinned to
     * Midnight would have silently ignored whatever the viewer chose.)
     */
    @Test
    fun `the default palette is still the pre-101 look`() {
        val midnight = TelevisionPalettes.of(ColorPalette.Midnight)

        assertEquals(Color(0xFF08090A), midnight.Black)
        assertEquals(Color(0xFF111315), midnight.BlackRaised)
        assertEquals(Color(0xFF0E1012), midnight.LibraryBackground)
        assertEquals(Color(0xFFF7F6F2), midnight.Paper)
        assertEquals(midnight.Paper, midnight.Ember)
        assertEquals(Color(0xFF1B1D20), midnight.ImagePlaceholder)

    }

    /** The surfaces are identical across siblings. */
    @Test
    fun `the accented palettes keep their sibling's surfaces`() {
        assertEquals(TelevisionPalettes.Midnight.Black, TelevisionPalettes.Ember.Black)
        assertEquals(TelevisionPalettes.Midnight.Paper, TelevisionPalettes.Ember.Paper)
        assertEquals(TelevisionPalettes.Daylight.Black, TelevisionPalettes.Sunrise.Black)
        assertEquals(TelevisionPalettes.Daylight.Paper, TelevisionPalettes.Sunrise.Paper)
        assertEquals(TelevisionPalettes.Midnight.Paper, TelevisionPalettes.Ember.Ember)
        assertEquals(TelevisionPalettes.Daylight.Paper, TelevisionPalettes.Sunrise.Ember)
    }
}
