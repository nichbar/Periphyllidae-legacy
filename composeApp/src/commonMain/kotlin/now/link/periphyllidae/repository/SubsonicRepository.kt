package now.link.periphyllidae.repository

import now.link.periphyllidae.models.*

interface SubsonicRepository {
    suspend fun authenticate(serverConfig: ServerConfig): Result<Boolean>
    suspend fun getRandomSongs(size: Int = 20): Result<List<Song>>
    suspend fun getNewestAlbums(size: Int = 20): Result<List<Album>>
    suspend fun getPlaylists(): Result<List<Playlist>>
    suspend fun search(query: String): Result<SearchResult>
    suspend fun getAlbum(id: String): Result<Album>
    suspend fun getPlaylist(id: String): Result<Playlist>
    fun getStreamUrl(songId: String): String
}
