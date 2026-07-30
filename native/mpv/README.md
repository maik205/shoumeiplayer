# Official mpv Android build

The app binds directly to **mpv v0.41.0** from the official
`https://github.com/mpv-player/mpv` repository. `upstream/` is a pinned git
submodule at tag `v0.41.0` (`41f6a645068483470267271e1d09966ca3b9f413`);
do not replace it with an AAR or a binary from another Android player.

The local native set is built for `arm64-v8a` and `x86_64` with NDK r29 from
this checked-out source and mpv's Meson build with
`-Dlibmpv=true -Dcplayer=false`. The resulting libraries live under:

Source manifest for the bundled native artifacts:

| Component | Revision |
| --- | --- |
| mpv | `41f6a645068483470267271e1d09966ca3b9f413` (`v0.41.0`) |
| FFmpeg | `38b88335f99e76ed89ff3c93f877fdefce736c13` |
| dav1d | `54706fc6bc0cdecab7e9593974a4039cc038fca7` |
| libass | `f9fd3d20dff1cd84b7c74c8ae7f79711ad7736fa` |
| libplacebo | `4c426e466814536def653cb23f1d1c287ea7a7f5` |

```text
app/src/main/jniLibs/arm64-v8a/libmpv.so
app/src/main/jniLibs/arm64-v8a/libavcodec.so
app/src/main/jniLibs/arm64-v8a/libavformat.so
app/src/main/jniLibs/arm64-v8a/libavutil.so
...other DT_NEEDED libraries from that same source build...
```

Gradle intentionally fails its native configure step when `libmpv.so` is
absent, preventing an APK from silently falling back to a repackaged library.

The JNI bridge compiles against `upstream/include/mpv/client.h`, so the
declarations always match the pinned official core.
