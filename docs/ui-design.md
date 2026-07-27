# Shoumei Player UI Design Specification (v2)

Canvas **960 × 540 dp** (1080p @ density 2.0; 4K panels report the same dp box); safe area **864 × 486 dp**, origin (48, 27), and every dp below assumes that box. Stack: `androidx.tv:tv-material3 1.1.0`, compose-foundation Lazy layouts, Coil 3; material3 **only** for `TextField` and `LinearProgressIndicator`. Dark only, one theme, no inversions.

v2 supersedes v1. The look is unchanged (true black, white edge-lit focus, one tungsten accent, 10-foot legibility); the chrome is stripped. Gone: the eyebrow-plus-rule-plus-count row header, the ghost numerals, the per-row hairlines, mono on every numeral, three of the four radii.

## 1. Concept

Shoumei means both *proof* and *lighting*. The UI is the lighting rig, never the subject: the room is true black, the artwork is the only lit thing, and the chrome is plain type carrying real Jellyfin metadata.

1. **Focus is literal illumination.** Unfocused art sits under a black veil; focused art loses the veil, gains a white edge light, and lifts. Nothing else competes for attention.
2. **One accent, one meaning.** `Tungsten #E8B472` marks *playback state only* (resume, progress, now playing, selected track). Never a button colour, never decoration, never a second accent anywhere.
3. **Hierarchy comes from type, not devices.** Size, weight and alpha do the ranking. No uppercase tracking, no folio numbers, no rules under headings, no index labels. A row is titled the way a person would say it.

Anti-goals, everywhere: hero billboards, brand hue, `#121212`, pills, blur or glass, `Modifier.shadow` depth, tonal elevation, springs and overshoot, shimmer, spinners over content, ripples (`indication = null`), text over unscrimmed art, centred layouts outside dialogs, type under 12sp (under 14sp if it carries meaning), and **long dashes of any kind in user-visible strings** (ranges take a hyphen, pauses take a comma).

## 2. Color tokens

### 2.1 `ui/theme/Color.kt` (unchanged from v1, audited clean)

```kotlin
val Ink000 = Color(0xFF000000) // background, OLED-first true black
val Ink050 = Color(0xFF08080A) // surface: sheets, focused rows
val Ink100 = Color(0xFF101014) // surfaceVariant: placeholders, skeletons, resting chips
val Ink150 = Color(0xFF17171C) // dialogs, track panel   |   Ink200 = pressed / raised
val Ink200 = Color(0xFF1F1F26); val Ink300 = Color(0xFF2E2E36) // Ink300 = resting hairline border
val Ash400 = Color(0xFF6E6E76); val Ash600 = Color(0xFFA8A8AF) // disabled; onSurfaceVariant metadata
val Paper  = Color(0xFFF5F5F2) // primary text, filled-slab fill (faintly warm white)
val Lit    = Color(0xFFFFFFFF) // focus border ONLY
val Tungsten = Color(0xFFE8B472); val TungstenPale = Color(0xFFF6DFC0)
val Signal   = Color(0xFFE06C6C) // error, desaturated, never fire-engine red
object Alpha {  // compose over black; never invent new hexes
    const val VeilUnfocused = 0.24f; const val Hairline = 0.08f
    const val TrackInactive = 0.16f; const val TrackBuffered = 0.28f
    const val TextTertiary = 0.55f; const val TextDisabled = 0.38f; const val Watched = 0.55f
}
```
`Alpha.Ghost` is deleted with the ghost numeral (§5.5). Contrast pairs that must survive review: `Paper #F5F5F2` (21:1) and `Ash600 #A8A8AF` (9.3:1) on `Ink000`. `Ash600` is the floor for any text carrying meaning.

### 2.2 `darkColorScheme`, `ui/theme/Theme.kt`

```kotlin
val ShoumeiColors = darkColorScheme(
    primary = Paper, onPrimary = Ink000,                       // filled Play slab
    primaryContainer = Ink200, onPrimaryContainer = Paper, inversePrimary = Ink200,
    secondary = Tungsten, onSecondary = Color(0xFF1A1206),     // playback state ONLY
    secondaryContainer = Color(0xFF2A1F12), onSecondaryContainer = TungstenPale,
    tertiary = Ash600, onTertiary = Ink000, tertiaryContainer = Ink150, onTertiaryContainer = Color(0xFFE4E4E6),
    background = Ink000, onBackground = Paper, surface = Ink050, onSurface = Paper,
    surfaceVariant = Ink100, onSurfaceVariant = Ash600,
    surfaceTint = Color.Transparent,                           // kill M3 elevation tinting
    inverseSurface = Paper, inverseOnSurface = Ink000, error = Signal, onError = Color(0xFF1A0808),
    errorContainer = Color(0xFF2A1113), onErrorContainer = Color(0xFFF3C7C7),
    border = Ink300, borderVariant = Color(0xFF15151A), scrim = Ink000,
)
```
Focused borders use literal `Color.White`, **never** `colorScheme.border`: focus must not inherit a muted token. One scheme, no `isInDarkTheme` branch, no section-level inversion. The `Paper` slab with an `Ink000` label is a component treatment, not an inverted surface.

### 2.3 Scrims, `ui/theme/Scrims.kt` (declared once; never hand-roll a gradient in a screen)

