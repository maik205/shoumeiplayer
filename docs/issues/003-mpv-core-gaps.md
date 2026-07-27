# 003 — mpv player-core gaps

**Status:** Partly fixed
**Binding:** `dev.jdtech.mpv:libmpv` 0.4.1. API surface confirmed by `javap` over the AAR's
`classes.jar` — the binding exposes only `command`, `setOptionString`, `get/setProperty{Int,Double,
Boolean,String}`, `observeProperty`, `attach/detachSurface`, and the event/log observer callbacks.

## Fixed in this pass

| Gap | Was | Now |
|---|---|---|
| Track addressing | Jellyfin indices written to `aid`/`sid` | Translated via `ff-index` — see [002](002-mpv-track-index-namespace.md) |
| External subtitles | `DeliveryUrl` parsed, never used | `sub-add <url> auto <title> <lang>` on `FILE_LOADED`; ids mapped back to Jellyfin indices |
| Position granularity | `time-pos`/`duration` observed as `MPV_FORMAT_INT64` → 1s steps in the seek bar and rounded resume points | Observed as `MPV_FORMAT_DOUBLE`, with the INT64 handler kept as a fallback |
| Buffered range | Not reported at all | `demuxer-cache-time` (DOUBLE, absolute seconds) → `PlayerEngine.bufferedMs` |
| Seek feedback | UI froze on `Playing` during refill | `MPV_EVENT_SEEK` / `MPV_EVENT_PLAYBACK_RESTART` bracket the refill, plus the `seeking` property; `seekTo` enters `Buffering` optimistically |
| Output scaling | `android-surface-size` never set — mpv scaled against a stale size after layout changes | `PlayerEngine.setSurfaceSize` wired from `SurfaceHolder.surfaceChanged` |
| Transcode teardown | Server-side ffmpeg leaked on every transcoded exit | `DELETE /Videos/ActiveEncodings?deviceId&playSessionId` on stop, guarded to transcode sessions |
| Session keep-alive | Server could reap a paused session between progress posts | `POST /Sessions/Playing/Ping?playSessionId` on the 10s reporter cadence, cancelled with the reporter |
| Network cache | Defaults only | `cache-secs=30`, `demuxer-readahead-secs=20`, `cache-pause-wait=1`, `network-timeout=15`, each commented with its purpose |

## Still open

1. **No playback speed control.** mpv's `speed` property is unused; there is no engine API and no
   OSD affordance.
2. **No audio/subtitle delay.** `audio-delay` / `sub-delay` — standard for fixing out-of-sync
   releases, absent.
3. **No subtitle styling.** `sub-font-size`, `sub-color`, `sub-border-size` are left at defaults;
   `sub-scale-with-window=yes` is set but nothing is user-adjustable.
4. **No volume or mute.** The engine has no `volume`/`mute` surface; the app relies entirely on the
   TV's own volume.
5. **`END_FILE` reason is not exposed by this binding.** `MPVLib.event(int)` passes only the event
   id, so EOF, user stop, and hard error are indistinguishable. The current heuristic — "ended
   without ever having loaded ⇒ error, using the last logged error line as the message" — is a
   reasonable approximation but will misreport an immediate user stop as an error.
6. **No hwdec fallback.** If `mediacodec-copy` fails for a codec, there is no retry with
   `hwdec=no`; playback just fails.
7. **No buffering percentage.** `cache-buffering-state` would give a 0–100 readout for the initial
   fill; the OSD currently shows an indeterminate hairline only.
8. **No secondary subtitles.** `secondary-sid` is unsupported.
9. **All external subtitles are attached at load.** `sub-add` runs for every externally-delivered
   subtitle on `FILE_LOADED`. Correct, but on an item with many subtitle files this is a burst of
   HTTP fetches at the worst moment. Lazily attaching on selection would be better.

## Testing note

`MpvEngine` itself remains untestable on the JVM — MPVLib is native-only. That is why all logic that
can be pure was extracted into `MpvTrackList`, which is covered. State-machine behaviour
(`updateState`) is still only exercised on device.


## Closed: transcode leak and session keep-alive

Both were the "server-side damage" items — they harmed the server rather than merely omitting a
feature — so they were fixed ahead of the remaining feature gaps.

**Transcode leak.** `PlaybackRepository.stopTranscode` issues
`DELETE /Videos/ActiveEncodings?deviceId&playSessionId` (both required query params, 204 response).
The direct-play short-circuit lives inside `stopTranscode` rather than at the call sites, so no
caller can forget it, and a session with a blank `playSessionId` is skipped too.

The call is paired with the stop report inside `PlaybackProgressReporter.reportStopped`, so it is
impossible to report a stop while leaking an encoder.

**Exactly-once teardown.** `PlayerViewModel.finishPlayback()` guards on an `AtomicBoolean` CAS and is
invoked from *both* `onStopped()` (screen `onDispose`, which covers a back-exit mid-buffer) and
`onCleared()`. Neither hook alone is sufficient, and together they would otherwise double-report.

This also fixed a latent bug: teardown previously launched on `viewModelScope`, which is cancelled by
the time `onCleared()` runs — the final `/Sessions/Playing/Stopped` could be killed before it left
the device. Teardown now runs on an application-lifetime scope injected as `teardownScope`
(`AppContainer.applicationScope`).

**Keep-alive.** `PlaybackProgressReporter.run` now wraps its collect in `coroutineScope` and launches
`keepSessionAlive` as a child, so the pings share the reporter's lifetime exactly — cancelling the
reporter stops them, with no second lifetime to get wrong. The ping is deliberately unconditional
rather than gated on `Playing`: progress reports stop while paused, which is precisely when a session
is most likely to be reaped.
