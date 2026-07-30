# Performance backlog checklist

This checklist tracks the non-Live-TV performance issues being implemented in this workstream.

## Cache and persistence

- [x] #39 Move library cache serialization off the main thread.
- [x] #40 Coalesce and scope persisted Home/library cache writes.
- [x] #2 Bound mpv stream cache settings to the device memory budget.
- [ ] #3 Decode and cache BlurHash artwork off the main thread.

## Compose and artwork

- [x] #41 Bound search result payloads and render work.
- [ ] #42 Add size-aware artwork decoding and TV prefetch limits.
- [x] #43 Reuse Home rail focus requesters without rebuilding keyed lists.
- [ ] #7 Memoize derived search and browse collections.
- [x] #11 Reduce audio player blur and offscreen compositing cost.

## Playback state and network work

- [ ] #1 Isolate high-frequency playback timeline state from the full player UI.
- [ ] #5 Reduce unnecessary work in playback progress reporting.
- [x] #6 Disable Ktor request logging in release builds.
- [x] #4 Stage and limit concurrent Home/browse network fan-out.
- [x] #8 Cancel stale detail, season, and browse reload jobs.
- [ ] #9 Avoid duplicate MediaItemUi mapping in detail loading.
- [ ] #10 Reduce raw DTO retention in television detail state.
- [ ] #12 Coalesce mpv timeline callbacks before crossing JNI.
- [ ] #13 Replace Home raw DTO hero lookup maps with lightweight hero data.

Live TV issues are intentionally excluded.
