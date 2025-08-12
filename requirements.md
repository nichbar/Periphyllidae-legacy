This document outlines the requirements for a Spotify-like music streaming application using Compose Multiplatform and a Subsonic-compatible server as the music source. The requirements are specified using the EARS (Easy Approach to Requirements Syntax).

Each requirement is given a unique ID (e.g., F-LOGIN-01) for traceability.

### Functional Requirements 🎶
These requirements define what the application must do.

##### Login & Server Management
- F-LOGIN-01: When a user provides a valid server URL, username, and password, the system shall authenticate with the specified Subsonic server.

- F-LOGIN-02: When the user launches the app without being authenticated, the system shall display the login screen.

- F-LOGIN-03: The system shall provide an interface for the user to switch to a different Subsonic server after the initial login.

##### Home Screen
- F-HOME-01: While on the home screen, the system shall display music content fetched from the Subsonic API (e.g., new albums, random songs, playlists) in a layout similar to the Spotify UI.

- F-HOME-02: When a user selects a playable item (e.g., a song, an album), the system shall initiate playback.

##### Search Screen
- F-SEARCH-01: When a user submits a query on the search screen, the system shall use the Subsonic API to fetch and display matching artists, albums, and songs.

#### Now Playing Screen
- F-NOWPLAYING-01: While music is playing, the system shall display a "Now Playing" screen with details of the current track, including album art, title, and artist.

- F-NOWPLAYING-02: The system shall provide playback controls (e.g., play/pause, next, previous) on the "Now Playing" screen.

##### Settings Screen
- F-SETTINGS-01: While on the settings screen, the system shall display the application name and icon.

- F-SETTINGS-02: The system shall provide a user interface on the settings screen to manage and switch the active Subsonic server.

### Non-Functional Requirements (NFRs) 🛠️
These requirements define how the system should be built and its technical constraints.

##### Platform Support
- NFR-PLATFORM-01: The system shall be developed using Compose Multiplatform.

- NFR-PLATFORM-02: The system shall support Android and Desktop (Windows, macOS, and Linux) as final build targets.

- NFR-PLATFORM-03: The system shall not support iOS or Web builds.

##### Architecture & Libraries
- NFR-ARCH-01: The system shall use Koin for dependency injection.

- NFR-ARCH-02: The system shall use Navigation Compose for all in-app navigation.

- NFR-ARCH-03: The system shall use Kermit for logging across all supported platforms.

##### Data & Networking
- NFR-DATA-01: The system shall use the siper/subsonic-api library as the bridge for all communication with the Subsonic API.

- NFR-DATA-02: The system shall use Ktor as its networking framework.

- NFR-DATA-03: The system shall use SQLDelight for managing the local database.

- NFR-DATA-04: The system shall use MultiplatformSettings for persistent storage of simple data like user preferences and server details.

##### Media & UI
- NFR-MEDIA-01: The system shall use the Media3 library for music playback on Android.

- NFR-MEDIA-02: The system shall use the VLCJ library for music playback on Desktop platforms.

- NFR-MEDIA-03: The system shall use the Coil library for asynchronous loading and display of images.

- NFR-UI-01: The system shall provide both a light theme and a dark theme.