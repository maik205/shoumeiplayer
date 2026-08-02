# Navigation and focus fault register

This document tracks the open screen-stack, D-pad navigation, and focus-transfer faults found in the current television UI source audit.

Live TV is intentionally excluded from this register. The checked-in source remains authoritative, and every item below is unverified on a device unless it is closed with explicit runtime evidence.

Each section is tracked as a GitHub issue: #107 global, #108 onboarding, #109 Home, #110 libraries, #111 Search, #112 Settings, #113 Detail, #114 player, #115 coverage.

## Status

- `[ ]` — open
- `[~]` — addressed in the checked-in source, but not yet closed: the closure requirements below
  are not met, because there is no Compose UI test and nothing here has been run on a device
- `[x]` — closed with the evidence listed under Closure requirements

## Severity

- **P0** — wrong destructive navigation, an unreachable primary action, competing modal ownership, or another failure that can block normal remote use
- **P1** — focus can be lost, stolen, stranded, or restored to the wrong control during an ordinary flow
- **P2** — navigation depends on geometry, state is not preserved, behavior is inconsistent, or automated coverage is missing

## Global navigation and focus

- [~] **NAV-001 · P0 — Profile Back destroys the authenticated stack.** Home, Search, Library, and Settings push `ProfilesRoute`, but Profiles Back clears the stack and navigates to Connect instead of returning to its opener. Evidence: `TelevisionNavGraph.kt`, `ProfilesRoute`.
- [~] **NAV-002 · P1 — Top-navigation switches discard destination state.** `navigateTop()` pops to Home without preserving Search query/results, Library filters and scroll, or the selected Settings section. Evidence: `TelevisionNavGraph.kt`, `navigateTop()`.
- [~] **NAV-003 · P1 — Child-return focus is restored to the navbar rather than the opener.** `LifecycleResumeEffect` cannot distinguish a newly selected tab from returning from Detail or Player. Evidence: `TelevisionNavigation.kt`, `TelevisionTopNavigation()`.
- [~] **NAV-004 · P1 — Key repeat can queue competing navbar focus moves.** Every Left/Right KeyDown launches an uncancelled scroll-and-focus coroutine. Evidence: `TelevisionNavigation.kt`, `televisionNavigationRing()`.
- [~] **NAV-005 · P1 — Key repeat can queue competing rail wrap moves.** Horizontal rail wrapping also launches an uncancelled coroutine for each edge KeyDown. Evidence: `TelevisionFocus.kt`, `televisionHorizontalWrap()`.
- [~] **NAV-006 · P1 — Async operations commonly remove the focused node without assigning a successor.** `TelevisionFocusSurface` removes disabled controls from focus, while callers generally do not transfer focus before loading, retry, or refresh state changes. Evidence: `TelevisionFocus.kt`, `TelevisionActions.kt`, and the screen-specific items below.
- [~] **NAV-007 · P2 — Player cross-navigation leaves an unrelated Detail underneath.** Detail A to Player to Extras target B pops Player and pushes B, so Back from B returns to A. Evidence: `TelevisionNavGraph.kt`, `PlayerRoute`.
- [~] **NAV-008 · P2 — Duplicate route contracts coexist.** `ui/television/navigation/TelevisionRoutes.kt` is active while `ui/navigation/Routes.kt` defines an incompatible, unused route model.
- [~] **NAV-009 · P2 — Splash input is silently discarded.** The root preview handler consumes Back, Center, and all directions while the splash is visible. Evidence: `MainActivity.kt`.
- [~] **NAV-010 · P2 — Route pushes are not consistently single-flight.** Hero playback uses `rememberPlaybackLaunchState`, but rail, episode, track, related-item, and media-key navigation can initiate repeated route or playback changes.

## Connect, Profiles, Login, and Recovery

