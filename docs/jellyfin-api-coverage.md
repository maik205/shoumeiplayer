# How much of the Jellyfin API does the client implement?

This page records the client’s Jellyfin API coverage as of 2026-07-31. The app implements most workflows required by an Android TV playback client, but it does not aim to expose the complete Jellyfin REST API.

## Coverage estimates

The bundled `jellyfin-openapi.json` contains about 426 HTTP operations across 349 paths. Source inspection identifies about 47 operations across 45 paths in the client.

| Scope | Estimated completion | Meaning |
|---|---:|---|
| Complete Jellyfin REST API | 11–12% | Includes server administration, metadata management, plugins, SyncPlay, scheduled tasks, and other non-player domains |
| Current Android TV baseline | 90–95% | Login, browsing, details, search, playback, reporting, artwork, and basic Live TV workflows |
| Mature Jellyfin TV client | 70–80% | Adds skip segments, remote control, preference synchronization, richer search, and other advanced client features |

These figures measure different goals. Endpoint count understates client readiness because a TV client needs only a focused subset of Jellyfin’s server API.

## Strongly covered workflows

The client has working coverage for:

- Password authentication, Quick Connect, logout, and persisted sessions
- User views, libraries, Continue Watching, Next Up, and latest media
- Movies, series, seasons, episodes, music, playlists, and collections
- Item search through `/Items`
- Favorite and played-state mutations
- PlaybackInfo negotiation, direct play, direct stream, and transcoding
- External subtitle delivery
- Playback start, progress, stop, and ping reporting
- Live stream open and close operations
- Transcode cleanup through `/Videos/ActiveEncodings`
- Artwork, chapter images, and trickplay transport
- User configuration reads and repository-level writes

The primary implementations live in:

- `core/data/src/main/java/com/maik205/shoumeiplayer/data/repo/AuthRepository.kt`
- `core/data/src/main/java/com/maik205/shoumeiplayer/data/repo/LibraryRepository.kt`
- `core/data/src/main/java/com/maik205/shoumeiplayer/data/repo/PlaybackRepository.kt`
- `core/jellyfin/src/main/java/com/maik205/shoumeiplayer/data/api/JellyfinClient.kt`

## Remaining TV-client gaps

The highest-value client gaps are:

1. Skip intro, credits, and recap through `/MediaSegments/{itemId}`
2. Remote-control and casting session capabilities
3. Server-backed persistence for selected audio and subtitle tracks
4. Trickplay image rendering in the scrub interface
5. Cinema intros and prerolls through `/Items/{itemId}/Intros`
6. Full `/Search/Hints` integration with categorized results
7. Rich queue state, playlist editing, and downloads
8. More precise codec, profile, level, and container constraints in the device profile

## API domains outside the client’s scope

Large Jellyfin domains remain absent because they do not serve the current TV playback scope:

- Server, user, device, and API-key administration
- Library creation, scanning, and metadata-provider configuration
- Metadata editing and remote metadata or image searches
- SyncPlay
- Scheduled tasks
- Plugin and package administration
- Backup and restore
- Activity and client logs
- Subtitle search, upload, and deletion
- Playlist and collection creation or mutation
- Most image mutation endpoints

## How to interpret this status

Treat the project as a focused Jellyfin Android TV client, not a general Jellyfin software development kit (SDK). Completing the TV client requires a small set of user-facing API integrations. Completing Jellyfin’s full REST API would require implementing most server administration and content-management domains.

Use [`jellyfin-api-surface.md`](jellyfin-api-surface.md) for the TV-oriented endpoint reference. The historical gap list in [`issues/001-jellyfin-api-gaps.md`](issues/001-jellyfin-api-gaps.md) is retained for context.
