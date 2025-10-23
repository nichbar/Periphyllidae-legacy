# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Periphyllidae is a Kotlin Multiplatform music client for Subsonic-compatible servers (Navidrome, Airsonic, etc.) with intelligent caching and cross-platform support. It runs on Android and desktop platforms (macOS, Windows, Linux).

## Common Development Commands

### Building and Running

```bash
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
- **MediaMP** - Cross-platform audio playback (ExoPlayer on Android, VLC on Desktop)
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
- **MusicPlayer** - Unified interface abstracting MediaMP implementations
- **MediampPlayerFactory** - Platform-specific factory for ExoPlayer/VLC
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
│   ├── models/              # Data models and cache structures
│   ├── repository/          # Data access layer
│   ├── store/               # State management (MVI)
│   ├── ui/
│   │   ├── screens/         # 8 main UI screens
│   │   ├── components/      # Reusable UI components
│   │   └── theme/           # Material Design 3 theming
│   ├── viewmodels/          # Screen-specific ViewModels
│   ├── platform/            # Platform abstractions
│   ├── cache/               # Caching system
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
- **VLC backend** - Comprehensive codec support
- **Custom window styling** - Undecorated, transparent with rounded corners

## Development Guidelines

### Adding New Screens
1. Create Component interface in `components/`
2. Implement in `components/Default*Component.kt`
3. Add to `RootComponent` navigation stack
4. Create ViewModel in `viewmodels/`
5. Implement Screen in `ui/screens/`
6. Add navigation configuration in `RootComponent.kt`

### Working with State
- Use `StateFlow` for reactive state management with `collectAsStateWithLifecycle`
- Follow MVI pattern: Intent → Reducer → State
- Keep ViewModels focused on screen-specific logic
- Use Stores for cross-component state sharing (AuthStore, MusicStore, AppStore)
- **Note**: SearchStore was merged into SearchViewModel to eliminate architectural redundancy
- **Memory Management**: Use `ApplicationScopeManager.scope` for long-running coroutines

### Platform Abstractions
- Use `expect/actual` for platform-specific implementations
- Prefer factory pattern for MediaMP to avoid direct expect/actual
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

Check if the project is compilable by running `./gradlew :composeApp:run` after every major changes.

## Current Code Quality Status

**Overall Grade: A- (Excellent with Minor Improvements)**

### ⚠️ Remaining Areas for Improvement
- Missing comprehensive testing infrastructure
- UI performance optimization opportunities
- Error handling standardization
- AlbumDetailViewModel direct repository access (architectural inconsistency)

The project maintains excellent architectural foundations and is ready for production with minor polishing.