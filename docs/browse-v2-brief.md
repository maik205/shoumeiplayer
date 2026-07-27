# Browse v2 — Fable Brief & Guardrails

Goal: a Netflix-level browse experience — homepage, listings, search — plus a
real settings surface. This brief sets scope and hard guardrails; the Opus plan
(docs/browse-v2-plan.md) details tasks; Sonnet implements; Opus gates.

## Reference UX (Netflix on TV), translated to our stack

**Homepage**
- Billboard hero: featured item (rotate through Latest/Resume picks), backdrop
  full-bleed behind everything, logo + synopsis + Play/More-info actions; the
  hero updates as row focus changes (focused card drives the backdrop, debounced).
- Rows below: Continue Watching, Next Up, per-library Latest, genre rows
  (from /Items with genre filters), My Media. Row titles plain (taste rules).
- Focus: vertical row list with focusRestorer; cards scale on focus; the
  focused card's metadata (title, year, runtime, rating) shows in the hero
  area, Netflix-style, without opening Detail.
- Left edge: collapsed nav rail (Home, Search, Libraries, Settings) that
  expands on focus — Netflix/Google-TV pattern; replaces the current top-bar
  glyphs. LEFT from the first card opens the rail.

**Listings (library grid)**
- Netflix-style dense poster grid with instant focus metadata strip (top of
  screen shows focused item's info rather than per-card text clutter).
- Filter/sort as a horizontal chip row above the grid (All · Unwatched ·
  Genres… · Sort), pill chips consistent with the OSD chip idiom.
- Fast alphabet/scrubber jump on LEFT edge is a stretch goal — only if cheap.

**Search**
- Netflix TV pattern: on-screen keyboard grid on the left (A–Z, 0–9, space,
  delete — D-pad optimized, NOT the system IME), results grid on the right
  updating as you type (existing debounced search), plus "Suggested" row
  (server /Search/Hints or Similar-of-recent) when the query is empty.

**Settings (expand fields)**
Grouped two-pane settings (left group list, right fields) — groups:
- Playback: preferred quality (reuse quality→bitrate mapping), subtitle mode +
  language, audio language, play default audio, autoplay next episode,
  remember selections — READ from and WRITE back to the server via
  POST /Users/Configuration (verify schema; this closes the deferred
  write-back gap).
- Appearance/behavior: focus scale on/off (LocalShoumeiMotion), clock in OSD.
- Server: current server/user info, switch user (sign out), server address.
- About: versions, open-source notices stub.
Client-only prefs persist in DataStore (extend SessionStore or a new
SettingsStore — one owner file).

## Hard guardrails (Fable)

1. docs/ui-design.md tokens stay law (true black, tungsten, type ramp, focus
   hazard kit, overscan). The OSD chip/pill radius amendment extends to browse
   chips and the nav rail. No new colors, no second accent.
2. Taste rules hold (no eyebrows beyond budget, no folio numbering, no
   em-dashes, separator discipline). Netflix-LEVEL polish, not Netflix cosplay:
   no red, no Netflix fonts.
3. All data through existing repositories; new endpoints (genre queries,
   /Search/Hints, POST /Users/Configuration) verified against
   jellyfin-openapi.json and added to the repo layer with MockEngine tests.
4. D-pad completeness: every element reachable; focusRestorer everywhere;
   overscan safe; the nav rail must never trap focus.
5. No regressions to the player work (OSD v3 lands first; browse agents do not
   touch src/ui/screens/player/** or src/player/**).
6. Blurhash/logo/backdrop asset fidelity carries over to all new surfaces.
7. Perf: hero backdrop crossfades debounced ≥300ms; rows lazy; no full-screen
   recomposition on card focus (hoist focused-item state narrowly).
8. Tests: pure logic (keyboard grid model, genre row assembly, settings
   mapping, config write-back) unit-tested; build+tests green per wave.

## Sequencing

Wave P (plan, Opus, docs-only — may run while OSD v3 implements):
write docs/browse-v2-plan.md with task blocks, pinned signatures (nav rail
component API, SettingsStore schema, keyboard model, hero state), file
ownership per wave, and endpoint verification notes.
Waves 1..n (Sonnet impl) + Opus gates: start only after the OSD v3 gate is
green. Shared-file ownership rule as in docs/plan.md (NavGraph, AppContainer,
strings.xml single-owner per wave).
