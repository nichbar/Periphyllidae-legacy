# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Periphyllidae is a Kotlin Multiplatform music client for Subsonic-compatible servers (Navidrome, Airsonic, etc.) with intelligent caching and cross-platform support. It runs on Android and desktop platforms (macOS, Windows, Linux).

## Common Development Commands

### Building and Running

```bash
# JVM compile check
./gradlew :composeApp:compileKotlinJvm

# Android compile check
./gradlew :composeApp:compileDebugKotlinAndroid

# JVM unit tests
./gradlew :composeApp:jvmTest

# Android Debug APK
./gradlew :composeApp:assembleDebug

# Desktop Application (development)
./gradlew :composeApp:run

# macOS Application Bundle
./gradlew :composeApp:createDistributable

# macOS DMG Package
./gradlew :composeApp:packageDmg

# Universal JAR for current platform
./gradlew :composeApp:packageUberJarForCurrentOS

# Clean build
./gradlew clean
```

## Architecture Overview

### Core Technologies
- **Kotlin Multiplatform** - Shared business logic across Android and JVM targets
- **Compose Multiplatform** - Unified UI framework with native performance
- **Decompose** - Navigation and component lifecycle management
- **MediaMP + MPV** - ExoPlayer-backed playback on Android; direct MPV playback engine on desktop
- **Ktor** - HTTP client and embedded server for caching
- **Koin** - Dependency injection
- **Kermit + LogManager** - Enhanced logging with in-memory buffering

### Architecture Patterns
- **MVI (Model-View-Intent)** - Reactive presentation layer with StateFlow
- **Repository Pattern** - Data access abstraction with `SubsonicRepository`
- **Component-based Navigation** - Decompose stack-based navigation
- **Factory Pattern** - Media player creation to avoid expect/actual issues

## Docs
Docs files are located in the `docs/` directory.

`CODE_QUALITY_ANALYSIS_REPORT.md` - Code quality report with identified issues and possible improvements.

`docs/mouse-drag-scrolling.md` - Desktop mouse drag scrolling implementation notes for vertical lists and horizontal album rows.

`docs/plans/2026-02-28-replace-vlc-with-mpv-design.md` - Desktop MPV replacement design notes.

`docs/plans/2026-02-28-replace-vlc-with-mpv-benchmark-notes.md` - MPV/VLC benchmark notes and validation status.

### Key Components

#### Navigation (`RootComponent`)
- Centralized navigation using sealed class `Nav` events
- Stack-based navigation with `StackNavigation`
- Component lifecycle management through Decompose

#### State Management
- **AuthStore** - Authentication and server configuration
- **MusicStore** - Player state and playback queue
- **AppStore** - Global app state (theme, settings)

#### Caching System
- **CacheManager** - LRU disk cache with configurable size (default 1GB)
- **Embedded HTTP Server** - Transparent streaming and caching
- **CacheDirectoryProvider** - Platform-specific cache locations
- **CacheInitializer** - Async cache system initialization

#### Media Playback
- **MusicPlayer** - Unified interface abstracting platform playback engines
- **PlaybackEngineFactory** - Platform-specific factory for ExoPlayer on Android and MPV on desktop
- **MpvPlaybackEngine / JnaMpvBridge** - Desktop MPV integration through JNA and packaged native libraries
- **SystemTrayManager** - Desktop media controls and tray integration

#### Logging System
- **LogManager** - Custom wrapper around Kermit with 10,000-entry circular buffer
- **Real-time log viewing** - Accessible through Logs screen
- **StateFlow integration** - Reactive log updates

#### Coroutine and Memory Management
- **ApplicationScopeManager** - Centralized coroutine scope management for memory leak prevention
- **Platform-specific cleanup** - JVM shutdown hooks and Android service lifecycle integration
- **Structured concurrency** - Proper cancellation and resource management

## Project Structure

