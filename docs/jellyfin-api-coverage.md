# Jellyfin API coverage

Shoumei Player implements the Jellyfin workflows needed for browsing and playback. It does not aim to expose the complete server administration API.

## Implemented workflows

The client currently covers:

- Public server information and connection checks
- Password authentication, Quick Connect, profile selection, logout, and session replacement
- User views, libraries, latest items, continue watching, and next up
- Movies, series, seasons, episodes, music, playlists, collections, people, and genres
- Item search and search hints
- Favorite and watched-state updates
- User configuration reads and updates
- Playback information, direct play, direct stream, transcoding, and external subtitles
- Audio and subtitle track selection
- Playback start, progress, stop, and ping reporting
- Trickplay metadata and image tiles
- Related items and cast data
- Live TV guide data and live-stream open and close operations
- Active transcode cleanup
- Artwork and chapter images

## Implementation locations

The primary API code lives in:

- [`JellyfinClient.kt`](../core/jellyfin/src/main/java/com/maik205/shoumeiplayer/data/api/JellyfinClient.kt): request execution, authentication headers, timeouts, and error mapping
- [`AuthRepository.kt`](../core/data/src/main/java/com/maik205/shoumeiplayer/data/repo/AuthRepository.kt): authentication and user configuration
- [`LibraryRepository.kt`](../core/data/src/main/java/com/maik205/shoumeiplayer/data/repo/LibraryRepository.kt): browse, search, metadata, and user-state operations
- [`PlaybackRepository.kt`](../core/data/src/main/java/com/maik205/shoumeiplayer/data/repo/PlaybackRepository.kt): playback negotiation, live streams, reporting, and cleanup

## Known gaps

The client does not currently implement:

- Intro, recap, and credit segments from `/MediaSegments/{itemId}`
- Jellyfin remote-control and casting sessions
- SyncPlay
- Cinema intros and prerolls from `/Items/{itemId}/Intros`
- Offline downloads
- Playlist and collection editing
- Server administration, scheduled tasks, plugins, backup, or metadata editing

## Reference data

Use [`jellyfin-api-surface.md`](jellyfin-api-surface.md) for endpoint and data-model notes. The checked-in `jellyfin-openapi.json` remains the authoritative API snapshot for this repository.
