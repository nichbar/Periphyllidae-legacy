package now.link.periphyllidae.models.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("subsonic-response")
data class SubsonicResponse(
    val status: String,
    val version: String,
    @SerialName("type") val type: String? = null,
    @SerialName("serverVersion") val serverVersion: String? = null,
    @SerialName("openSubsonic") val openSubsonic: Boolean? = null,
    val error: SubsonicError? = null,
    val randomSongs: RandomSongs? = null,
    val newestAlbums: AlbumList? = null,
    val playlists: PlaylistList? = null,
    val searchResult3: SearchResult3? = null,
    val album: SubsonicAlbum? = null,
    val playlist: SubsonicPlaylist? = null,
    val ping: Ping? = null
)

@Serializable
data class SubsonicError(
    val code: Int,
    val message: String
)

@Serializable
data class Ping(
    val status: String = "ok"
)

@Serializable
data class RandomSongs(
    val song: List<SubsonicSong> = emptyList()
)

@Serializable
data class AlbumList(
    val album: List<SubsonicAlbum> = emptyList()
)

@Serializable
data class PlaylistList(
    val playlist: List<SubsonicPlaylist> = emptyList()
)

@Serializable
data class SearchResult3(
    val artist: List<SubsonicArtist> = emptyList(),
    val album: List<SubsonicAlbum> = emptyList(),
    val song: List<SubsonicSong> = emptyList()
)

@Serializable
data class SubsonicArtist(
    val id: String,
    val name: String,
    val albumCount: Int? = null,
    val starred: String? = null,
    val coverArt: String? = null,
    val album: List<SubsonicAlbum> = emptyList()
)

@Serializable
data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val created: String? = null,
    val starred: String? = null,
    val song: List<SubsonicSong> = emptyList()
)

@Serializable
data class SubsonicSong(
    val id: String,
    val parent: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val track: Int? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val size: Long? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val coverArt: String? = null,
    val created: String? = null,
    val starred: String? = null,
    val path: String? = null
)

@Serializable
data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val public: Boolean? = null,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null,
    val allowedUser: List<String> = emptyList(),
    val entry: List<SubsonicSong> = emptyList()
)
