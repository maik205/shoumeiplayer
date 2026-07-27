# 002 — mpv `aid`/`sid` were set to Jellyfin stream indices

**Status:** Fixed
**Severity:** High — silently played the wrong audio track and reported the wrong stream to the server.

## The bug

Two different, both-1-based numbering schemes were being used interchangeably:

- **Jellyfin** addresses tracks by `MediaStream.Index` — the ffmpeg stream index within the
  container. In a typical file the video is 0, audio 1, subtitles 2.
- **mpv** numbers tracks *per type*, starting at 1. The first audio track is `aid=1`, the first
  subtitle is `sid=1`, regardless of their container indices.

`MpvEngine` mixed them in three places:

1. `applyPreferredTracks()` wrote the Jellyfin index straight into `aid`/`sid`.
2. `selectTrack()` did the same for user picks.
3. `parseTracks()` built `PlayerTrack.id` from mpv's `id`, and `PlayerViewModel` then reported that
   value back to Jellyfin as `AudioStreamIndex`/`SubtitleStreamIndex`.

For the common single-audio file the two happened to coincide often enough to look correct. On any
multi-track release they diverge: with video 0 / audio 2,3 / subs 4,5, asking for Jellyfin audio
index 3 set `aid=3`, which is a track mpv does not have, and picking mpv's audio 2 reported stream
index 2 to the server — the *video* stream.

## The fix

`player/MpvTrackList.kt` — pure, unit-tested translation between the two namespaces. mpv exposes the
container index as `ff-index` in `track-list`, which is the bridge:

```
streamIndexOf(track) = externalIndexByMpvId[track.mpvId]   // sideloaded subs, recorded at sub-add
                    ?: track.ffIndex                        // muxed tracks: the Jellyfin index
                    ?: track.mpvId                          // transcodes (see limitation below)
```

`PlayerTrack.id` is now always a Jellyfin stream index, so the whole app speaks one dialect; the
engine translates to `aid`/`sid` at the boundary via `MpvTrackList.mpvIdFor(...)`. An index the
source does not contain resolves to `null` and is logged and skipped, rather than switching mpv to
an arbitrary track.

## Known limitation

Transcoded output is a freshly remuxed container, so mpv reports no `ff-index` and the fallback uses
the mpv id. Indices in that case are not comparable to the original item's — but the server has
already baked the track choice into the transcode, so there is nothing meaningful to select. This is
documented in `streamIndexOf`'s KDoc.

## Coverage

`MpvTrackListTest` (8 tests) pins: ff-index preference, per-type isolation (audio 4 vs subtitle 4),
sideloaded-subtitle mapping, the "Off" entry, missing-track → `null`, the transcode fallback, and
label fallbacks. The fixture deliberately uses a file where mpv id ≠ ff-index.
