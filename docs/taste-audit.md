# Taste Audit — Shoumei Player UI

Audited: `docs/ui-design.md`, `ui/theme/**`, `ui/components/**`, `ui/screens/**`, `res/values/strings.xml`.
Paths are relative to `app/src/main/`.

Headline finding: the spec's "numerals are evidence" concept is implemented as a **device stack** — every row
title is at once an uppercase tracked eyebrow, a hairline rule, and a zero-padded folio. One component
(`RowHeader.kt`) breaks three rules simultaneously and is reused on every surface.

## 1. Violation inventory

### R1 EM-DASH BAN — 3 sites, 4 glyphs (user-visible only)

| Location | Text | Fix |
|---|---|---|
| `res/values/strings.xml:22` | `Quick Connect — coming soon` | `Quick Connect is not available yet` |
| `ui/screens/library/LibraryGrid.kt:393` | `"— %03d —".format(count)` | delete (see R3) |
| `ui/screens/detail/DetailViewModel.kt:285,295,297` | `EN_DASH = '–'`; `2016–2022`, `2016–` | hyphen: `2016-2022`, `2016-` |
| `ui/screens/detail/DetailViewModel.kt:279` | `if (it.contains(EN_DASH)) "YEARS"` | branch on the new hyphen constant |

KDoc/comment em-dashes (~90 occurrences across `ui/**`) are **not** user-visible and are out of scope.
Do not sweep them; churn without benefit.

### R2 EYEBROW RATIONING — budget ~2-3 total, actual ~25+ across 8 surfaces

| Location | Instance |
|---|---|
| `ui/components/RowHeader.kt:66` | `eyebrow.uppercase()` at `labelLarge` (1.8sp tracking) — the engine |
| via `MediaRow.kt:75` | Home: Continue Watching, Next Up, Latest in *N*, My Media = 4+ on one screen |
| `ui/screens/detail/DetailScreen.kt:561,733` | `EPISODES`, `CAST` |
| `ui/screens/settings/SettingsScreen.kt:118,128` | `Server`, `About` (uppercased by RowHeader) |
| `ui/components/ScreenScaffold.kt:61` | `title.uppercase()` — every scaffolded screen gets one |
| `ui/screens/library/LibraryScreen.kt:202` | `LIBRARY` above the library's own name (says nothing) |
| `ui/screens/library/LibraryGrid.kt:354` | per-tile eyebrow — Search puts one on **every** result |
| `ui/screens/serverentry/ServerEntryScreen.kt:149` | `SERVER URL` |
| `ui/screens/login/LoginScreen.kt:157,173,235` | `USERNAME`, `PASSWORD` |
| `ui/screens/player/PlayerOsd.kt:378,384,584` | `AUDIO`, `SUBS` |
| `ui/screens/detail/DetailViewModel.kt:255-279` | 8 spec-rail micro-labels: RUNTIME/RATING/AUDIO/SUBS/CODEC/SCORE/CRITIC/YEAR |
| `ui/components/SlabButton.kt:90` | `text.uppercase()` — buttons are not eyebrows |
| `ui/screens/settings/SettingsScreen.kt:195` | `secondary.uppercase()` → `VERSION 10.9.11` |
| `ui/theme/Type.kt:105` | `labelLarge` exists solely as the eyebrow slot |

Keep **one**: the two field labels on ServerEntry/Login are the only place an eyebrow is load-bearing
(they replace a floating `TextField` label). Everything else becomes plain sentence-case text.

### R3 NO SECTION NUMBERING DEVICES — 11 sites

