# 037 — Detached MediaCodec surfaces

**Status:** Blocked by the current mpv ownership boundary

## Finding

The current native bridge already has an mpv-level detach operation, but it does not detach an
Android `MediaCodec` output surface. `MpvNative.detachSurface()` sets mpv's window handle to an
invalid value and clears the JNI global `Surface` reference. The replacement surface is later passed
back through `attachSurface()`.

That is materially different from Android 15 `MediaCodec.detachOutputSurface()`: Shoumei has no
underlying codec object on which to call that API, and no proof that the pinned mpv/FFmpeg Android
decoder uses `FEATURE_DetachedSurface`.

## Decision

Do not change normal Surface lifecycle semantics. The current mpv-level detach/attach path remains the
fallback for API 35 and below, unsupported codecs, and all codecs until an mpv-owned experiment proves
decoder state, HDR metadata, buffered data, and A/V continuity survive a detached surface.

## Required future experiment

The experiment must live beside the mpv MediaCodec wrapper and expose only a narrow result to Shoumei:
whether the current decoder retained state after detach/attach. It must compare decoder restart time,
black-frame duration, memory, buffer continuity, and Jellyfin progress reporting across activity
recreation, display mode changes, background/foreground, and episode transitions.

Reference: https://developer.android.com/reference/android/media/MediaCodec#detachOutputSurface()