```kotlin
object Scrims {  // all stops are aRRGGBB on black
    val DetailWipe = Brush.horizontalGradient(0f to Color(0xFF000000), .30f to Color(0xF2000000), .58f to Color(0x99000000), .86f to Color(0x1F000000), 1f to Color(0x00000000))
    val BottomSettle = Brush.verticalGradient(0f to Color(0x00000000), .45f to Color(0x4D000000), .78f to Color(0xD9000000), 1f to Color(0xFF000000))
    val AmbientDamp = Brush.verticalGradient(0f to Color(0xB3000000), .38f to Color(0xD9000000), .72f to Color(0xFA000000), 1f to Color(0xFF000000))
    val AmbientLeft = Brush.horizontalGradient(0f to Color(0xFF000000), .34f to Color(0x99000000), .70f to Color(0x00000000))
    val OsdBottom = Brush.verticalGradient(0f to Color(0x00000000), .40f to Color(0x73000000), .75f to Color(0xCC000000), 1f to Color(0xF2000000))
    val OsdTop = Brush.verticalGradient(0f to Color(0xC2000000), 1f to Color(0x00000000))      // top 112dp
    val TopVignette = Brush.verticalGradient(0f to Color(0xB3000000), 1f to Color(0x00000000)) // under headers
    val CardFoot = Brush.verticalGradient(.45f to Color(0x00000000), 1f to Color(0xE6000000))  // wide-card text
}
```

## 3. Typography, `ui/theme/Type.kt`

One family: `FontFamily.Default` (Roboto). `FontFamily.Monospace` is reserved for **timestamps and durations only**. Use the weights Roboto ships (W300/W400/W500/W700); **W300 is the floor below 40sp**, thinner weights synthesise badly on some TV builds. Emphasis is weight or italic of the same family, never a second family and never a colour.

```kotlin
private val F = FontFamily.Default; private val M = FontFamily.Monospace

val ShoumeiTypography = Typography(   // slot = TextStyle(family, weight, size, lineHeight, letterSpacing)
    displayLarge   = TextStyle(F, FontWeight.W300, 56.sp, lineHeight = 60.sp, letterSpacing = (-1.5).sp),
    displayMedium  = TextStyle(F, FontWeight.W300, 40.sp, lineHeight = 46.sp, letterSpacing = (-0.8).sp),
    displaySmall   = TextStyle(F, FontWeight.W300, 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineLarge  = TextStyle(F, FontWeight.W400, 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(F, FontWeight.W400, 24.sp, lineHeight = 30.sp),
    headlineSmall  = TextStyle(F, FontWeight.W400, 22.sp, lineHeight = 28.sp),
    titleLarge     = TextStyle(F, FontWeight.W500, 20.sp, lineHeight = 26.sp, letterSpacing = 0.1.sp),
    titleMedium    = TextStyle(F, FontWeight.W500, 18.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp), // row titles
    titleSmall     = TextStyle(F, FontWeight.W500, 16.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
    bodyLarge      = TextStyle(F, FontWeight.W400, 18.sp, lineHeight = 28.sp, letterSpacing = 0.1.sp),
    bodyMedium     = TextStyle(F, FontWeight.W400, 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodySmall      = TextStyle(F, FontWeight.W400, 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelLarge     = TextStyle(F, FontWeight.W500, 14.sp, lineHeight = 18.sp, letterSpacing = 1.2.sp),  // §3.1 only
    labelMedium    = TextStyle(F, FontWeight.W400, 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelSmall     = TextStyle(F, FontWeight.W400, 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)
object ShoumeiType {  // extra styles, not M3 slots
    val Timecode      = TextStyle(M, FontWeight.W400, 18.sp, lineHeight = 22.sp, letterSpacing = 0.5.sp)
    val TimecodeLarge = Timecode.copy(fontSize = 26.sp, lineHeight = 30.sp)
    val Duration      = TextStyle(M, FontWeight.W400, 16.sp, lineHeight = 22.sp)  // "2h 44m", "1:04:12"
}
```
Deleted in v2: `ShoumeiType.Ghost` (served the ghost numeral only), `Wordmark` (tracked-out display type used once), `SpecValue` (mono spec values, now plain `bodyMedium`).

### 3.1 Type rules

- **Mono is for clock-like numbers only**: player timecodes, seek deltas, durations, resume offsets. Counts, years, ratings, versions, codecs, resolutions and badges are `FontFamily.Default`; mono anywhere else is a costume.
- **No uppercase transforms in components.** `RowHeader`, `ScreenScaffold` and `SlabButton` render the string as authored. Row titles, buttons, section headings and chips are sentence case.
- **Eyebrow budget: one use in the whole app.** `labelLarge` uppercase exists solely for the two auth field labels in §5.5, where it replaces a floating `TextField` label that would otherwise shrink below 12sp. Any other uppercase tracked label is a defect.
- **Ranking is size and weight; state is alpha** (0.55 resting, 1.0 focused, 0.38 disabled). Titles clamp `maxLines = 2`, `Ellipsis`, `TextAlign.Start`. Copy caps at 420dp (Detail) or 560dp (auth, Settings); a paragraph running the full 864dp is a defect.

### 3.2 Metadata lines and separator discipline