| Location | Device |
|---|---|
| `ui/components/RowHeader.kt:79-92` | `folio(count)` zero-pad on every row header; `fun folio()` itself |
| `ui/screens/detail/DetailScreen.kt:567` | `"%02d".format(episodes.size)` |
| `ui/screens/library/LibraryGrid.kt:84,185,198-204,386-393` | `FolioRule` every 5th row: hairline + `— 025 —` |
| `ui/screens/library/LibraryScreen.kt:220` | `"${groupedCount(total)} ITEMS"` pagination readout |
| `ui/screens/search/SearchScreen.kt:174` | `"${size} RESULTS"` index label |
| `ui/screens/serverentry/ServerEntryScreen.kt:127` | `"01 / 02"` |
| `ui/screens/login/LoginScreen.kt:135` | `"02 / 02"` |
| `ui/screens/serverentry/ServerEntryScreen.kt:119,188+` | `GhostNumeral("01")` backdrop |
| `ui/screens/login/LoginScreen.kt:120-126` | `"02"` at `ShoumeiType.Ghost` (220sp, `Paper @0.05`) |
| `ui/theme/Type.kt:114` | `ShoumeiType.Ghost` — style exists only to serve the ghost numeral |
| `ui/screens/serverentry/ServerEntryScreen.kt:56`, `login/LoginScreen.kt:62` | `FolioTop` dimens |

Counts may survive as plain language where they inform (`12 episodes`), never zero-padded, never mono,
never right-aligned to a rule. `SEARCHING` / `n RESULTS` collapse into one plain line.

### R4 SEPARATOR DISCIPLINE — 5 sites over budget

| Location | Dots on one line |
|---|---|
| `ui/screens/detail/DetailViewModel.kt:309-325` | genres joined by `" · "` **inside** an outer `" · "` join → up to 5 |
| `ui/components/ItemMapping.kt:17-18,108-122` | `SpecSeparator` joins 3-4 fields → 2-3 dots |
| `ui/screens/player/PlayerOsd.kt:653-658` | `S1:E04 · Direct Play · 1080p · CHAPTER` → 3 |
| `ui/screens/home/HomeScreen.kt:336` | `Wednesday · 21:14` — 1 dot, but see R6 |
| `ui/screens/serverentry/ServerEntryScreen.kt:170-173` | `FOUND · "name"` plus a 6dp tungsten square = dot **and** a decorative status mark |

Fix: cap at one `·` per line; move the rest to spacing/columns. The Detail spec line should be
`2019   TV-MA   2h 44m` on a tab rhythm, not a dot chain.

Decorative status dots to remove: the `MarkerLine` square in ServerEntry/Login (the word `FOUND`
already carries the state). **Real-state squares stay** (see Preserve).

### R5 NO DECORATIVE HAIRLINES — 5 sites

| Location | Rule |
|---|---|
| `ui/components/RowHeader.kt:73-78` | hairline spanning eyebrow→folio on every row header. Organizes nothing |
| `ui/screens/library/LibraryGrid.kt:389-393` | folio rule every 25 tiles |
| `ui/screens/login/LoginScreen.kt:202-207` | colophon hairline above the Quick Connect line |
| `ui/screens/settings/SettingsScreen.kt:205` | `Hairline()` under **every** `LedgerRow` — the banned border-on-every-row spec list |
| `docs/ui-design.md:229` / Detail spec rail | "separated by 1.dp rail-width hairlines" between all 4-6 rows — same defect |

Settings and the Detail rail both become **grouped chunks**: one hairline between the Server group and
the About group, none between rows inside a group. The Detail rail becomes a 2-column block, no rules.

### R6 NO FAKE-PRECISE / PERFORMATIVE / MICRO-META — 6 sites

| Location | Instance |
|---|---|
| `ui/screens/home/HomeScreen.kt:326-351` | `Wednesday · 21:14` clock stamp above the ambient title. A media app is not a station clock; it is a micro-meta line under nothing |
| `ui/screens/library/LibraryScreen.kt:220` | `groupedCount` renders `1 248 ITEMS` with a thin-space group — precision theatre |
| `ui/screens/detail/DetailScreen.kt:444` | comment quoting *"The 'proof' concept made literal."* |
| `ui/screens/detail/DetailViewModel.kt:273,276` | `SCORE 8.4` / `CRITIC 87%` alongside `RATING` — three rating scales in one rail |
| `docs/ui-design.md:7,192,213,236,263,277,300,312` | performative section labels: "dark room, lit evidence", "ambient, not billboard", "the contact sheet", "the colophon", "the ledger", "hairline, not HUD" |
| `docs/ui-design.md:11,18` | "chrome is **evidence**", "the app's editorial signature" |

