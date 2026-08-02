# Architecture

Shoumei Player is split into eight Gradle modules. The application module owns Android integration and screen composition. Core modules contain reusable data, playback, and UI contracts.

## Module boundaries

| Module | Responsibility |
| --- | --- |
| `:app` | Application setup, dependency composition, navigation, screens, and Android services |
| `:feature:player` | Player state, session coordination, queues, and playback metadata |
| `:core:data` | Repositories, session and settings persistence, image URLs, and caches |
| `:core:jellyfin` | Ktor client and Jellyfin request and response models |
| `:core:player` | Playback contracts, mpv integration, Media3 fallback, and playback utilities |
| `:core:model` | Shared domain models and result types |
| `:core:designsystem-tv` | Shared Compose components, dimensions, colors, and focus behavior |
| `:mpvroid` | Java Native Interface (JNI) bridge and native mpv libraries |

The module dependencies follow this direction:

```text
:app
├── :feature:player
├── :core:data
├── :core:player
├── :core:jellyfin
├── :core:model
└── :core:designsystem-tv

:feature:player -> :core:player -> :mpvroid
:core:data -> :core:jellyfin -> :core:model
:core:data -> :core:player
```

## Dependency composition

`AppContainer` constructs process-level services without a dependency-injection framework. It owns the session stores, repositories, network monitor, playback engine chain, and application coroutine scope.

Most services use lazy initialization. `SessionStore` updates cached connection values for synchronous image URL generation and clears audio-resumption data when the active session ends.

## Data and sessions

`JellyfinClient` uses Ktor with the OkHttp engine and kotlinx serialization. Each request resolves the active server and authentication token from `SessionStore`.

The data layer follows these boundaries:

- `AuthRepository` handles password authentication, Quick Connect, logout, and user configuration
- `LibraryRepository` handles browsing, search, details, related items, and user-state mutations
- `PlaybackRepository` resolves media sources, opens live streams, reports progress, and closes transcodes
- `SessionStore` and `SettingsStore` persist credentials and preferences with DataStore
- `LibraryCacheStore` and the shared Coil loader cache library data and artwork

An authenticated `401` response expires the active session through `SessionManager`. Authentication failures before a token exists remain on the login flow.

## Playback

`PlayerEngine` defines playback state, timeline, track selection, seeking, speed, surface attachment, and lifecycle operations. `PlayerEngineFactory` creates a `SwitchingPlayerEngine` with mpv as the default backend and Media3 as the system fallback.

The app decorates the selected backend with Android-specific behavior:

```text
Audio focus
└── Caption preferences
    └── Audio route
        └── HDR policy
            └── Frame-rate matching
                └── Selected playback backend
```

`MpvEngine` communicates with official mpv through the app-owned JNI bridge in `:mpvroid`. `PlaybackProgressReporter` sends start, progress, and stop events to Jellyfin independently of the selected backend.

## User interface

The television interface uses Jetpack Compose, AndroidX TV Material, typed serializable routes, and `StateFlow`-based ViewModels. Navigation is centralized in `TelevisionNavGraph.kt`.

The active routes cover connection, profile selection, authentication, home, library, search, details, people, Live TV, settings, and playback. Shared loading, empty, error, media, navigation, and focus components live in `:core:designsystem-tv`.

## Verification

Run unit tests and lint from the repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

Build the debug application with:

```powershell
.\gradlew.bat :app:assembleDebug
```
