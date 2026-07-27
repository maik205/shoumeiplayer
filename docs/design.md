# Shoumei Player — Design

Native Android TV media player for Jellyfin, Jetpack Compose (androidx.tv) first.
Playback engine is intentionally a **shim**: the real engine lives in `../mplayer`
(Rust) and will be bound later via JNI. Everything else — auth, browsing,
details, player UI, progress reporting — is real and works against a Jellyfin
server.

Companion doc: `docs/jellyfin-api-surface.md` (trimmed API reference extracted
from `jellyfin-openapi.json`). Implementers use that doc, not the raw spec.

## Constraints & fixes to the existing scaffold

- `minSdk = 36` is wrong for TV — no Android TV device runs API 36. Lower to
  **minSdk 28**, keep `compileSdk 36`, `targetSdk 36`.
- `AndroidManifest.xml` is missing `android.permission.INTERNET` — add it, plus
  `ACCESS_NETWORK_STATE`.
- `android.software.leanback` stays `required=false`; keep touchscreen not
  required. Add `android:usesCleartextTraffic="true"` (home-lab Jellyfin servers
  are frequently plain HTTP) — scoped via a network security config that allows
  cleartext to user-entered hosts.
- Version catalog is stale (Compose BOM 2024.09, tv-material alpha07). Bump to
  current stable: Compose BOM 2025.x, `androidx.tv:tv-material:1.0.x` (stable),
  lifecycle 2.9.x, activity-compose 1.11.x. Add: kotlinx-serialization plugin +
  json, Ktor client (core/okhttp/content-negotiation/serialization), Coil 3
  (compose + network-okhttp), DataStore preferences, navigation-compose,
  lifecycle-viewmodel-compose, kotlinx-coroutines-test/junit for tests.
- `appcompat` dependency is unnecessary for a Compose TV app — remove.

## Architecture

Single `:app` module. Layered by package, unidirectional data flow,
`StateFlow`-based ViewModels. **Manual DI** via an `AppContainer` held by the
`Application` class — no Hilt/KSP; the object graph is small and this avoids
build fragility on AGP 9.2/Kotlin 2.2.

```
com.maik205.shoumeiplayer
├── ShoumeiApp.kt            // Application; owns AppContainer
├── di/AppContainer.kt       // constructs client, repos, engine; vm factories
├── data/
│   ├── api/                 // JellyfinClient (Ktor), request/response DTOs
│   ├── session/             // SessionStore: DataStore-backed server url + token + userId
│   └── repo/                // AuthRepository, LibraryRepository, PlaybackRepository
├── player/
│   ├── PlayerEngine.kt      // the shim contract (below)
│   ├── MplayerEngine.kt     // stub impl, JNI TODOs → ../mplayer
│   └── SimulatedPlayerEngine.kt // debug-only fake that "plays" so UI is testable
└── ui/
    ├── theme/               // existing
    ├── navigation/          // NavHost, routes (kotlinx-serialization typed routes)
    ├── components/          // MediaCard, MediaRow, PosterImage, focus helpers
    └── screens/             // serverentry, login, home, library, detail, player, search, settings
```

### Data layer

- **Ktor** client with OkHttp engine, kotlinx-serialization JSON
  (`ignoreUnknownKeys = true` — Jellyfin DTOs are huge; we model only needed
  fields). Base URL + `Authorization: MediaBrowser ...` header injected from
  `SessionStore`.
- DTOs are hand-written, trimmed `BaseItemDto` etc. per
  `docs/jellyfin-api-surface.md`. No codegen from the OpenAPI spec — it's 2.2 MB
  and 95% unused.
- `SessionStore` (DataStore preferences): `serverUrl`, `accessToken`, `userId`,
  `deviceId` (random UUID generated once). Exposed as `Flow<Session?>`; login
  writes it, 401 responses clear it (single retry-less redirect to login).
- Repositories return `Result<T>`-style sealed outcomes; no exceptions crossing
  into UI.
- Image URLs are constructed client-side
  (`{server}/Items/{id}/Images/Primary?tag=...&maxWidth=...&quality=90`) by a
  small `ImageUrlBuilder` — unit-tested.

### Player shim contract

The UI and Jellyfin plumbing must be **engine-agnostic** so `../mplayer` drops
in later without touching screens:

```kotlin
interface PlayerEngine {
    val state: StateFlow<PlayerState>          // Idle, Loading, Buffering, Playing, Paused, Ended, Error(msg)
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long?>
    val tracks: StateFlow<List<PlayerTrack>>   // type AUDIO/SUBTITLE, id, label, selected
    fun setSurface(surface: Surface?)          // player screen owns a SurfaceView via AndroidView
    fun load(item: PlayRequest)                // url, headers, startPositionMs, preferred track ids
    fun play(); fun pause(); fun seekTo(ms: Long)
    fun selectTrack(track: PlayerTrack)
    fun stop(); fun release()
}
```