A metadata line carries **at most one** `·`. Three or more fields use a tab rhythm, not a dot chain: `2019    TV-MA    2h 44m` on Detail, `Direct Play · 1080p` on the OSD, `Wednesday, Season 2` on an episode. Never nest a joined list inside another joined list; the v1 genre bug put five dots on one line. A list needing its own separators gets its own line. Pipes are never used. Decorative status dots are banned; squares carrying **real state** stay (selected audio or subtitle track, watched marker).

## 4. Shape and focus

### 4.1 One radius system

```kotlin
val ShoumeiShapes = Shapes(                 // ui/theme/Shape.kt
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(4.dp), extraLarge = RoundedCornerShape(4.dp),
)
```
**4dp is the only radius in the app**: cards, chips, slab buttons, dialogs, skeleton blocks. `RectangleShape` (0dp) applies only where an element is flush to an edge or is a bar: full-bleed imagery (Detail backdrop, Home ambient, player surface), progress and seek bars, chapter ticks, the scrubber, edge-light focus bars, and the right-anchored track panel. No pills, no circles, no asymmetric corner sets, no 2/6/8dp variants.

#### 4.1a Amendment — the player OSD (docs/osd-v3.md §6)

The **player screen only** is granted a documented exception to the radius lock, approved with
the OSD v3 spec. Its control track is a YouTube-TV chip row, and a chip that expands into a
labelled pill on focus is the entire focus affordance; forcing 4dp onto it produces a rounded
rectangle that reads as a mis-sized button rather than a control.

Permitted inside `ui/screens/player/` and nowhere else:

| Element | Radius |
| --- | --- |
| Transport / options chips (resting 42dp icon button, focused pill) | circular / fully rounded pill |
| Panel list items (subs, audio, speed, quality) | 21dp pill |
| Shelf card thumbs ("More like this") | 8dp |
| Person portraits (Cast shelf) | circular |
| Trickplay preview frame | 8dp |
| Up Next card and its thumb | 8dp / 4dp inner |
| Seek scrubber (`3 × 14dp`) | 1.5dp, i.e. a pill end |

Everything else on the player stays per this document: colours, type ramp, motion durations,
the buffering hairline at the top, the scrims, and the `RectangleShape` seek track, buffered
segment, played fill and chapter ticks of §4.1 above. The scrubber is the one bar that moved:
osd-v3 §3 specifies a *rounded* `3 × 14dp` mark, so it takes the pill end above and the v2
rectangle is retired for the player only. The exception is not a licence for pills anywhere else
in the app — Home, Library, Detail, Search and Settings remain 4dp-only.

**Browse v2 extension (M-B7):** the osd-v3 §6 pill amendment now also covers two v2-only,
non-player elements: **browse filter chips** (`ChipRow`/`PillChip`, §3.5 of
`docs/browse-v2-plan.md`) at 18dp radius, 36dp tall, and the **nav rail** selected-item pill
at 18dp radius. Both are additive exceptions alongside the player OSD list above, not a
reopening of the 4dp lock elsewhere. Text chips (season chips, track lists) remain the §4.3
underline idiom below, not a pill; `Tungsten` still colours playback only and never a chip
of either kind.

### 4.2 Focus, the hazard kit (preserve verbatim)

Resting to focused, three simultaneous signals: **(a) veil** `Black @0.24 → 0`, **(b) edge light** `none → 2.dp #FFFFFF` in the card shape, **(c) inner hairline** `none → 1.dp Black @0.55` inside the rim, plus **(d) scale** `1.00 → 1.08` poster / `1.06` wide / `1.04` grid tile / `1.03` slab button. (c) is load bearing: it keeps the white rim readable against pale artwork. (b) is the non-negotiable signal. The v1 `Glow` is removed per feasibility flag 2: a white spot shadow on black is near invisible and cost a fourth tween.

```kotlin
// ui/components/FocusSurface.kt, canonical card container
Surface(
    onClick = onClick,
    modifier = Modifier
        .zIndex(if (focused) 1f else 0f)                        // else the neighbour clips the scale
        .graphicsLayer { transformOrigin = TransformOrigin(0.5f, 0.62f) }, // grows upward, feet aligned
    shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent, pressedContainerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContentColor = MaterialTheme.colorScheme.onSurface),
    border = ClickableSurfaceDefaults.border(border = Border.None,
        focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(4.dp)),
        pressedBorder = Border(BorderStroke(2.dp, Tungsten), shape = RoundedCornerShape(4.dp))),
    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f, pressedScale = 1.04f),
    glow = ClickableSurfaceDefaults.glow(Glow.None, focusedGlow = Glow.None),
) { /* PosterImage + animated veil Box(Color.Black.copy(alpha = veil)) + inner hairline */ }
```

**Clipping hazards, fixed once in the shared components:** every Lazy row or grid needs `contentPadding` at or above overscan **plus 12dp** on the cross axis and at least 14dp of vertical breathing room per row, and the row `Column` must not `clipToBounds()`, or the 1.08 scale is sliced by the viewport edge. Every lazy container carries `focusRestorer()`. Cross-boundary focus is wired explicitly (`focusProperties { down = ... }`), never left to default focus search.

### 4.3 Non-card focusables, depth, and where hairlines are allowed

