package com.maik205.shoumeiplayer.ui.television.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.settings.InterfaceScale

/**
 * Outfit is checked in as static instances rather than one variable font.
 *
 * Android's Compose font loader does not select the `wght` axis from a variable
 * TTF just because the same resource is registered at several [FontWeight]s.
 * The variable file's default axis value is Outfit Thin (100), which made every
 * TV text style render at the lightest weight. Each resource below has a fixed
 * OS/2 weight class, so Compose can select the requested face normally.
 */
val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

val TelevisionTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 58.sp,
        lineHeight = 62.sp,
        letterSpacing = (-1.4).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 8.sp,
        lineHeight = 11.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 13.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 8.sp,
        lineHeight = 12.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 7.sp,
        lineHeight = 10.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 8.sp,
        lineHeight = 10.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 7.sp,
        lineHeight = 9.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 6.sp,
        lineHeight = 8.sp,
    ),
)

/**
 * Per-role font-size/line-height multiplier for the Settings > Interface > "Interface scale" row
 * (#89). [InterfaceScale.Comfortable] is exactly `1f` so it reproduces [TelevisionTypography] --
 * the shipped baseline -- byte-for-byte; only Compact/Large actually change anything.
 */
internal fun InterfaceScale.televisionTextMultiplier(): Float = when (this) {
    InterfaceScale.Compact -> 0.88f
    InterfaceScale.Comfortable -> 1f
    InterfaceScale.Large -> 1.16f
}

/**
 * [TelevisionTypography] scaled for [scale].
 *
 * This is the seam #89 asks for: every TV screen should read its typography through this function
 * (via a local `androidx.tv.material3.MaterialTheme(typography = televisionTypography(scale)) { }`
 * override) instead of the [TelevisionTypography] constant directly, or the interface-scale picker
 * keeps having no effect no matter what a viewer chooses.
 */
fun televisionTypography(scale: InterfaceScale): Typography {
    val multiplier = scale.televisionTextMultiplier()
    fun TextStyle.scaled(): TextStyle =
        if (multiplier == 1f) this else copy(fontSize = fontSize * multiplier, lineHeight = lineHeight * multiplier)

    return Typography(
        displayLarge = TelevisionTypography.displayLarge.scaled(),
        displayMedium = TelevisionTypography.displayMedium.scaled(),
        displaySmall = TelevisionTypography.displaySmall.scaled(),
        headlineLarge = TelevisionTypography.headlineLarge.scaled(),
        headlineMedium = TelevisionTypography.headlineMedium.scaled(),
        headlineSmall = TelevisionTypography.headlineSmall.scaled(),
        titleLarge = TelevisionTypography.titleLarge.scaled(),
        titleMedium = TelevisionTypography.titleMedium.scaled(),
        titleSmall = TelevisionTypography.titleSmall.scaled(),
        bodyLarge = TelevisionTypography.bodyLarge.scaled(),
        bodyMedium = TelevisionTypography.bodyMedium.scaled(),
        bodySmall = TelevisionTypography.bodySmall.scaled(),
        labelLarge = TelevisionTypography.labelLarge.scaled(),
        labelMedium = TelevisionTypography.labelMedium.scaled(),
        labelSmall = TelevisionTypography.labelSmall.scaled(),
    )
}
