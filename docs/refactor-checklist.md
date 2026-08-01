# Architecture refactor checklist

This file is the durable resume point for the 2026-07-30 architecture refactor.
After any context compaction or interrupted session, read this file before editing.

## Objective

Move Shoumei Player toward a feature-first architecture with this dependency direction:

```text
TV features -> domain contracts <- data/Jellyfin implementations
                         ^
                         |
                  persistence models
```

Preserve the active Google TV UI, D-pad behavior, cache-first startup, and the app-owned official
mpv JNI boundary.

The user explicitly authorized build-tool verification after this checklist was created. Targeted
Gradle compilation and tests may now be used to validate the refactor. Prefer the narrowest useful
task before broader builds.

## Current breakpoint

The browse-domain slice, navigation-independent ViewModel factory, process-owned session
lifecycle, typed settings persistence, the first player decomposition, and the active TV detail
domain boundary are complete.
`PlayerViewModel` now delegates session lifecycle, reporting, resolving, track policy, queue
navigation, and metadata loading. `TelevisionDetailViewModel` now consumes provider-neutral detail
models through `MediaDetailsRepository`. All active non-Live-TV ViewModels are now free of Jellyfin
transport DTOs. The unreachable legacy navigation and `ui/screens/**` presentation tree has also
been removed. The TV settings screen is now split into route wiring, a row catalog, schema models,
and reusable focus/drawer components, with section and row labels backed by Android resources.
Device capability requirements are explicit for refresh-rate switching and audio passthrough.
The package graph is now acyclic: provider-neutral playback resolution, reporting, stream,
track-selection, trickplay, and player-settings contracts no longer point from feature/player
code into `data`. The Android-free domain tree has been extracted into the first enforced Gradle
module, `:core:model`.
The active browse presentation is split into common utilities, Home screen and hero/shelf
components, standard Library, Music library, and Search. The active player presentation is split
into video chrome, transport/timeline, selection drawers, system/post-play overlays, extras,
selection-row formatting, audio layouts, audio controls, and audio context. Detail presentation is
split into its coordinator, hero, choices, series/episodes, rails, and about sections. Stable
module boundaries are enforced, the unreachable legacy presentation island is gone, and final
static plus Gradle verification is complete.

The generated file below existed before this refactor and is not part of the work:

```text
mpvroid/build/intermediates/cxx/Debug/4m1d3r66/logs/arm64-v8a/generate_cxx_metadata_927_timing.txt
```

Do not add or delete it as part of the architecture refactor.

## Completed

- [x] Audited active entry point, package sizes, large files, repository responsibilities, DTO
      leakage, settings representation, cache dependencies, and player orchestration.
- [x] Added Compose-free, serializable browse models in
      `domain/model/BrowseModels.kt`.
- [x] Changed `LibraryCacheStore` to persist domain models instead of
      `ui.television.model` types.
- [x] Retained temporary television type aliases so UI call sites can migrate incrementally.
- [x] Moved Jellyfin `BaseItemDto` to media-item mapping into
      `data/mapping/JellyfinMediaMapper.kt`.
- [x] Added the narrow `domain/repository/MediaCatalog` contract.
- [x] Added `data/repo/JellyfinMediaCatalog` as the Jellyfin-backed implementation.
- [x] Exposed one shared `mediaCatalog` instance from `AppContainer`.
- [x] Migrated the active TV home, library, search, and shell ViewModels to `MediaCatalog`.
- [x] Updated their constructors in `TelevisionNavGraph`.
- [x] Ran `git diff --check`; it reported no whitespace errors at the checkpoint.
- [x] Added stable cache IDs for browse sort/view and bumped library-page cache keys to `v2`.
- [x] Added focused `JellyfinMediaMapperTest` and `JellyfinMediaCatalogTest` coverage.
- [x] Added the one-provider `ImageUrlBuilder` constructor needed by existing and new JVM tests.
- [x] Ran `:app:compileDebugKotlin`; it passed.
- [x] Ran the two focused browse-domain test classes; both passed.
- [x] Extracted `containerViewModel` into `ui/navigation/ContainerViewModel.kt`.
- [x] Added process-owned `SessionManager` and replaced the global `AuthEvents` bus.
- [x] Injected session expiration into `JellyfinClient` from the composition root.
- [x] Migrated active and legacy navigation collectors to `SessionManager.events`.
- [x] Ran `UnauthorizedHandlingTest`; all cases passed.
- [x] Replaced all string-valued `ClientSettings` choices with typed options and stable storage
      IDs while preserving legacy display-label migrations.
