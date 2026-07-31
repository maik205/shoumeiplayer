# Jellyfin API Surface — Android TV Player Client

For current implementation estimates and remaining client gaps, see [`jellyfin-api-coverage.md`](jellyfin-api-coverage.md).

Extracted from `jellyfin-openapi.json` (repo root). Only endpoints a TV playback
client needs are covered. Every path below was verified to exist in the spec
(`paths` map) — see the note at the end for anything that didn't match the
assumed/guessed path.

**Ticks = 100-nanosecond units.** `ticks / 10_000_000` = seconds.

All request paths are relative to the server base URL, e.g. `http://host:8096`.

---

## 0. BaseItemDto — key fields (referenced throughout)

`BaseItemDto` is the universal item DTO returned by browsing/detail endpoints.
Only the fields a TV UI typically needs are listed; the schema has ~150 fields
total.

| Field | Type | Notes |
|---|---|---|
| `Id` | string (guid) | item id |
| `ServerId` | string | |
| `Name` | string | |
| `Type` | `BaseItemKind` enum | `Movie`, `Series`, `Season`, `Episode`, `MusicAlbum`, `Audio`, `Folder`, `CollectionFolder`, `UserView`, etc. |
| `Overview` | string | plot summary |
| `RunTimeTicks` | integer (int64) | ticks |
| `ProductionYear` | integer | |
| `PremiereDate` | string (date-time) | |
| `CommunityRating` | number | |
| `OfficialRating` | string | e.g. "TV-14" |
| `IndexNumber` | integer | episode number |
| `ParentIndexNumber` | integer | season number |
| `SeriesName` / `SeriesId` | string | for episodes |
| `SeasonId` / `SeasonName` | string | for episodes |
| `ParentId` | string | parent folder/season/series id |
| `IsFolder` | boolean | |
| `ImageTags` | object `{ "Primary": "<tag>", "Thumb": "<tag>", ... }` | keys are `ImageType` values, values are cache-busting tags |
| `BackdropImageTags` | array\<string\> | |
| `ParentThumbImageTag`, `ParentThumbItemId`, `SeriesPrimaryImageTag` | string | for episode rows that fall back to series/season art |
| `UserData` | `UserItemDataDto` | see below |
| `MediaSources` | array\<`MediaSourceInfo`\> | populated when `fields` includes `MediaSources` or via `/PlaybackInfo` |
| `MediaStreams` | array\<`MediaStream`\> | |
| `Genres`, `Studios`, `People`, `Tags` | arrays | |
| `ChildCount`, `RecursiveItemCount` | integer | for folders/series |
| `CollectionType` | string | for library root views: `movies`, `tvshows`, `music`, etc. |

### `UserItemDataDto` (the `UserData` sub-object)

| Field | Type | Notes |
|---|---|---|
| `Played` | boolean | |
| `PlayedPercentage` | number (0–100) | for resume progress bars |
| `PlaybackPositionTicks` | integer | resume position |
| `PlayCount` | integer | |
| `IsFavorite` | boolean | |
| `LastPlayedDate` | string | |
| `Key` | string | |
| `ItemId` | string | |

### `BaseItemKind` enum (`Type` field values, non-exhaustive, relevant subset)
`Movie`, `Series`, `Season`, `Episode`, `Audio`, `MusicAlbum`, `MusicArtist`,
`MusicVideo`, `BoxSet`, `Playlist`, `Folder`, `CollectionFolder`, `UserView`,
`Video`, `Genre`, `Person`, `Studio`, `Trailer`, `TvChannel`, `Book`, `Photo`.

### `ImageType` enum (used in image URLs and `ImageTags` keys)
`Primary`, `Art`, `Backdrop`, `Banner`, `Logo`, `Thumb`, `Disc`, `Box`,
`Screenshot`, `Menu`, `Chapter`, `BoxRear`, `Profile`.

---

## 1. Server discovery & system info

### `GET /System/Info/Public`
No auth required. Used to validate a server URL before login.

**Response** (`PublicSystemInfo`): `Id`, `ServerName`, `Version`, `ProductName`,
`OperatingSystem`, `LocalAddress`, `StartupWizardCompleted`.