The doc's own prose is the source of the device stack. Rewrite §1 and the §5 section titles to plain
descriptions ("Home", "Library grid", "Sign-in") before touching code, or the code will regrow them.

### R7 PALETTE LOCK — PASS
`ui/theme/Color.kt` is clean: `Ink000 #000000` base, `Ink050 #08080A` for surfaces needing depth, a single
`Tungsten #E8B472` accent, `Signal #E06C6C` used only as the semantic error role. No second accent, no brand
hue. Player letterboxing stays `Color.Black` (`PlayerScreen.kt`). No action.

### R8 SHAPE LOCK — soft violation
`ui/theme/Shape.kt:12-22` ships four radii (2/4/6/8) plus `RectangleShape` plus an asymmetric
`RoundedCornerShape(topStart = 8, bottomStart = 8)` for the track panel (`ui-design.md:258`). That is a
scale, not one system. Collapse to **4dp everywhere, 0dp for full-bleed art**; keep 2dp only if it is
documented as "bars and ticks", and drop 6dp entirely.

### R9 THEME LOCK — PASS
One `darkColorScheme` (`ui/theme/Theme.kt`), no `isInDarkTheme` branch, no section inversions. The
`Paper`-fill slab with an `Ink000` label is a component treatment, not a surface inversion. No action.

### R10 MOTION MOTIVATED — target 4, currently ~6

| Location | Verdict |
|---|---|
| `ui/components/FocusSurface.kt:76-130` focus scale + 2dp rim | **Keep.** Feedback: the only thing telling a 10-foot viewer where D-pad focus is |
| `ui/screens/player/PlayerOsd.kt:624-632` buffering pulse (900ms infinite) | **Keep.** State: real, and it replaces a spinner over video |
| `LinearProgressIndicator` ×3 (`LibraryScreen.kt:165`, `LoginScreen.kt:266`, `ServerEntryScreen.kt:244`) | **Keep.** State: indeterminate loading |
| `shakeOnce()` ±8dp, 2 cycles (Login/ServerEntry) | **Keep.** Feedback: an auth failure needs a body |
| `ui/screens/home/HomeScreen.kt:328-333` `while(true) delay(30_000)` clock tick | **Remove** with the clock (R6) — a loop driving decoration |
| `docs/ui-design.md:350` Home ambient art drift `1.04 → 1.00` over 600ms | **Remove.** Decorative, and it breaks the doc's own 420ms cap |
| `docs/ui-design.md:356` Detail 40ms-staggered fade of 5 blocks (`DetailScreen.kt:430`) | **Reduce** to one 220ms fade for the whole column. Choreography for its own sake |
| `FocusSurface` glow `Glow(Color.White, 12.dp)` (`ui-design.md:160`) | **Remove.** The doc itself (flag 2) says it is near-invisible |
| Four separate tweens per focus event: scale 140 / veil 160 / glow 180 / border 120 | **Collapse** to scale+rim on one 140/100 tween, veil on the same. The "stagger is the whole feel" claim is the dial running hot |

### R11 TYPE-LED — 2 systemic violations
- **Mono over-applied.** `ui/theme/Type.kt:106-107` makes `labelMedium` and `labelSmall` `FontFamily.Monospace`,
  so mono lands on spec lines, badges, folios, counts, the Home clock, ambient subtitles, and Settings
  secondary values. Rule: mono is for **timestamps and durations only**. Confine `Monospace` to
  `ShoumeiType.Timecode` / `TimecodeLarge` (`Type.kt:110-111`) and the Detail `RUNTIME` value; return
  `labelMedium`/`labelSmall` to `FontFamily.Default`.
- **Emphasis by alpha + tracking + case, not weight.** `Type.kt:17` and `ui-design.md:119` mandate
  "emphasis is carried by alpha, never a second colour". Combined with `uppercase()` and 1.2-1.8sp tracking
  that is three devices doing one job. Use size and weight (W400 → W500) for hierarchy; keep alpha for the
  focused/resting distinction only.