- **Full-width rows** (Settings, dialog rows) never scale; a scaled full-width row reads as broken. Instead: background `Ink050`, a `3.dp × rowHeight` `#FFFFFF` edge-light bar on the leading edge, label `#FFFFFF`.
- **Text chips** (seasons, track lists) have zero container: inactive `@0.55`; selected `#FFFFFF` with a `2.dp Tungsten` underline; focused `#FFFFFF` with a `2.dp White` underline; selected and focused = white underline with a 4dp tungsten cap at the leading end. `Tungsten` is still playback-only elsewhere and never colours a chip's fill or border. Browse filter/sort chips are `PillChip` (§4.1a Browse v2 extension), not this text-chip idiom.
- **Slab buttons**: primary = `Paper` fill, `#000000` label, 52dp tall, 28dp h-padding, sentence case; focused to `#FFFFFF` fill, 2dp white border, scale 1.03. Secondary = transparent with `1.dp Ink300`; focused to a 2dp white border and `#FFFFFF @0.06`.
- **Elevation does not exist**: `surfaceTint = Transparent`, no `Modifier.shadow()`, no glass. Depth is three planes: **0** image or black, **1** scrim gradient, **2** chrome (type, edge light). Dialogs are Plane 2: `Ink150` fill, `1.dp #FFFFFF @8%` hairline, 4dp radius, over a `#000000 @0.72` scrim.
- **Hairlines** are permitted only where they separate two groups of different kinds of content: between the Settings *Server* and *About* groups, above the destructive Sign out row, and as the left edge of the track panel. Banned: rules under row headings, rules between rows inside a group, rules between spec rows, periodic rules in the grid, and any rule whose job is to look designed.

## 5. Per-screen art direction

`ui/theme/Dimens.kt`: `OverscanHorizontal 48` · `OverscanVertical 27` · `Gutter 96` · `CardWidth 160` · `CardHeight 240` · `PosterLabelHeight 52` · `WideCardWidth 280` · `WideCardHeight 158` · `EpisodeCardWidth 320` · `EpisodeCardHeight 180` · `WideLabelHeight 56` · `ItemSpacing 16` · `RowSpacing 40` · `RowTitleGap 24` · `GridHSpacing 16` · `GridVSpacing 28` · `BodyMaxWidth 420`. Deleted: `FolioTop`. Coil (`ImageUrlBuilder` maxWidth px, quality 90): poster 320, wide 560, episode thumb 640, Detail backdrop 1920, Home ambient 1280 (scrimmed to ~20%, do not pay for 1920 twice). Blurhash stays (`ImageBlurHashes`); a flat `Ink100` rect with a `1.dp #212125` inset hairline is the fallback when a hash is absent. `crossfade(220)`, never a shimmer.

**Row header**, shared by Home, Detail, Library and Settings (`ui/components/RowHeader.kt`): one `Text`, nothing else. `titleMedium` sentence case at x=48, alpha `0.55` resting to `1.0` when the row owns focus, `RowTitleGap 24` below. No uppercase, no tracking, no rule, no count, no folio.

### 5.1 Home

No carousel, no logo art, no trailer autoplay. The *focused card's own* backdrop washes the screen at `alpha 0.20` under `AmbientDamp` and `AmbientLeft`: the room takes the colour of whatever you point at.

- **Top bar** y=27, h=40: search and settings glyphs only, 24dp `@0.55`; focused to `#FFFFFF`, scale 1.15, plus a `2.dp × 28dp` white underline. No chips, no labels, no background.
- **Ambient header** y=88 to 182: the focused item's title (`headlineLarge`, 1 line) over its metadata line (`bodyMedium @ Ash600`, §3.2). The v1 clock stamp and its 30s tick loop are deleted. The header is empty before first focus, and focus lands on the first card in the first frame.
- **Row titles** are plain sentences: "Continue watching", "Next up", "Latest in Films", "My media".
- **Poster card** 160×240, radius 4, gap 16; title *below* the art (`titleSmall`, 8dp gap, 1 line, `@0.55` to `@1.0`), subtitle `bodyMedium @ Ash600`; the label block is fixed at 52dp so the row never reflows. **Wide card** 280×158 (My media, Next up); My media sets the library name *inside* the art at bottom-left over `CardFoot`, `titleSmall`. **Progress overlay**: a `3.dp` bar flush to the art's bottom edge, inset 0, radius 0, `Tungsten` on `#FFFFFF @16%`, a light leak rather than a widget, never a percentage label.
- Row `contentPadding = PaddingValues(start = 48.dp, end = 24.dp)`: the fifth poster deliberately clips to signal scrollability without a chevron. `RowSpacing 40`; first row title at y=196. At rest the screen holds the ambient header, row 1, and roughly 48dp of row 2.

### 5.2 Detail

Backdrop **wiped from the left**, not scrimmed from the bottom: type sits on solid black and art stays uncropped on the right. Layers: backdrop (`Crop`, `alignment = CenterEnd`, alpha 0.85), `DetailWipe`, `BottomSettle` over the lower 200dp.

Copy column, x=48, width 420: **y=96** title `displayLarge` 56sp, at most 2 lines · **y=176** metadata line `bodyMedium @ Ash600` on the §3.2 tab rhythm (`2019    TV-MA    2h 44m`, duration in `ShoumeiType.Duration`) · **y=212** resume bar `3.dp × 240dp` `Tungsten` on `#FFFFFF @16%`, only when `PlaybackPositionTicks > 0` · **y=248** overview `bodyLarge @ onSurface 0.82`, `maxLines = 4` · **y=380** actions `Resume 1:04:12` (filled slab, initially focused), `Play from start`, `More` · **y=452** season chips (series only) per §4.3, 32dp apart.

