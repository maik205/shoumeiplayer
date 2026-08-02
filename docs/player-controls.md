# Player controls

The player interface separates transport controls, seeking, playback options, and related content. Video continues while panels and shelves are open unless playback is paused.

## 1. Visibility and focus

When the controls are hidden, the player surface owns focus. Directional and selection keys reveal the controls or perform the configured playback action.

When the controls are visible, focus remains inside the active control row, drawer, or shelf. Back closes the innermost layer before hiding the controls or leaving playback.

Automatic hiding pauses while the viewer is seeking or using a drawer, shelf, or post-play prompt.

## 2. Transport controls

The transport row contains the actions available for the current item:

- Previous and next episode when episode adjacency is available
- Backward seek
- Play or pause
- Forward seek
- Subtitle selection
- Audio-track selection
- Playback speed
- Streaming quality
- Additional playback information and display options

Unavailable actions are omitted or disabled instead of retaining empty focus targets.

## 3. Seeking and trickplay

Seeking uses a temporary playhead. Left and right update the preview position, selection commits the seek, and Back cancels it.

Held input increases the seek step from 10 seconds to 30 seconds and then 60 seconds. Chapter markers can move the preview directly to adjacent chapters.

When Jellyfin returns trickplay metadata, the preview loads the matching tile from `/Videos/{itemId}/Trickplay/{width}/{index}.jpg`. Items without trickplay data show the target time without an image.

## 4. Media details

The player uses Jellyfin artwork and metadata for the current item. Episodes show series and episode context. Movies and audio items use the fields available for their media type.

Text replaces missing logo artwork. Playback state and the current position remain readable when artwork fails to load.

## 5. Playback options and related content

Playback options include:

- Audio and subtitle tracks
- Playback speeds from `0.5` to `2.0`
- Automatic or capped streaming quality
- Frame and display settings supported by the active backend
- Video, audio, and network diagnostics

Changing the quality cap resolves a new Jellyfin media source and preserves the current position where possible. The previous transcode session is closed after the replacement starts.

The player can also expose related items, cast members, and the next episode. Selecting related content exits the current session cleanly before navigation or replacement playback begins.

## 6. Playback state

The interface distinguishes initial loading, buffering, paused, playing, ended, and error states. Playback errors remain visible and expose a retry or exit action when the current state supports one.

`PlaybackProgressReporter` sends immediate updates for major state changes and periodic progress while playback continues. Stop reporting and transcode cleanup run independently of the player screen lifecycle.