- [x] Updated mpv configuration to exhaustively consume typed playback options.
- [x] Centralized reusable numeric/language setting choices in `SettingsChoices`.
- [x] Added legacy-settings migration coverage and ran settings store/ViewModel tests.
- [x] Re-ran `:app:compileDebugKotlin`; it passed.
- [x] Moved player state/models into `feature/player/PlayerModels.kt`, migrated production and test
      imports, and deleted `PlayerCompatibility.kt`.
- [x] Extracted `PlaybackMetadataLoader` for item metadata, cast, similar items, music context,
      lyrics, and episode adjacency.
- [x] Extracted `PlaybackResolver`; `PlayerViewModel` no longer depends directly on
      `PlaybackRepository` or `LibraryRepository`.
- [x] Re-ran focused player compilation and tests after both extractions; they passed.
- [x] Extracted `PlaybackSessionCoordinator` and `PlaybackReporter`, preserving resolve-first stream
      swaps and idempotent final teardown.
- [x] Extracted `TrackController` and `QueueNavigator`.
- [x] Ran the complete `:app:testDebugUnitTest` task: all 315 tests passed after correcting the
      stale `MpvTrackListTest` expectation for intentionally exposed video tracks.
- [x] Removed the centralized television browse-model compatibility type aliases; presentation
      files now import the domain models directly.
- [x] Added provider-neutral detail, person, stream, and playable-target models.
- [x] Added `MediaDetailsRepository` and `JellyfinMediaDetailsRepository`.
- [x] Migrated the active TV detail ViewModel and composables away from `LibraryRepository`,
      `ImageUrlBuilder`, and Jellyfin DTOs.
- [x] Preserved the existing series resolution request count by extracting
      `LibraryRepository.resolveSeriesPlayableTarget`.
- [x] Added detail-mapper coverage and re-ran all app unit tests successfully.
- [x] Removed `BaseItemDto`, `ChapterInfoDto`, and trickplay DTO exposure from the player ViewModel
      and player contracts.
- [x] Moved `JellyfinPlaybackMetadataLoader` into the data layer and retained an eight-item bounded
      raw metadata cache so music, adjacency, and trickplay enrichment do not refetch item details.
- [x] Normalized player chapters before they enter feature state.
- [x] Verified all active non-Live-TV ViewModels are transport-DTO-free and re-ran the complete app
      unit-test suite successfully.
- [x] Confirmed `MainActivity` reaches only `TelevisionNavGraph` and no active television file
      imports the legacy presentation stack.
- [x] Deleted the unreachable legacy `ui/navigation/NavGraph.kt` and complete `ui/screens/**`
      production tree after extracting shared player/domain code.
- [x] Deleted UI-only tests for the unreachable stack and moved `PlayerViewModelTest` into the
      feature package.
- [x] Added focused replacement coverage for active player timeline helpers, then recompiled and
      ran all 196 remaining active/shared unit tests successfully after the legacy removal.
- [x] Extracted the TV settings section/schema models and complete row catalog from the route
      composable.
- [x] Changed the focus-sensitive settings renderer to consume prebuilt rows instead of settings
      storage state and update callbacks.
- [x] Extracted reusable settings rows, toggles, and the choice drawer into their own presentation
      component file without changing D-pad handling.
