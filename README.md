# Periphyllidae

**Still in early development! Not all features are implemented yet.**

A cross-platform Subsonic client for the modern music lover, built with Kotlin Multiplatform and Compose Multiplatform.

## Technology Stack

- **Kotlin Multiplatform** for shared business logic across platforms
- **Compose Multiplatform** for unified UI with native performance
- **MediaMP** for cross-platform audio playback
  - ExoPlayer backend on Android for optimized mobile playback
  - VLC backend on Desktop for comprehensive codec support
- **Intelligent Disk Cache** with embedded HTTP server for transparent music streaming caching
- **Ktor** for HTTP networking and Subsonic API communication
- **Koin** for dependency injection and service management
- **Kermit** wrapped by custom LogManager for enhanced debugging

## Architecture

This project follows modern Android architecture principles:

- **MVI (Model-View-Intent)** presentation pattern for reactive UI
- **Repository pattern** for data access abstraction
- **Dependency injection** with Koin for modular design
- **Reactive programming** with Kotlin StateFlow and Flow
- **Custom logging system** with in-memory capture and real-time updates

### Key Components

- **LogManager**: Custom logging wrapper with 10,000-entry circular buffer and StateFlow updates
- **HomeContentCache**: Intelligent caching system for home screen data
- **DiskLruCache**: Advanced music streaming cache with embedded HTTP server for transparent caching
- **AuthStore**: Secure authentication state management with server configuration
- **MusicPlayer**: Unified player interface abstracting MediaMP implementations
- **SubsonicRepository**: Comprehensive API interface for Subsonic servers

## Platform-specific Features

### Android

- Native ExoPlayer integration for optimized audio playback
- Material Design 3 components with dynamic theming
- Background playback support with media notifications
- Platform-specific MediaMP factory

### Desktop (JVM)

- VLC backend for comprehensive media format support
- Native system integration with platform look-and-feel
- Cross-platform UI consistency with desktop-optimized layouts
- Efficient resource management for desktop environments

## Getting Started

### Prerequisites

- JDK 17 or higher
- Android SDK 34+ (for Android builds)
- Gradle 8.0+
- VLC Media Player (for desktop builds)

### Building the Application

#### Android APK

```bash
./gradlew :composeApp:assembleDebug
```

#### Desktop Application

```bash
./gradlew :composeApp:run
```

#### macOS Application Bundle

```bash
./gradlew :composeApp:createDistributable
```

#### macOS DMG Package

```bash
./gradlew :composeApp:packageDmg
```

#### Universal JAR (Cross-platform)

```bash
./gradlew :composeApp:packageUberJarForCurrentOS
```

#### Linux .deb Package (via Docker)

```bash
# Build using Docker container
docker build -f Dockerfile.linux -t periphyllidae-linux .
docker run --rm -v "$(pwd)/build-output:/output" periphyllidae-linux

# Or use the build script
chmod +x build-linux.sh
./build-linux.sh
```

#### Cross-platform Builds with GitHub Actions

The project includes a GitHub Actions workflow for automated Linux builds. Push to the `main` branch or create a pull request to trigger builds.

### First-time Setup

1. Launch the application
2. You'll be prompted to configure your Subsonic server:
   - **Server URL**: Your Subsonic server address (e.g., `https://music.example.com`)
   - **Username**: Your Subsonic username
   - **Password**: Your Subsonic password or API token
3. Tap "Connect" to authenticate and save your configuration
4. Browse your music library from the home screen

## Project Structure

```
composeApp/
├── src/
│   ├── commonMain/kotlin/now/link/periphyllidae/
│   │   ├── models/          # Data models and cache structures
│   │   ├── repository/      # Data access interfaces
│   │   ├── ui/
│   │   │   ├── screens/     # 8 main UI screens
│   │   │   ├── components/  # Reusable UI components
│   │   │   └── theme/       # Material Design 3 theming
│   │   ├── platform/        # Platform abstraction layer
│   │   ├── utils/           # LogManager and utilities
│   │   └── di/              # Dependency injection modules
│   ├── androidMain/kotlin/  # Android-specific implementations
│   │   ├── platform/        # ExoPlayer MediaMP factory
│   │   └── di/              # Android DI modules
│   └── jvmMain/kotlin/      # Desktop-specific implementations
│       ├── platform/        # VLC MediaMP factory
│       └── di/              # Desktop DI modules
├── build.gradle.kts         # Multi-platform build configuration
└── assets/                  # Shared application resources
```

## Advanced Features

### Intelligent Music Caching

- **Transparent caching**: Music streams are automatically cached with an embedded HTTP server
- **LRU eviction**: Automatic cache management with configurable size limits (default 1GB)
- **Concurrent downloads**: Single download per URL shared across multiple requests
- **Cross-platform support**: Platform-optimized cache locations for Android and Desktop
- **Cache preloading**: Manual preloading of songs and playlists for offline listening
- **Real-time statistics**: Monitor cache usage and performance

### Debug Logging System

- **Real-time log viewer**: Access logs through the Logs screen
- **In-memory buffering**: 10,000 most recent log entries stored in memory
- **Log level filtering**: Filter by Verbose, Debug, Info, Warning, and Error levels
- **Copy and export**: Copy individual log entries or export entire log history
- **Live updates**: Logs update in real-time using StateFlow

### Cross-platform Audio

- **Unified player interface**: Single API across all platforms
- **State synchronization**: Real-time playback state updates
- **Queue management**: Full playlist and queue control
- **Format support**: Wide audio format support via platform-optimized backends

## TODO

- [x] Implement artist detail screen (completed)
- [x] Implement album detail screen (completed)
- [ ] Implement play queue management
- [ ] Now playing screen should have better UI/UX
- [ ] Check why FLAC playback is not working on Android
- [ ] Home screen caching needs improvement
- [ ] Support playing on background on Android
- [ ] Support notifications on Android
- [ ] Add transitions and animations between MusicBar and NowPlayingScreen
- [ ] Remove nested scaffolds in mainContent
- [ ] Search history

## Dependencies

### Core Framework

- `org.jetbrains.compose.runtime` - Compose Multiplatform runtime
- `org.jetbrains.kotlin.multiplatform` - Kotlin Multiplatform plugin
- `org.jetbrains.compose` - Compose Multiplatform framework

### Media Playback

- `org.openani.mediamp:mediamp-core` - Cross-platform media playback
- `org.openani.mediamp:mediamp-exoplayer` - ExoPlayer backend for Android
- `org.openani.mediamp:mediamp-vlc` - VLC backend for Desktop

### Networking & Data

- `io.ktor:ktor-client-core` - HTTP client for API communication
- `org.jetbrains.kotlinx:kotlinx-serialization-json` - JSON serialization

### Dependency Injection & Utilities

- `io.insert-koin:koin-core` - Dependency injection framework
- `co.touchlab:kermit` - Multi-platform logging (wrapped by LogManager)
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` - Coroutines support

## Build Configurations

The project supports multiple target formats:

- **DMG** (macOS disk images)
- **MSI** (Windows installers)
- **DEB** (Debian/Ubuntu packages via Docker)
- **Universal JAR** (runs on any JVM platform)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. When contributing:

1. Follow the existing code style and architecture patterns
2. Add appropriate logging using `LogManager`
3. Update tests for new functionality
4. Ensure cross-platform compatibility

## License

This project is licensed under the GPL License - see the LICENSE file for details.

## Subsonic Compatibility

This client is compatible with Subsonic API and its derivatives including:

- Subsonic
- Airsonic
- Airsonic-Advanced
- Navidrome
- Gonic
