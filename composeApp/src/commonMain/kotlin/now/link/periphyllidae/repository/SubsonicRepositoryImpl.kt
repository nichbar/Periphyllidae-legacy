package now.link.periphyllidae.repository

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.URLBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import now.link.periphyllidae.models.*
import now.link.periphyllidae.models.subsonic.*
import now.link.periphyllidae.utils.CryptoUtils
import now.link.periphyllidae.utils.generateFixedSalt
import now.link.periphyllidae.utils.LogManager

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
    val playlist: SubsonicPlaylist? = null
)

class SubsonicRepositoryImpl(
    private val httpClient: HttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository
) : SubsonicRepository {
    
    private var currentConfig: ServerConfig? = null
    private var authSalt: String = ""
    private var authToken: String = ""
    private var cachedAuthParams: Map<String, String>? = null
    
    init {
        // Load saved server configuration on initialization
        loadSavedConfiguration()
    }

    override suspend fun scrobbleSong(songId: String) {
        val config = currentConfig ?: throw IllegalStateException("No server config")
        val authParams = getAuthParams()

        try {
            val response = httpClient.get("${config.url}/rest/scrobble") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("id", songId)
                parameter("time", System.currentTimeMillis())
            }

            if (response.status.isSuccess()) {
                LogManager.i("SubsonicRepository", "Scrobbled song $songId successfully")
            } else {
                LogManager.w("SubsonicRepository", "Failed to scrobble song $songId: ${response.status}")
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Error scrobbling song $songId: $e")
        }
    }
    
    private fun loadSavedConfiguration() {
        try {
            currentConfig = settingsRepository.getServerConfig()
            if (currentConfig != null) {
                LogManager.i("SubsonicRepository", "Loaded saved server configuration: ${currentConfig?.url}")
                // Note: We'll need to re-authenticate to get fresh tokens
                // The auth tokens (salt/token) are not persisted for security reasons
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Failed to load saved configuration", e)
        }
    }
    
    private fun generateAuthParams(config: ServerConfig): Map<String, String> {
        // Generate a server-specific key for salt storage
        val serverKey = CryptoUtils.md5("${config.url}:${config.username}")
        
        // Try to get existing fixed salt for this server, or generate and save a new one
        val salt = settingsRepository.getServerSalt(serverKey) ?: run {
            val newSalt = generateFixedSalt(config.url, config.username)
            settingsRepository.saveServerSalt(serverKey, newSalt)
            newSalt
        }
        
        val token = CryptoUtils.md5(config.password + salt)
        
        authSalt = salt
        authToken = token
        
        // Invalidate cached auth params since tokens changed
        cachedAuthParams = null
        
        val params = mapOf(
            "u" to config.username,
            "t" to token,
            "s" to salt,
            "v" to "1.16.1",
            "c" to "Periphyllidae",
            "f" to "json"
        )
        
        // Cache the new params
        cachedAuthParams = params
        return params
    }
    
    private fun getAuthParams(): Map<String, String> {
        val config = currentConfig ?: throw IllegalStateException("Not authenticated")
        
        // If we don't have auth tokens, try to re-authenticate with saved config
        if (authToken.isEmpty() || authSalt.isEmpty()) {
            regenerateAuthTokens(config)
        }
        
        // Return cached params if available, otherwise create and cache them
        return cachedAuthParams ?: run {
            val params = mapOf(
                "u" to config.username,
                "t" to authToken,
                "s" to authSalt,
                "v" to "1.16.1",
                "c" to "Periphyllidae",
                "f" to "json"
            )
            cachedAuthParams = params
            params
        }
    }
    
    private fun regenerateAuthTokens(config: ServerConfig) {
        // Generate a server-specific key for salt storage
        val serverKey = CryptoUtils.md5("${config.url}:${config.username}")
        
        // Try to get existing fixed salt for this server, or generate and save a new one
        val salt = settingsRepository.getServerSalt(serverKey) ?: run {
            val newSalt = generateFixedSalt(config.url, config.username)
            settingsRepository.saveServerSalt(serverKey, newSalt)
            newSalt
        }
        
        val token = CryptoUtils.md5(config.password + salt)
        
        authSalt = salt
        authToken = token
        
        // Invalidate cached auth params since tokens changed
        cachedAuthParams = null
        
        LogManager.d("SubsonicRepository", "Regenerated auth tokens for saved configuration")
    }
    
    override suspend fun authenticate(serverConfig: ServerConfig): Result<Boolean> {
        return try {
            currentConfig = serverConfig
            val authParams = generateAuthParams(serverConfig)
            
            val response = httpClient.get("${serverConfig.url}/rest/ping") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                LogManager.d("SubsonicRepository", "Ping response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok") {
                    LogManager.i("SubsonicRepository", "Authentication successful")
                    Result.success(true)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Authentication failed"
                    LogManager.e("SubsonicRepository", "Authentication failed: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e("SubsonicRepository", "Authentication failed with status: ${response.status}")
                Result.failure(Exception("Authentication failed with status: ${response.status}"))
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Authentication error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getRandomSongs(size: Int): Result<List<Song>> {
        return try {
            val config = currentConfig ?: return Result.failure(Exception("Not authenticated"))
            val authParams = getAuthParams()
            
            val response = httpClient.get("${config.url}/rest/getRandomSongs") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                // Ensure size is within reasonable limits (some servers have max limits)
                parameter("size", size.coerceAtMost(500).toString())
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
//                LogManager.d("SubsonicRepository", "Random songs response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.randomSongs != null) {
                    val songs = subsonicResponse.randomSongs.song.map { song ->
                        song.toDomainModel(this)
                    }
                    LogManager.i("SubsonicRepository", "Fetched ${songs.size} random songs")
                    Result.success(songs)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch random songs"
                    LogManager.e("SubsonicRepository", errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e("SubsonicRepository", "Failed to fetch random songs with status: ${response.status}")
                Result.failure(Exception("Failed to fetch random songs"))
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Error fetching random songs", e)
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
            val config = currentConfig ?: return Result.failure(Exception("Not authenticated"))
            val authParams = getAuthParams()

            val response = httpClient.get("${config.url}/rest/getAlbumList") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("type", type)
                parameter("size", size.toString())
            }

            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
//                LogManager.d("SubsonicRepository", "Album list ($type) response: $responseBody")

                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse

                if (subsonicResponse.status == "ok" && subsonicResponse.albumList != null) {
                    val albums = subsonicResponse.albumList.album.map { it.toDomainModel(this) }
                    LogManager.i("SubsonicRepository", "Fetched ${albums.size} albums for type: $type")
                    Result.success(albums)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch albums"
                    LogManager.e("SubsonicRepository", errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e("SubsonicRepository", "Failed to fetch albums with status: ${response.status}")
                Result.failure(Exception("Failed to fetch albums"))
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Error fetching albums", e)
            Result.failure(e)
        }
    }

    override suspend fun getPlaylists(): Result<List<Playlist>> {
        return try {
            val config = currentConfig ?: return Result.failure(Exception("Not authenticated"))
            val authParams = getAuthParams()
            
            val response = httpClient.get("${config.url}/rest/getPlaylists") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
//                LogManager.d("SubsonicRepository", "Playlists response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok") {
                    // Handle case where playlists might be null or empty
                    val playlists = subsonicResponse.playlists?.playlist?.map { it.toDomainModel(this) } ?: emptyList()
                    LogManager.i("SubsonicRepository", "Fetched ${playlists.size} playlists")
                    Result.success(playlists)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch playlists"
                    LogManager.e("SubsonicRepository", errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e("SubsonicRepository", "Failed to fetch playlists with status: ${response.status}")
                Result.failure(Exception("Failed to fetch playlists"))
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Error fetching playlists", e)
            Result.failure(e)
        }
    }
    
    override suspend fun search(query: String): Result<SearchResult> {
        return try {
            val config = currentConfig ?: return Result.failure(Exception("Not authenticated"))
            val authParams = getAuthParams()
            
            val response = httpClient.get("${config.url}/rest/search3") {
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
//                LogManager.d("SubsonicRepository", "Search response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.searchResult3 != null) {
                    val searchResult = subsonicResponse.searchResult3.toDomainModel(this)
                    LogManager.i("SubsonicRepository", "Search completed for query: $query")
                    Result.success(searchResult)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Search failed"
                    LogManager.e("SubsonicRepository", errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e("SubsonicRepository", "Search failed with status: ${response.status}")
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Error searching", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAlbum(id: String): Result<Album> {
        return try {
            val config = currentConfig ?: return Result.failure(Exception("Not authenticated"))
            val authParams = getAuthParams()
            
            val response = httpClient.get("${config.url}/rest/getAlbum") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("id", id)
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
//                LogManager.d("SubsonicRepository", "Album response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.album != null) {
                    val album = subsonicResponse.album.toDomainModel(this)
                    LogManager.i("SubsonicRepository", "Fetched album: $id")
                    Result.success(album)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch album"
                    LogManager.e("SubsonicRepository", errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e("SubsonicRepository", "Failed to fetch album with status: ${response.status}")
                Result.failure(Exception("Failed to fetch album"))
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Error fetching album", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getPlaylist(id: String): Result<Playlist> {
        return try {
            val config = currentConfig ?: return Result.failure(Exception("Not authenticated"))
            val authParams = getAuthParams()
            
            val response = httpClient.get("${config.url}/rest/getPlaylist") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("id", id)
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
//                LogManager.d("SubsonicRepository", "Playlist response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.playlist != null) {
                    val playlist = subsonicResponse.playlist.toDomainModel(this)
                    LogManager.i("SubsonicRepository", "Fetched playlist: $id")
                    Result.success(playlist)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch playlist"
                    LogManager.e("SubsonicRepository", errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                LogManager.e("SubsonicRepository", "Failed to fetch playlist with status: ${response.status}")
                Result.failure(Exception("Failed to fetch playlist"))
            }
        } catch (e: Exception) {
            LogManager.e("SubsonicRepository", "Error fetching playlist", e)
            Result.failure(e)
        }
    }
    
    override fun getStreamUrl(songId: String): String {
        val config = currentConfig ?: throw IllegalStateException("Not authenticated")
        val authParams = getAuthParams()
        
        val urlBuilder = URLBuilder("${config.url}/rest/stream")
        authParams.forEach { (key, value) ->
            urlBuilder.parameters.append(key, value)
        }
        urlBuilder.parameters.append("id", songId)
        
        return urlBuilder.buildString()
    }
    
    override fun getCoverArtUrl(coverArtId: String): String {
        val config = currentConfig ?: throw IllegalStateException("Not authenticated")
        val authParams = getAuthParams()
        
        val urlBuilder = URLBuilder("${config.url}/rest/getCoverArt")
        authParams.forEach { (key, value) ->
            urlBuilder.parameters.append(key, value)
        }
        urlBuilder.parameters.append("id", coverArtId)
        
        return urlBuilder.buildString()
    }
    
    override suspend fun getArtist(artistId: String): Result<Artist> {
        // TODO: Implement actual API call
        return Result.success(
            Artist(
                id = artistId,
                name = "Unknown Artist",
                albumCount = 0,
                starred = null,
                coverArt = null
            )
        )
    }
    
    override suspend fun getTopSongs(artistId: String, count: Int): Result<List<Song>> {
        // TODO: Implement actual API call
        return Result.success(emptyList())
    }
    
    override suspend fun getAlbumSongs(albumId: String): Result<List<Song>> {
        return getAlbum(albumId).map { album -> album.songs }
    }
}