- `MplayerEngine`: compiles, wires nothing. Every method body is a logged no-op
  with `// TODO(mplayer): bind via JNI to ../mplayer` markers; `load()` moves
  state to `Error("mplayer engine not yet implemented")`. One
  `MplayerNative.kt` object sketches the intended `external fun` JNI surface as
  commented signatures so the Rust side knows what to export.
- `SimulatedPlayerEngine` (debug builds via `AppContainer`): fakes a clock —
  `load` → Playing, position ticks 1s/s toward a fake duration, seek/pause work.
  This lets the whole player screen, OSD, and progress reporting be built and
  demoed before mplayer exists.
- Progress reporting (`/Sessions/Playing[/Progress|/Stopped]`) is driven by
  observing `PlayerEngine.state` + a 10s ticker in the player ViewModel —
  engine-agnostic by construction. Position ↔ ticks conversion (×10 000 000)
  lives in one utility, unit-tested.
- Playback source selection: POST `PlaybackInfo` with a minimal `DeviceProfile`
  claiming broad direct-play support (mkv/mp4, h264/hevc/av1, aac/ac3/opus/flac,
  subrip/ass/pgs) — mplayer will handle nearly anything, so prefer the direct
  stream URL from `MediaSourceInfo`; fall back to transcode URL only if the
  server forces it.

### UI / screens (androidx.tv material3)

Navigation: `navigation-compose` with typed (serializable) routes. Start
destination decided by `SessionStore`: no server → ServerEntry, no token →
Login, else Home.

1. **ServerEntry** — URL text field, validates via `/System/Info/Public`, shows
   server name on success.
2. **Login** — username/password against `/Users/AuthenticateByName`.
   (Quick Connect: out of scope v1, noted as follow-up.)
3. **Home** — vertical list of horizontal rows: *Continue Watching* (Resume
   items), *Next Up*, *Latest* per movie/TV library, *My Media* (user views).
   `MediaCard` = poster + focus scale + progress bar overlay when partially
   watched.
4. **Library** — paged grid (`LazyVerticalGrid`, page size 100, startIndex
   paging), sort menu (name / date added / premiere), filter watched/unwatched.
5. **Detail** — backdrop hero, metadata (year, runtime from `RunTimeTicks`,
   rating, overview), actions: Play / Resume (from
   `UserData.PlaybackPositionTicks`). Series detail shows season selector +
   episode row; episodes show thumb + watched state.
6. **Player** — fullscreen `SurfaceView` in `AndroidView`, OSD overlay
   (title, transport controls, seek bar with position/duration, audio/subtitle
   track dialog) auto-hides after 5s, any D-pad key reveals it. Back hides OSD
   first, then exits. Works fully against `SimulatedPlayerEngine`.
7. **Search** — text field + result grid via `searchTerm` on `/Items`.
8. **Settings** — server/user info, sign out (clears SessionStore).

TV UX rules: everything D-pad reachable; `focusRestorer` on rows/grids so focus
returns to the last card; overscan-safe padding (48dp horizontal / 27dp
vertical) on screen roots; no touch-only affordances.

### Testing & verification

- JVM unit tests: `ImageUrlBuilder`, ticks conversion, DTO deserialization from
  captured JSON fixtures, repository logic with a fake Ktor `MockEngine`,
  playback progress reporter timing (virtual time).
- Definition of done per milestone: `gradlew :app:assembleDebug` and
  `gradlew :app:testDebugUnitTest` green.

## Milestones

- **M0 Foundation**: catalog bumps, new deps, manifest fixes, minSdk, App/DI
  skeleton, theme untouched.
- **M1 Data**: Ktor client, DTOs, SessionStore, Auth/Library/Playback repos + tests.
- **M2 Player shim**: contract, MplayerEngine stub, SimulatedPlayerEngine, JNI sketch.
- **M3 Core UI**: navigation, ServerEntry, Login, Home, components.
- **M4 Browse UI**: Library, Detail (movie + series), Search.
- **M5 Player UI**: player screen + OSD + progress reporting wired to the shim.
- **M6 Polish**: Settings, 401 handling, focus/overscan pass, build + tests green.

M1 and M2 are independent (parallel). M3–M5 depend on M1/M2 signatures but can
be parallelized against agreed interfaces once M0 lands.
