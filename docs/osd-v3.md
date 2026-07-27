# Player OSD v3 — Interaction & Feature Spec (APPROVED)

Approved by the user via the interactive preview (claude.ai artifact
"Shoumei OSD v3", round: youtube-tv-shelf). The Compose implementation follows
THIS document; the preview is the visual reference. docs/ui-design.md still
governs tokens (true black, tungsten, type ramp) with one amendment noted in §6.

Reference UX: YouTube on TV (down-shelf, chip controls), Google TV player.

## 1. Layers & focus model

**Layer 0 — surface (OSD hidden).** Root owns focus.
- LEFT/RIGHT: seek ±10s. Shows only the slim seek strip (bar + timecodes),
  auto-fades 1.5s after the last press. NOT the full OSD.
- Fast double-press LEFT/RIGHT (same direction ≤400ms, chapters present):
  chapter jump. Existing `isChapterDoublePress` + tests survive.
- CENTER: play/pause + reveal OSD. UP/DOWN/MENU: reveal OSD (consumed).
- BACK: exit playback.

**Layer 1 — OSD.** Focus always lives inside the OSD while visible (root never
holds focus then; on reveal, focus is handed to play/pause via FocusRequester —
directional search cannot enter overlay bounds).

Vertical focus geography (UP/DOWN moves between these; LEFT/RIGHT within):
```
row 0  seek bar (scrub mode)
row 1  THE control track (single horizontal track)
row 2  shelf: "More like this"  (opens on DOWN from row 1)
row 3  shelf: "Cast"
```
- Up Next card (when visible) takes DOWN priority from row 1 before the shelf.
- BACK: closes panel → closes shelf → cancels scrub → hides OSD → exits.
- Auto-hide 5s; suspended while a panel is open, while scrubbing, and while the
  shelf is open. When OSD hides, focus returns to root (existing mechanism).

## 2. The control track (row 1) — YouTube-TV chips

ONE horizontal track, two visual clusters, LEFT/RIGHT walks across the gap:
```
[prev-ep] [-10s] [play/pause] [+10s] [next-ep]   ···   [subs] [audio] [speed] [quality]
```
- prev/next-ep: episodes only (omitted for movies).
- Chips: resting = 42dp circular icon button (icons from material-icons-core;
  closest available glyphs for skip-10s — or hand-drawn only if the set truly
  lacks them, noted); focused = expands into a white pill with inline label
  (tv-material inverse focus), e.g. `▶ Play / pause`, `CC Subtitles — English`.
- Active state (subs on, speed ≠ 1×, quality ≠ Auto): 4dp tungsten dot under
  the chip.
- CENTER on subs/audio/speed/quality opens the right-side panel (existing
  TrackDialog idiom restyled to pill list items); on transport chips acts.

## 3. Seek bar (row 0) — scrub mode

- Slim 3dp line (5dp focused), full content width, timecodes at the ends,
  chapter ticks, buffered segment, tungsten fill, 3×14dp rounded scrubber.
- LEFT/RIGHT move a VIRTUAL playhead (no live seek). Hold-repeat acceleration:
  step 10s → 30s after 10 repeats → 60s after 20 (reset on release/direction
  change). Double-press = chapter jump of the virtual playhead.
- CENTER commits (seek to virtual playhead); BACK cancels scrub.
- Trickplay preview above the scrubber while virtual ≠ live: tile sub-rect
  painter from `/Videos/{itemId}/Trickplay/{width}/{index}.jpg` (+api_key,
  mediaSourceId — verify params in spec); 8dp radius, hairline border, chapter
  name + timecode caption. No trickplay data → caption only.

## 4. Top-of-plate identity

- Item **Logo image** (Jellyfin ImageLogo; series logo for episodes) bottom-left
  above the bar, max-height ≈ 96dp; text title fallback when absent/failed.
- Under it: `S3 · E06  Immolation` line (mono season/episode, sans name); for
  movies: year · quality badge line. Non-1× speed appends `1.5×`.

## 5. Features

- **SPEED**: 0.5/0.75/1/1.25/1.5/2 — engine `speed: StateFlow<Float>` +
  `setSpeed`; mpv `speed` property; Simulated honors; Mplayer no-op.
- **QUALITY**: Auto/4K/1080p/720p/480p → maxStreamingBitrate mapping (Auto =
  omit/huge; 4K≈80Mbps, 1080p≈20, 720p≈8, 480p≈3 — sane, comment them),
  then RE-RESOLVE PlaybackInfo with forced transcode allowed and swap the
  stream in place preserving position/tracks. Uses the existing transcode
  cleanup on the old session.
- **Prev/Next episode**: adjacency via /Shows/{seriesId}/Episodes;
  PlayerViewModel.switchTo(itemId) swaps in place (stop report + transcode
  cleanup + fresh resolve), resume position of the target item.
- **Up Next overlay**: episodes, remaining ≤30s: bottom-right card (next thumb
  + title + mono countdown when user config EnableNextEpisodeAutoPlay).
  CENTER plays now; BACK dismisses permanently for this item. Movies: never.
- **Shelf — "More like this"**: `/Items/{itemId}/Similar` (verify params:
  userId, limit≈12, fields for cards). Cards 16:9 thumb + 2-line title;
  CENTER → stop playback cleanly and navigate to DetailRoute of the item.
- **Shelf — "Cast"**: BaseItemDto.People (already fetched) — circular
  portraits + name + role; CENTER → exit player and navigate to a
  person-filtered library/search results screen (simplest correct: SearchRoute
  pre-seeded or a PersonRoute if trivial; do not build a new full screen —
  reuse the library grid with personIds filter on /Items).
- Shelf opens as a bottom sheet sliding over the plate (video keeps playing),
  rows lazily loaded, focusRestorer per row.

## 6. Design-token amendment

The player screen is granted a documented exception to the 4dp radius lock:
circular/pill controls (chips, panel list items 21dp pill, shelf thumbs 8dp,
person portraits circular). Record this in docs/ui-design.md §shape when
implementing. Everything else (colors, type, motion durations, buffering
hairline at top, scrims) stays per ui-design v2.

## 7. Ownership map for implementation

- ENGINE+DATA: PlayerEngine (+3 impls: speed), quality bitrate mapping +
  re-resolve path in PlaybackRepository/PlayerViewModel, Similar() in
  LibraryRepository, trickplay tile painter + URL (unit-tested), person-filter
  query support if trivial.
- OSD UI: PlayerOsd rewrite (chips track, slim bar, logo block, shelf,
  panels restyle, Up Next), TrickplayPreview composable.
- SCREEN: PlayerScreen input model per §1 (row state machine, scrub
  acceleration, shelf/panel/UpNext arbitration, focus handoffs).
- Tests: keep/extend chapter + double-press tests; scrub-acceleration pure
  function tests; quality→bitrate mapping; Similar repo test; trickplay painter
  rect math.