### `GET /System/Info`
Authenticated, admin-oriented (fuller `SystemInfo`: adds `WebSocketPortNumber`,
`OperatingSystemDisplayName`, `HasUpdateAvailable`, `SystemArchitecture`, etc.).
Not usually needed by a playback client beyond `/System/Info/Public`.

### `GET /System/Info/Storage`
Disk space info — not needed by a player client.

---

## 2. Authentication

### `POST /Users/AuthenticateByName`
No prior auth token required, but the `Authorization`/`X-Emby-Authorization`
header (unauthenticated form, see below) must still be sent.

**Body** (`AuthenticateUserByName`):
```json
{ "Username": "string", "Pw": "string" }
```

**Response** (`AuthenticationResult`):
| Field | Type |
|---|---|
| `User` | `UserDto` (`Id`, `Name`, `ServerId`, `PrimaryImageTag`, `Configuration`, `Policy`, ...) |
| `SessionInfo` | `SessionInfoDto` |
| `AccessToken` | string — use as `Token=` in future auth headers |
| `ServerId` | string |

### Quick Connect
- `GET /QuickConnect/Enabled` → boolean, whether QC is enabled on the server.
- `POST /QuickConnect/Initiate` → `QuickConnectResult` (`Secret`, `Code`,
  `Authenticated`, `DeviceId`, `DeviceName`, `AppName`, `AppVersion`,
  `DateAdded`). Client polls `GET /QuickConnect/Connect?secret=<Secret>` (same
  response shape) until `Authenticated` becomes `true`.
- `POST /QuickConnect/Authorize` — used by the *second* device (the one
  entering the `Code`) to approve; not used by a TV client that is initiating.
- `POST /Users/AuthenticateWithQuickConnect` — body `QuickConnectDto`
  `{ "Secret": "string" }` → returns `AuthenticationResult` (same shape as
  password auth) once the code has been approved elsewhere.

### Authorization header format
The spec declares only a generic `apiKey` security scheme named
`Authorization` (header), which does not capture Jellyfin's actual composite
header format — this is Jellyfin convention, not enumerated in the OpenAPI
document itself. Both `Authorization` and the legacy alias
`X-Emby-Authorization` are accepted; format:

```
MediaBrowser Client="<app name>", Device="<device name>", DeviceId="<stable id>", Version="<app version>", Token="<access token>"
```

- Before login: omit `Token=` entirely (or omit the whole header and pass
  nothing) — `AuthenticateByName` does not require a token, but sending the
  header without `Token` is standard practice.
- After login: include `Token="<AccessToken>"` from the `AuthenticationResult`
  on every subsequent request. Sessions/devices are tracked by `DeviceId`.
- No refresh-token flow exists; the access token is long-lived until the user
  logs out or the token is revoked server-side.

---

## 3. User views / libraries

### `GET /UserViews`
| Query param | Type |
|---|---|
| `userId` | string |
| `includeExternalContent` | boolean |
| `presetViews` | array |
| `includeHidden` | boolean |

**Response**: `QueryResult<BaseItemDto>` (`{ Items: [...], TotalRecordCount, StartIndex }`).
Each item is `Type: "CollectionFolder"` or `"UserView"` with `CollectionType`
(`movies`, `tvshows`, `music`, `homevideos`, etc.), `Name`, `Id`, `ImageTags`.

Note: there is no `/Users/{userId}/Views` in this spec — `/UserViews` (with
`userId` as a query param) is the current path.

### `GET /UserViews/GroupingOptions`
Library-grouping settings — rarely needed by a player.

---

## 4. Item browsing

### `GET /Items`
The general-purpose browse/query endpoint. ~80 query params exist; the ones
that matter for a TV client:

