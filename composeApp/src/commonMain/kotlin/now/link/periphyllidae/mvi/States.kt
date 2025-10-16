package now.link.periphyllidae.mvi

import now.link.periphyllidae.models.*
import now.link.periphyllidae.platform.PlaybackState
import now.link.periphyllidae.viewmodels.ServerConfigFormState

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

// Global Auth State (persistent authentication data only)
data class AuthState(
    val isAuthenticated: Boolean = false,
    val currentServer: ServerConfig? = null,
    val savedServers: List<ServerConfig> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isServerAdded: Boolean = false
) : State

// Global Music State (combines NowPlaying + Music concerns)
data class MusicState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val isRepeat: Boolean = false,
    val isShuffle: Boolean = false,
    val volume: Float = 0.7f,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val randomSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val newestAlbums: List<Album> = emptyList(),
    val recentlyPlayedAlbums: List<Album> = emptyList(),
    val mostPlayedAlbums: List<Album> = emptyList(),
    val isLoadingHome: Boolean = false,
    val homeError: String? = null
) : State

// Global App Intents
sealed interface AppIntent : Intent {
    data class ChangeTheme(val theme: AppTheme) : AppIntent
}

// Global Auth Intents (authentication operations only)
sealed interface AuthIntent : Intent {
    data class Login(val serverUrl: String, val username: String, val password: String, val alias: String) : AuthIntent
    data class AddServer(val serverUrl: String, val username: String, val password: String, val alias: String) : AuthIntent
    object ResetServerAdded : AuthIntent
    object Logout : AuthIntent
    object ClearError : AuthIntent
    object RefreshSavedServers : AuthIntent
    data class ChangeServer(val server: ServerConfig) : AuthIntent
    data class DeleteServer(val server: ServerConfig) : AuthIntent
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
    data class SetVolume(val volume: Float) : MusicIntent
    
    // Music loading
    object LoadHomeContent : MusicIntent
    object RefreshHomeContent : MusicIntent
    object PlayRandomSong : MusicIntent
    data class PlaySong(val song: Song) : MusicIntent
    data class PlayAlbum(val album: Album) : MusicIntent
    data class PlayAllSongsOfArtist(val artist: Artist) : MusicIntent
    data class SetQueue(val songs: List<Song>, val startIndex: Int = 0) : MusicIntent
    
    // Server change handling
    object InvalidatePlayerState : MusicIntent
}

// Search Screen (remains screen-scoped for now)
sealed interface SearchIntent : Intent {
    data class UpdateQuery(val query: String) : SearchIntent
    data class Search(val query: String) : SearchIntent
    object ClearResults : SearchIntent
}

data class SearchState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: SearchResult? = null,
    val error: String? = null
) : State

// Artist Detail State
data class ArtistDetailState(
    val artist: Artist? = null,
    val topSongs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) : State

// Artist Detail Intents
sealed interface ArtistDetailIntent : Intent {
    data class LoadArtistDetail(val artistId: String) : ArtistDetailIntent
    object PlayAllSongsOfArtist : ArtistDetailIntent
    data class PlayAlbum(val album: Album) : ArtistDetailIntent
    data class PlaySong(val song: Song) : ArtistDetailIntent
}

// Album Detail State
data class AlbumDetailState(
    val album: Album? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) : State

// Album Detail Intents
sealed interface AlbumDetailIntent : Intent {
    data class LoadAlbumDetail(val albumId: String) : AlbumDetailIntent
    object PlayAllSongsOfAlbum : AlbumDetailIntent
    data class PlaySong(val song: Song) : AlbumDetailIntent
}

// Login State (local form state for Login screen)
data class LoginState(
    override val serverUrl: String = "",
    override val username: String = "",
    override val password: String = "",
    override val alias: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false
) : State, ServerConfigFormState

// Add Server State (local form state for AddServer screen)
data class AddServerState(
    override val serverUrl: String = "",
    override val username: String = "",
    override val password: String = "",
    override val alias: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isServerAdded: Boolean = false
) : State, ServerConfigFormState
