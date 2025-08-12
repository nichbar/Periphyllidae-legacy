package now.link.periphyllidae.mvi

import now.link.periphyllidae.models.*

// Base Intent interface
sealed interface Intent

// Base State interface
interface State

// App State
data class AppState(
    val isAuthenticated: Boolean = false,
    val currentServer: ServerConfig? = null,
    val theme: AppTheme = AppTheme.SYSTEM
) : State

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

// Login Screen
sealed interface LoginIntent : Intent {
    data class UpdateServerUrl(val url: String) : LoginIntent
    data class UpdateUsername(val username: String) : LoginIntent
    data class UpdatePassword(val password: String) : LoginIntent
    object Login : LoginIntent
    object ClearError : LoginIntent
}

data class LoginState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) : State

// Home Screen
sealed interface HomeIntent : Intent {
    object LoadContent : HomeIntent
    object Refresh : HomeIntent
    data class PlaySong(val song: Song) : HomeIntent
    data class PlayAlbum(val album: Album) : HomeIntent
}

data class HomeState(
    val isLoading: Boolean = false,
    val recentAlbums: List<Album> = emptyList(),
    val randomSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null
) : State

// Search Screen
sealed interface SearchIntent : Intent {
    data class UpdateQuery(val query: String) : SearchIntent
    data class Search(val query: String) : SearchIntent
    object ClearResults : SearchIntent
    data class PlaySong(val song: Song) : SearchIntent
    data class PlayAlbum(val album: Album) : SearchIntent
}

data class SearchState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: SearchResult? = null,
    val error: String? = null
) : State

// Now Playing Screen
sealed interface NowPlayingIntent : Intent {
    object Play : NowPlayingIntent
    object Pause : NowPlayingIntent
    object Next : NowPlayingIntent
    object Previous : NowPlayingIntent
    data class SeekTo(val position: Long) : NowPlayingIntent
    object ToggleRepeat : NowPlayingIntent
    object ToggleShuffle : NowPlayingIntent
}

data class NowPlayingState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isRepeat: Boolean = false,
    val isShuffle: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0
) : State

// Settings Screen
sealed interface SettingsIntent : Intent {
    data class ChangeTheme(val theme: AppTheme) : SettingsIntent
    object Logout : SettingsIntent
    data class ChangeServer(val server: ServerConfig) : SettingsIntent
}

data class SettingsState(
    val currentServer: ServerConfig? = null,
    val theme: AppTheme = AppTheme.SYSTEM,
    val appVersion: String = "1.0.0"
) : State
