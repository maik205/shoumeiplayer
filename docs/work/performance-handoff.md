# Performance work handoff

**Repository:** `maik205/shoumeiplayer`
**Branch:** `main`
**Scope:** non-Live-TV performance backlog
**Last updated:** 2026-07-30

## Current state

The performance pass is substantially complete. The tracked worktree is clean; the only untracked paths are generated Gradle/build outputs and must not be treated as source changes. No Gradle build, compile, install, or test command was run in this pass because the project convention is to leave verification to the user unless explicitly requested.

The durable checklist is [docs/performance-checklist.md](../performance-checklist.md). It is the source of truth for the backlog and intentionally excludes Live TV.

## Remaining GitHub issues

Two performance issues remain open and require implementation before the backlog can be called complete:

1. [#1 — Isolate high-frequency playback timeline state from the full player UI](https://github.com/maik205/shoumeiplayer/issues/1)
   - Already completed: 100 ms UI timeline sampling in `PlayerViewModel` and native mpv throttling for high-frequency `time-pos` / `demuxer-cache-time` callbacks.
   - Remaining: split the player composables/state so timeline ticks do not recompose the static player chrome, controls, metadata, or track UI. Preserve exact position reads for explicit seek/report paths.
   - Acceptance: timeline updates only invalidate the progress-dependent slice; controls and static content remain stable during playback; seek, pause, end, and track changes remain immediate.

2. [#42 — Add size-aware artwork decoding and TV prefetch limits](https://github.com/maik205/shoumeiplayer/issues/42)
   - Already completed: reduced rail artwork URL widths in `JellyfinMediaMapper` for landscape, square, portrait, episode preview, and fallback artwork.
   - Remaining: make Coil decode size-aware from actual TV layout constraints and add bounded viewport-aware prefetch/concurrency. Avoid decoding full-resolution artwork for small cards and avoid retaining a large number of off-screen bitmaps.
   - Acceptance: request/decode dimensions follow rendered card size; prefetch is limited to a small viewport window and bounded concurrency; scrolling remains smooth without unbounded memory growth; hero/backdrop artwork retains its larger budget.

## Completed implementation batches

These commits are already on `main`:

- `cd4b97d` — cache IO/coalescing, bounded search and Home fan-out, stale-job cancellation, mpv cache budgeting, release logging, initial player timeline sampling.
- `2bcaf0f` — removed hidden audio blur compositing.
- `2a5138a` — stabilized TV rail focus keys and avoided temporary keyed-list allocations.
- `bc05950` — reduced playback progress reporter work and allocation churn.
- `20ea5d4` — throttled high-frequency mpv timeline JNI callbacks.
- `6a6b5d2` — right-sized TV rail artwork request widths.

Documentation/checkpoint commits record the state of the checklist and partial work:

- `1a9836b`, `db4e3d3`, `408fcdb`, `b5b789e`, `475e879`, `a6b52e2`.

## Completed issue areas

- Cache serialization moved to `Dispatchers.IO`; duplicate/obsolete writes are coalesced and scoped.
- mpv stream cache limits are derived from the device memory class.
- Release Ktor request logging is disabled.
- Search payloads and Home/browse network fan-out are bounded.
- Stale detail, season, and browse reload jobs are cancelled.
- Home rail requester keys and TV rail derived keys no longer allocate temporary ID lists on recomposition.
- Audio player blur/offscreen compositing was removed.
- Playback progress reporting and native timeline callback frequency were reduced without removing immediate state/seek events.
- Detail/Home state no longer retains unnecessary raw DTOs; mapping is performed in the data layer.
- Active TV search no longer has the legacy repeated derived-filter path; the BlurHash issue is not applicable because there is no active UI decode path.

## Recommended next sequence

1. Inspect the player screen entry composable and identify the smallest stable static subtree versus the timeline-dependent subtree.
2. Introduce a narrow timeline/progress state holder or child composable, keeping callbacks and exact seek behavior unchanged.
3. Commit #1 as an atomic change and update its GitHub issue/checklist.
4. Inspect the Coil 3 image-loading APIs already used by the app; use the actual measured TV constraints to set decode size.
5. Add a bounded, viewport-aware prefetch policy for rails, with cancellation when a rail leaves the active window.
6. Commit #42 as an atomic change and update its GitHub issue/checklist.
7. Run the requested Gradle verification only when the user explicitly asks for it, then perform a final `git diff --check`, `git status`, and issue-state audit.

## Guardrails

- Do not include Live-TV issues in this pass.
- Do not delete or commit generated `build/` output.
- Preserve immediate seek, pause, end, and track-change behavior while optimizing high-frequency updates.
- Keep artwork URL/cache identity stable while changing only size, decode, and prefetch policy.
- Commit each independently reviewable batch as requested by the user.
