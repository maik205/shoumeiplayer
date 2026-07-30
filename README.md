# Shoumei Player (証明プレイヤー)

Shoumei ("証明" — proof, or attestation) Player is a native Android TV client for
[Jellyfin](https://jellyfin.org), built with Jetpack Compose and
`androidx.tv:tv-material`. It targets living-room, D-pad-driven navigation
rather than a phone UI ported to a big screen.

The name is a nod to what this project is: proof that the stack works
end-to-end against a real Jellyfin server, from browsing through to decoding
video with libmpv.

## Features

Scope, per `docs/design.md`. All milestones (M0–M6) are implemented; the visual
design is specified in `docs/ui-design.md` and applied throughout. Playback is
handled by libmpv (see the playback-engine section below).

- Jellyfin server discovery + username/password authentication
- Home screen with horizontal rows: Continue Watching, Next Up, and Latest
  per library, plus a row of the user's library views
- Library browsing: paged grid, sort (name / date added / premiere), filter
  by watched state
- Item detail: metadata, backdrop hero, resume/play actions; series detail
  with season selector and episode list
- Search across the server's catalog
- Full player UI: transport controls, seek bar, audio/subtitle track
  selection, auto-hiding OSD, and progress reporting back to the server
  (`/Sessions/Playing`, `/Progress`, `/Stopped`)

## Playback engine

`MpvEngine` drives official mpv v0.41.0 directly through the app-owned
`MpvNative` JNI bridge and is used in both debug and release builds. The mpv
source is pinned as `native/mpv/upstream`; no player AAR or repackaged mpv
binding is used. It renders through `vo=gpu` /
`gpu-context=android` with `hwdec=mediacodec-copy` and `ao=audiotrack`, maps
mpv properties onto the `PlayerEngine` state/position/duration/track flows,
and passes Jellyfin auth headers through `http-header-fields`.

- `MplayerEngine` remains the *planned* native successor behind the exact same
  `PlayerEngine` interface: it still compiles as a logged no-op stub with
  `// TODO(mplayer): bind via JNI to ../mplayer` markers. The real engine lives
  in a sibling Rust project at `../mplayer` that is not part of this
  repository; see `docs/mplayer-integration.md` for the integration plan.
- `SimulatedPlayerEngine` is retained as a fake clock for JVM unit tests and
  UI work that should not touch a real decoder.

## Architecture

Single `:app` module, layered by package, manual dependency injection (no
Hilt/KSP — the object graph is small and this avoids build fragility on
AGP 9.2 / Kotlin 2.4), unidirectional data flow with `StateFlow`-based
ViewModels.

```
com.maik205.shoumeiplayer
├── ShoumeiApp.kt            // Application; owns AppContainer
├── di/AppContainer.kt       // constructs client, repos, engine; vm factories
├── data/
│   ├── api/                 // JellyfinClient (Ktor), request/response DTOs
│   ├── session/             // SessionStore: DataStore-backed server url + token + userId
│   └── repo/                // AuthRepository, LibraryRepository, PlaybackRepository
├── player/
│   ├── PlayerEngine.kt      // engine-agnostic contract
│   ├── MpvEngine.kt         // real engine: official libmpv via app-owned JNI
│   ├── MplayerEngine.kt     // stub impl, JNI TODOs → ../mplayer (planned successor)
│   └── SimulatedPlayerEngine.kt // fake clock for tests / UI work
└── ui/
    ├── theme/
    ├── navigation/          // NavHost, typed (kotlinx-serialization) routes
    ├── components/          // MediaCard, MediaRow, PosterImage, focus helpers
    └── screens/             // serverentry, login, home, library, detail, player, search, settings
```

Key data-layer choices:

- **Ktor** (OkHttp engine) with kotlinx-serialization JSON,
  `ignoreUnknownKeys = true` — Jellyfin's `BaseItemDto` has roughly 150
  fields; only what the UI needs is modeled.
- DTOs are hand-written and trimmed rather than generated from the Jellyfin
  OpenAPI spec (`jellyfin-openapi.json` is ~2.2 MB, most of it unused by a TV
  playback client). See `docs/jellyfin-api-surface.md` for the reference this
  client was implemented against.
- `SessionStore` (DataStore Preferences) holds `serverUrl`, `accessToken`,
  `userId`, and a generated `deviceId`, exposed as `Flow<Session?>`.

The `PlayerEngine` contract (`player/PlayerEngine.kt`) exposes state,
position, duration, and track flows plus `load` / `play` / `pause` /
`seekTo` / `selectTrack` / `stop` / `release`. Everything above the engine —
screens, ViewModels, progress reporting — talks only to this interface, so
swapping libmpv for the `../mplayer` JNI engine later should not require UI
changes.

## Build & run

Requirements: JDK 17+, Android Studio (or the command line), Android NDK r28+,
and an Android TV emulator or device running API 28 or newer. Before assembling
an APK, build the native libraries from the pinned official source as described
in `native/mpv/README.md`.

```
.\gradlew.bat :app:assembleDebug
```

Install the resulting debug APK on an Android TV emulator/device and launch
it. On first run it asks for a Jellyfin server URL, then username/password.

- Both debug and release builds use `MpvEngine`. Gradle deliberately refuses
  to assemble an APK when the official `libmpv.so` build is absent.
- Home-lab Jellyfin servers are frequently plain HTTP rather than HTTPS.
  Cleartext traffic is allowed via a scoped network security config, so
  entering `http://192.168.x.x:8096`-style addresses works without any
  extra configuration.

Run JVM unit tests with:

```
.\gradlew.bat :app:testDebugUnitTest
```

## Docs

- `docs/design.md` — architecture and design rationale, including the
  player shim decision
- `docs/plan.md` — task breakdown and wave/parallelism plan used to build
  this app
- `docs/jellyfin-api-surface.md` — trimmed Jellyfin API reference this
  client implements against
- `docs/ui-design.md` — the visual design specification (color tokens,
  typography, focus treatment, per-screen art direction) applied across the UI
- `docs/mplayer-integration.md` — JNI contract and milestones for binding the
  real `../mplayer` Rust engine as the successor to libmpv

## Status

| Milestone | Scope | Status |
|---|---|---|
| M0 | Foundation: catalog bumps, manifest fixes, minSdk, App/DI skeleton | Done |
| M1 | Data: Ktor client, DTOs, SessionStore, repos + tests | Done |
| M2 | Player shim: contract, `MplayerEngine` stub, `SimulatedPlayerEngine`, JNI sketch | Done |
| M3 | Core UI: navigation, ServerEntry, Login, Home, components | Done |
| M4 | Browse UI: Library, Detail, Search | Done |
| M5 | Player UI: player screen, OSD, progress reporting | Done |
| M6 | Polish: Settings, 401 handling, focus/overscan pass, build+tests green | Done (32 unit tests; debug + release builds green, zero compile warnings) |
