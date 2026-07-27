package com.maik205.shoumeiplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * §2.2 — dark only. Parameter names verified against `androidx.tv.material3.ColorScheme` in
 * tv-material 1.1.0: the TV scheme uses `border` / `borderVariant` / `scrim` and has **no**
 * `outline` / `outlineVariant` / `surfaceContainer*` (§7 flag 1).
 *
 * Focused borders use literal [Color.White], **never** `colorScheme.border` — focus must not
 * inherit a muted token.
 */
val ShoumeiColors = darkColorScheme(
    // Filled Play slab.
    primary = Paper,
    onPrimary = Ink000,
    primaryContainer = Ink200,
    onPrimaryContainer = Paper,
    inversePrimary = Ink200,
    // Playback state ONLY — never a button colour, never decoration.
    secondary = Tungsten,
    onSecondary = Color(0xFF1A1206),
    secondaryContainer = Color(0xFF2A1F12),
    onSecondaryContainer = TungstenPale,
    tertiary = Ash600,
    onTertiary = Ink000,
    tertiaryContainer = Ink150,
    onTertiaryContainer = Color(0xFFE4E4E6),
    background = Ink000,
    onBackground = Paper,
    surface = Ink050,
    onSurface = Paper,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ash600,
    // Kill M3 elevation tinting — elevation does not exist in this app.
    surfaceTint = Color.Transparent,
    inverseSurface = Paper,
    inverseOnSurface = Ink000,
    error = Signal,
    onError = Color(0xFF1A0808),
    errorContainer = Color(0xFF2A1113),
    onErrorContainer = Color(0xFFF3C7C7),
    border = Ink300,
    borderVariant = Color(0xFF15151A),
    scrim = Ink000,
)

@Composable
fun ShoumeiPlayerTheme(
    motion: ShoumeiMotion = ShoumeiMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalShoumeiMotion provides motion) {
        MaterialTheme(
            colorScheme = ShoumeiColors,
            shapes = ShoumeiShapes,
            typography = ShoumeiTypography,
            content = content,
        )
    }
}
