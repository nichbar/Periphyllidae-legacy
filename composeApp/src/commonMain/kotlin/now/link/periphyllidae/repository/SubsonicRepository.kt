package now.link.periphyllidae.repository

import now.link.periphyllidae.models.*

interface SubsonicRepository {
    suspend fun authenticate(serverConfig: ServerConfig): Result<Boolean>
    suspend fun getRandomSongs(size: Int = 20): Result<List<Song>>
    suspend fun getRecentlyPlayedAlbums(size: Int = 20): Result<List<Album>>
    suspend fun getMostPlayedAlbums(size: Int = 20): Result<List<Album>>
    suspend fun getNewestAlbums(size: Int = 20): Result<List<Album>>
    suspend fun getPlaylists(): Result<List<Playlist>>
    suspend fun search(query: String): Result<SearchResult>
    suspend fun getAlbum(id: String): Result<Album>
    suspend fun getPlaylist(id: String): Result<Playlist>
    suspend fun getArtist(id: String): Result<Artist>
    suspend fun getTopSongs(artistId: String, limit: Int = 10): Result<List<Song>>
    suspend fun getAlbumSongs(albumId: String): Result<List<Song>>
    suspend fun scrobbleSong(songId: String)
    fun getStreamUrl(songId: String): String
    fun getCoverArtUrl(coverArtId: String): String
}