- [x] Added focused catalog coverage for unique keys, selected choices, updates, and action
      callbacks; focused and complete app unit-test tasks passed.
- [x] Replaced player similar-title state with the provider-neutral `PlayerShelfItem`; the
      Jellyfin playback adapter no longer imports legacy UI card models or mapping functions.
- [x] Removed the Compose-only immutability annotation from persisted `ClientSettings`, leaving
      data/domain packages free of UI and Compose imports.
- [x] Replaced hard-coded TV settings section and row labels with resource IDs while preserving
      the existing visible wording and keeping dynamic values in the catalog.
- [x] Added a domain playback-capability contract and Android display/audio-route detector.
- [x] Declared capability requirements on refresh-rate switching and AC3/E-AC3/DTS passthrough
      rows; unreported HDMI capabilities conservatively retain the controls.
- [x] Added focused platform-resolution and settings-visibility coverage, then re-ran the complete
      app unit-test suite successfully.
- [x] Split the 1,500-line active browse presentation into common navigation/end-state utilities,
      Home, Home hero/shelves, standard Library, Music library, and Search files.
- [x] Preserved the original focus requesters, bring-into-view specs, horizontal wrapping, and
      composable bodies during the browse split; compilation and all app unit tests passed.
- [x] Split the active player controls into transport/timeline, selection drawers, post-play and
      system overlays, extras, selection-row formatting, and video chrome files.
- [x] Split the active audio player into its coordinator, normal/lyrics layouts, controls/tools,
      and lyrics/queue context files.
- [x] Preserved the original player focus traps, D-pad handlers, timing constants, post-play
      behavior, and official mpv surface boundary; compilation and all app unit tests passed.
- [x] Split active detail presentation into the screen coordinator, hero/actions, choice field,
      series/episode sections, media/people rails, and about metadata.
- [x] Preserved the detail hero focus path, whole-hero snapping behavior, rail wrapping, and
      playback option selection; compilation and all app unit tests passed.
- [x] Extracted player composition-root/ViewModel construction into `TelevisionPlayerRoute`;
      `TelevisionPlayerContent` now receives immutable UI state and a narrow controller contract.
- [x] Verified active presentation files no longer access `AppContainer` or `containerViewModel`
      outside route/navigation wiring.
- [x] Moved `ApiResult`/`ApiError` into `domain.result` and persisted `ClientSettings` into
      `domain.settings`, removing the last `domain -> data` edges.
- [x] Moved resolved-playback, reporting, resolution, stream-metadata, track-preference, and
      track-selection contracts into the player boundary.
- [x] Normalized Jellyfin media streams and user configuration in data adapters before they enter
      player policy.
- [x] Removed unused chapter/trickplay DTO fields from `ResolvedPlayback`; metadata loading remains
      the single source for chapters and trickplay.
- [x] Moved trickplay geometry/source models into the player boundary and normalize Jellyfin
      manifests at the repository edge.
- [x] Added the narrow `PlayerSettingsRepository`; `PlayerViewModel` no longer imports
      `SettingsStore`.
- [x] Re-scanned package dependencies: `domain` has no inward project dependencies, `player`
      depends on domain/util, `feature` depends on domain/player/util, and no feature/player file
      imports `data`.
- [x] Extracted all domain models, settings, results, and repository contracts into the real
      `:core:model` Android library module.
- [x] Recompiled `:core:model` and `:app`, fixed two cross-module smart casts, and re-ran the
      complete app unit-test suite successfully.
- [x] Extracted the player contracts, track/trickplay policy, reporting loop, simulated engine, and
      app-owned mpv implementation into `:core:player`.
- [x] Moved player-owned unit tests with their module so internal mpv track parsing remains tested
      without widening its API.
- [x] Kept `:mpvroid` behind `:core:player` and removed the app module's redundant direct native
      bridge dependency.
