package now.link.periphyllidae.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import now.link.periphyllidae.models.subsonic.*

// JSON Response wrapper for Subsonic API
@Serializable
data class SubsonicJsonResponse(
    @SerialName("subsonic-response") val subsonicResponse: SubsonicJsonContainer
)

@Serializable
data class SubsonicJsonContainer(
    val status: String,
    val version: String,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: SubsonicError? = null,
    val randomSongs: RandomSongs? = null,
    val newestAlbums: AlbumList? = null,
    val albumList: AlbumList? = null,  // Add albumList for getAlbumList endpoint
    val playlists: PlaylistList? = null,
    val searchResult3: SearchResult3? = null,
    val album: SubsonicAlbum? = null,
    val playlist: SubsonicPlaylist? = null,
    val artist: SubsonicArtist? = null
)
