package now.link.periphyllidae.mvi

import now.link.periphyllidae.models.*

// Base Intent interface
sealed interface Intent

// Base State interface
interface State

// Global App State
data class AppState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val appVersion: String = "1.0.0",
    val isLoading: Boolean = false
) : State

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

// Global Auth State (combines Login + Auth concerns)
data class AuthState(
    val isAuthenticated: Boolean = false,
    val currentServer: ServerConfig? = null,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) : State

// Global Music State (combines NowPlaying + Music concerns)
data class MusicState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isRepeat: Boolean = false,
    val isShuffle: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val randomSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val newestAlbums: List<Album> = emptyList(),
    val isLoadingHome: Boolean = false,
    val homeError: String? = null
) : State

// Global App Intents
sealed interface AppIntent : Intent {
    data class ChangeTheme(val theme: AppTheme) : AppIntent
}

// Global Auth Intents (combines Login + Settings auth concerns)
sealed interface AuthIntent : Intent {
    data class UpdateServerUrl(val url: String) : AuthIntent
    data class UpdateUsername(val username: String) : AuthIntent
    data class UpdatePassword(val password: String) : AuthIntent
    object Login : AuthIntent
    object Logout : AuthIntent
    object ClearError : AuthIntent
    data class ChangeServer(val server: ServerConfig) : AuthIntent
    object TestConnection : AuthIntent
    object SaveConfiguration : AuthIntent
}

// Global Music Intents (combines Home + NowPlaying concerns)
sealed interface MusicIntent : Intent {
    // Playback controls
    object Play : MusicIntent
    object Pause : MusicIntent
    object Next : MusicIntent
    object Previous : MusicIntent
    data class SeekTo(val position: Long) : MusicIntent
    object ToggleRepeat : MusicIntent
    object ToggleShuffle : MusicIntent
    
    // Music loading
    object LoadHomeContent : MusicIntent
    object RefreshHomeContent : MusicIntent
    data class PlaySong(val song: Song) : MusicIntent
    data class PlayAlbum(val album: Album) : MusicIntent
    data class SetQueue(val songs: List<Song>, val startIndex: Int = 0) : MusicIntent
}

// Search Screen (remains screen-scoped for now)
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
