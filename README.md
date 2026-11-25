# Local Music Player (Android)

Plays the songs stored on your phone. No internet, no accounts, no external APIs.

## Open and run
1. Install Android Studio (Koala 2024.1 or newer).
2. File > Open > select this `LocalMusic` folder.
3. Let Gradle sync (it downloads dependencies once; accept any prompt to use/upgrade the Gradle wrapper).
4. Run on a real phone (USB debugging on) or an emulator with some audio files added.

## Features
- Scans all music on the device through MediaStore
- Search by title, artist or album
- Songs and Folders tabs: browse your music folder by folder
- Background playback with notification, lock-screen and Bluetooth controls
- Mini player + full-screen player (seek bar, next/previous)
- Shuffle and repeat (off / all / one)
- Album art, dynamic colors on Android 12+, dark mode
- Pauses on calls and when headphones are unplugged

## Files
- `Song.kt`            song model and MediaStore loader
- `PlaybackService.kt` ExoPlayer inside a MediaSessionService
- `MainActivity.kt`    connects to the service, theme
- `PlayerUiState.kt`   syncs player state into Compose
- `MusicApp.kt`        all screens

## Build without Android Studio
Push this project to a GitHub repository. The workflow in `.github/workflows/build.yml`
builds the APK automatically. Download it from the repo's Actions tab > latest run > Artifacts.