### R12 STATES COMPLETE — 3 gaps
- **Loading is a word, not a skeleton.** `ui/components/StateViews.kt:34-47` renders `Loading` at
  `displaySmall @0.38`. Rule asks for skeleton-shaped loading. Replace with `Ink100` placeholder rects in
  the shape of the content (poster row / grid tiles / ledger rows). This also removes the last reason for
  `R.string.loading`.
- **Empty states are dead ends.** `ui/screens/home/HomeScreen.kt:161` "Nothing here yet." and
  `ui/screens/library/LibraryScreen.kt:118` "Nothing in this library." offer no action. `EmptyView`
  (`StateViews.kt:72-86`) has no action slot. Add an optional action; Home → open Settings/Search,
  Library → clear the filter.
- **Search has no loading state.** `ui/screens/search/SearchScreen.kt:112` renders `""` during the 350ms
  debounce; the only cue is the `SEARCHING` label being deleted under R3. Give it the skeleton grid.
- Error states pass: inline, plain language, one retry slab (`StateViews.kt:51-68`, `DetailScreen.kt:124`).

---

## 2. Preserve list — must survive verbatim

| Item | Location |
|---|---|
| Focus hazard kit: `zIndex(1f)` when focused, `graphicsLayer` + `TransformOrigin(0.5f, 0.62f)`, 2dp white rim, inner hairline | `ui/components/FocusSurface.kt:49-130` |
| Focus rim uses **literal** `Color.White`, never `colorScheme.border` | `FocusSurface.kt:128` |
| Same kit duplicated inline (keep all three) | `DetailScreen.kt:496,624`; `LibraryGrid.kt:267` |
| Cross-axis glow/scale bleed + **no** `clipToBounds` on rows | `ui/components/MediaRow.kt:28-45` |
| `focusRestorer()` on every lazy container | `MediaRow.kt:84`; `LibraryScreen.kt:134,236`; `DetailScreen.kt:361,574,736`; `SearchScreen.kt:103` |
| Row snap via `animateScrollToItem(index, 0)` (the sanctioned fallback) | `MediaRow.kt:65` |
| Explicit cross-boundary focus wiring | `SearchScreen.kt:159` (`down = gridFirstFocus`), `LibraryGrid` `rowZeroUp` |
| `FocusRequester` on every `TextField` (TV default focus is too quiet) | `LoginScreen.kt:106-107`; `ServerEntryScreen.kt:105-106` |
| Overscan-safe padding: 48/27 | `ui/theme/Dimens.kt`; `ui/components/ScreenScaffold.kt` |
| Hand-drawn seek bar + scrubber (never material3 `Slider`) | `ui/screens/player/PlayerOsd.kt:123,414-430` |
| Chapter marks and chapter nav (UP/DOWN) | `PlayerViewModel.kt:26-61,107-119`; `PlayerScreen.kt:166-180` |
| Blurhash decode + painter + tag-pairing | `data/image/BlurHash.kt`; `ui/components/BlurHashPainter.kt`; `ItemMapping.kt:22-104` |
| Blurhash-absent fallback: flat `Ink100` + inset hairline | `ui/components/PosterImage.kt:64` |
| Logo-as-title fallback chain | `DetailViewModel.kt:53`; `ItemMapping.kt:76-104` |
| Watched indicator (real state): art `@0.55` + white check | `MediaCard.kt:168-170`; `LibraryGrid.kt` |
| Tungsten square marking the **selected** audio/subtitle track (real state) | `PlayerOsd.kt`; `TrackDialog.kt` |
| Tungsten progress/resume bar as a hand-drawn `Box`, never `LinearProgressIndicator` | `MediaCard.kt:65-123`; `DetailScreen.kt` |
| Player background `Color.Black`, letterboxing pure black | `PlayerScreen.kt` |
| OSD auto-hide 5000ms + timer suspension while the track panel is open or the bar is held | `PlayerScreen.kt`; `PlayerOsd.kt` |
| All ViewModel wiring, state flows, navigation events, DTOs, repositories | `ui/screens/**/*ViewModel.kt`; `data/**` |
| Contrast pairs: `Paper #F5F5F2` and `Ash600 #A8A8AF` on `Ink000` | `ui/theme/Color.kt:32-35` |

