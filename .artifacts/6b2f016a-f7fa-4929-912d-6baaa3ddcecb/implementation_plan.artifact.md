# Overhaul Player UI (Porting Anime-vsub style)

Overhaul the existing video player UI in `Komorei` to match the feature-rich and clean aesthetic of modern anime players like `anime-vsub`. This includes adding a "Lock" mode, Aspect Ratio controls, Track Selection (Subtitles/Audio), and a visual redesign of the control overlay.

## User Review Required

> [!IMPORTANT]
> - The new "Lock" mode will disable all UI interactions except for the unlock button itself.
> - Track selection will depend on the source providing multiple audio/subtitle tracks in the HLS/MP4 stream.
> - The "Anime-vsub" layout style will reorganize current buttons (Settings, Servers, Episodes) for better ergonomics.

## Proposed Changes

### [Player Component]

#### [MODIFY] [PlayerUiState.kt](file:///home/shin/komorei-app/app/src/main/java/git/shin/komorei/ui/player/PlayerUiState.kt)
- Add `isLocked: Boolean = false`
- Add `aspectRatio: Int = RESIZE_MODE_FIT` (matching Media3 `AspectRatioFrameLayout` constants)
- Add `availableTracks: Tracks? = null`
- Add `selectedAudioTrack: String? = null`, `selectedSubtitleTrack: String? = null`

#### [MODIFY] [PlayerViewModel.kt](file:///home/shin/komorei-app/app/src/main/java/git/shin/komorei/ui/player/PlayerViewModel.kt)
- Implement `toggleLock()`
- Implement `cycleAspectRatio()`
- Add `onTracksChanged` listener to `exoPlayer` to update `availableTracks`
- Implement `selectTrack(trackGroup: TrackGroup, trackIndex: Int)`
- Update `exoPlayer.setVideoResizeMode` based on the new aspect ratio state.

#### [MODIFY] [PlayerVideoArea.kt](file:///home/shin/komorei-app/app/src/main/java/git/shin/komorei/ui/player/PlayerVideoArea.kt)
- Redesign **Top Bar**: Back button, Title/Episode info, and a "Quick Settings" icon.
- Redesign **Center Controls**: Larger icons, cleaner circles, consistent spacing.
- Redesign **Bottom Bar**: Compact slider using `AnimeRed`, Time display, Next Episode, Fullscreen, and **Lock** button.
- Add **Side Shortcuts**: Buttons for "Episodes" and "Servers" moved to the right edge (or integrated into a side-sliding panel).
- Add **Unified Settings Overlay**: A bottom sheet or side menu that handles:
    - Playback Speed
    - Video Quality (linked to Servers)
    - Audio Track Selection
    - Subtitle Track Selection
    - Aspect Ratio (Fit, Fill, Zoom, 16:9, 4:3)
- Implement **Lock Logic**: When `isLocked` is true, show only a small unlock button; hide/disable all other controls.
- Enhance **Gestures**: Ensure gestures (volume, brightness, seek) are disabled when locked.

#### [MODIFY] [VideoPlayerSheet.kt](file:///home/shin/komorei-app/app/src/main/java/git/shin/komorei/ui/player/VideoPlayerSheet.kt)
- Connect the new ViewModel methods and states to the UI components.

#### [MODIFY] [strings.xml](file:///home/shin/komorei-app/app/src/main/res/values/strings.xml)
- Add Vietnamese strings for: "Lock", "Unlock", "Aspect Ratio", "Fit", "Fill", "Zoom", "Audio", "Subtitles", "Track", etc.

## Verification Plan

### Automated Tests
- `PlayerViewModelTest`: Verify state transitions for locking, aspect ratio cycling, and track selection.
- `PlayerVideoAreaScreenshotTest` (Roborazzi): Compare the new UI layout against the old one.

### Manual Verification
- Deploy to device/emulator.
- Test "Lock" mode: Ensure no controls respond except "Unlock".
- Test "Aspect Ratio": Verify the video surface changes size (Fit/Fill/Zoom).
- Test "Track Selection": Switch between different audio/subtitle tracks (if sample stream allows).
- Verify the new layout feels intuitive and matches the anime-vsub aesthetic.