- [~] **ONB-001 · P1 — Connect initial focus is one-shot across discovery recovery.** `initialFocusAssigned` remains true after an error or retry removes the original target, so later server/address content is not focused. Evidence: `OnboardingScreens.kt`, `ConnectScreen()`.
- [ ] **ONB-002 · P1 — Connecting removes the focused action.** Server, Connect, and insecure-HTTP actions become disabled while connecting, with no explicit successor or failure restoration.
- [ ] **ONB-003 · P1 — Connection failure has no deterministic focus target.** Retry renders with `requestInitialFocus = false` after the initiating control may already have lost focus.
- [~] **ONB-004 · P1 — The insecure-HTTP confirmation is not modal.** Discovery, address, refresh, and connect controls remain focusable behind it.
- [~] **ONB-005 · P1 — Back does not dismiss the insecure-HTTP confirmation.** Its only cancellation path is the inline Cancel button.
- [~] **ONB-006 · P1 — Closing the insecure-HTTP confirmation does not restore its opener.** Neither Accept failure nor Cancel returns focus to the server/address action that opened it.
- [ ] **ONB-007 · P2 — The server address field has no explicit D-pad edge to Connect.** Left/Right remain text editing and no Up/Down requesters are supplied.
- [ ] **ONB-008 · P2 — Connect’s Refresh, server list, address field, and Connect action have no complete explicit directional graph.** Traversal depends on spatial geometry.
- [~] **ONB-009 · P1 — Passwordless profile authentication removes the focused profile.** The rail is disabled while authenticating and failure does not restore the selected profile.
- [ ] **ONB-010 · P1 — Profile failure Retry has no deterministic entry focus.** Inline errors use `requestInitialFocus = false` after the profile target may have been disabled.
- [ ] **ONB-011 · P2 — Profiles has no explicit vertical graph.** Server Back, the profile rail, and Use Another Account depend on spatial navigation.
- [~] **ONB-012 · P1 — Password IME submission clears focus before login completes.** Validation or authentication failure leaves no selected control. Evidence: `OnboardingScreens.kt`, `LoginScreen()`.
- [~] **ONB-013 · P1 — Sign In removes itself from focus while pending.** It is disabled during authentication and is not explicitly refocused on failure.
- [~] **ONB-014 · P1 — Quick Connect removes itself from focus while pending.** Loading, result, and error changes have no focus-return policy.
- [ ] **ONB-015 · P2 — Login’s action row uses a surprising horizontal ring.** Both Left and Right move between Quick Connect and Sign In rather than stopping at the visual edge.
- [~] **ONB-016 · P0 — Recovery focuses non-action result text.** Result/error text has no visible focus affordance and no directional contract, making the remote appear stranded. Evidence: `OnboardingScreens.kt`, `RecoveryScreen()`.
- [~] **ONB-017 · P1 — Recovery removes the focused request action while pending.** The eventual result focus moves to text rather than a usable next action.
- [~] **ONB-018 · P2 — Recovery has no explicit field-to-action-to-Back graph.** Movement outside the text field is geometry-dependent.
- [~] **ONB-019 · P2 — Session-expired and account-locked screens have no explicit Back policy.** Because the previous stack is cleared, hardware Back exits the application instead of selecting an authentication path.

## Home

- [~] **HOME-001 · P1 — Empty Home and the selected navbar race for initial focus.** Both can request focus during route entry. Evidence: `TelevisionHomeScreen.kt`.
- [~] **HOME-002 · P1 — Partial-error Retry is outside the explicit hero-to-first-rail chain.** Down from Hero bypasses Retry and Up from the first rail returns to Hero.
- [ ] **HOME-003 · P2 — Navbar Down has no attached target during initial Home loading.** Hero and fallback requesters are not yet usable.
- [ ] **HOME-004 · P2 — Only the first Home rail has explicit vertical relationships.** Deeper rail-to-rail movement depends on geometry.
- [~] **HOME-005 · P1 — Home cards are not restored by media ID after returning from Detail or Player.** Navbar resume restoration wins instead.
- [~] **HOME-006 · P1 — Refresh can remove or reorder the focused card without a focus repair path.** Rail requesters are recreated from the changed item list.

## Standard and music libraries

- [~] **LIB-001 · P1 — Empty/error Retry or Clear Filter disappears when its action starts.** Successful loading does not focus a filter or the first result. Evidence: `TelevisionLibraryScreen.kt`.
- [~] **LIB-002 · P1 — Load-more Retry disappears while pagination retries.** No successor is assigned.
- [~] **LIB-003 · P1 — Library cards have no stable per-media focus restoration.** Refresh, sorting, filtering, pagination, and Detail return cannot restore by media ID.
- [~] **LIB-004 · P1 — A focused card can be removed by sorting/filtering without recovery.** The grid has no nearest-item fallback.
- [~] **LIB-005 · P2 — Filter/sort-to-grid movement is spatial.** There is no explicit first-row target or first-row Up mapping.
- [~] **LIB-006 · P1 — Music loading/error/empty states leave the navbar content requester unattached.** Navbar Down targets `entryFocus`, which is created only in the content branch. Evidence: `TelevisionMusicLibraryContent.kt`.
- [~] **LIB-007 · P1 — Music Retry disappears without transferring focus.** Successful loading attaches filters but never requests one after the removed Retry target.
- [~] **LIB-008 · P1 — Music filter activation scrolls its focused control offscreen without transferring focus.** Focus restoration and programmatic scrolling can fight each other.
- [~] **LIB-009 · P2 — Music shelf vertical relationships are incomplete.** Only the first shelf has a defined Up target; later shelves depend on geometry.
- [~] **LIB-010 · P1 — Music shelf/card focus is not restored after Detail or audio Player navigation.** Route resume returns to the navbar.