---

## 3. Per-file rewrite worklist

### Group A — THEME + COMPONENTS (do first; everything downstream depends on it)

| File | Work |
|---|---|
| `docs/ui-design.md` | Rewrite §1 concept and all §5 section titles in plain language (R6). Delete the folio, ghost-numeral, and per-row-hairline specs from §5.1/§5.2/§5.4/§5.5/§5.7. Amend §3 to confine mono. Amend §6: delete art drift, glow, block stagger. Document the single radius (§4). |
| `ui/theme/Type.kt` | `labelMedium`/`labelSmall` → `FontFamily.Default`, drop tracking to ≤0.5sp (R11). Delete `ShoumeiType.Ghost` (R3). Keep `Timecode`/`TimecodeLarge` mono. Update the §3 KDoc. |
| `ui/theme/Shape.kt` | Collapse to one system: 4dp default, 0dp full-bleed; document it (R8). |
| `ui/theme/Motion.kt` | Delete `GlowFade`; merge `VeilFade` into `FocusIn`/`FocusOut`; add a one-sentence justification per remaining `Dur` constant (R10). |
| `ui/theme/Color.kt`, `Theme.kt`, `Scrims.kt` | No change (R7, R9). |
| `ui/components/RowHeader.kt` | **Rewrite.** Sentence-case `titleMedium` heading, no `uppercase()`, no hairline, no folio. Delete `fun folio()`. Keep the focused/resting alpha animation (R2, R3, R5). |
| `ui/components/ScreenScaffold.kt` | Drop `title.uppercase()`; render as a plain heading (R2). |
| `ui/components/SlabButton.kt` | Drop `text.uppercase()` (R2). |
| `ui/components/ItemMapping.kt` | Cap `SpecSeparator` at one use per line; add a column/spacing joiner for 3+ fields (R4). |
| `ui/components/StateViews.kt` | Add skeleton loading composables (poster row, grid, ledger); add an optional action to `EmptyView` (R12). |
| `ui/components/FocusSurface.kt` | Remove the `Glow`; keep everything else verbatim (R10 + Preserve). |
| `MediaCard.kt`, `MediaRow.kt`, `PosterImage.kt`, `BlurHashPainter.kt` | No taste changes; verify the `RowHeader` signature change compiles. |

### Group B — HOME + LIBRARY + SEARCH

| File | Work |
|---|---|
| `ui/screens/home/HomeScreen.kt` | Delete `AmbientHeader`'s clock stamp and its `while(true)` loop (`:326-351`) — the header starts at the focused title (R6, R10). Row headings become plain via the new `RowHeader`. Empty state gets an action (R12). |
| `ui/screens/home/HomeViewModel.kt` | Row titles already sentence-case; no change. |
| `ui/screens/library/LibraryScreen.kt` | Delete the `LIBRARY` eyebrow (`:202`) — the library name is the heading (R2). Replace `"n ITEMS"` (`:220`) with plain `n items` or drop it (R3, R6). Empty state gets a clear-filter action (R12). |
| `ui/screens/library/LibraryGrid.kt` | Delete `FolioRule`, `FOLIO_INTERVAL`, the `folioRules` param and its lazy item (`:84,185,198-204,386-393`) (R3, R5). Drop the per-tile `eyebrow` uppercase at `:354` → sentence case at `bodySmall @0.55`. Fix `"— %03d —"` by deletion (R1). Add the skeleton grid. |
| `ui/screens/search/SearchScreen.kt` | Replace `SEARCHING` / `n RESULTS` (`:171-180`) with one plain line or nothing (R2, R3). Add the debounce skeleton (R12). Keep `focusProperties` wiring verbatim. |
| `ui/screens/search/SearchViewModel.kt` | `typeEyebrow()` (`:97-101`) → sentence-case `typeLabel()`: Film / Series / Episode. |

### Group C — DETAIL