```
composeApp/src/
├── commonMain/kotlin/now/link/periphyllidae/
│   ├── components/           # Decompose navigation components
│   ├── mvi/                  # State and intent definitions
│   ├── models/              # Data models and cache structures
│   ├── repository/          # Data access layer
│   ├── store/               # State management (MVI)
│   ├── ui/
│   │   ├── screens/         # Main UI screens
│   │   ├── components/      # Reusable UI components
│   │   ├── content/         # Root/main content routing
│   │   ├── layout/          # Shared app layout and overlays
│   │   ├── animation/       # Shared animation utilities
│   │   ├── utils/           # UI-specific utilities
│   │   └── theme/           # Material Design 3 theming
│   ├── viewmodels/          # Screen-specific ViewModels
│   ├── platform/            # Platform abstractions
│   ├── cache/               # Caching system
│   ├── services/            # Shared service helpers
│   ├── utils/               # LogManager and utilities
│   └── di/                  # Dependency injection
├── androidMain/kotlin/      # Android-specific code
└── jvmMain/kotlin/          # Desktop-specific code
```

## Platform-Specific Considerations

### Android
- **MainActivity.kt** - Entry point with edge-to-edge display
- **MusicServiceManager** - Background playback with media notifications
- **ExoPlayer backend** - Optimized mobile audio playback
- **Media Style notifications** - System media controls

### Desktop
- **main.kt** - JVM entry point with window management
- **System tray integration** - Minimize to tray, media controls
- **MPV backend** - Desktop playback through `MpvPlaybackEngine`, `JnaMpvBridge`, and `MpvNativeLoader`
- **Custom window styling** - Undecorated, transparent with rounded corners

## Development Guidelines

### Adding New Screens
1. Create or update the relevant Component interface in `components/`
2. Add navigation events/configuration in `RootComponent` or `MainComponent`, depending on whether the screen is root-level or tab-level
3. Create or update the ViewModel in `viewmodels/`
4. Implement the Screen in `ui/screens/`
5. Wire content routing in `ui/content/` and layout behavior in `ui/layout/` when needed

### Working with State
- Use `StateFlow` for reactive state management with `collectAsStateWithLifecycle`
- Follow the existing MVI style: Intent → Store/ViewModel handler → StateFlow state update
- Keep ViewModels focused on screen-specific logic
- Use Stores for cross-component state sharing (AuthStore, MusicStore, AppStore)
- **Note**: Search/Find behavior is screen-scoped in ViewModels rather than a global SearchStore
- **Memory Management**: Use `ApplicationScopeManager.scope` for long-running coroutines

### Platform Abstractions
- Use `expect/actual` for platform-specific implementations
- Prefer `PlaybackEngineFactory` for platform playback differences
- Implement platform-specific DI modules (`platformModule`)

### Logging
- Always use `LogManager` instead of direct logging
- Include appropriate log levels (Verbose, Debug, Info, Warning, Error)
- Use descriptive tags for better debugging

## Dependencies

The project uses version catalogs (`libs.versions.toml`) for dependency management. Key dependencies include:
- Compose Multiplatform and Material 3
- Decompose for navigation
- MediaMP for audio playback
- Ktor for networking
- Koin for DI
- Kermit for logging

## Testing

Use targeted verification after changes:

```bash
# Fast desktop/common compile check
./gradlew :composeApp:compileKotlinJvm

# Android compile check
./gradlew :composeApp:compileDebugKotlinAndroid

# JVM playback/native-loader test suite
./gradlew :composeApp:jvmTest

# Full project checks when broader changes justify it
./gradlew :composeApp:check
```

Run `./gradlew :composeApp:run` when you need to manually inspect the desktop app behavior, not as the default compile check.

## Current Code Quality Status

**Overall Grade: A- (Excellent with Minor Improvements)**

### ⚠️ Remaining Areas for Improvement
- Broader test coverage is still needed outside the current common and JVM playback/native-loader tests
- UI performance optimization opportunities
- Error handling standardization
- AlbumDetailViewModel direct repository access (architectural inconsistency)

The project maintains excellent architectural foundations and is ready for production with minor polishing.
