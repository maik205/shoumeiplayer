package com.maik205.shoumeiplayer.player

// TODO(mplayer): bind via JNI to ../mplayer
//
// This object sketches the JNI surface that ../mplayer (the Rust/native
// player core) is expected to export. Every `external fun` below is left
// commented out until the native library actually exists and is built as
// part of this project; uncommenting any of these before that point would
// break compilation (unresolved external references) and this task requires
// the build to stay green with zero uncommented JNI references.
//
// Intended handle-based lifecycle:
//   1. nativeCreate() -> handle
//   2. nativeSetSurface(handle, surface) as the output Surface becomes
//      available/unavailable
//   3. nativeLoad(handle, url, headersJson, startMs) to begin loading a URL
//   4. nativePlay/nativePause/nativeSeek(handle, ms) to control playback
//   5. nativeSelectTrack(handle, trackType, trackId) to switch audio/subtitle
//   6. nativeGetPositionMs/nativeGetDurationMs/nativeGetTracksJson/
//      nativeGetState polled or callback-driven to report state back to
//      MplayerEngine's StateFlows
//   7. nativeDestroy(handle) on release()
object MplayerNative {

    init {
        // TODO(mplayer): bind via JNI to ../mplayer
        // System.loadLibrary("mplayer_jni")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeCreate(): Long

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeDestroy(handle: Long)

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeSetSurface(handle: Long, surface: Surface?)

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeLoad(handle: Long, url: String, headersJson: String, startMs: Long)

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativePlay(handle: Long)

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativePause(handle: Long)

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeSeek(handle: Long, ms: Long)

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeSelectTrack(handle: Long, trackType: Int, trackId: Int)

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeGetPositionMs(handle: Long): Long

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeGetDurationMs(handle: Long): Long

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeGetTracksJson(handle: Long): String

    // TODO(mplayer): bind via JNI to ../mplayer
    // external fun nativeGetState(handle: Long): Int
}