| Param | Type | Notes |
|---|---|---|
| `userId` | string | scopes UserData/permissions |
| `parentId` | string | folder/library/season to browse |
| `includeItemTypes` | array\<string\> | e.g. `Movie`, `Series`, `Episode` |
| `excludeItemTypes` | array\<string\> | |
| `recursive` | boolean | needed to flatten folders |
| `sortBy` | array\<string\> | `SortName`, `PremiereDate`, `DateCreated`, `CommunityRating`, `Random`, etc. |
| `sortOrder` | array\<string\> | `Ascending` / `Descending` |
| `filters` | array\<string\> | `IsUnplayed`, `IsPlayed`, `IsFavorite`, `IsResumable`, etc. |
| `fields` | array\<string\> | extra fields to include, e.g. `Overview`, `MediaSources`, `MediaStreams` (many fields are omitted by default for payload size) |
| `startIndex` / `limit` | integer | pagination |
| `searchTerm` | string | free-text search (see §9) |
| `genres`, `years`, `tags`, `studios`, `personIds` | array | filtering |
| `isFavorite`, `isPlayed` | boolean | |
| `imageTypeLimit`, `enableImageTypes` | integer / array | control which image tags are returned |
| `enableUserData` | boolean | include `UserData` block |
| `enableTotalRecordCount` | boolean | |

**Response**: `QueryResult<BaseItemDto>` — `{ Items: BaseItemDto[], TotalRecordCount, StartIndex }`.

### `GET /Items/Latest`
Home-screen "latest" rows.

| Param | Type |
|---|---|
| `userId` | string |
| `parentId` | string (library to scope to) |
| `includeItemTypes` | array |
| `isPlayed` | boolean |
| `limit` | integer |
| `groupItems` | boolean (group episodes by series) |
| `fields`, `enableImages`, `enableUserData`, `imageTypeLimit`, `enableImageTypes` | — |

**Response**: `BaseItemDto[]` directly (not wrapped in `QueryResult`).

### `GET /UserItems/Resume`
"Continue watching" row.

| Param | Type |
|---|---|
| `userId` | string |
| `parentId` | string |
| `mediaTypes`, `includeItemTypes`, `excludeItemTypes` | array |
| `startIndex` / `limit` | integer |
| `excludeActiveSessions` | boolean |
| `enableUserData`, `enableImages`, `imageTypeLimit`, `enableImageTypes` | — |

**Response**: `QueryResult<BaseItemDto>`. Note: old API name `/Users/{userId}/Items/Resume` does not exist in this spec; use `/UserItems/Resume`.

### `GET /Shows/NextUp`
"Next Up" row for in-progress/next-episode-to-watch series.

| Param | Type |
|---|---|
| `userId` | string |
| `seriesId` | string (optional — scope to one series) |
| `parentId` | string |
| `startIndex` / `limit` | integer |
| `enableResumable` | boolean |
| `enableRewatching` | boolean |
| `nextUpDateCutoff` | string (date-time) |
| `disableFirstEpisode` | boolean |
| `fields`, `enableImages`, `enableUserData`, `imageTypeLimit`, `enableImageTypes` | — |

**Response**: `QueryResult<BaseItemDto>`.

### `GET /Shows/{seriesId}/Seasons`
| Param | Type |
|---|---|
| `seriesId` | path, required |
| `userId` | string |
| `isSpecialSeason`, `isMissing` | boolean |
| `enableImages`, `enableUserData`, `imageTypeLimit`, `enableImageTypes`, `fields` | — |

**Response**: `QueryResult<BaseItemDto>` where each item `Type == "Season"`.

### `GET /Shows/{seriesId}/Episodes`
| Param | Type |
|---|---|
| `seriesId` | path, required |
| `userId` | string |
| `season` | integer (season number) |
| `seasonId` | string |
| `startItemId`, `adjacentTo` | string |
| `startIndex` / `limit` | integer |
| `sortBy` | string |
| `enableImages`, `enableUserData`, `imageTypeLimit`, `enableImageTypes`, `fields` | — |

**Response**: `QueryResult<BaseItemDto>` where each item `Type == "Episode"`.

---

## 5. Item details

### `GET /Items/{itemId}`
| Param | Type |
|---|---|
| `itemId` | path, required |
| `userId` | query, optional (needed to get `UserData`) |

**Response**: single `BaseItemDto`.

Note: `/Users/{userId}/Items/{itemId}` (the older API shape) does **not**
exist in this spec — use `/Items/{itemId}?userId=` instead.

### `GET /Items/Similar` — actually `/Items/{itemId}/Similar`
Related/"more like this" rows. Params: `userId`, `excludeArtistIds`, `limit`,
`fields`. Response: `QueryResult<BaseItemDto>`.