- **No poster.** Backdrop plus title *is* the identity. Fallback when only `Primary` exists: render it right-aligned 220×330 at x=660, y=105, `DetailWipe` unchanged.
- **Spec block**, x=684 to 912, right-aligned, from y=196, hidden on Series: a **two-column block** of 4 to 5 rows from `MediaStreams`, `RunTimeTicks`, `OfficialRating` and `CommunityRating`. Label `bodySmall @0.55` left, value `bodyMedium Paper` right, 14dp row pitch, **no rules between rows**: `Runtime 2h 44m` (mono value), `Rating TV-MA`, `Audio 5.1 EAC3`, `Subtitles 3`, `Codec HEVC`. One rating scale only: `CommunityRating` renders as `Score 8.4` and the critic percentage is dropped.
- **Genres** take their own line under the overview, joined by `, `, never nested inside another join.
- **Episodes** under the heading "Episodes" (no count): 320×180 thumbs with an `S1:E04` badge (`labelSmall @0.7`, top-left, 8dp inset, no plate); title `titleMedium` and runtime `bodySmall` *below* the card. Watched = thumb `alpha 0.55` plus a 12dp white check bottom-right, never a coloured dot; focus restores alpha over 180ms. **Cast** uses the same row under the heading "Cast".

### 5.3 Player OSD

No panel, no card, no rounded container: typography and one rule of light over the frame. Screen background and letterboxing are pure `Color.Black`.

- **Top plate** 112dp (`OsdTop`): y=40 title `headlineSmall @1.0`; y=70 `S1:E04    Direct Play · 1080p` (`bodyMedium @0.55`, one dot maximum, episode code on its own tab column); wall clock right at x=912, `ShoumeiType.Timecode @0.38`. **Bottom plate** 236dp (`OsdBottom`), y=404 timecodes: position left at x=48 (`ShoumeiType.Timecode`, `Paper`), duration right at x=912 (`@ Ash600`); tabular, so digits never shift.
- y=440 **seek bar** x=48 to 912: track `#FFFFFF @16%`, height `4dp` growing to `6dp` when it holds focus (`animateDpAsState`, 140ms); buffered `#FFFFFF @28%`; played fill `Tungsten`. **The scrubber is a `4.dp × 18.dp` vertical `#FFFFFF` registration mark**, never a circle, present only while the bar has focus, entering `scaleIn(0.4f to 1f, 140ms)`. **Chapter ticks**: `1.dp × 10.dp` `#FFFFFF @0.45` marks on the track at chapter starts, drawn under the fill; UP and DOWN jump chapter. LEFT and RIGHT seek 10s; held past 600ms they seek 30s every 200ms. While seeking, the position switches to `TimecodeLarge` in `Tungsten`, the fill beyond it ghosts at `Tungsten @45%`, and a delta (`-00:30`) renders beside it in `ShoumeiType.Timecode Tungsten`.
- y=478 **transport** at x=48, 28dp glyphs, 24dp apart, `@0.55` to `@1.0` plus 1.15 scale and a `2.dp × 32dp` white underline on focus; no circular backgrounds ever. Right at x=912: `Audio` / `English` and `Subtitles` / `Off` as stacked text buttons (`bodySmall @0.55` label over `titleSmall Paper` value), focus underlines 2dp white; the active subtitle track keeps its `6dp` tungsten square (real state).
- **Track panel**: right-anchored, 360dp wide, full height minus overscan, `Ink150 @0.96`, left hairline `#FFFFFF @8%`, `RectangleShape` because it is flush to the screen edge. Rows 56dp `titleMedium`; the selected row is prefixed with a `6dp` tungsten square, not a check; the focused row takes the edge-light bar; enters `slideInHorizontally` 220ms.
- OSD content is **100% opacity**; the plate carries the transparency. Auto-hide 5000ms; the hidden state renders *nothing*; the timer resets on any key and is suspended while the track panel is open or the seek bar is held.

### 5.4 Library

- Pinned header y=27 to 124: the library's own name at `displayMedium`, x=48. No label above it (the name says what it is), no item-count readout, no rule under it. Sort and filter chips at y=140 per §4.3, 24dp apart, text only. `TopVignette` over the top 72dp so tiles dissolve under the header.
- Grid from y=188: `LazyVerticalGrid(GridCells.Fixed(5))`, `spacedBy(16.dp)` and `spacedBy(28.dp)`, `contentPadding = PaddingValues(start = 48, end = 48, top = 20, bottom = 54)`, `Modifier.focusRestorer()`. Tile 160×240 plus a 52dp label block. `GridCells.Adaptive(160.dp)` is an acceptable fallback; `Fixed(5)` is the intended rhythm. Focus scale here is **1.04**; 1.08 collides visually in a grid. Watched: poster `alpha 0.55` plus a 14dp white check, 8dp inset top-right.
- **No periodic rules and no position markers in the grid.** The content conveys scroll position.
- Paging: the next page fades in 220ms from `Ink100` skeleton tiles; "loading more" is a `2.dp` tungsten indeterminate bar pinned to the screen's bottom edge. No spinner in the grid.

### 5.5 Sign-in (ServerEntry and Login)

