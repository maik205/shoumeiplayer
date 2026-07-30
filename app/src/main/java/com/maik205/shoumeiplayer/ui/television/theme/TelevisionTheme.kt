package com.maik205.shoumeiplayer.ui.television.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TelevisionColorScheme = darkColorScheme(
    primary = TelevisionColors.Paper,
    onPrimary = TelevisionColors.Black,
    primaryContainer = TelevisionColors.Paper,
    onPrimaryContainer = TelevisionColors.Black,
    secondary = TelevisionColors.PaperMuted,
    onSecondary = TelevisionColors.Black,
    secondaryContainer = TelevisionColors.BlackRaised,
    onSecondaryContainer = TelevisionColors.Paper,
    tertiary = TelevisionColors.PaperSoft,
    onTertiary = TelevisionColors.Black,
    background = TelevisionColors.Black,
    onBackground = TelevisionColors.Paper,
    surface = TelevisionColors.Black,
    onSurface = TelevisionColors.Paper,
    surfaceVariant = TelevisionColors.BlackRaised,
    onSurfaceVariant = TelevisionColors.PaperMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = TelevisionColors.Paper,
    inverseOnSurface = TelevisionColors.Black,
    error = TelevisionColors.Paper,
    onError = TelevisionColors.Black,
    errorContainer = TelevisionColors.BlackRaised,
    onErrorContainer = TelevisionColors.Paper,
    border = Color.Transparent,
    borderVariant = Color.Transparent,
    scrim = TelevisionColors.Black,
)

@Composable
fun ShoumeiTelevisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TelevisionColorScheme,
        typography = TelevisionTypography,
        shapes = TelevisionShapes,
        content = content,
    )
}
