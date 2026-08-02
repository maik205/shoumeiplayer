<img width="3840" height="1032" alt="Rectangle" src="https://github.com/user-attachments/assets/5e1b4591-6b66-40cc-a9ea-8f538f007217" />

# Shoumei Player

Shoumei Player is an opinionated Jellyfin client specifically built for Google TVs. The app is written in Kotlin with Jetpack Compose and uses mpv for audio and video playback. I love mpv <3


<img width="400" alt="Screenshot 2026-08-02 at 13 40 21" src="https://github.com/user-attachments/assets/fe38a0d5-fea1-4cb2-beb8-3cde12b91303" />
<img width="400" alt="image" src="https://github.com/user-attachments/assets/abec40a5-32d0-43d7-8ab4-9de14736a051" />

## Features

- Jellyfin server discovery and username/password authentication
- Home sections for libraries, latest items, continue watching, and next up
- Library browsing with pagination, sorting, and watched-state filters
- Catalog search
- Movie, series, season, and episode details
- Audio and subtitle track selection
- Playback progress reporting to Jellyfin
- Configurable playback, interface, network, subtitle, and screensaver settings

## Playback

`MpvEngine` connects to official mpv v0.41.0 through the app-owned `MpvNative` Java Native Interface (JNI) bridge. Both debug and release builds use this engine.

The mpv source is pinned in `native/mpv/upstream`. Gradle downloads a checksum-pinned native bundle for `armeabi-v7a`, `arm64-v8a`, and `x86_64`. The project does not depend on a player Android Archive (AAR) or a repackaged mpv binding.

The current mpv configuration uses Android GPU output, MediaCodec hardware decoding, AudioTrack output, and Jellyfin authentication headers. Application code accesses playback through the `PlayerEngine` interface.

## Project structure

The project uses eight Gradle modules with manual dependency injection and `StateFlow`-based ViewModels:

| Module | Purpose |
| --- | --- |
| `:app` | Application setup, navigation, screens, and Android platform integration |
| `:feature:player` | Player ViewModel and playback-session coordination |
| `:core:jellyfin` | Ktor client and Jellyfin data transfer objects |
| `:core:data` | Authentication, library, playback, settings, and cache repositories |
| `:core:player` | Playback contracts and mpv integration |
| `:core:model` | Shared domain models |
| `:core:designsystem-tv` | Shared Compose components and theme definitions |
| `:mpvroid` | JNI bridge and native mpv provisioning |

## Requirements

- JDK 17 or newer
- Android Studio or the Android command-line tools
- Android SDK 36.1
- Android NDK r29
- Android TV device or emulator running API 28 or newer

## Build

Initialize the pinned mpv source after cloning the repository:

```powershell
git submodule update --init --recursive
```

Build a debug Android Package (APK):

```powershell
.\gradlew.bat :app:assembleDebug
```

The first build downloads and verifies the pinned mpv native bundle. Gradle caches the archive for later builds. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run the Java Virtual Machine (JVM) unit tests and Android lint checks with:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

## Network policy

The client accepts HTTPS Jellyfin servers. It also supports HTTP for loopback addresses, private IPv4 addresses, and local hostnames ending in `.local`, `.lan`, `.home`, or `.internal`.

For eligible local servers, onboarding tries HTTPS first. Falling back to HTTP requires a warning and explicit confirmation. Public HTTP endpoints are rejected.

## Documentation

- [`docs/README.md`](docs/README.md): maintained project documentation
- [`native/mpv/README.md`](native/mpv/README.md): pinned mpv source and native library manifest
