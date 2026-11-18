# Periphyllidae

## Overview

I've been porting all my songs from phone to server lately, so that I can
access them from any devices.

Navidrome is the service I chose to provide music-streaming service. However, I
found that the open source clients are either buggy or web-based.

So I decided to build my own client app to meet my specific demands.

- run on macOS at work and Windows at home
- shuffle-all is the no.1 priority
- simple offline caching

So Periphyllidae was born. It's buggy and incomplete, but it just works for me.
I will fix bugs and add features when I have time.

Periphyllidae is a Kotlin Multiplatform music client for Subsonic-compatible
servers such as Navidrome, Airsonic, and Gonic. The app targets the desktop JVM
with a shared Compose Multiplatform UI, server-side music library browsing,
playback queues, and a disk-backed streaming cache.

## Features

- Connect to one or more Subsonic-compatible servers
- Browse home content, artists, albums, songs, playlist summaries, and search
  results
- Shuffle and play music through a shared playback queue
- Manage the current queue, including play, remove, clear, and restore
- Cache streamed audio through an embedded local HTTP cache server
- Configure cache size and clear cached content from the app
- View in-app logs with level filtering and live updates
- Use desktop tray/menu bar controls on JVM platforms

## Technology Stack

- **Kotlin Multiplatform** (`jvm()` target) for shared business logic
- **Compose Multiplatform** and **Material 3** for the UI
- **Decompose** for component-based navigation and lifecycle handling
- **MPV + JNA** for direct desktop playback through packaged native libraries
- **Ktor** for the Subsonic client and embedded cache server
- **Koin** for dependency injection
- **Kache** and **Okio** for disk cache storage
- **Multiplatform Settings** for persisted app, auth, and queue state
- **Kermit** with a custom `LogManager` for structured in-app logging

## Architecture

The application uses a shared MVI-style state layer backed by Kotlin
`StateFlow`. Cross-screen state lives in stores such as `AuthStore`,
`MusicStore`, and `AppStore`; screen-specific behavior lives in ViewModels.
Data access is isolated behind `SubsonicRepository`, and platform differences
are handled through small abstractions such as `PlaybackEngineFactory`,
`CacheDirectoryProvider`, and `SystemTrayManager`.

Navigation is built on Decompose. `RootComponent` owns root-level destinations
such as login, server management, cache management, logs, now playing, queue,
artist detail, and album detail. `MainComponent` owns the primary app tabs.

## Platform Notes

### Desktop JVM

- Uses `MpvPlaybackEngine` through `JnaMpvBridge`
- Loads native MPV libraries from `composeApp/appResources/mpv`
- Supports macOS, Windows, and Linux native distribution targets through
  Compose Desktop (`dmg`, `msi`, and `deb`)
- Includes desktop tray/menu bar controls where supported by the platform
- Uses desktop-specific cache locations under the user's home directory

See `composeApp/appResources/mpv/README.md` for the expected native MPV binary
layout.

## Getting Started

### Prerequisites

- JDK 17 or newer
- A Subsonic-compatible server such as Navidrome, Airsonic, Subsonic, or Gonic
- Native MPV libraries for the desktop platform you want to run or package

### Common Commands

```bash
# Fast desktop/common compile check
./gradlew :composeApp:compileKotlinJvm

# JVM unit tests
./gradlew :composeApp:jvmTest

# Desktop application for development
./gradlew :composeApp:run

# Native desktop app bundle for the current platform
./gradlew :composeApp:createDistributable

# macOS DMG package
./gradlew :composeApp:packageDmg

# Universal JAR for the current OS
./gradlew :composeApp:packageUberJarForCurrentOS
```

## First Run

1. Launch the app.
2. Add a Subsonic-compatible server with its URL, username, password, and
   optional alias.
3. Connect to the server.
4. Browse or search the library, then start playback from a song, album, artist,
   or shuffle action.

Saved servers, app settings, search history, and playback queue state are
persisted locally.

## Project Structure

```text
composeApp/src/
├── commonMain/kotlin/now/link/periphyllidae/
│   ├── cache/          # Disk cache and embedded cache server setup
│   ├── components/     # Decompose components and navigation contracts
│   ├── di/             # Koin modules
│   ├── models/         # App and Subsonic data models
│   ├── mvi/            # Shared state and intent definitions
│   ├── platform/       # Common playback, tray, and platform abstractions
│   ├── repository/     # Subsonic and settings repositories
│   ├── store/          # Auth, music, and app state stores
│   ├── ui/             # Screens, reusable UI, layout, and theme
│   ├── utils/          # Logging, search history, crypto, and app scope helpers
│   └── viewmodels/     # Screen-specific presentation logic
└── jvmMain/kotlin/     # Desktop entry point, MPV bridge, tray, cache, and DI
```

Additional project notes live in `docs/`, including the current code quality
report and the desktop VLC-to-MPV replacement design notes.

## Development Guidelines

- Use `LogManager` instead of direct logging.
- Keep long-running coroutines under `ApplicationScopeManager.scope`.
- Follow the existing MVI flow: UI intent, Store or ViewModel handler, then
  `StateFlow` state update.
- Prefer existing repository, store, component, and platform abstractions before
  adding new ones.
- Run targeted Gradle checks after changes. Use `:composeApp:compileKotlinJvm`
  for fast shared/desktop validation and `:composeApp:jvmTest` for unit tests.

## Compatibility

Periphyllidae uses the Subsonic API and is intended to work with:

- Navidrome
- Subsonic
- Airsonic
- Airsonic-Advanced
- Gonic

## License

No root project license file is currently declared in this repository.
