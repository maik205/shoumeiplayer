# Build official mpv for Android

Shoumei Player binds directly to official mpv through the app-owned Java Native Interface (JNI) bridge. The project does not use a repackaged player library or Android Archive (AAR).

## Source pins

The `native/mpv/upstream` submodule pins mpv `v0.41.0`. The Android libraries use these revisions:

| Component | Revision |
| --- | --- |
| mpv | `41f6a645068483470267271e1d09966ca3b9f413` |
| FFmpeg | `38b88335f99e76ed89ff3c93f877fdefce736c13` |
| dav1d | `54706fc6bc0cdecab7e9593974a4039cc038fca7` |
| libass | `f9fd3d20dff1cd84b7c74c8ae7f79711ad7736fa` |
| libplacebo | `4c426e466814536def653cb23f1d1c287ea7a7f5` |

The native bundle uses Android Native Development Kit (NDK) r29 and supports `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`.

No third-party player AAR or prebuilt media library is accepted. `build-android.sh` compiles the pinned official mpv source and each required codec, TLS, text-shaping, and subtitle dependency from its original upstream source. `sources.env` is the immutable source/toolchain lock.

From the repository root inside Linux or WSL2:

```bash
./native/mpv/build-android.sh
```

Pass one or more ABI names to perform an incremental targeted build, for example `./native/mpv/build-android.sh x86`.

## Provision native libraries

Gradle downloads the native bundle from the `native-mpv-v0.41.0-r1` GitHub release. The URL and SHA-256 digest are pinned in `gradle.properties`.

The `:mpvroid:provisionMpvNativeLibraries` task verifies the archive before extraction. Gradle stores the downloaded archive under its user cache and extracts libraries to `mpvroid/build/generated/mpvNative/jniLibs`.

Native configuration fails when the bundle is unavailable, its digest differs from the pin, or a required library is absent. CMake builds `libmpvroid_jni.so` for each supported application binary interface (ABI).

## Publish a new native bundle

Build each ABI from the pinned source with mpv's Android build scripts and NDK r29. Use `tools/package_mpv_native.py` to create the deterministic archive:

```powershell
python .\tools\package_mpv_native.py --help
```

Publish the archive under a new immutable `native-mpv-vVERSION-rREVISION` release. Update the bundle version, URL, and SHA-256 properties together. Never replace an asset referenced by an existing digest.
