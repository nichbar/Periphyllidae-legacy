# Periphyllidae - Subsonic Music Player

**Still in early development! Not all features are implemented yet.**

A modern, cross-platform music streaming application built with Compose Multiplatform that connects to Subsonic-compatible servers.

## Features

- **Cross-Platform**: Runs on Android and Desktop (Windows, macOS, Linux)
- **Modern UI**: Material 3 design with light and dark themes
- **Subsonic Integration**: Full compatibility with Subsonic API
- **Music Playback**: High-quality audio playback with queue management
- **Search**: Search for artists, albums, and songs
- **Offline Support**: Local caching for improved performance
- **MVI Architecture**: Predictable state management with unidirectional data flow

## Media Playback

Periphyllidae uses [MediaMP](https://github.com/open-ani/mediamp), a cross-platform media player library for Compose Multiplatform, providing:

- **Unified API**: Same code for audio playback across all platforms
- **High-Quality Backends**:
  - Android: ExoPlayer for robust streaming and format support
  - Desktop: VLC for extensive codec compatibility
- **Streaming Optimized**: Direct URL streaming from Subsonic servers
- **Format Support**: Wide range of audio formats (MP3, FLAC, M4A, OGG, etc.)
- **Performance**: Native performance on each platform

## Architecture

The application follows the Model-View-Intent (MVI) architectural pattern with:

- **Compose Multiplatform** for cross-platform UI
- **Koin** for dependency injection
- **Ktor** for networking
- **SQLDelight** for local database
- **MediaMP** for cross-platform audio playback (ExoPlayer on Android, VLC on Desktop)
- **Coil** for image loading
- **Navigation Compose** for navigation

## Tech Stack

### Common Dependencies

- Compose Multiplatform for UI
- Kotlin Coroutines for async operations
- Koin for dependency injection
- Ktor for HTTP networking
- SQLDelight for local persistence
- Navigation Compose for navigation
- Kermit for logging
- Coil for image loading
- MultiplatformSettings for preferences
- MediaMP for cross-platform media playback
- Subsonic API client library

### Platform-Specific

- **Android**: MediaMP with ExoPlayer backend for audio playback
- **Desktop**: MediaMP with VLC backend for audio playback

## Requirements

- **Server**: Subsonic-compatible music server (Subsonic, Navidrome, Airsonic, etc.)
- **Android**: API level 24+ (Android 7.0)
- **Desktop**: Java 11+

## Building and Running

### Prerequisites

1. Install JDK 11 or higher
2. Install Android SDK (for Android builds)
3. Clone this repository

### Android

```bash
# Debug build
./gradlew :composeApp:assembleDebug

# Release build
./gradlew :composeApp:assembleRelease

# Install on connected device
./gradlew :composeApp:installDebug
```

### Desktop

```bash
# Run the application
./gradlew :composeApp:run

# Create distributable packages
./gradlew :composeApp:createDistributable

# Package for current OS
./gradlew :composeApp:packageDmg          # macOS
./gradlew :composeApp:packageMsi          # Windows
./gradlew :composeApp:packageDeb          # Linux
```

## Configuration

### First Time Setup

1. Launch the application
2. Enter your Subsonic server details:
   - **Server URL**: Your server's full URL (e.g., `https://music.example.com`)
   - **Username**: Your Subsonic username
   - **Password**: Your Subsonic password
3. Tap "Connect" to authenticate

### Supported Servers

This application works with any Subsonic API-compatible server:

- [Subsonic](http://www.subsonic.org/)
- [Navidrome](https://www.navidrome.org/)
- [Airsonic](https://airsonic.github.io/)
- [Airsonic-Advanced](https://github.com/airsonic-advanced/airsonic-advanced)
- [Gonic](https://github.com/sentriz/gonic)
- [Funkwhale](https://funkwhale.audio/)

## Project Structure

```
composeApp/
├── src/
│   ├── commonMain/           # Shared Kotlin code
│   │   ├── kotlin/
│   │   │   └── now/link/periphyllidae/
│   │   │       ├── di/       # Dependency injection
│   │   │       ├── models/   # Data models
│   │   │       ├── mvi/      # States and intents
│   │   │       ├── navigation/ # Navigation logic
│   │   │       ├── platform/ # Platform abstractions
│   │   │       ├── repository/ # Data repositories
│   │   │       ├── store/    # ViewModels/Stores
│   │   │       └── ui/       # UI components and screens
│   │   └── sqldelight/       # Database schema
│   ├── androidMain/          # Android-specific code
│   └── jvmMain/             # Desktop-specific code
├── build.gradle.kts
└── ...
```

## Key Features

### Music Playback

- Cross-platform audio streaming using MediaMP
- Play individual songs or entire albums
- Queue management with shuffle and repeat
- Background playback support (Android)
- Seek controls and progress tracking
- Support for various audio formats (MP3, FLAC, M4A, etc.)

### Discovery

- Browse recently added albums
- Discover random songs
- Access your playlists
- Search across your entire music library

### User Experience

- Clean, modern Material 3 interface
- Dark and light theme support
- Responsive design for different screen sizes
- Smooth animations and transitions

## Development

### Running in Development

```bash
# Run on Android emulator/device
./gradlew :composeApp:installDebug

# Run on Desktop
./gradlew :composeApp:run
```

### Code Style

The project follows Kotlin coding conventions and uses:

- 4-space indentation
- 120-character line length
- Explicit visibility modifiers
