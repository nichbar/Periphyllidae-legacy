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
import co.touchlab.kermit.Logger

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
    val playlists: PlaylistList? = null,
    val searchResult3: SearchResult3? = null,
    val album: SubsonicAlbum? = null,
    val playlist: SubsonicPlaylist? = null
)

class SubsonicRepositoryImpl(
    private val httpClient: HttpClient,
    private val json: Json,
    private val logger: Logger,
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
    
    private fun loadSavedConfiguration() {
        try {
            currentConfig = settingsRepository.getServerConfig()
            if (currentConfig != null) {
                logger.i("Loaded saved server configuration: ${currentConfig?.url}")
                // Note: We'll need to re-authenticate to get fresh tokens
                // The auth tokens (salt/token) are not persisted for security reasons
            }
        } catch (e: Exception) {
            logger.e("Failed to load saved configuration", e)
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
        
        logger.d("Regenerated auth tokens for saved configuration")
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
                logger.d("Ping response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok") {
                    logger.i("Authentication successful")
                    Result.success(true)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Authentication failed"
                    logger.e("Authentication failed: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                logger.e("Authentication failed with status: ${response.status}")
                Result.failure(Exception("Authentication failed with status: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.e("Authentication error", e)
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
                parameter("size", size.toString())
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                logger.d("Random songs response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.randomSongs != null) {
                    val songs = subsonicResponse.randomSongs.song.map { song ->
                        song.toDomainModel(this)
                    }
                    logger.i("Fetched ${songs.size} random songs")
                    Result.success(songs)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch random songs"
                    logger.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                logger.e("Failed to fetch random songs with status: ${response.status}")
                Result.failure(Exception("Failed to fetch random songs"))
            }
        } catch (e: Exception) {
            logger.e("Error fetching random songs", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getNewestAlbums(size: Int): Result<List<Album>> {
        return try {
            val config = currentConfig ?: return Result.failure(Exception("Not authenticated"))
            val authParams = getAuthParams()
            
            val response = httpClient.get("${config.url}/rest/getNewestAlbums") {
                authParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                parameter("size", size.toString())
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                logger.d("Newest albums response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.newestAlbums != null) {
                    val albums = subsonicResponse.newestAlbums.album.map { it.toDomainModel(this) }
                    logger.i("Fetched ${albums.size} newest albums")
                    Result.success(albums)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch newest albums"
                    logger.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                logger.e("Failed to fetch newest albums with status: ${response.status}")
                Result.failure(Exception("Failed to fetch newest albums"))
            }
        } catch (e: Exception) {
            logger.e("Error fetching newest albums", e)
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
                logger.d("Playlists response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.playlists != null) {
                    val playlists = subsonicResponse.playlists.playlist.map { it.toDomainModel(this) }
                    logger.i("Fetched ${playlists.size} playlists")
                    Result.success(playlists)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch playlists"
                    logger.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                logger.e("Failed to fetch playlists with status: ${response.status}")
                Result.failure(Exception("Failed to fetch playlists"))
            }
        } catch (e: Exception) {
            logger.e("Error fetching playlists", e)
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
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                logger.d("Search response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.searchResult3 != null) {
                    val searchResult = subsonicResponse.searchResult3.toDomainModel(this)
                    logger.i("Search completed for query: $query")
                    Result.success(searchResult)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Search failed"
                    logger.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                logger.e("Search failed with status: ${response.status}")
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            logger.e("Error searching", e)
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
                logger.d("Album response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.album != null) {
                    val album = subsonicResponse.album.toDomainModel(this)
                    logger.i("Fetched album: $id")
                    Result.success(album)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch album"
                    logger.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                logger.e("Failed to fetch album with status: ${response.status}")
                Result.failure(Exception("Failed to fetch album"))
            }
        } catch (e: Exception) {
            logger.e("Error fetching album", e)
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
                logger.d("Playlist response: $responseBody")
                
                val jsonResponse = json.decodeFromString<SubsonicJsonResponse>(responseBody)
                val subsonicResponse = jsonResponse.subsonicResponse
                
                if (subsonicResponse.status == "ok" && subsonicResponse.playlist != null) {
                    val playlist = subsonicResponse.playlist.toDomainModel(this)
                    logger.i("Fetched playlist: $id")
                    Result.success(playlist)
                } else {
                    val errorMsg = subsonicResponse.error?.message ?: "Failed to fetch playlist"
                    logger.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                logger.e("Failed to fetch playlist with status: ${response.status}")
                Result.failure(Exception("Failed to fetch playlist"))
            }
        } catch (e: Exception) {
            logger.e("Error fetching playlist", e)
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