---

## 6. Images

Two URL forms exist; prefer the simple query-param form for a TV client.

### `GET /Items/{itemId}/Images/{imageType}`
| Param | Type | Notes |
|---|---|---|
| `itemId` | path | |
| `imageType` | path, `ImageType` enum | `Primary`, `Backdrop`, `Thumb`, `Logo`, `Banner`, `Art`, `Disc`, `Box`, `Screenshot`, `Menu`, `Chapter`, `BoxRear`, `Profile` |
| `tag` | query | value from `ImageTags[imageType]` / `BackdropImageTags[n]`, cache-busts |
| `maxWidth` / `maxHeight` | query, integer | constrain longest side |
| `width` / `height` | query, integer | exact resize |
| `fillWidth` / `fillHeight` | query, integer | crop-to-fill |
| `quality` | query, integer | JPEG quality 0–100 |
| `blur` | query, integer | |
| `format` | query | output format override, e.g. `Jpg`, `Webp` |
| `imageIndex` | query, integer | for multi-image types (e.g. multiple `Backdrop`s); can also use the indexed path form below |

Typical construction:
```
{server}/Items/{itemId}/Images/Primary?tag={ImageTags.Primary}&maxWidth=400&quality=90
```

### `GET /Items/{itemId}/Images/{imageType}/{imageIndex}`
Same params, with `imageIndex` in the path instead of query — used for the
Nth backdrop/screenshot etc.

Both endpoints also support `HEAD`.

---

## 7. Playback

### `GET|POST /Items/{itemId}/PlaybackInfo`
POST is preferred for real clients since it accepts a `DeviceProfile` body describing codec capabilities, letting the server decide direct-play vs. transcode.

**Query params (POST)**: `userId`, `maxStreamingBitrate`, `startTimeTicks`,
`audioStreamIndex`, `subtitleStreamIndex`, `maxAudioChannels`,
`mediaSourceId`, `liveStreamId`, `autoOpenLiveStream`, `enableDirectPlay`,
`enableDirectStream`, `enableTranscoding`, `allowVideoStreamCopy`,
`allowAudioStreamCopy`.

**Body** (`PlaybackInfoDto`): mirrors the above as body fields, plus
`DeviceProfile` (`DeviceProfile` schema, see below).

**`DeviceProfile` essentials**:
| Field | Type | Notes |
|---|---|---|
| `Name`, `Id` | string | |
| `MaxStreamingBitrate`, `MaxStaticBitrate` | integer | |
| `DirectPlayProfiles` | array\<`DirectPlayProfile`\> | `{ Container, AudioCodec, VideoCodec, Type }` — `Type` is `DlnaProfileType` (`Video`/`Audio`/`Photo`) |
| `TranscodingProfiles` | array\<`TranscodingProfile`\> | `{ Container, Type, VideoCodec, AudioCodec, Protocol, Context, MaxAudioChannels, MinSegments, SegmentLength, BreakOnNonKeyFrames, ... }` |
| `CodecProfiles` | array\<`CodecProfile`\> | `{ Type, Codec, Container, Conditions[], ApplyConditions[] }` — expresses per-codec constraints (max resolution, bit depth, etc.) |
| `ContainerProfiles` | array | container-level constraints |
| `SubtitleProfiles` | array\<`SubtitleProfile`\> | `{ Format, Method, Container, Language, DidlMode }` — `Method` is `SubtitleDeliveryMethod`: `Encode`, `Embed`, `External`, `Hls`, `Drop` |

A minimal Android TV profile typically declares direct-play for `h264`/`hevc`/`av1`
in `mp4`/`mkv` with `aac`/`ac3`/`eac3` audio, plus one HLS `TranscodingProfile`
(`Container: "ts"`, `Protocol: "hls"`) as fallback.

**Response** (`PlaybackInfoResponse`):
| Field | Type |
|---|---|
| `MediaSources` | array\<`MediaSourceInfo`\> |
| `PlaySessionId` | string — pass to progress-reporting and stream URLs |
| `ErrorCode` | `PlaybackErrorCode` enum (nullable) |

