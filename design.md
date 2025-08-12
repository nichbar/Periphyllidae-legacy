This document outlines the high-level technical design and architecture for the Subsonic music streaming application, based on the established system requirements.

### 1. Architecture
##### 1.1. Architectural Pattern
We will adopt the Model-View-Intent (MVI) pattern. MVI is a unidirectional data flow architecture that enforces a predictable state management cycle, which is highly suitable for a reactive UI framework like Compose Multiplatform.

- Model: Represents the state of the UI. It is an immutable data structure. A new state is created in response to an action.

- View: The UI layer, built with Compose Multiplatform. It renders the state and translates user actions into Intents.

- Intent: Represents a user's intention to perform an action (e.g., SearchQueryChanged, PlaySongClicked).

A central component, often called a Store or Reducer, will process these Intents, interact with the data layer (Repositories), and produce a new State object.

##### 1.2. Module Structure
The project will be organized into a multi-module structure to enforce separation and maximize code sharing.

- shared: The core Kotlin Multiplatform module.

- commonMain: Contains all shared logic: State holders/Stores, Repositories, Use Cases, Ktor networking, SQLDelight database schemas, Koin dependency injection modules, and navigation logic. This is where ~90% of the code will reside.

- androidMain: Contains Android-specific implementations (actual declarations) like the Media3 player and any platform-specific utilities.

- desktopMain: Contains Desktop-specific implementations (actual declarations) like the VLCJ player.

- androidApp: The Android application module. It contains the MainActivity, Android-specific resources, and initializes the shared module.

- desktopApp: The Desktop application module. It contains the main function to launch the desktop window and initializes the shared module.

### 2. Core Components & Libraries
This section details the role of each chosen library as defined in the requirements.

- UI Framework (Compose Multiplatform): The single UI framework for rendering the entire application UI on both Android and Desktop.

- Dependency Injection (Koin): Used to manage and provide dependencies (e.g., Repositories, Stores, API clients) throughout the shared and platform-specific modules.

- Networking (Ktor & subsonic-api): Ktor will be the underlying HTTP client. The siper/subsonic-api library will be used as a type-safe wrapper to abstract away the direct Subsonic API calls, simplifying data fetching.

- Navigation (Navigation Compose): The default library will be used to define a navigation graph within the shared module, managing transitions between screens (Login, Home, Search, etc.).

- Image Loading (Coil): The multiplatform version of Coil will be used for efficiently loading and caching album art and other images from the Subsonic server.

- Database (SQLDelight): Used for local persistence. Its primary roles will be caching API responses (e.g., playlists, album lists) to improve performance and enable limited offline functionality.

- Logging (Kermit): Provides a simple, unified logging API across Android (Logcat) and Desktop (console).

- Simple Data Storage (MultiplatformSettings): Used for storing non-sensitive, simple key-value data like the current server URL, username, and the selected theme (light/dark).

### 3. Platform-Specific Implementations
The `expect`/`actual` pattern is key to handling platform differences while maintaining a common API in the `shared` module.

Music Playback
A `MusicPlayer` interface will be defined in `commonMain` using the `expect` keyword.

```kotlin
// In: shared/src/commonMain/kotlin
expect class MusicPlayer {
    fun play(songUrl: String)
    fun pause()
    fun resume()
    fun stop()
    fun isPlaying(): Boolean
    // Add state flows for current position, track changes, etc.
}
```

Platform-specific `actual` implementations will be provided:

- `androidMain`: The `actual` `MusicPlayer` class will be a wrapper around Media3. It will manage a `MediaSession` and handle audio focus and lifecycle events specific to Android.

- `desktopMain`: The `actual` `MusicPlayer` class will be a wrapper around VLCJ. It will manage the media player instance and translate the common interface calls to VLCJ's API.

### 4. UI/UX Flow
##### 4.1. Navigation Graph
The app's navigation will be structured as follows:

- Root: A conditional root that shows either the LoginScreen or the MainScaffold.

- LoginScreen: A standalone screen. Upon successful login, it navigates to the MainScaffold and clears the back stack.

- MainScaffold: A scaffold containing a bottom navigation bar for switching between HomeScreen, SearchScreen, and SettingsScreen.

- NowPlayingScreen: Presented modally (e.g., as a full-screen dialog or a bottom sheet) over the MainScaffold when a song is playing.

##### 4.2. Theming
Two color schemes (lightColors, darkColors) will be defined in the shared module using Material 3 guidelines. A ThemeStore or similar state holder will use MultiplatformSettings to persist the user's choice and provide the current theme state to the top-level Composable.

### 5. Data Flow
The data will flow unidirectionally in a cycle, ensuring a predictable and traceable state.

View -> Intent -> Store/Reducer -> Repository -> Store/Reducer -> State (Model) -> View

1. User Action (View): A user interacts with the UI, which generates an Intent (e.g., SearchTapped("query")).

2. Intent Processing (Store): The Intent is passed to the screen's Store.

3. Data Fetching (Repository): The Store calls a Repository to fetch or manipulate data. The Repository decides the source of truth (API or local SQLDelight cache).

4. State Creation (Store): The Store receives data back from the Repository and combines it with the current state to produce a new, immutable State object.

5. UI Update (View): The View observes the State (e.g., via StateFlow). When a new state is emitted, the Composable UI automatically recomposes to reflect the changes.