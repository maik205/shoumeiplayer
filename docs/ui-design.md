# UI design

The television interface uses a dark theme, Outfit typography, fixed layout tokens, and explicit focus behavior. The implementation files listed below are the source of truth.

## Theme sources

| Area | Source |
| --- | --- |
| Colors | `core/designsystem-tv/src/main/java/com/maik205/shoumeiplayer/ui/television/theme/TelevisionColors.kt` |
| Dimensions and shapes | `core/designsystem-tv/src/main/java/com/maik205/shoumeiplayer/ui/television/theme/TelevisionDimensions.kt` |
| Typography | `app/src/main/java/com/maik205/shoumeiplayer/ui/television/theme/TelevisionTypography.kt` |
| Material theme | `app/src/main/java/com/maik205/shoumeiplayer/ui/television/theme/TelevisionTheme.kt` |
| Focus behavior | `core/designsystem-tv/src/main/java/com/maik205/shoumeiplayer/ui/television/components/TelevisionFocus.kt` |

## Colors

The palette uses warm white over near-black surfaces. Color is reserved for state and artwork instead of decorative chrome.

| Token | Value | Use |
| --- | --- | --- |
| `Black` | `#08090A` | Main background and scrim base |
| `BlackRaised` | `#111315` | Raised surfaces and panels |
| `LibraryBackground` | `#0E1012` | Library screen background |
| `Paper` | `#F7F6F2` | Primary text and selected controls |
| `PaperMuted` | `Paper` at 68% | Secondary text |
| `PaperSoft` | `Paper` at 48% | Tertiary text |
| `PaperDisabled` | `Paper` at 28% | Disabled content |
| `ImagePlaceholder` | `#1B1D20` | Artwork placeholders and skeletons |
| `Ember` | `#DF754F` | Playback progress state |

Use `MaterialTheme.colorScheme` or `TelevisionColors` instead of defining screen-local color constants.

## Typography

The application bundles static Outfit font files for regular, medium, semibold, and bold weights. Static files ensure Compose selects the intended weight on Android.

`TelevisionTypography` defines every Material role. `ShoumeiTelevisionTheme` multiplies the system font scale by `1.25` while preserving the device density.

Apply typography roles by hierarchy:

- Display roles for primary titles and hero copy
- Headline and title roles for section headings and card titles
- Body roles for descriptions and metadata
- Label roles for controls and compact status text

Do not encode state with font size alone. Pair text hierarchy with position, opacity, and focus.

## Layout tokens

Shared dimensions keep spacing and media sizes consistent:

| Token | Value |
| --- | ---: |
| `SafeHorizontal` | `48 dp` |
| `SafeTop` | `16 dp` |
| `SafeBottom` | `17 dp` |
| `NavigationHeight` | `36 dp` |
| `FocusRadius` | `6 dp` |
| `ShelfGap` | `27 dp` |
| `TileGap` | `10 dp` |
| `PosterWidth` × `PosterHeight` | `104 dp` × `156 dp` |
| `LandscapeWidth` × `LandscapeHeight` | `210 dp` × `118 dp` |
| `SquareSize` | `116 dp` |

All Material shape roles use the shared `6 dp` corner radius. Full-screen artwork, progress tracks, and other edge-aligned elements may remain rectangular.

## Focus behavior

`TelevisionFocusSurface` uses contrast, opacity, and scale without adding a panel, glow, border, or elevation.

The focus scale values are:

- Navigation: `1.06`
- Actions, landscape cards, posters, and square cards: `1.055`

Focus transitions use short tweens. The default scale and translation duration is `120 ms`; alpha changes use `100 ms` on focus and `80 ms` on blur.

Horizontal media rows use `televisionHorizontalWrap` when navigation must stay within the row. Disabled items do not remain focusable.

## Shared states

Every data-backed screen must represent loading, empty, error, and content states. Use the shared components in `TelevisionStates.kt` so retry actions and focus restoration behave consistently.

Artwork requests must include the rendered size when known. Use the shared image URL builder and cache policies instead of constructing URLs inside a screen.

## Screen composition

Screens should use shared navigation, media, action, and focus components before introducing local variants. Keep route-level composables responsible for state wiring and delegate large visual sections to focused components.

Player-specific controls and overlays are documented in [`player-controls.md`](player-controls.md).