Pure black, one left-anchored column at x=**96** (`Gutter`), vertically centred, width 480, with a great deal of empty space to its right. The focal element is an oversized plain-language heading, not a numeral.

```
y=132  Connect to your server     displayLarge 56sp W300 Paper   (Login: "Sign in")
y=236  SERVER ADDRESS             labelLarge @0.55   (the app's single rationed eyebrow)
y=264  http://…:8096              displaySmall, underline field, width 480
y=312  2dp rule, Ink300 to White on focus, becomes indeterminate while working
y=332  helper or error line       bodySmall
y=396  [ Connect ]                slab 52dp
```

- **No ghost numeral, no step counter, no wordmark.** The app name appears once, quietly, at x=96 y=48 in `titleSmall @0.38`; the heading carries the screen and the right two thirds of the canvas stay empty on purpose.
- **The underline is the progress indicator.** Fields are underlines, not boxes: material3 `TextField` with `TextFieldDefaults.colors(focusedContainerColor = Transparent, unfocusedContainerColor = Transparent, focusedIndicatorColor = White, unfocusedIndicatorColor = Ink300, cursorColor = Tungsten, focusedTextColor = Paper, unfocusedTextColor = Ash600)`, `textStyle = displaySmall`, **no label** (the eyebrow above replaces it, since floating labels shrink below 12sp). While connecting or signing in, that same 2dp rule becomes an indeterminate `LinearProgressIndicator` (`White` on `Ink300`) in exactly its own place; nothing else moves, and no spinner appears on these screens. Each `TextField` needs an explicit `FocusRequester`, because TV default focus is far too quiet at 10 feet.
- **Success**: `Connected to "Basement NAS", version 10.9.11` in `bodyMedium Tungsten`. No square, no check icon, no green. **Failure**: the same line in `Signal` plus a **160ms, ±8dp, 2-cycle** horizontal shake on the field. Never a dialog, never a toast.
- Login is the same layout with two field blocks 88dp apart. The Quick Connect note sits bottom-left with **no hairline above it**, sentence case, `bodySmall @0.38`, non-focusable: `Quick Connect is not available yet`.

### 5.6 Search

- x=48, y=64: a search glyph 28dp `@0.55`, then the live query at `displayMedium`, caret `Tungsten`, initially focused; an `864dp × 2dp` rule at y=124, `#FFFFFF` while the field holds focus. **No result count and no searching label**: during the 350ms debounce the grid renders skeleton tiles, which is the loading state.
- Results from y=188 use the **same** `Fixed(5)` grid composable as Library, plus a sentence-case type label (`Film`, `Series`, `Episode`) at `bodySmall @0.55` above each title, because mixed-type results are otherwise ambiguous. One label per tile, no uppercase, no per-tile rule.
- States are left-aligned at x=48, y=220, never centred (§6). Wire focus explicitly: `focusProperties { down = gridFirst }` on the field and `up = fieldRequester` on grid row 0.

### 5.7 Settings

- Single column, rows **72dp**, spanning x=48 to 912. Label `titleMedium` at x=48; value `bodyMedium @ Ash600` right-aligned to x=912; secondary value `bodySmall @0.38` on a second line, sentence case (`Version 10.9.11`).
- **Rows are grouped, not ruled.** No divider under each row. Group headings ("Server", "Playback", "About") use the §5 row header 48dp above their group, and one `1.dp #FFFFFF @8%` rule separates group from group.
- Pure-info rows (server URL, version, engine name) are **non-focusable**. A focused actionable row takes background `Ink050`, a `3.dp × 72dp` white edge-light bar at x=48, and a `#FFFFFF` label, with **no scale**. Engine name (`SimulatedPlayerEngine` or `MplayerEngine`) and app version ship as ordinary value rows.
- **Sign out** is the last row, under the one permitted hairline: label `Signal`, resting border `1.dp errorContainer`, focused taking the same white edge light so the destructive colour stays in the text. Confirmation dialog required.

## 6. States, required on every screen

Every screen ships three states. None is a centred spinner and none is a single word.

- **Loading is a skeleton of the content**: `Ink100` blocks at 4dp radius in the shape of what is coming, static, no shimmer and no pulse. `ui/components/StateViews.kt` exports `SkeletonRow` (5 tiles plus label block), `SkeletonGrid` (10 tiles), `SkeletonLedger` (6 rows) and `SkeletonDetail` (title bar, meta bar, three text bars). Skeletons replace `R.string.loading`, and they use the real content's fixed dimensions so the swap does not jump.
- **Empty is composed and actionable**: a `displaySmall @0.55` sentence left-aligned at x=48, an optional `bodyMedium @ Ash600` line under it, and one slab button. Home: `Nothing to watch yet` plus `Open settings`. Library: `No items match this filter` plus `Clear filter`. Search under 2 characters: `Type to search`, no button. Search with no match: `Nothing matched "hoth"` plus `Clear search`.
- **Error is inline and plain**: a `displaySmall` headline, a `bodyMedium @0.55` detail in ordinary language (no error codes unless the user can act on them), and one white slab reading `Try again`. Never a dialog, never a toast, never an icon. The player error uses the same construction over black.

## 7. Motion budget, `ui/theme/Motion.kt`

**Four systems, nothing else.** Anything not listed here does not animate.