- [x] Ran `:core:player:testDebugUnitTest`, `:app:compileDebugKotlin`, and
      `:app:testDebugUnitTest` together successfully after extraction.
- [x] Replaced `JellyfinClient`'s concrete `SessionStore` fallback cast with the
      `SessionProvider.invalidate` contract.
- [x] Extracted the Ktor client, session contract, device profile, and Jellyfin transport DTOs into
      `:core:jellyfin`.
- [x] Hid `MpvEngine` behind `PlayerEngineFactory`; the app module now sees only `PlayerEngine` and
      no longer needs mpv observer types on its compile classpath.
- [x] Recompiled `:core:jellyfin`, `:core:player`, and `:app`, then ran both module/app unit-test
      suites successfully.
- [x] Extracted reusable repositories, mappers, persistence stores, artwork/cache ownership,
      discovery, and Android playback-capability detection into `:core:data`.
- [x] Moved 121 data tests beside their implementation and exposed the shared fake Jellyfin server
      plus JSON resources through Gradle test fixtures.
- [x] Reclassified `JellyfinPlaybackMetadataLoader` as a feature adapter instead of leaving a
      feature-dependent implementation in the data package.
- [x] Extracted the complete player feature orchestration and its 11 tests into
      `:feature:player`; app now owns only player route composition and presentation.
- [x] Extracted resource-independent TV colors, dimensions, focus, navigation, media, state,
      background, bring-into-view, and action components into `:core:designsystem-tv`.
- [x] Kept resource-backed theme assembly and brand splash in app so approved binary fonts and
      artwork remain untouched.
- [x] Replaced the deprecated Android source-set `srcDir` call with the AGP 9 `directories` API.
- [x] Deleted the final unreachable legacy `ui/components/**` and `ui/theme/**` island after
      confirming it had no consumers in the active television graph.
- [x] Moved the Jellyfin-specific playback metadata adapter into the app composition layer and
      removed `:core:data` from `:feature:player` production dependencies.
- [x] Preserved the player adapter's 24-person cast limit while moving its integration coverage
      into the app test module.
- [x] Ran the final 151-task Gradle verification successfully: `:core:data:testDebugUnitTest`,
      `:core:player:testDebugUnitTest`, `:feature:player:testDebugUnitTest`,
      `:core:designsystem-tv:compileDebugKotlin`, `:app:compileDebugKotlin`, and
      `:app:testDebugUnitTest`.
- [x] Verified 203 JVM tests with zero failures, errors, or skips: 121 core-data, 63 core-player,
      2 feature-player, and 17 app tests.

## Immediate continuation

- [x] Review every new/changed browse-domain file for unresolved references and type mismatches.
- [x] Verify `JellyfinMediaCatalog` maps paging counts, favorites, sort values, date filters, and
      collection types without changing behavior.
- [x] Verify cache keys remain stable or intentionally version/bump them.
- [x] Search active browse/search/shell code for remaining `BaseItemDto`, `QueryResult`,
      `LibraryRepository`, or Jellyfin query-string dependencies.
- [x] Add focused JVM tests for `JellyfinMediaMapper` and `JellyfinMediaCatalog` using existing
      Ktor/Jellyfin fixtures. Write tests, but do not run Gradle without authorization.

## Phase 2: session and dependency composition

- [x] Extract `containerViewModel` from the obsolete `ui/navigation/NavGraph.kt` into a small
      active composition/factory file.
- [x] Introduce narrow feature dependency interfaces instead of allowing screens to reach all of
      `AppContainer`.
- [x] Replace the global `AuthEvents` singleton with a process-owned `SessionManager` event/state
      contract.
- [x] Keep `AppContainer` as the manual composition root; do not introduce a DI framework merely
      for this refactor.

## Phase 3: repository boundaries

- [x] Split the consumer contracts currently concentrated in `LibraryRepository`:
  - [x] browse/catalog
  - [x] media details
  - [x] search
  - [x] music
  - [x] people/credits
  - [x] user-data mutations
  - [x] playable-media resolution
