# mplayer ↔ shoumeiplayer JNI integration contract

Status: **design, not yet implemented on either side.** The Kotlin side
(`app/src/main/java/com/maik205/shoumeiplayer/player/`) has `PlayerEngine`,
`MplayerEngine` (logged no-ops) and `MplayerNative` (commented `external fun`s).
The Rust side (`../mplayer`) has no Android/JNI story at all (see Appendix).

This document is the frozen interface so the Rust work can proceed independently.
Anything not written here is the Rust side's free choice.

---

## 1. Crate / library shape

Add a **second crate**, `mplayer/android/` (`mplayer-android`), rather than
mutating `mplayer` itself — `src/lib.rs` holds the JNI entry points, `src/engine.rs`
the render+event thread that owns a `mplayer::player::Player`. Workspace member or
standalone crate depending on `mplayer` by path, either works.
Rationale: the JNI crate needs `crate-type = ["cdylib"]`, must build with
`--no-default-features --features player` (no `winit`, no `desktop`), and must
not drag the desktop shell into the APK.

```toml
[package]
name = "mplayer-android"
edition = "2024"

[lib]
name = "mplayer_jni"          # produces libmplayer_jni.so
crate-type = ["cdylib"]

[dependencies]
mplayer = { path = "..", default-features = false, features = ["player"] }
jni = "0.21"
ndk = "0.9"                   # ANativeWindow_fromSurface
raw-window-handle = "0.6"     # bridge ANativeWindow -> wgpu::Surface
log = "0.4"
android_logger = "0.14"
```

`System.loadLibrary("mplayer_jni")` in `MplayerNative`'s `init` block matches
`libname = "mplayer_jni"`. **Do not rename this without updating Kotlin.**

**Toolchain notes**
- Targets: `aarch64-linux-android` (required — TV boxes and Shield are arm64),
  `x86_64-linux-android` (emulator only). Skip 32-bit. Use **API 26+** for the
  native ABI (`AAudio`, usable Vulkan): `ANDROID_PLATFORM=android-26`.
- Build with `cargo-ndk` (`cargo ndk -t arm64-v8a -o app/src/main/jniLibs build --release`)
  rather than hand-rolled linker config. Gradle picks `.so` up from `jniLibs/`
  automatically — no CMake/`externalNativeBuild` needed.
- **FFmpeg is the hard part.** `.cargo/config.toml` currently points
  `ffmpeg-next` at Windows vcpkg. Android needs FFmpeg cross-compiled against
  the NDK sysroot and either statically linked or shipped as extra `.so`s in
  `jniLibs/`. Build a minimal config (`--disable-programs --disable-doc
  --enable-protocol=file,http,https,tcp,tls --enable-demuxer=matroska,mov,mpegts
  --enable-decoder=h264,hevc,av1,aac,ac3,eac3,opus,flac,mp3`) and set
  `FFMPEG_DIR` for `ffmpeg-next`'s build script. Static linking is preferred:
  one `.so` to ship, no `dlopen` ordering issues.
- `cpal` on Android needs its `oboe` backend (`cpal` builds an AAudio/OpenSL
  path via the `oboe` crate); verify it links before anything else, it is the
  most likely surprise.

**jni crate vs raw `#[no_mangle] extern "C"`**: use the **`jni` crate** with
`#[no_mangle] pub extern "system" fn Java_...` functions taking `JNIEnv`,
`JClass`/`JObject`. Do not use `#[jni_fn]`-style macro magic — the mangled names
below must be greppable in source. Implement `JNI_OnLoad` **only** to (a) cache
the `MplayerEngine` method ID, (b) stash the `JavaVM` in a `OnceLock` for the
event thread, (c) init `android_logger`. Return `JNI_VERSION_1_6`.

Every entry point must wrap its body in `catch_unwind`; a Rust panic unwinding
across the JNI boundary is UB and will hard-crash the app. On panic, log and
emit `EVENT_ERROR`.

---

## 2. JNI symbol table

Kotlin declarations (uncomment in `MplayerNative.kt` when the `.so` exists):