1. **Focus feedback.** Scale, veil and rim run on **one** tween per focus event, because the only job is telling a 10-foot viewer where the D-pad is, and v1's four staggered tweens made that answer arrive late.
2. **State transitions.** OSD show and hide, track panel slide, seek and progress fill, play/pause glyph crossfade: each renders a change the user just caused or a position that is really moving.
3. **Indeterminate loading.** The buffering hairline, the auth underline and the grid paging bar exist only while the app is genuinely waiting on the network or the decoder.
4. **Content transitions.** Route fades, the Home ambient crossfade and the Detail column fade keep a full-screen content swap from being a hard cut at 55 inches.

```kotlin
object Ease {
    val Decel    = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f) // arriving, lights up
    val Accel    = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f) // leaving, lights out
    val Standard = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f) // scroll
}
object Dur {  // tagged by motion system: 1 focus, 2 state, 3 indeterminate, 4 content
    const val FocusIn = 140; const val FocusOut = 100                                          // 1
    const val OsdIn = 160; const val OsdOut = 240; const val OsdAutoHide = 5000                // 2
    const val PanelIn = 220; const val ProgressValue = 240; const val ScrubTick = 90           // 2
    const val GlyphFade = 120; const val WatchedFade = 180; const val Shake = 160              // 2
    const val BufferPulse = 900; const val BufferDelay = 400                                   // 3
    const val ScreenIn = 220; const val ScreenOut = 180; const val AmbientCross = 400          // 4
    const val AmbientDebounce = 250; const val RowScroll = 320; const val GridScroll = 260     // 4
}
```
Deleted in v2: `GlowFade` and `BorderFade` (the glow is gone and the rim shares the focus tween), `VeilFade` (merged into the focus tween), the Home ambient artwork drift, the Home clock loop, and the 40ms per-block Detail stagger.

- **Focus**: `animateFloatAsState(tween(if (focused) FocusIn else FocusOut, easing = if (focused) Decel else Accel))` drives scale, veil and rim together.
- **Row snap**: the focused card lands at the row's leading edge (x=48), never centred. Prefer a custom `BringIntoViewSpec` at `tween(RowScroll, Standard)` with `calculateScrollDistance = { offset, _, _ -> offset }`; if it does not resolve against the pinned foundation version, fall back to `LazyListState.animateScrollToItem(index, scrollOffset = 0)` on focus change. Grids use `GridScroll`; the focused vertical row settles at y=176.
- **Home ambient**: debounce focus 250ms, then `Crossfade(tween(400, LinearEasing))`. Header text `fadeOut 120` then `fadeIn 200 + slideInVertically(+8dp)`. The artwork itself does not move.
- **OSD**: in `fadeIn + slideInVertically(tween(160, Decel)) { it / 6 }`; out `fadeOut + slideOutVertically(tween(240, Accel)) { it / 12 }`; plates slide from their own edges and hide slower than they show. **Progress and seek fill** use `animateFloatAsState(tween(240, LinearEasing))`, linear because eased playback progress reads as a lie. **Buffering** is a `2.dp` full-width line at y=0 pulsing `#FFFFFF` 0.25 to 0.85 on a 900ms loop, delayed 400ms so brief stalls do not flash; it and indeterminate progress are the only loops in the app.
- **Screen transitions**: `fadeIn(tween(220))` and `fadeOut(tween(180))`; Detail adds `scaleIn(initialScale = 0.98f, tween(220, Decel))`, its backdrop `fadeIn 400ms`, and **one** `fadeIn(220) + slideInVertically(+16dp)` for the whole copy column rather than per block. **No horizontal slides**, a phone idiom that reads cheap at 55 inches. Exits never animate position; they fight Back.
- Nothing exceeds 420ms. No springs and no overshoot: a light does not bounce, and a card still moving when the next D-pad press lands reads as lag. Gate `scale` behind `LocalShoumeiMotion` for weak SoCs; the white rim alone must still satisfy "focus is always visible".

## 8. Application checklist, spec to files