### `MediaSourceInfo` — key fields
| Field | Type | Notes |
|---|---|---|
| `Id` | string | = `mediaSourceId` for stream URLs |
| `Protocol` | `MediaProtocol` | `File`, `Http`, etc. |
| `Container` | string | e.g. `mkv`, `mp4` |
| `RunTimeTicks` | integer | |
| `Size`, `Bitrate` | integer | |
| `SupportsDirectPlay` / `SupportsDirectStream` / `SupportsTranscoding` | boolean | decide playback strategy client-side |
| `TranscodingUrl` | string | relative URL to use when transcoding (already has query params baked in) |
| `TranscodingSubProtocol` | `MediaStreamProtocol` | `http` or `hls` |
| `TranscodingContainer` | string | |
| `DefaultAudioStreamIndex` / `DefaultSubtitleStreamIndex` | integer | |
| `MediaStreams` | array\<`MediaStream`\> | |
| `RequiresOpening` / `OpenToken` | boolean / string | for live streams |

### `MediaStream` — key fields
| Field | Type | Notes |
|---|---|---|
| `Index` | integer | stream index, used to select audio/subtitle track |
| `Type` | `MediaStreamType` | `Video`, `Audio`, `Subtitle`, `EmbeddedImage`, `Data`, `Lyric` |
| `Codec` | string | e.g. `h264`, `aac`, `subrip` |
| `Language` | string | ISO code |
| `IsDefault` | boolean | |
| `IsForced` | boolean | |
| `IsExternal` | boolean | |
| `DeliveryMethod` | `SubtitleDeliveryMethod` | for subtitle streams: `Encode`, `Embed`, `External`, `Hls`, `Drop` |
| `DeliveryUrl` | string | for external/HLS subtitle delivery |
| `Width` / `Height` | integer | video streams |
| `Channels` | integer | audio streams |
| `DisplayTitle` | string | human-readable label for track pickers |
| `VideoRange` / `VideoRangeType` | string | SDR/HDR info |

### Direct stream / transcode URL construction

- **Direct stream (progressive)**: `GET /Videos/{itemId}/stream[.{container}]`
  — query params include `static=true` (direct copy, no re-encode),
  `mediaSourceId`, `tag`, `deviceId`, `playSessionId`, plus the full
  transcoding-parameter set (`videoCodec`, `audioCodec`, `maxWidth`,
  `videoBitRate`, `subtitleStreamIndex`, `subtitleMethod`, etc.) used when the
  server decides to transcode instead.
- **HLS adaptive**: `GET /Videos/{itemId}/master.m3u8` — same transcoding
  param set, plus `mediaSourceId` (**required** here, unlike on `/stream`),
  `enableAdaptiveBitrateStreaming`, `enableTrickplay`,
  `alwaysBurnInSubtitleWhenTranscoding`. Also has a `HEAD` verb.
- Other HLS variants exist but are less relevant to a client:
  `/Videos/{itemId}/main.m3u8`, `/Videos/{itemId}/live.m3u8`,
  `/Videos/{itemId}/hls1/{playlistId}/{segmentId}.{container}`.
- Trickplay (BIF-style thumbnail scrubbing): `GET /Videos/{itemId}/Trickplay/{width}/tiles.m3u8` and `.../{index}.jpg`.
- External subtitle track as WebVTT-in-HLS: `GET /Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/subtitles.m3u8`.

In practice: prefer `MediaSourceInfo.TranscodingUrl` from `PlaybackInfo` when
transcoding is required (pre-built by the server); construct the direct-play
URL yourself (`/Videos/{itemId}/stream?static=true&mediaSourceId=...`) when
`SupportsDirectPlay`/`SupportsDirectStream` is true.

