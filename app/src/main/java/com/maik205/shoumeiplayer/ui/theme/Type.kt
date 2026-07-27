package com.maik205.shoumeiplayer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

/**
 * §3 - one family. [FontFamily.Default] (Roboto) carries every string in the app;
 * [FontFamily.Monospace] is reserved for clock-like numbers only (player timecodes, seek deltas,
 * durations, resume offsets). Counts, years, ratings, versions, codecs and resolutions are Roboto:
 * mono anywhere else is a costume.
 *
 * Only weights Roboto ships (W300/W400/W500/W700). W300 is the floor below 40sp, thinner weights
 * synthesise badly on some TV builds. Emphasis is weight or italic of the same family, never a
 * second family and never a colour.
 *
 * No component uppercases its string. [labelLarge] is the app's single rationed eyebrow slot
 * (§3.1): the two auth field labels, where a floating TextField label would shrink below 12sp.
 * Any other uppercase tracked label is a defect. Ranking is size and weight; state is alpha
 * (1.0 focused, 0.55 resting, 0.38 disabled).
 */
private val F = FontFamily.Default
private val M = FontFamily.Monospace

val ShoumeiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W300,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W300,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W300,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W400,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W400,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W400,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W500,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W500,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W400,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    // §3.1 - the app's single rationed eyebrow: the two auth field labels, nowhere else.
    labelLarge = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.2.sp,
    ),
    // Small plain labels: chip text, type labels, episode codes. Sentence case, never mono.
    labelMedium = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = F,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
)

/** §3 - extra styles that are not M3 slots. Mono lives here and nowhere else. */
object ShoumeiType {
    /** OSD position / duration. Tabular digits stop the readout jittering as it counts. */
    val Timecode = TextStyle(
        fontFamily = M,
        fontWeight = FontWeight.W400,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp,
    )

    /** Seeking position readout. */
    val TimecodeLarge = Timecode.copy(fontSize = 26.sp, lineHeight = 30.sp)

    /** Runtimes and resume offsets in chrome: `2h 44m`, `1:04:12`. */
    val Duration = TextStyle(
        fontFamily = M,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
}
