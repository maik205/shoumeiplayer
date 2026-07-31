# 001 — Jellyfin API surface gaps

**Status:** Superseded by [`../jellyfin-api-coverage.md`](../jellyfin-api-coverage.md). Retained as a historical gap list.
**Verified against:** `jellyfin-openapi.json` — every path and parameter name here was read out of
the spec's `paths` map, not recalled.

## Endpoints the client currently calls (17)

`/System/Info/Public`, `/Users/AuthenticateByName`, `/Users/Me`, `/UserViews`, `/UserItems/Resume`,
`/Shows/NextUp`, `/Shows/{id}/Seasons`, `/Shows/{id}/Episodes`, `/Items`, `/Items/Latest`,
`/Items/{id}/PlaybackInfo`, `/Videos/{id}/stream`,
`/Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/…/Stream.{format}` (new — external subtitles),
`/Sessions/Playing`, `/Sessions/Playing/Progress`, `/Sessions/Playing/Stopped`, `/Images/…`.

## Gaps, ranked by user-visible damage

| # | Gap | Endpoint | Consequence |
|---|---|---|---|
| 1 | ~~No transcode teardown~~ | `DELETE /Videos/ActiveEncodings?deviceId&playSessionId` | **Fixed** — see [003](003-mpv-core-gaps.md). Was: every transcoded exit leaked a live ffmpeg process server-side until it timed out. |
| 2 | ~~No session keep-alive~~ | `POST /Sessions/Playing/Ping?playSessionId` | **Fixed** — see [003](003-mpv-core-gaps.md). Was: the server reaped "now playing" between our 10s progress posts while paused. |
| 3 | ~~External subtitles never loaded~~ | `GET /Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream.{format}` | **Fixed** — see [003](003-mpv-core-gaps.md). Was: `DeliveryUrl`/`DeliveryMethod` parsed and dropped while our own device profile advertises `method="External"` for srt/vtt, so those tracks were offered and did nothing. |
| 4 | No skip intro/credits | `GET /MediaSegments/{itemId}` — `MediaSegmentDto{Id,ItemId,Type,StartTicks,EndTicks}`, `MediaSegmentType` = `Unknown\|Commercial\|Preview\|Recap\|Outro\|Intro` | Table-stakes TV feature, entirely absent. |
| 5 | Track selections never written back | `POST /Users/Configuration?userId` (body `UserConfiguration`) | `RememberAudioSelections` / `RememberSubtitleSelections` are now read and cached but neither is honoured — manual track picks die with the session. |
| 6 | Trickplay parsed but unusable | `GET /Videos/{itemId}/Trickplay/{width}/{index}.jpg?mediaSourceId` | `TrickplayInfoDto` flows into `ResolvedPlayback.trickplay`, but `ImageUrlBuilder` has no tile method and no UI consumes it — dead data end to end. |
| 7 | No played / favourite writes | `POST\|DELETE /UserPlayedItems/{itemId}`, `POST\|DELETE /UserFavoriteItems/{itemId}` | Strictly read-only client. |
| 8 | Logout is local-only | `POST /Sessions/Logout` | `AuthRepository.logout()` clears the DataStore; the server-side access token stays valid indefinitely. |
| 9 | No capabilities announcement | `POST /Sessions/Capabilities/Full` (body `ClientCapabilitiesDto`) | Client is invisible to "Play on…" / remote control from the web app. |
| 10 | Live stream lifecycle unhandled | `POST /LiveStreams/Open`, `POST /LiveStreams/Close`; `liveStreamId` + `AutoOpenLiveStream` on `PlaybackInfoDto` | Live TV and some transcode paths will not start at all. |
| 11 | Search goes through `/Items?searchTerm` | `GET /Search/Hints` | No people/genre/studio hits and no server-ranked relevance. |
| 12 | No Quick Connect | `POST /QuickConnect/Initiate`, `POST /Users/AuthenticateWithQuickConnect` | On-TV login means typing a password on a D-pad. |
| 13 | No intros/prerolls | `GET /Items/{itemId}/Intros` | Cinema-intro plugins ignored. |

## Notes

- #1 and #2 are done — they were the only two causing server-side damage rather than merely
  missing a feature. Of what remains, #4 (media segments / skip intro) is the biggest user-facing
  gap and #10 (live stream lifecycle) the biggest correctness one.
- #5 is now half-done: the read side landed with the user-configuration work, so only the write
  remains.