Auth for stream URLs: pass the token via the `Authorization`/
`X-Emby-Authorization` header, or as an `api_key` query param (useful when a
player component can't set headers) — accepted by Jellyfin's global auth
middleware even though it isn't modeled as an explicit parameter on these paths.

---

## 8. Playback progress reporting

All three endpoints are `POST`, return `204 No Content`, and share the same
core fields (`PlaybackStartInfo`/`PlaybackProgressInfo` are structurally
identical; `PlaybackStopInfo` is a trimmed-down variant).

### `POST /Sessions/Playing`
Call once when playback starts. Body (`PlaybackStartInfo`):

| Field | Type |
|---|---|
| `ItemId` | string |
| `MediaSourceId` | string |
| `PlaySessionId` | string (from `PlaybackInfoResponse`) |
| `SessionId` | string |
| `PositionTicks` | integer |
| `PlayMethod` | `PlayMethod` enum: `Transcode`, `DirectStream`, `DirectPlay` |
| `AudioStreamIndex` / `SubtitleStreamIndex` | integer |
| `CanSeek` | boolean |
| `IsPaused` / `IsMuted` | boolean |
| `VolumeLevel` | integer |
| `RepeatMode` | `RepeatMode` enum |
| `PlaybackOrder` | `PlaybackOrder` enum |
| `Item` | `BaseItemDto` (optional, full item echo) |
| `NowPlayingQueue` | array (for queue-aware sessions) |

### `POST /Sessions/Playing/Progress`
Same body shape (`PlaybackProgressInfo`); call periodically (e.g. every
~10s) and on seek/pause/resume/track-change, updating `PositionTicks`,
`IsPaused`, `PlayMethod`, etc.

### `POST /Sessions/Playing/Stopped`
Body (`PlaybackStopInfo`) — smaller: `ItemId`, `MediaSourceId`,
`PositionTicks` (final position — used to persist resume point / mark
watched), `PlaySessionId`, `SessionId`, `LiveStreamId`, `Failed` (boolean,
report `true` if playback errored out), `NextMediaType`, `NowPlayingQueue`.

### `POST /Sessions/Playing/Ping`
Heartbeat with no body requirement beyond the play session — keeps a
transcode session alive; call periodically during playback if idle progress
pings might otherwise lapse.

---

## 9. Search

Two options; prefer `/Search/Hints` for a global search box.

### `GET /Search/Hints`
| Param | Type | Notes |
|---|---|---|
| `searchTerm` | query, **required** | |
| `userId` | string | |
| `includeItemTypes` / `excludeItemTypes` | array | |
| `mediaTypes` | array | |
| `parentId` | string | |
| `isMovie`, `isSeries`, `isNews`, `isKids`, `isSports` | boolean | |
| `includePeople`, `includeMedia`, `includeGenres`, `includeStudios`, `includeArtists` | boolean | toggle result categories |
| `startIndex` / `limit` | integer | |

**Response**: `SearchHintResult` — `{ SearchHints: SearchHint[], TotalRecordCount }`.
`SearchHint` is a lighter-weight DTO than `BaseItemDto` (id, name, type, image
tag, matched term) — cheaper for typeahead than full `/Items` queries.

### Alternative: `GET /Items?searchTerm=...`
Reuses the full `/Items` browsing endpoint (§4) with `searchTerm` set — returns
full `BaseItemDto` results and supports all the filtering/sorting params, at
the cost of a heavier payload. Use this when search results need to be
rendered identically to browse rows (with `UserData`, images, etc.) rather
than as a lightweight hint list.

---

## Notes / surprises

- No `/Users/{userId}/Items/{itemId}` or `/Users/{userId}/Views` — this spec's
  API generation flattened those to `/Items/{itemId}?userId=` and
  `/UserViews?userId=`.
- No `/Users/{userId}/Items/Resume` — it's `/UserItems/Resume`.
- `X-Emby-Authorization`/`Authorization` composite header format is not
  actually documented in the OpenAPI `securitySchemes` block (it's collapsed
  to a generic `apiKey` on `Authorization`) — the `MediaBrowser Client=...,
  Token=...` format is Jellyfin convention, not spec-derived.
- `/Items/Latest` and `/Shows/{seriesId}/Seasons` / `/Episodes` return bare
  arrays or `QueryResult<BaseItemDto>` inconsistently — check wrapper shape
  per endpoint (`Items/Latest` is a raw array, most others are `QueryResult`).
- `master.m3u8` requires `mediaSourceId`; `stream`/`stream.{container}` does
  not (falls back to a source lookup by `itemId`).
- `PlaybackStartInfo` and `PlaybackProgressInfo` are schema-identical; the
  server just treats the endpoint you call as the semantic signal (start vs.
  ongoing), not a body field.
