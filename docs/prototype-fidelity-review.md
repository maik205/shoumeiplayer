# Prototype fidelity review

This checklist tracks visual and interaction fidelity against `prototype/`.
Update it as each state is reviewed with the user.

## Cleared

- [x] Home compact browsing state
  - Confirmed from the running TV app on 2026-07-29.
- [x] Library grid/list/sort states
  - Confirmed by the user on 2026-07-29.
- [x] Subtitles drawer
  - Ruled out by the user on 2026-07-29.
- [x] Audio drawer
  - Ruled out by the user on 2026-07-29.
- [x] Video OSD hidden
  - Confirmed by the user on 2026-07-29.
- [x] Chapters drawer
  - Same as the previously reviewed drawer; confirmed by the user on 2026-07-29.

## Needs fidelity work

- [ ] Search keyboard and result states
  - Replaced the app-owned keyboard with a focused, contained query field that invokes the TV IME.
  - Added the labeled Home control and All, Films, Series, Live, Audio, and Photos filters.
  - Added the prototype results heading/count and retained the consistent five-column grid.
  - Loading, loaded, short-query, error, and empty states are now mutually exclusive.
  - Awaiting review in the running app.
- [ ] Series season and episode browser
  - Corrected the Next Up/episode rail spacing and the oversized episode cards to the prototype scale.
  - Season selection, current-episode entry focus, metadata, progress, and playback remain wired.
  - Awaiting review in the running app.
- [ ] In-flight mini seeker
  - Implemented a compact bottom-up gradient and coordinated fade/slide entrance and exit.
  - Awaiting review in the running app.
- [ ] Playback speed
  - Prototype option ladder is wired; awaiting review.
  - Nested OSD Back navigation now returns to the parent options drawer.
- [ ] Frame/aspect ratio
  - Fit, Fill, Original, 16:9, and 4:3 now apply directly to mpv; awaiting review.
- [ ] HDR handling
  - Auto, passthrough, tone-map, and SDR conversion routes now apply to mpv; awaiting review.
- [ ] Video-track selection
  - mpv video tracks are now surfaced and selectable; awaiting review.
- [ ] Audio delay
  - Dedicated prototype-style submenu is wired to mpv; awaiting review.
- [ ] Subtitle delay
  - Dedicated prototype-style submenu is wired to mpv; awaiting review.
- [ ] Deinterlacing
  - Auto, On, and Off now apply directly to mpv; awaiting review.
- [ ] Sleep timer
  - Timed and end-of-episode actions are functional; awaiting review.
- [ ] Playback information
  - Prototype information drawer is implemented; runtime metadata fidelity still needs review.
- [ ] Post-play episode browser
  - Full-bleed post-play state and remaining-episode browser are implemented; awaiting review.
- [ ] Generic loading, empty, and unavailable overlays
  - Loading now uses the prototype five-card skeleton state instead of a three-dot marker.
  - Empty/unavailable states now use the prototype’s large message hierarchy and reveal action.
  - Awaiting review.
- [ ] Settings: Playback options and submenus
  - Row inventory and working choice submenus match the prototype; awaiting visual review.
- [ ] Settings: Video options and submenus
  - Row inventory and working choice submenus match the prototype; awaiting visual review.
- [ ] Settings: Audio options and submenus
  - Row inventory and working choice submenus match the prototype; awaiting visual review.
- [ ] Settings: Subtitle options and submenus
  - Row inventory and working choice submenus match the prototype; awaiting visual review.

## Pending review

- [ ] Music browsing/compact hero state
- [ ] Album track-actions panel
- [ ] Audio player lyrics, queue, and suggestions
- [ ] Video OSD visible

## In scope, pending review

- [ ] Settings: Interface
  - Implemented with the prototype row inventory and working submenus; awaiting review.
- [ ] Settings: Network
  - Implemented with the prototype row inventory and working submenus; awaiting review.
- [ ] Settings: Screensaver
  - Implemented with the prototype row inventory and working submenus; awaiting review.
- [ ] Settings: Server
  - Implemented with live server values/actions; awaiting review.
- [ ] Settings: Account
  - Implemented with live profile/session values/actions; awaiting review.

## Out of scope

- Live TV guide-focused state
