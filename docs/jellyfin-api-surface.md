# Jellyfin API surface

This reference lists the Jellyfin endpoints used by Shoumei Player. The checked-in `jellyfin-openapi.json` defines the server snapshot used for request and response models.

## Authentication headers

`JellyfinClient` sends the Jellyfin `MediaBrowser` authorization format. Authenticated requests include the active access token. Pre-authentication requests omit the token.

The header contains:

- Client name and application version
- Device name and stable device identifier
- Access token when a session exists

`SessionStore` supplies the server URL, token, user identifier, and device identifier. An authenticated `401` response expires the active session.

## Server and authentication endpoints

`AuthRepository` uses these endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/System/Info/Public` | Validate a server and read public information |
| `GET` | `/Users/Public` | List public profiles |
| `GET` | `/Users/Me` | Read the active profile, verify tokens, and refresh configuration |
| `POST` | `/Users/AuthenticateByName` | Authenticate with a username and password |
| `GET` | `/QuickConnect/Enabled` | Check whether Quick Connect is available |
| `POST` | `/QuickConnect/Initiate` | Start Quick Connect |
| `GET` | `/QuickConnect/Connect` | Poll Quick Connect state |
| `POST` | `/Users/AuthenticateWithQuickConnect` | Exchange an approved secret for a session |
| `POST` | `/Users/ForgotPassword` | Request the server’s password-recovery action |
| `POST` | `/Users/Configuration` | Replace the active profile configuration after a read-modify-write update |
| `POST` | `/Sessions/Logout` | Revoke a session token |

Session replacement verifies the new token through `/Users/Me` before persisting it. The previous token is revoked only after the replacement is stored.

## Library and search endpoints

`LibraryRepository` uses these endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/UserViews` | List libraries and user views |
| `GET` | `/UserItems/Resume` | Load continue-watching items |
| `GET` | `/Shows/NextUp` | Load next episodes |
| `GET` | `/Items/Latest` | Load recent items by library |
| `GET` | `/Items` | Browse, filter, search, and load item details |
| `GET` | `/Genres` | List genres |
| `GET` | `/Search/Hints` | Load categorized search hints |
| `GET` | `/Shows/{seriesId}/Seasons` | List seasons |
| `GET` | `/Shows/{seriesId}/Episodes` | List episodes and episode adjacency |
| `GET` | `/Items/{itemId}/Similar` | Load related items |
| `GET` | `/Artists` | Browse artists |
| `GET` | `/Playlists/{playlistId}/Items` | Load playlist items |
| `GET` | `/Persons` | Browse people |
| `GET` | `/Audio/{itemId}/Lyrics` | Load lyrics |
| `GET` | `/LiveTv/Channels` | List Live TV channels |
| `GET` | `/LiveTv/Programs` | Load guide programs |
| `GET` | `/LiveTv/Recordings` | List recordings |
| `POST`, `DELETE` | `/UserFavoriteItems/{itemId}` | Set favorite state |
| `POST`, `DELETE` | `/UserPlayedItems/{itemId}` | Set watched state |

`/Items` supplies most browse and detail data. Requests select only the fields needed by the current screen and use `startIndex` and `limit` for pagination.

## Images and trickplay

`ImageUrlBuilder` constructs item image URLs with a cache tag and rendered width. Supported uses include primary, backdrop, thumbnail, logo, chapter, artist, and profile artwork.

Image requests use `/Items/{itemId}/Images/{imageType}` or the indexed variant `/Items/{itemId}/Images/{imageType}/{imageIndex}`.

Trickplay previews use `/Videos/{itemId}/Trickplay/{width}/{index}.jpg`. The player selects a trickplay band from item metadata and calculates the tile rectangle for the current preview position.

## Playback negotiation and streams

`PlaybackRepository` uses these endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/Items/{itemId}/PlaybackInfo` | Resolve direct-play, direct-stream, or transcode sources |
| `GET` | `/Videos/{itemId}/stream` | Open video playback |
| `GET` | `/Audio/{itemId}/stream` | Open audio playback |
| `POST` | `/LiveStreams/Open` | Open a Live TV source |
| `POST` | `/LiveStreams/Close` | Close a Live TV source |
| `DELETE` | `/Videos/ActiveEncodings` | Stop an active transcode |

The playback-information request includes the device profile, selected tracks, start position, and optional bitrate cap. The response supplies the media source, play-session identifier, stream tracks, and any server-generated transcode URL.

The client passes Jellyfin authentication headers to the playback backend. Trickplay image URLs may use `api_key` because the image loader does not share the playback request headers.

## Playback reporting

Playback state is reported through:

| Method | Path | Event |
| --- | --- | --- |
| `POST` | `/Sessions/Playing` | Playback started |
| `POST` | `/Sessions/Playing/Progress` | Position, pause, seek, or track state changed |
| `POST` | `/Sessions/Playing/Stopped` | Playback stopped or failed |
| `POST` | `/Sessions/Playing/Ping` | Session heartbeat |

Reports include the item, media source, play session, position in Jellyfin ticks, play method, and selected stream indices when available.

## Data models

Jellyfin data transfer objects live in `:core:jellyfin`. They use kotlinx serialization with `ignoreUnknownKeys = true` so the client can model only the fields it consumes.

The main response types are:

- `BaseItemDto` for libraries, media, people, and artwork metadata
- `UserDto` and `UserConfigurationDto` for profiles and preferences
- `QueryResult<T>` for paginated responses
- `PlaybackInfoResponse` and `MediaSourceInfoDto` for playback negotiation
- `MediaStreamDto` for audio, video, and subtitle tracks
- `LiveStreamResponse` for Live TV playback

Update this document when adding or removing a repository endpoint. Update `jellyfin-openapi.json` separately when changing the server snapshot.
