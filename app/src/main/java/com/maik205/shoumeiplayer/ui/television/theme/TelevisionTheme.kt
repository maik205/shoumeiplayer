package com.maik205.shoumeiplayer.ui.television.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.darkColorScheme as composeDarkColorScheme
import androidx.compose.material3.lightColorScheme as composeLightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.domain.settings.ClientSettings

private const val TelevisionFontScale = 1.25f

/**
 * The Compose-Material scheme, which is what the `androidx.compose.material3` progress indicators
 * scattered across onboarding, the library and the player read.
 *
 * `primary` is the palette accent, so those spinners are the one place the accent already renders
 * today -- and now renders the accent the *viewer* chose rather than a hardcoded orange.
 */
private fun composeSchemeFor(palette: TelevisionPalette) = when {
    palette.isLight -> composeLightColorScheme(
        primary = palette.Ember,
        onPrimary = palette.OnEmber,
        background = palette.Black,
        onBackground = palette.Paper,
        surface = palette.Black,
        onSurface = palette.Paper,
    )

    else -> composeDarkColorScheme(
        primary = palette.Ember,
        onPrimary = palette.OnEmber,
        background = palette.Black,
        onBackground = palette.Paper,
        surface = palette.Black,
        onSurface = palette.Paper,
    )
}

/**
 * The TV-Material scheme.
 *
 * The accent owns the slots tv-material3 reserves for emphasis and focus -- `primary`,
 * `primaryContainer` and `border`/`borderVariant`, the colour `ClickableSurfaceDefaults` draws a
 * focus ring with. Those slots held `Paper` and `Color.Transparent` before #101, which is why
 * `Ember` could never reach a focus ring no matter what it was set to.
 *
 * `inverseSurface`/`inverseOnSurface` stay on the paper/black ends of the ramp because that is what
 * an unstyled focused `Surface` fills with, and inverting the ramp is exactly right in both modes.
 *
 * There is no light/dark variant to pick: `androidx.tv.material3.ColorScheme` carries no mode flag,
 * and every one of its slots is supplied here, so `lightColorScheme` would return the same object.
 */
private fun televisionSchemeFor(palette: TelevisionPalette): ColorScheme = darkColorScheme(
    primary = palette.Ember,
    onPrimary = palette.OnEmber,
    primaryContainer = palette.Ember,
    onPrimaryContainer = palette.OnEmber,
    inversePrimary = palette.Black,
    secondary = palette.PaperMuted,
    onSecondary = palette.Black,
    secondaryContainer = palette.BlackRaised,
    onSecondaryContainer = palette.Paper,
    tertiary = palette.PaperSoft,
    onTertiary = palette.Black,
    tertiaryContainer = palette.BlackRaised,
    onTertiaryContainer = palette.Paper,
    background = palette.Black,
    onBackground = palette.Paper,
    surface = palette.Black,
    onSurface = palette.Paper,
    surfaceVariant = palette.BlackRaised,
    onSurfaceVariant = palette.PaperMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = palette.Paper,
    inverseOnSurface = palette.Black,
    error = palette.Paper,
    onError = palette.Black,
    errorContainer = palette.BlackRaised,
    onErrorContainer = palette.Paper,
    border = palette.Ember,
    borderVariant = palette.Ember,
    scrim = palette.Black,
)

/**
 * Mounts the palette the signed-in viewer chose (#101).
 *
 * The settings are read here rather than passed in because this is the only call site
 * (`MainActivity`), and a parameter with a default is how a personalisation feature ends up wired to
 * nothing. `LocalAppContainer` is provided one level above in `MainActivity`, and the initial value
 * is `ClientSettings()` -- i.e. Midnight -- so the first frame paints the shipped look and swaps
 * only if this account chose otherwise.
 */
@Composable
fun ShoumeiTelevisionTheme(content: @Composable () -> Unit) {
    val settingsStore = LocalAppContainer.current.settingsStore
    val defaults = remember { ClientSettings() }
    val settings by settingsStore.settings.collectAsState(initial = defaults)
    val palette = TelevisionPalettes.resolve(
        choice = settings.colorPalette,
        theme = settings.theme,
        systemInDarkTheme = isSystemInDarkTheme(),
    )

    val systemDensity = LocalDensity.current
    val televisionDensity = remember(systemDensity) {
        Density(
            density = systemDensity.density,
            fontScale = systemDensity.fontScale * TelevisionFontScale,
        )
    }

    CompositionLocalProvider(
        LocalDensity provides televisionDensity,
        LocalTelevisionPalette provides palette,
    ) {
        ComposeMaterialTheme(colorScheme = remember(palette) { composeSchemeFor(palette) }) {
            MaterialTheme(
                colorScheme = remember(palette) { televisionSchemeFor(palette) },
                typography = TelevisionTypography,
                shapes = TelevisionShapes,
                content = content,
            )
        }
    }
}
