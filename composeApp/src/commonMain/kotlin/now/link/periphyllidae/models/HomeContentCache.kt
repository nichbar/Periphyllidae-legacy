package now.link.periphyllidae.models

import kotlinx.serialization.Serializable

@Serializable
data class HomeContentCache(
    val randomSongs: List<Song>,
    val playlists: List<Playlist>,
    val mostPlayedAlbums: List<Album>,
    val recentlyPlayedAlbums: List<Album>,
    val newestAlbums: List<Album>,
    val timestamp: Long
)
