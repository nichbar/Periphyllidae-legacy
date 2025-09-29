package now.link.periphyllidae.repository

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json
import now.link.periphyllidae.models.*
import now.link.periphyllidae.models.subsonic.*
import now.link.periphyllidae.utils.CryptoUtils
import now.link.periphyllidae.utils.LogManager

/**
 * Server-specific implementation of SubsonicRepository
 * Each instance is immutable and configured for a single server
 * Thread-safe and stateless (except for immutable server config)
 */
class ServerSpecificSubsonicRepository(
    private val serverConfig: ServerConfig,
    private val httpClient: HttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository
) : SubsonicRepository {
    
    companion object {
        private const val TAG = "SubsonicRepository"
    }
    
    // Pre-computed auth parameters - immutable and thread-safe
    private val authParams: Map<String, String> by lazy { generateAuthParams() }
    
    private fun generateAuthParams(): Map<String, String> {
        // Generate a server-specific key for salt storage
        val serverKey = CryptoUtils.md5("${serverConfig.url}:${serverConfig.username}")
        
        // Try to get existing fixed salt for this server, or generate and save a new one
        val salt = settingsRepository.getServerSalt(serverKey) ?: run {
            val newSalt = CryptoUtils.generateFixedSalt(serverConfig.url, serverConfig.username)
            settingsRepository.saveServerSalt(serverKey, newSalt)
            newSalt
        }
        
        val token = CryptoUtils.md5(serverConfig.password + salt)

        return mapOf(
            "u" to serverConfig.username,
            "t" to token,
            "s" to salt,
            "v" to "1.16.1",
            "c" to "Periphyllidae",
            "f" to "json"
        )
    }

    override suspend fun authenticate(serverConfig: ServerConfig): Result<Boolean> {
        // Note: This method now validates the provided config against our configured server
        if (serverConfig.url != this.serverConfig.url || serverConfig.username != this.serverConfig.username) {
            return Result.failure(Exception("Server config mismatch"))
        }

        val tempAuthParams = generateAuthParams()

        return try {
            val response = httpClient.get("${serverConfig.url}/rest/ping") {
                tempAuthParams.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                LogManager.d(TAG, "Ping response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok") {
                    LogManager.i(TAG, "Authentication successful for ${serverConfig.url}")
                    Result.success(true)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Authentication failed"
                    LogManager.e(TAG, "Authentication failed: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e(TAG, "Authentication failed with status: ${response.status}")
                Result.failure(Exception("Authentication failed with status: ${response.status}"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Authentication error", e)
            Result.failure(e)
        }
    }

    override suspend fun scrobbleSong(songId: String) {
        try {
            val response = httpClient.get("${serverConfig.url}/rest/scrobble") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("id", songId)
                parameter("time", System.currentTimeMillis())
            }

            if (response.status.isSuccess()) {
                LogManager.i(TAG, "Scrobbled song $songId successfully")
            } else {
                LogManager.w(TAG, "Failed to scrobble song $songId: ${response.status}")
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error scrobbling song $songId: $e")
        }
    }
    
    override suspend fun getRandomSongs(size: Int): Result<List<Song>> {
        return try {
            val response = httpClient.get("${serverConfig.url}/rest/getRandomSongs") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                // Ensure size is within reasonable limits (some servers have max limits)
                parameter("size", size.coerceAtMost(500).toString())
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.randomSongs != null) {
                    val songs = subsonicResponse.randomSongs.song.map { song ->
                        song.toDomainModel(this)
                    }
                    LogManager.i(TAG, "Fetched ${songs.size} random songs")
                    Result.success(songs)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch random songs"
                    LogManager.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e(TAG, "Failed to fetch random songs with status: ${response.status}")
                Result.failure(Exception("Failed to fetch random songs"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error fetching random songs", e)
            Result.failure(e)
        }
    }

    override suspend fun getMostPlayedAlbums(size: Int): Result<List<Album>> {
        return getAlbumList("frequent", size)
    }

    override suspend fun getRecentlyPlayedAlbums(size: Int): Result<List<Album>> {
        return getAlbumList("recent", size)
    }
    
    override suspend fun getNewestAlbums(size: Int): Result<List<Album>> {
        return getAlbumList("newest", size)
    }

    private suspend fun getAlbumList(type: String, size: Int): Result<List<Album>> {
        return try {
            val response = httpClient.get("${serverConfig.url}/rest/getAlbumList") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("type", type)
                parameter("size", size.toString())
            }

            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()

                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse

                if (subsonicResponse.status == "ok" && subsonicResponse.albumList != null) {
                    val albums = subsonicResponse.albumList.album.map { it.toDomainModel(this) }
                    LogManager.i(TAG, "Fetched ${albums.size} albums for type: $type")
                    Result.success(albums)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch albums"
                    LogManager.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e(TAG, "Failed to fetch albums with status: ${response.status}")
                Result.failure(Exception("Failed to fetch albums"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error fetching albums", e)
            Result.failure(e)
        }
    }

    override suspend fun getPlaylists(): Result<List<Playlist>> {
        return try {
            val response = httpClient.get("${serverConfig.url}/rest/getPlaylists") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok") {
                    // Handle case where playlists might be null or empty
                    val playlists = subsonicResponse.playlists?.playlist?.map { it.toDomainModel(this) } ?: emptyList()
                    LogManager.i(TAG, "Fetched ${playlists.size} playlists")
                    Result.success(playlists)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch playlists"
                    LogManager.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e(TAG, "Failed to fetch playlists with status: ${response.status}")
                Result.failure(Exception("Failed to fetch playlists"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error fetching playlists", e)
            Result.failure(e)
        }
    }
    
    override suspend fun search(query: String): Result<SearchResult> {
        return try {
            val response = httpClient.get("${serverConfig.url}/rest/search3") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("query", query)
                parameter("artistCount", "10")
                parameter("albumCount", "20") 
                parameter("songCount", "30")
                // Add offset parameter if supported
                parameter("artistOffset", "0")
                parameter("albumOffset", "0")
                parameter("songOffset", "0")
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.searchResult3 != null) {
                    val searchResult = subsonicResponse.searchResult3.toDomainModel(this)
                    LogManager.i(TAG, "Search completed for query: $query")
                    Result.success(searchResult)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Search failed"
                    LogManager.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e(TAG, "Search failed with status: ${response.status}")
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error searching", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAlbum(id: String): Result<Album> {
        return try {
            val response = httpClient.get("${serverConfig.url}/rest/getAlbum") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("id", id)
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.album != null) {
                    val album = subsonicResponse.album.toDomainModel(this)
                    LogManager.i(TAG, "Fetched album: $id")
                    Result.success(album)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch album"
                    LogManager.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e(TAG, "Failed to fetch album with status: ${response.status}")
                Result.failure(Exception("Failed to fetch album"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error fetching album", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getPlaylist(id: String): Result<Playlist> {
        return try {
            val response = httpClient.get("${serverConfig.url}/rest/getPlaylist") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("id", id)
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.playlist != null) {
                    val playlist = subsonicResponse.playlist.toDomainModel(this)
                    LogManager.i(TAG, "Fetched playlist: $id")
                    Result.success(playlist)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch playlist"
                    LogManager.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e(TAG, "Failed to fetch playlist with status: ${response.status}")
                Result.failure(Exception("Failed to fetch playlist"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error fetching playlist", e)
            Result.failure(e)
        }
    }
    
    override fun getStreamUrl(songId: String): String {
        val urlBuilder = URLBuilder("${serverConfig.url}/rest/stream")
        authParams.forEach { (key, value) ->
            urlBuilder.parameters.append(key, value)
        }
        urlBuilder.parameters.append("id", songId)
        
        return urlBuilder.buildString()
    }
    
    override fun getCoverArtUrl(coverArtId: String): String {
        val urlBuilder = URLBuilder("${serverConfig.url}/rest/getCoverArt")
        authParams.forEach { (key, value) ->
            urlBuilder.parameters.append(key, value)
        }
        urlBuilder.parameters.append("id", coverArtId)
        
        return urlBuilder.buildString()
    }
    
    override suspend fun getArtist(id: String): Result<Artist> {
        return try {
            val response = httpClient.get("${serverConfig.url}/rest/getArtist") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("id", id)
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.artist != null) {
                    val artist = subsonicResponse.artist.toDomainModel(this)
                    LogManager.i(TAG, "Fetched artist: $id")
                    Result.success(artist)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch artist"
                    LogManager.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e(TAG, "Failed to fetch artist with status: ${response.status}")
                Result.failure(Exception("Failed to fetch artist"))
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error fetching artist", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getTopSongs(artistId: String, count: Int): Result<List<Song>> {
        // TODO: Implement actual API call
        return Result.success(emptyList())
    }
    
    override suspend fun getAlbumSongs(albumId: String): Result<List<Song>> {
        return getAlbum(albumId).map { album -> album.songs }
    }

    override fun refreshConfig() {
        // In the new architecture, each repository instance is immutable
        // So this method is no longer needed - server changes create new instances
        LogManager.d(TAG, "Repository is server-specific and immutable - no config to refresh")
    }
}
