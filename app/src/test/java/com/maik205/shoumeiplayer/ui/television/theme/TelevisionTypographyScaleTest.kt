package com.maik205.shoumeiplayer.ui.television.theme

import com.maik205.shoumeiplayer.domain.settings.InterfaceScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #89: Settings > Interface > "Interface scale" persisted a choice nothing read. [televisionTypography]
 * is the seam every TV screen is meant to render through; these assert the ON (Compact/Large) and
 * OFF (Comfortable) renderings actually produce different [androidx.compose.ui.text.TextStyle]s, not
 * just that the function returns without throwing.
 */
class TelevisionTypographyScaleTest {

    @Test
    fun `comfortable reproduces the shipped baseline exactly`() {
        val scaled = televisionTypography(InterfaceScale.Comfortable)

        assertEquals(TelevisionTypography.displayLarge.fontSize, scaled.displayLarge.fontSize)
        assertEquals(TelevisionTypography.bodySmall.fontSize, scaled.bodySmall.fontSize)
        assertEquals(TelevisionTypography.titleSmall.lineHeight, scaled.titleSmall.lineHeight)
    }

    @Test
    fun `compact shrinks every role relative to the baseline`() {
        val compact = televisionTypography(InterfaceScale.Compact)

        assertTrue(compact.displayLarge.fontSize.value < TelevisionTypography.displayLarge.fontSize.value)
        assertTrue(compact.bodySmall.fontSize.value < TelevisionTypography.bodySmall.fontSize.value)
        assertTrue(compact.titleSmall.lineHeight.value < TelevisionTypography.titleSmall.lineHeight.value)
    }

    @Test
    fun `large grows every role relative to the baseline`() {
        val large = televisionTypography(InterfaceScale.Large)

        assertTrue(large.displayLarge.fontSize.value > TelevisionTypography.displayLarge.fontSize.value)
        assertTrue(large.bodySmall.fontSize.value > TelevisionTypography.bodySmall.fontSize.value)
        assertTrue(large.titleSmall.lineHeight.value > TelevisionTypography.titleSmall.lineHeight.value)
    }

    @Test
    fun `compact and large are not the same rendering`() {
        val compact = televisionTypography(InterfaceScale.Compact)
        val large = televisionTypography(InterfaceScale.Large)

        assertNotEquals(compact.displayLarge.fontSize, large.displayLarge.fontSize)
    }
}