| File | Work |
|---|---|
| `ui/screens/detail/DetailScreen.kt` | Delete the `"%02d"` episode folio (`:567`) and the `EPISODES`/`CAST` uppercase eyebrows (`:561,733`) → plain headings (R2, R3). Spec rail: 2-column block, **no** inter-row hairlines (R5). Collapse the 40ms block stagger to one fade (`:430`) (R10). Delete the quoted-concept comment (`:444`). |
| `ui/screens/detail/DetailViewModel.kt` | `EN_DASH` → hyphen (`:285,295,297`) and fix the `:279` branch (R1). Un-nest the double `" · "` join (`:309-325`) to one dot max (R4). Reduce the rail to 4-5 rows; drop `CRITIC` (`:276`) so one rating scale remains (R6). Spec labels stay uppercase **only** if the rail is the single rationed eyebrow surface — otherwise sentence case (R2). |

### Group D — PLAYER + AUTH + SETTINGS

| File | Work |
|---|---|
| `ui/screens/player/PlayerOsd.kt` | Trim the meta line (`:653-658`) to one `·` max (R4). `AUDIO`/`SUBS` (`:378,384,584`) → sentence case (R2). **Keep** the buffering pulse, hand-drawn bar, timecode mono, tungsten selection square. |
| `ui/screens/player/PlayerScreen.kt`, `TrackDialog.kt` | No taste changes. Preserve OSD timing, chapter nav, `Color.Black`. |
| `ui/screens/serverentry/ServerEntryScreen.kt` | Delete `GhostNumeral` and its composable (`:119,188+`), `"01 / 02"` (`:127`), `FolioTop` (`:56`) (R3). Drop the `MarkerLine` square; `FOUND · "name"` → `Connected to "name"` (R4). Keep the `SERVER URL` field eyebrow (the one rationed use). Keep `clipToBounds` removal in mind — the box exists only for the ghost. |
| `ui/screens/login/LoginScreen.kt` | Delete the `"02"` ghost (`:120-126`), `"02 / 02"` (`:135`), `FolioTop` (`:62`), and the colophon hairline (`:202-207`) (R3, R5). Drop `.uppercase()` on the Quick Connect line (`:210`). Keep the two field eyebrows. |
| `res/values/strings.xml:22` | `Quick Connect is not available yet` (R1). |
| `ui/screens/settings/SettingsScreen.kt` | Delete `Hairline()` from `LedgerRow` (`:205`); one rule between the Server and About groups only (R5). Group headings via the rewritten `RowHeader`, sentence case (`:118,128`) (R2). Drop `secondary.uppercase()` (`:195`) → `Version 10.9.11`. Keep non-focusable info rows, the edge-light focus bar, and the sign-out confirmation dialog. |
| `ui/navigation/NavGraph.kt` | No change. `fadeIn(220)` / `fadeOut(180)` are motivated; keep Detail's `scaleIn(0.98f)`. |

---

## 4. Dial reading

| Dial | Current | Target | Gap |
|---|---|---|---|
| **VARIANCE** | **8** | 6 | Every surface invents its own device set: Home has an ambient clock header, Library a pinned header with folio rules, Search a query-line header, Detail a wipe plus spec rail, Auth a ghost numeral and wordmark, Settings a ledger. Six header idioms for eight screens. Target 6 keeps the Detail wipe, the Player OSD and the auth column as genuinely distinct, and makes Home / Library / Search share one heading system. |
| **MOTION** | **6** | 4 | Nine distinct animation systems; four fire on a single focus event (scale, veil, glow, border) on four different tweens. Removing the glow, merging veil into the focus tween, deleting the ambient drift and the clock loop, and collapsing the Detail block stagger lands at 4: focus feedback, state transitions (OSD, progress), loading indeterminates, and screen fades. |
| **DENSITY** | **5** | 3 | Chrome per unit of content is high: every row costs an eyebrow + a rule + a folio; every Settings row costs a hairline; every Detail rail row costs a label + a hairline; every Search tile costs a type eyebrow. Deleting folios, per-row hairlines and 20-odd eyebrows removes roughly a third of the drawn elements without losing a single piece of information. |