- [x] Keep a shared Jellyfin client/data source underneath these implementations.
- [x] Ensure feature ViewModels consume domain contracts and models, not Jellyfin DTOs.
- [x] Leave Live TV behavior unchanged and isolate its contract when that feature is revisited.

## Phase 4: typed and customizable settings

- [x] Replace persisted display strings with enums or stable IDs.
- [x] Add backward migrations for existing string values.
- [x] Define reusable settings descriptors/catalog entries so choice lists are not duplicated in
      `TelevisionSettingsScreen`.
- [x] Separate settings storage, settings schema, and TV rendering.
- [x] Make device/capability-dependent visibility explicit.
- [x] Keep user-facing labels resource-backed rather than using persistence IDs as display text.

## Phase 5: player decomposition

- [x] Move shared player state/models out of the legacy `ui/screens/player` namespace.
- [x] Migrate all production and test imports to `feature/player`, then delete the temporary
      `ui/screens/player/PlayerCompatibility.kt` aliases and wrappers.
- [x] Split `PlayerViewModel` responsibilities into:
  - [x] `PlaybackSessionCoordinator`
  - [x] `PlaybackResolver`
  - [x] `PlaybackReporter`
  - [x] `TrackController`
  - [x] `QueueNavigator`
  - [x] `PlaybackMetadataLoader`
- [x] Keep `PlayerViewModel` responsible for UI intents and combined presentation state.
- [x] Preserve `PlayerEngine` and the official mpv JNI implementation.
- [x] Do not introduce a generic `BaseViewModel`.

## Phase 6: active UI decomposition

- [x] Split route/ViewModel wiring from stateless screen composables.
- [x] Break down `BrowseScreens.kt` by home hero, shelves, library, music, and search.
- [x] Break down player UI by video controls, audio layout, drawers, modals, and post-play.
- [x] Break down settings into schema/model rendering and reusable row/drawer components.
- [x] Break down detail UI into feature-specific sections.
- [x] Preserve visuals, focus requesters, rail wraparound, and D-pad behavior during moves.

## Phase 7: obsolete UI and module enforcement

- [x] Remove the temporary `ui.television.model` compatibility aliases once every call site uses
      the domain browse models directly.
- [x] Confirm which old `ui/screens`, `ui/components`, `ui/theme`, routes, and tests are unreachable
      from `MainActivity`.
- [x] Move genuinely shared code into active feature/core packages.
- [x] Delete the obsolete navigation/presentation stack only after its shared code is extracted.
- [x] Re-scan references after deletion; do not delete player state still used by TV.
- [x] Once package dependencies are acyclic, extract stable Gradle modules:
  - [x] `:core:model`
  - [x] `:core:jellyfin`
  - [x] `:core:data`
  - [x] `:core:player`
  - [x] `:core:designsystem-tv`
  - [x] active `:feature:*` modules where the boundary provides real value
- [x] Keep `:app` as composition root and `:mpvroid` as the native bridge.

## Static verification before handoff

- [x] `git diff --check` passed.
- [x] Data/domain packages have no imports from `ui` or Compose; lower core modules have no
      outward feature, UI, or DI imports.
- [x] `:feature:player` has no data/Jellyfin imports. The only active presentation DTO imports are
      `TelevisionModels.kt` and `TelevisionLiveViewModel.kt`, retained intentionally because Live
      TV is outside this refactor.
- [x] The active graph reaches only `TelevisionNavGraph`; no obsolete `ui.navigation.NavGraph` or
      `ui.screens` route references remain.
- [x] Reviewed `git status`, restored the two tracked Gradle-generated files, and left the
      pre-existing untracked mpv timing file untouched.
- [x] Exact final Gradle result: build successful, 151 actionable tasks, with 203 tests and zero
      failures, errors, or skips.