| # | Spec | Files | Work |
|---|---|---|---|
| A1 | §2 | `ui/theme/Color.kt`, `Theme.kt`, `Scrims.kt` | No change except deleting `Alpha.Ghost`. One scheme, no branch; no gradient may be declared outside `Scrims.kt`. |
| A2 | §3 | `ui/theme/Type.kt` | `labelMedium` and `labelSmall` to `FontFamily.Default` with 0.2sp tracking; `labelLarge` to 14sp/1.2sp, documented as the single eyebrow slot; delete `Ghost`, `Wordmark`, `SpecValue`; add `Duration`. |
| A3 | §4.1 | `ui/theme/Shape.kt` | Collapse all five slots to 4dp; delete the 2/6/8dp variants and the asymmetric track-panel shape. |
| A4 | §7 | `ui/theme/Motion.kt` | Delete `GlowFade`, `VeilFade`, `BorderFade`; add `PanelIn`, `GlyphFade`, `BufferPulse`, `BufferDelay`, `Shake`, `WatchedFade`; tag each constant with its motion system. |
| A5 | §5 | `ui/theme/Dimens.kt` | Delete `FolioTop`. Everything else unchanged. |
| B1 | §4.2 | `ui/components/FocusSurface.kt` | Set `focusedGlow = Glow.None` and put veil on the focus tween. Keep `zIndex`, `graphicsLayer`, `TransformOrigin(0.5f, 0.62f)`, the 2dp literal-white rim and the inner hairline **verbatim**. |
| B2 | §5 | `ui/components/RowHeader.kt` | Rewrite: one sentence-case `titleMedium`, alpha 0.55 to 1.0 on row focus. Delete `uppercase()`, the hairline, the folio and `fun folio()`. |
| B3 | §3.1 | `ui/components/ScreenScaffold.kt`, `SlabButton.kt` | Drop `title.uppercase()` and `text.uppercase()`; render strings as authored. |
| B4 | §3.2 | `ui/components/ItemMapping.kt` | Cap `SpecSeparator` at one use per line; add a tab-column joiner for 3 or more fields; un-nest joined lists. |
| B5 | §6 | `ui/components/StateViews.kt` | Add `SkeletonRow`, `SkeletonGrid`, `SkeletonLedger`, `SkeletonDetail`; add an action slot to `EmptyView`; delete the word-only loading view. |
| B6 | §5.1 | `ui/components/MediaCard.kt`, `MediaRow.kt`, `PosterImage.kt`, `BlurHashPainter.kt` | No taste change. Keep the hand-drawn tungsten progress `Box`, blurhash placeholders, the `Ink100` inset-hairline fallback, glow bleed padding, no `clipToBounds`, `focusRestorer()`. Recompile against the new `RowHeader` signature. |
| C1 | §5.1 | `ui/screens/home/HomeScreen.kt` | Delete the clock stamp and its `while(true)` loop; the header starts at the focused title. Plain row titles. Empty state gains `Open settings`. |
| C2 | §5.4 | `ui/screens/library/LibraryScreen.kt`, `LibraryGrid.kt` | Delete the `LIBRARY` label, the item-count readout, `FolioRule`, `FOLIO_INTERVAL`, the `folioRules` param and the dashed count string it formats. Per-tile label to sentence case `bodySmall @0.55`. Add the skeleton grid and a `Clear filter` empty state. |
| C3 | §5.2 | `ui/screens/detail/DetailScreen.kt`, `DetailViewModel.kt` | Replace the `EN_DASH` constant with a hyphen (`2016-2022`, `2016-`) and rebranch the year test. Delete the zero-padded episode count and the `EPISODES` and `CAST` labels. Spec block to two columns, sentence-case labels, no rules, critic score dropped. Un-nest the genre join. One 220ms column fade. Delete the quoted-concept comment. |
| C4 | §5.3 | `ui/screens/player/PlayerOsd.kt`, `PlayerScreen.kt`, `TrackDialog.kt` | Meta line to one `·` maximum; `AUDIO` and `SUBS` to `Audio` and `Subtitles`; track panel to `RectangleShape`. **Keep** the hand-drawn seek bar, chapter ticks and chapter nav, registration-mark scrubber, timecode mono, tungsten selection square, buffering pulse, `Color.Black`, and the 5000ms OSD timing and suspension rules. |
| C5 | §5.5 | `ui/screens/serverentry/ServerEntryScreen.kt`, `login/LoginScreen.kt` | Delete `GhostNumeral`, both step counters, the wordmark, `FolioTop`, the colophon hairline and the `MarkerLine` square. Add the oversized heading. Keep the two field eyebrows, the underline-as-progress indicator, `FocusRequester` on every field and the shake on error. |
| C6 | §5.6 | `ui/screens/search/SearchScreen.kt`, `SearchViewModel.kt` | Delete the searching and result-count labels; add the debounce skeleton grid; `typeEyebrow()` becomes sentence-case `typeLabel()`. Keep the explicit `focusProperties` wiring verbatim. |
| C7 | §5.7 | `ui/screens/settings/SettingsScreen.kt` | Delete `Hairline()` from `LedgerRow`; one rule between groups and one above Sign out. Sentence-case group headings and secondary values. Keep non-focusable info rows, the edge-light focus bar and the sign-out confirmation. |
| C8 | §1 | `res/values/strings.xml` | `Quick Connect is not available yet`. Sweep every user-visible string for long dashes before shipping. |
| D1 | §7 | `ui/navigation/NavGraph.kt` | No change. `fadeIn(220)`, `fadeOut(180)` and Detail's `scaleIn(0.98f)` are motivated. |

### Feasibility flags to resolve during the polish wave

1. **Verify every `darkColorScheme` parameter name against tv-material3 1.1.0 before mass edits.** The TV scheme uses `border`, `borderVariant` and `scrim` and has no `outline` or `outlineVariant`; an unknown named argument is a compile error, not a silent fallback.
2. **`Glow` is API 28+ and maps to the platform spot-shadow colour.** White glow on black is near invisible on many panels, which is why v2 removes it; the 2dp white rim plus the inner dark hairline is the real signal.
3. **`BringIntoViewSpec` and `LocalBringIntoViewSpec` are experimental foundation API** that has moved between versions; if it does not resolve against the pinned BOM, use the `animateScrollToItem` fallback in §7.
4. **material3 `LinearProgressIndicator` (M3 1.4.x) draws a track gap and stop indicator by default.** Every card, Detail and seek progress bar must be a hand-drawn `Box`; use the indicator only for the three indeterminate cases in §7.
5. **material3 `TextField` on TV** needs an explicit `FocusRequester` plus `bringIntoView`; its default focus is far too quiet at 10 feet, hence the recipe in §5.5.
