# 038 — Loudness and bit-perfect USB audio

**Status:** No safe application-layer integration point

## Finding

Shoumei's audio path is also owned by libmpv. The app can negotiate the active Android route and
report route capabilities, but it cannot obtain the mpv-created `AudioTrack`, its audio session ID,
or the exact PCM format at the Android mixer boundary.

This prevents a safe implementation of either feature:

- `LoudnessCodecController` requires a codec/session path whose ownership and metadata flow are
  visible to the app. mpv may decode through FFmpeg or an internal MediaCodec path, and the current
  bridge cannot distinguish those paths or attach the controller safely.
- `AudioMixerAttributes.BIT_PERFECT` requires a matching device, format, attributes, and lifecycle
  ownership. The current bridge cannot guarantee that mpv's `AudioTrack` matches the requested mixer
  attributes or clear the preference on focus loss, route changes, and device removal.

Bit-perfect output would also intentionally bypass normal system volume processing. Enabling it without
an explicit, route-aware contract would be a user-visible audio regression, not an optimization.

## Decision

Do not add a user-facing toggle or call either API from the current Kotlin layer. Continue using mpv's
normal audio output and the existing Android route/focus policy.

## Required future experiment

An upstream mpv hook must expose the audio session, exact format, and teardown callback. Only then can a
device matrix test compare loudness metadata handling and USB mixer attributes against mpv ReplayGain
or normalization, including route changes, focus loss, device removal, and item transitions.

References:

- https://developer.android.com/reference/android/media/LoudnessCodecController
- https://developer.android.com/reference/android/media/AudioManager#setPreferredMixerAttributes(android.media.AudioAttributes,android.media.AudioDeviceInfo,android.media.AudioMixerAttributes)