```kotlin
object MplayerNative {
    init { System.loadLibrary("mplayer_jni") }
    external fun nativeCreate(engine: MplayerEngine): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeSetSurface(handle: Long, surface: Surface?)
    external fun nativeLoad(handle: Long, url: String, headersJson: String, startMs: Long)
    external fun nativePlay(handle: Long)
    external fun nativePause(handle: Long)
    external fun nativeSeek(handle: Long, ms: Long)
    external fun nativeSelectTrack(handle: Long, trackType: Int, trackId: Int)
    external fun nativeStop(handle: Long)
    external fun nativeGetPositionMs(handle: Long): Long
    external fun nativeGetDurationMs(handle: Long): Long
    external fun nativeGetTracksJson(handle: Long): String
    external fun nativeGetState(handle: Long): Int
}
```

`MplayerNative` is a Kotlin `object`, so every method is an **instance** method
on the singleton — the second JNI parameter is `jobject`, not `jclass`. There is
no overloading, so **no signature-suffixed symbol names are needed.**

| Symbol | C ABI | Notes |
|---|---|---|
| `Java_com_maik205_shoumeiplayer_player_MplayerNative_nativeCreate` | `jlong f(JNIEnv*, jobject, jobject engine)` | `NewGlobalRef(engine)`; returns `Box::into_raw` as `jlong`. `0` = failure. |
| `..._nativeDestroy` | `void f(JNIEnv*, jobject, jlong)` | Joins threads, `DeleteGlobalRef`, `Box::from_raw`. Idempotent for `0`. |
| `..._nativeSetSurface` | `void f(JNIEnv*, jobject, jlong, jobject surface)` | `surface` may be `null`. |
| `..._nativeLoad` | `void f(JNIEnv*, jobject, jlong, jstring url, jstring headersJson, jlong startMs)` | |
| `..._nativePlay` | `void f(JNIEnv*, jobject, jlong)` | |
| `..._nativePause` | `void f(JNIEnv*, jobject, jlong)` | |
| `..._nativeSeek` | `void f(JNIEnv*, jobject, jlong, jlong ms)` | |
| `..._nativeSelectTrack` | `void f(JNIEnv*, jobject, jlong, jint trackType, jint trackId)` | |
| `..._nativeStop` | `void f(JNIEnv*, jobject, jlong)` | |
| `..._nativeGetPositionMs` | `jlong f(JNIEnv*, jobject, jlong)` | |
| `..._nativeGetDurationMs` | `jlong f(JNIEnv*, jobject, jlong)` | `-1` when unknown → Kotlin `null`. |
| `..._nativeGetTracksJson` | `jstring f(JNIEnv*, jobject, jlong)` | Never null; `"[]"` when empty. |
| `..._nativeGetState` | `jint f(JNIEnv*, jobject, jlong)` | State enum of §3. |

**Parameter semantics**

- `handle`: opaque `Box<AndroidEngine>` pointer. `0` is always a safe no-op.
- `url`: absolute `http(s)://` Jellyfin URL, already containing `api_key=<token>`
  and `static=true` (built by `PlaybackRepository`). UTF-8, may contain `%`-escapes.
- `headersJson`: a flat JSON object, e.g.
  `{"X-Emby-Token":"...","User-Agent":"Shoumei/1.0 (Android TV)"}`. `"{}"` when
  empty. JSON avoids a `Map` marshalling dance across JNI. Rust may parse it with
  a 30-line hand-rolled parser; no `serde` dependency is required.
- `trackType`: `0 = AUDIO`, `1 = SUBTITLE` (matches `TrackType` ordinal).
- `trackId`: the **engine-assigned** track id from `nativeGetTracksJson` /
  `EVENT_TRACKS`, i.e. the FFmpeg stream index. `-1` means "off" (subtitles only).
  Note: `PlayerTrack.id` is documented as the Jellyfin `MediaStream.Index`; those
  two numberings are **not guaranteed equal**. The engine is authoritative — the
  track list Kotlin displays comes from the engine, not from Jellyfin metadata.

**Track JSON shape** (`nativeGetTracksJson` and `EVENT_TRACKS` payload):

```json
[{"id":1,"type":0,"label":"English (AAC 5.1)","language":"eng","isDefault":true,"selected":true},
 {"id":3,"type":1,"label":"English (SRT)","language":"eng","isDefault":false,"selected":false}]
```

Field names match `PlayerTrack` exactly. `language` may be `null`.

---

## 3. Rust → Kotlin event path

**Recommendation: a single upcall on a dedicated event thread. Not polling.**

Rust holds a `GlobalRef` to the `MplayerEngine` instance handed to
`nativeCreate`. A dedicated thread (`AttachCurrentThreadAsDaemon`, attached once
for its lifetime — never per-call) drains an internal `mpsc` channel and calls:

```kotlin
// MplayerEngine.kt — called from a native thread, NOT the main looper
@Suppress("unused")
private fun onNativeEvent(type: Int, arg: Long, sarg: String?) { ... }
```

Method ID cached in `JNI_OnLoad`, descriptor `(IJLjava/lang/String;)V`.

*Why this and not polling getters:* state transitions are edge-triggered and
sparse (a `Loading→Buffering→Playing` sequence can complete in under one poll
interval and would be lost); a poll loop keeps the CPU awake and burns battery
on a device that is otherwise idle during playback; and `MplayerEngine` must
publish into `StateFlow`s, which are push-shaped already. `MutableStateFlow.value`
is thread-safe, so the upcall can write it directly with no Handler hop.
The getters in §2 remain in the contract as a **debug/recovery** path only
(non-blocking snapshot reads) — the event stream is the source of truth.

**Event table**

| Const | `type` | `arg` | `sarg` |
|---|---|---|---|
| `EVENT_STATE` | `1` | new state int (below) | `null` |
| `EVENT_POSITION` | `2` | position ms | `null` |
| `EVENT_DURATION` | `3` | duration ms, `-1` unknown | `null` |
| `EVENT_TRACKS` | `4` | `0` | tracks JSON array |
| `EVENT_ERROR` | `5` | `0` | human-readable message |
| `EVENT_VIDEO_SIZE` | `6` | `(width << 32) \| height` | `null` |

**State ints** (also the `nativeGetState` return):
`0 Idle`, `1 Loading`, `2 Buffering`, `3 Playing`, `4 Paused`, `5 Ended`,
`6 Error`. These map 1:1 onto `PlayerState`. `EVENT_ERROR` carries the message
for `PlayerState.Error(msg)` and must be emitted **before** the
`EVENT_STATE(6)` that follows it, so Kotlin never has an `Error` with a stale
message.

Cadence: `EVENT_POSITION` every **250 ms** while `Playing`, plus one immediately
after every completed seek and on every pause. Do not emit while `Paused`.
`EVENT_DURATION` and `EVENT_TRACKS` once per successful load, and `EVENT_TRACKS`
again after any `selectTrack` (the `selected` flags change).

---

## 4. Threading rules

- **All `native*` calls must return in < ~5 ms.** They are invoked from the
  Compose UI / main thread. Every one of them is a `Sender::send` onto the
  engine thread's command queue plus an immediate return. `nativeLoad` in
  particular must not open the URL synchronously — network I/O on the main
  thread is an ANR. The only exceptions are `nativeCreate` (thread spawn) and
  `nativeDestroy` (see below).
- **`nativeDestroy` blocks** — it joins the render and event threads. Kotlin
  calls it from `release()`, which happens in `onCleared()`/`onDestroy()`.
  Bound it: if the render thread does not exit in 2 s, log and detach rather
  than deadlocking the app teardown.
- **Surface lifecycle.** `SurfaceHolder.Callback.surfaceCreated` →
  `nativeSetSurface(handle, surface)`; `surfaceDestroyed` →
  **`nativeSetSurface(handle, null)`, and this call must block** until the render
  thread has dropped both the `wgpu::Surface` and the `ANativeWindow`. Returning
  early lets Android free the buffer queue under a live GPU surface — an
  immediate native crash. This is the one place a short block is mandatory;
  keep it under 500 ms.
  - With no surface the engine keeps decoding audio and advancing the clock, or
    pauses — implementer's choice, but it must **not** transition to `Error`.
    A TV app loses its surface on every backgrounding.
  - Re-attach: a later `nativeSetSurface(handle, newSurface)` rebuilds the wgpu
    surface and resumes rendering at the current clock position. `load` state is
    preserved across a destroy/create cycle.
- **Seeking during `Buffering` or `Loading`** is legal and must be coalesced:
  keep only the newest pending target, drop the rest. `seekTo` before
  `EVENT_DURATION` clamps to `[0, duration]` once duration is known.
  Emit `EVENT_STATE(Buffering)` on seek start and the previous state on
  completion.
- **Release ordering**, strictly: `stop()` → `setSurface(null)` → `release()`.
  Kotlin's `release()` must tolerate being called twice and must null its handle
  first, so a late event upcall against a freed `Box` is impossible. On the Rust
  side, the event thread is joined **before** the `GlobalRef` is deleted.