## Search

- [~] **SEARCH-001 · P1 — The visible Back button is not connected to the navbar or text field.** With no results, arrow navigation cannot reliably reach it. Evidence: `TelevisionSearchScreen.kt`.
- [~] **SEARCH-002 · P1 — Search errors steal focus from the query.** `TelevisionErrorState` defaults to requesting Retry focus.
- [~] **SEARCH-003 · P1 — Search Retry disappears while the request runs.** Results or a later empty state do not receive focus.
- [~] **SEARCH-004 · P1 — Results have no stable focus identity.** Query updates can remove or reorder the focused result without a nearest-item fallback.
- [~] **SEARCH-005 · P1 — Returning from Detail does not restore the originating result.** Focus returns to the Search navbar item.
- [~] **SEARCH-006 · P2 — IME Search blindly moves focus Down.** Loading, short-query, error, and empty-result states provide no result target.

## Settings

- [~] **SET-001 · P1 — Settings Retry controls disappear during updates without focus transfer.** This affects settings-update and library-refresh errors. Evidence: `TelevisionSettingsScreen.kt`.
- [~] **SET-002 · P1 — Left/Right is consumed on every interactive row to change sections.** Choice rows display a right arrow, but Right changes section instead of opening the choice drawer. Evidence: `TelevisionSettingsComponents.kt`, `SettingsInteractiveRow()`.
- [~] **SET-003 · P2 — Boundary Left/Right is consumed while doing nothing.** The first and last Settings sections have no target but still swallow the key.
- [~] **SET-004 · P2 — Settings section and row position are lost after top-navigation changes.** The route is recreated above Home.
- [~] **SET-005 · P2 — Error banners sit between section tabs and content without explicit directional relationships.** Access depends on spatial geometry.

## Detail and person screens

- [~] **DETAIL-001 · P1 — Detail always restores Play or Back on resume.** Returning from Player, Person, a nested Detail, or another child loses the originating episode, track, person, or related card. Evidence: `TelevisionDetailScreen.kt`.
- [~] **DETAIL-002 · P0 — Hero focus scrolls to list index zero rather than the Hero item.** When load/action errors precede Hero, the focused hero control can be scrolled offscreen.
- [~] **DETAIL-003 · P1 — Load and action Retry controls disappear without returning focus.** Their removal can strand focus during successful retry.
- [~] **DETAIL-004 · P1 — Section Retry controls are dynamically inserted and removed without a focus policy.** This includes playable, season, episode, related, cast, and other section errors.
- [ ] **DETAIL-005 · P2 — Hero-to-first-section navigation is incomplete.** Series sections have internal routing, but there is no universal Hero-to-Next-Up/rail and rail-to-Hero contract.
- [ ] **DETAIL-006 · P2 — Next Up and Season Up routing is conditional and incomplete when neighboring sections are absent.** Spatial fallback decides the final destination.
- [~] **DETAIL-007 · P1 — Episode, track, people, and related rails have no stable return focus.** Focus cannot be restored by item ID after nested navigation or data refresh.
- [~] **DETAIL-008 · P2 — Nested Detail and Person routes can grow the stack without deduplication.** Reopening an item does not use `launchSingleTop` or an item-aware replacement policy.
- [~] **DETAIL-009 · P2 — Episode, track, and related playback actions are not consistently single-flight.** Hero Play is guarded, but lower-section actions are not.

## Video and audio player

