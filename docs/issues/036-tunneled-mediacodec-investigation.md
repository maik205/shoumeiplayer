# 036 — Tunneled MediaCodec playback

**Status:** Blocked by the current mpv ownership boundary

## Finding

The app does not own an Android `MediaCodec`. The pinned playback path is app-owned JNI over
libmpv, and the exposed native surface is limited to mpv's `wid` property:

- `mpvroid/src/main/java/com/maik205/mpvroid/MpvNative.kt` exposes `attachSurface` and `detachSurface`.
- `mpvroid/src/main/cpp/mpv_jni.cpp` converts the Android `Surface` to an mpv window handle.
- `core/player/src/main/java/com/maik205/shoumeiplayer/player/MpvEngine.kt` configures mpv and observes
  mpv state, tracks, timing, and frame-rate properties.

No JNI handle or callback exposes `MediaCodec`, codec capabilities, an audio session ID, or the
configuration point required to request `FEATURE_TunneledPlayback`. Adding a capability probe alone
would therefore report device support without proving that mpv can use the feature.

## Consequences

Tunneled playback cannot be safely enabled without an upstream mpv integration or a fork that owns
the MediaCodec creation/configuration path. Even if a device advertises tunneled support, Shoumei
could not currently verify:

- the required shared audio session ID;
- compatibility with mpv's `audiotrack` output;
- Compose overlay and subtitle behavior;
- HDR/tone-mapping behavior;
- trickplay, speed, delay, and Surface recreation behavior.

## Decision

Do not add a production setting or a misleading capability flag. Keep the existing `mediacodec-copy`
and mpv fallback behavior. Reopen implementation work only after an upstream or vendored mpv change
exposes a tested MediaCodec/audio-session contract.

## Required future experiment

The smallest valid prototype must be implemented in the mpv/FFmpeg layer, not the Kotlin wrapper. It
must compare normal and tunneled playback on representative 1080p/4K devices while recording first
frame time, dropped frames, CPU/GPU load, thermal state, A/V drift, HDR, subtitles, speed, and Surface
recreation behavior.

Reference: https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback
