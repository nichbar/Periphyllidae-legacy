package now.link.periphyllidae.mvi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.serializer
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
    val cacheSizeMB: Int = 1024, // Default 1GB
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
    val homeError: String? = null,
    val songDetailsCache: Map<String, Song> = emptyMap()
) : State

// Global App Intents
sealed interface AppIntent : Intent {
    data class ChangeTheme(val theme: AppTheme) : AppIntent
    data class ChangeCacheSize(val sizeMB: Int) : AppIntent
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
    object Stop : MusicIntent
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
    data class PlayAlbumFromPosition(val album: Album, val startIndex: Int) : MusicIntent
    data class PlayAllSongsOfArtist(val artist: Artist) : MusicIntent
    data class SetQueue(val songs: List<Song>, val startIndex: Int = 0) : MusicIntent
    data class GetSongDetails(val songId: String) : MusicIntent

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

// Find Screen MVI Components
@Serializable
data class HistoryItem(
    val id: String,
    val type: HistoryItemType,
    val name: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val imageId: String? = null,
    val timestamp: Long,
    @Serializable(with = HistoryItemDataSerializer::class)
    val data: Any // Song, Album, or Artist
)

@Serializable
sealed interface HistoryItemType {
    @Serializable
    object Song : HistoryItemType
    @Serializable
    object Album : HistoryItemType
    @Serializable
    object Artist : HistoryItemType
}

sealed interface FindView {
    object Search : FindView
    object AllSongs : FindView
    object AllAlbums : FindView
    object AllArtists : FindView
}

data class FindState(
    val currentView: FindView = FindView.Search,
    val searchQuery: String = "",
    val searchResults: SearchResult? = null,
    val searchHistory: List<HistoryItem> = emptyList(),
    val browseSongs: List<Song> = emptyList(),
    val browseAlbums: List<Album> = emptyList(),
    val browseArtists: List<Artist> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
) : State

sealed interface FindIntent : Intent {
    data class SearchQuery(val query: String) : FindIntent
    data class SelectHistoryItem(val item: HistoryItem) : FindIntent
    object BrowseAllSongs : FindIntent
    object BrowseAllAlbums : FindIntent
    object BrowseAllArtists : FindIntent
    object ClearHistory : FindIntent
    data class AddToHistory(val item: HistoryItem) : FindIntent
    data class LoadMoreSongs(val offset: Int) : FindIntent
    data class LoadMoreAlbums(val offset: Int) : FindIntent
    data class LoadMoreArtists(val offset: Int) : FindIntent
    object ClearError : FindIntent
    object BackToSearch : FindIntent
}

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

// Custom serializer for HistoryItem data field (polymorphic serialization)
object HistoryItemDataSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HistoryItemData") {
        element<String>("type")
        element<JsonElement>("data")
    }

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonElement = when (value) {
            is Song -> Json.encodeToJsonElement(Song.serializer(), value)
            is Album -> Json.encodeToJsonElement(Album.serializer(), value)
            is Artist -> Json.encodeToJsonElement(Artist.serializer(), value)
            else -> throw SerializationException("Unsupported type for HistoryItem data: ${value::class}")
        }

        val jsonOutput = JsonObject(mapOf(
            "type" to Json.encodeToJsonElement(String.serializer(), value::class.simpleName ?: "Unknown"),
            "data" to jsonElement
        ))

        encoder.encodeSerializableValue(JsonElement.serializer(), jsonOutput)
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("HistoryItemDataSerializer can only be used with JSON")

        val jsonElement = jsonDecoder.decodeJsonElement()
        val jsonObject = jsonElement.jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content
            ?: throw SerializationException("Missing type field in HistoryItem data")

        val dataElement = jsonObject["data"]
            ?: throw SerializationException("Missing data field in HistoryItem data")

        return when (type) {
            "Song" -> Json.decodeFromJsonElement(Song.serializer(), dataElement)
            "Album" -> Json.decodeFromJsonElement(Album.serializer(), dataElement)
            "Artist" -> Json.decodeFromJsonElement(Artist.serializer(), dataElement)
            else -> throw SerializationException("Unknown type for HistoryItem data: $type")
        }
    }
}