- The `MplayerEngine` `StateFlow` writes happen on the native event thread;
  Compose collectors are on the main dispatcher. No extra synchronization needed.

---

## 5. HTTP / network specifics

The engine fetches Jellyfin URLs itself; the app does **not** proxy bytes.

- **Auth is in the URL.** `PlaybackRepository` emits
  `{server}/Videos/{id}/stream?static=true&mediaSourceId=...&api_key={token}`.
  The engine must preserve the full query string verbatim, including on redirect
  follow. Do not strip or re-encode.
- `headersJson` additionally carries `X-Emby-Token` and a `User-Agent`. Pass
  these to FFmpeg via the `headers` AVOption (CRLF-joined) and `user_agent`.
  This requires `format::input_with_dictionary` instead of the current bare
  `format::input(&path)`.
- **Redirects**: follow, up to 5 (`follow_location=1`, `max_reload`). Jellyfin
  behind a reverse proxy commonly 302s to a different host.
- **TLS**: self-signed / private-CA servers are common in the homelab case.
  FFmpeg must be built with a TLS backend (mbedTLS or OpenSSL). Certificate
  policy is strict by default; a "trust anything" toggle, if added later, is an
  explicit app-level setting, never a default.
- **Reconnect**: `reconnect=1`, `reconnect_streamed=1`, `reconnect_delay_max=5`.
  A transient drop should surface as `Buffering`, not `Error`. Only give up
  after the reconnect budget is exhausted, then `EVENT_ERROR` + `EVENT_STATE(6)`.
- **Range requests / seek** rely on the server honoring HTTP Range. Direct-play
  static streams do; transcode streams generally do not. If a seek on a
  non-seekable input fails, stay at the current position and emit
  `EVENT_ERROR` with a clear message — do not enter a broken state.
- **Subtitles**: embedded tracks only for v1 — enumerate subtitle streams in the
  container, decode and render the selected one. External subtitle URLs
  (Jellyfin `/Subtitles/...`) are explicitly **out of scope** until the embedded
  path works. Text (subrip/ass) before bitmap (PGS/VobSub). The `sassy` submodule
  already vendored in `../mplayer` is the intended ASS renderer.
- No caching to disk. No prefetch beyond the decoder's own buffers.

---

## 6. Rust-side milestone checklist

| # | Deliverable | Done when |
|---|---|---|
| **R0** | Cross-compile spike | `libmplayer_jni.so` for `aarch64-linux-android` builds and links, FFmpeg + cpal/oboe included. No player logic. |
| **R1** | Stub `.so`, full symbol table | All 13 symbols exported, `nativeCreate` returns a handle, `nativeLoad` immediately emits `EVENT_ERROR("not implemented")` + `EVENT_STATE(6)`. Kotlin uncomments `MplayerNative`, app shows the error in the player UI. **This is the integration gate** — everything after it is engine work behind a stable ABI. |
| **R2** | Surface + video only | `nativeSetSurface` builds a `wgpu::Surface` from `ANativeWindow`; a local file or plain HTTP URL renders video on a real device. `EVENT_STATE` reaches `Playing`, `EVENT_VIDEO_SIZE` fires. Correct behavior across background/foreground (surface destroy + re-attach). |
| **R3** | Audio + seek + Jellyfin HTTP | A/V sync via the existing clock; `nativeSeek` works (this requires **new** work in `mplayer` — see Appendix); `startPositionMs` honored on load; auth'd Jellyfin URL with headers and redirects plays. `EVENT_POSITION`/`EVENT_DURATION` correct. Pause/resume clean. |
| **R4** | Tracks | `nativeGetTracksJson` / `EVENT_TRACKS` enumerate audio + subtitle streams; `nativeSelectTrack` switches audio without a visible stall; embedded subtitles render via `sassy`. |
| **R5** | Production | Panic safety verified (no unwind escapes JNI), no leaks over a 100× load/release cycle, hardware decode (MediaCodec via FFmpeg or a native path), 4K HEVC on target hardware, `x86_64` emulator build for CI. |

Kotlin can land R1 and then track the rest with no further API churn: the ABI
above is frozen at R1.

---

## Appendix — current state of `../mplayer` (read-only survey, 2026-07-26)

**What it is.** A ~3.6 kLOC Rust 2024 crate: `mplayer` lib (`src/lib.rs`) plus a
`mplayer` bin gated on the `desktop` feature. Features: `ffmpeg` (`ffmpeg-next`
8), `player` (= ffmpeg + `cpal` 0.18 + `num`), `desktop` (= player + `winit` 0.30
+ `pollster` + `terminal_size`). Default is `player + desktop`.

