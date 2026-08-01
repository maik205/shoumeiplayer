# Performance work handoff

**Repository:** `maik205/shoumeiplayer`
**Branch:** `main`
**Scope:** non-Live-TV performance backlog
**Last updated:** 2026-07-30

## Current state

The non-Live-TV performance pass is complete. The only untracked paths are generated Gradle/build outputs and must not be treated as source changes. No Gradle build, compile, install, or test command was run in this pass because the project convention is to leave verification to the user unless explicitly requested.

The durable checklist is [docs/performance-checklist.md](../performance-checklist.md). It is the source of truth for the backlog and intentionally excludes Live TV.

## Completed final issues

The final two performance issues have been implemented:

1. [#1 — Isolate high-frequency playback timeline state from the full player UI](https://github.com/maik205/shoumeiplayer/issues/1)
   - Completed: sampled timeline values now live in `PlayerTimelineState`; only video/audio progress and synchronized lyrics collect that flow. Static player state no longer contains position or buffered values. Exact engine reads remain in seek/report paths.

2. [#42 — Add size-aware artwork decoding and TV prefetch limits](https://github.com/maik205/shoumeiplayer/issues/42)
   - Completed: TV card requests use rendered pixel dimensions, rails prefetch only the next two cards with cancellation, and the shared Coil loader bounds fetch/decode concurrency. Hero and backdrop requests retain their larger constraint-derived budget.

## Completed implementation batches

These commits are already on `main`:

- `cd4b97d` — cache IO/coalescing, bounded search and Home fan-out, stale-job cancellation, mpv cache budgeting, release logging, initial player timeline sampling.
- `2bcaf0f` — removed hidden audio blur compositing.
- `2a5138a` — stabilized TV rail focus keys and avoided temporary keyed-list allocations.
- `bc05950` — reduced playback progress reporter work and allocation churn.
- `20ea5d4` — throttled high-frequency mpv timeline JNI callbacks.
- `6a6b5d2` — right-sized TV rail artwork request widths.
- `c5e7683` — isolated sampled timeline state to progress-dependent player composables.
- `28d94f9` — bounded TV artwork decode dimensions, prefetch windows, and loader concurrency.

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

## Final verification

Run Gradle verification only when explicitly requested. The source-only pass uses `git diff --check`, `git status`, and a GitHub issue-state audit.

## Guardrails

- Do not include Live-TV issues in this pass.
- Do not delete or commit generated `build/` output.
- Preserve immediate seek, pause, end, and track-change behavior while optimizing high-frequency updates.
- Keep artwork URL/cache identity stable while changing only size, decode, and prefetch policy.
- Commit each independently reviewable batch as requested by the user.