- [~] **PLAYER-001 · P0 — Hardware Back bypasses the on-screen exit confirmation.** The visible control requires two selections, while Back immediately stops playback and pops Player. Evidence: `TelevisionPlayerScreen.kt`.
- [~] **PLAYER-002 · P0 — Player layers are not represented by one exclusive modal stack.** Selection panels, Extras, While Watching, Queue, Lyrics, Error, Post Play, and Still Watching can coexist.
- [~] **PLAYER-003 · P1 — Back prioritizes playback state over the topmost visible panel.** Error, Ended, and Still Watching exit Player before closing an open drawer or browser.
- [~] **PLAYER-004 · P1 — Continue from Still Watching removes the focused overlay without assigning a successor.** Neither root nor transport focus is requested.
- [ ] **PLAYER-005 · P1 — Player state changes can steal focus.** Changes to audio mode, loading, error, or title reveal the OSD and request Play/Pause regardless of the current control.
- [ ] **PLAYER-006 · P1 — Play/Pause becomes non-focusable while loading or buffering.** There is no adjacent successor or explicit restoration after it re-enables.
- [ ] **PLAYER-007 · P1 — Closing a top-level panel always focuses the timeline.** Focus does not return to the toolbar control that opened the panel.
- [ ] **PLAYER-008 · P1 — Returning from a nested panel loses the opener row.** The parent drawer is recreated and focuses its first row.
- [~] **PLAYER-009 · P1 — Selection drawers focus the first option rather than the selected option.** This affects track, language, subtitle, quality, speed, and display choices. Evidence: `PlayerSelectionDrawers.kt`.
- [~] **PLAYER-010 · P1 — Playback Information rows are focusable no-op buttons.** Center produces no action or feedback.
- [~] **PLAYER-011 · P2 — Menu is consumed in audio mode without doing anything.** Evidence: `TelevisionPlayerScreen.kt`, root key handler.
- [~] **PLAYER-012 · P0 — Compact While Watching can make Retry unreachable.** Toolbar Down always targets the first similar-item requester, which is unattached during empty/loading/error states. Evidence: `TelevisionVideoPlayerChrome.kt`, `PlayerWhileWatchingRail()`.
- [ ] **PLAYER-013 · P1 — Full Extras Retry disappears while shelves load.** Focus is not transferred to Back or to newly loaded content.
- [~] **PLAYER-014 · P1 — OSD auto-hide continues while the While Watching shelf is open.** This contradicts `docs/player-controls.md`; an idle reader can lose the entire focused shelf.
- [~] **PLAYER-015 · P0 — Post-play autoplay continues while the episode browser is open.** Playback can switch while the user is choosing another episode. Evidence: `PlayerPostPlayOverlays.kt`, `PostPlayOverlay()` and the parent countdown effect.
- [~] **PLAYER-016 · P1 — Hardware Back in the post-play episode browser exits Player.** The visible Back button only closes the browser.
- [ ] **PLAYER-017 · P1 — Audio queue changes refocus the first item.** Playing an item, refreshing suggestions, or changing cover/list mode steals focus from the current target. Evidence: `TelevisionAudioPlayer.kt`.
- [ ] **PLAYER-018 · P1 — Opening an empty or failed audio queue assigns no modal entry focus.** Focus can remain on the dimmed player underneath.
- [~] **PLAYER-019 · P1 — Lyrics uses a focusable container or plain text with no visible focus affordance.** Evidence: `AudioPlayerContext.kt`, `LyricsPane()`.
- [~] **PLAYER-020 · P1 — Lyrics consumes Up/Down at both list boundaries.** Back is the only exit from the focus target.
- [~] **PLAYER-021 · P1 — Synced lyric auto-scroll fights manual D-pad scrolling.** Every active-line change can override the user’s position.
- [ ] **PLAYER-022 · P2 — Audio transport, tools, context columns, and Exit lack a complete explicit directional graph.** Traversal depends on geometry.
- [ ] **PLAYER-023 · P2 — One shared audio-context error renders two Retry controls.** Each empty column creates an independent action for the same failure.
- [ ] **PLAYER-024 · P2 — Player panel, queue, lyrics, and overlay state is not saveable.** Activity recreation can restore playback with unrelated visible-layer and focus state.
- [ ] **PLAYER-025 · P2 — Previous/Next hardware and rail actions are not single-flight.** Repeated input can start overlapping item switches.

## Verification and regression coverage

- [ ] **TEST-001 · P0 — There are no app-level Compose navigation/focus tests.** `app` declares Compose UI test dependencies but has no `app/src/androidTest` coverage for television navigation.
- [ ] **TEST-002 · P1 — Back-stack contracts are untested.** Profiles, authentication replacement, top-navigation switching, nested Detail, Player exit, and player child navigation have no automated assertions.
- [ ] **TEST-003 · P1 — D-pad edge traversal is untested.** No test covers first/last navbar items, rails, grids, text fields, Settings rows, drawers, or player controls.
- [ ] **TEST-004 · P1 — Async focus transfer is untested.** Loading, retry, refresh, disabled actions, empty-to-content, and error-to-content transitions have no focus assertions.
- [ ] **TEST-005 · P1 — Modal exclusivity and Back order are untested.** Player drawers, Extras, Queue, Lyrics, Post Play, Still Watching, and playback errors have no layered-focus regression suite.

## Closure requirements

An item should be checked only when all applicable evidence exists:

1. The source has one explicit focus owner and a deterministic directional/Back contract.
2. Async state changes transfer focus before removing or disabling the current target.
3. Returning from a child restores the exact opener when it still exists, with a defined fallback when it does not.
4. A Compose UI test exercises the relevant remote-key sequence and asserts the focused semantic node and route.
5. The flow is verified on a TV emulator or device, including held-key repeat where applicable.