**Modules.** `video/` — a wgpu 30 `VideoLayer` that uploads a decoded frame and
records one draw call into the caller's render pass (`shaders.wgsl` does YUV→RGB;
CPU fallback to RGBA). `compose/` — `Layer` trait + `Compositor` stack.
`player/` — headless demux/decode/A-V-sync: `MPlayerCore` spawns a media thread
and per-stream `DecodeThread`s over mpsc channels; `Player` owns the master
`Clock` and cpal output. `desktop/` — winit shell, wgpu renderer, small TUI.
`tests/` has `gpu_video.rs` and `player_smoke.rs`. `sassy/` is a git submodule
(`maik205/sassy`), an ASS/SSA subtitle renderer, not yet referenced from code.

**Public playback API.** `Player::new()`, `open(&str)` (async; result arrives as
`PlayerNotice::MediaOpened` / `OpenFailed`), `play()`, `pause()`,
`is_paused()`, `position() -> Duration`, `duration() -> Option<Duration>`,
`poll_frame(|frame| ...)`, `poll_frame_owned()`, `take_events() -> Vec<PlayerNotice>`,
`audio_devices()`, `set_audio_device()`, and `handle() -> PlayerHandle` — a
`Clone + Send` mpsc remote with `Open/Play/Pause/TogglePause/SetAudioDevice`.
`PlayerNotice` = `MediaOpened | OpenFailed | EndOfStream | AudioError | DecodeError`.

**How it maps to this contract — and where it does not.**

1. **No Android story whatsoever.** No `jni`/`ndk` dependency, no cdylib target,
   no `.so`, no cross-compile config. `.cargo/config.toml` pins `VCPKG_ROOT` to a
   Windows path for FFmpeg — Android needs an entirely separate FFmpeg build.
   Android is mentioned only in three forward-looking comments
   (`desktop/renderer.rs:40`, `desktop/shell.rs:107,202`, `main.rs:59`).
2. **Pull-based, host-driven render loop.** `Player::poll_frame` expects an
   external event loop (winit) to call it once per rendered frame. Kotlin only
   hands over a `Surface`; the JNI crate must therefore **own** its own render
   thread, wgpu instance/surface and vsync pacing, and call `poll_frame` itself.
   That is genuinely new code, not a wrapper — budget for it in R2.
3. **Seek does not exist.** `MediaThreadCommand::Seek(i64)` and
   `MediaThreadStatus::Seeking` are declared but `#[allow(dead_code)]` and
   unwired; there is no `Player::seek`. `seekTo` **and** `startPositionMs` both
   depend on implementing this from scratch (R3).
4. **No track enumeration or selection.** `open_media` picks
   `streams().best(Video)` and `streams().best(Audio)` and nothing else. There is
   no stream list, no `select_track`, and `MediaInfo` is `#[allow(dead_code)]`.
   `tracks` / `selectTrack` are unbacked (R4).
5. **Subtitles are commented out.** `MPlayerCore.subtitle` exists as a field but
   the whole subtitle `DecodeThread` block in `core.rs:237-250` is commented.
   `sassy` is vendored but unused.
6. **No HTTP options plumbing.** `open_media` calls `format::input(&path)` with
   no `AVDictionary`, so there is no way to pass `user_agent`, `headers`,
   `follow_location` or `reconnect`. §5 requires switching to
   `input_with_dictionary`. The `api_key`-in-URL scheme does work today since
   the URL is passed through untouched.
7. **State model is thinner than `PlayerState`.** There is no `Loading`,
   `Buffering` or explicit `Paused` notice — only `is_paused()` and the open/EOF
   edges. The JNI layer must synthesize the seven-state machine of §3 from
   `PlayerNotice` + clock/buffer inspection.
8. **Audio backend risk.** `cpal` 0.18 must resolve to its Android
   (oboe/AAudio) backend; this is unproven here and is the most likely R0 blocker.
9. **README is stale**: it advertises SDL3 for windowing/audio/rendering, but the
   code uses wgpu + winit + cpal. Trust the code.

Net: the decode/sync/GPU-upload core is genuinely reusable and maps well onto
`load/play/pause/position/duration/state`. Everything the app needs *around* it —
JNI, an owned render thread, seek, tracks, subtitles, HTTP options — is new work.
