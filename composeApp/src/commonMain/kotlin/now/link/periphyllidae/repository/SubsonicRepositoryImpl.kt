package now.link.periphyllidae.repository

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import now.link.periphyllidae.models.*
import co.touchlab.kermit.Logger

@Serializable
data class SubsonicResponse<T>(
    val subsonic: SubsonicContainer<T>
)

@Serializable
data class SubsonicContainer<T>(
    val status: String,
    val version: String,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: SubsonicError? = null,
    val data: T? = null
)

@Serializable
data class SubsonicError(
    val code: Int,
    val message: String
)

class SubsonicRepositoryImpl(
    private val httpClient: HttpClient,
    private val json: Json,
    private val logger: Logger
) : SubsonicRepository {
    
    private var currentConfig: ServerConfig? = null
    
    override suspend fun authenticate(serverConfig: ServerConfig): Result<Boolean> {
        return try {
            currentConfig = serverConfig
            
            val response = httpClient.get("${serverConfig.url}/rest/ping") {
                parameter("u", serverConfig.username)
                parameter("p", serverConfig.password)
                parameter("v", "1.16.1")
                parameter("c", "Periphyllidae")
                parameter("f", "json")
            }
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                logger.d("Ping response: $responseBody")
                // Simple check - if we get a valid response, assume success
                Result.success(true)
            } else {
                logger.e("Authentication failed with status: ${response.status}")
                Result.failure(Exception("Authentication failed"))
            }
        } catch (e: Exception) {
            logger.e("Authentication error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getRandomSongs(size: Int): Result<List<Song>> {
        return try {
            val config = currentConfig ?: return Result.failure(Exception("Not authenticated"))
            
            // Return mock data for now since we don't have a real Subsonic server
            val mockSongs = (1..size).map { i ->
                Song(
                    id = "song_$i",
                    title = "Song $i",
                    artist = "Artist $i",
                    album = "Album $i",
                    duration = 180 + i * 10,
                    track = i,
                    year = 2020 + (i % 5),
                    genre = "Rock",
                    coverArt = null,
                    streamUrl = getStreamUrl("song_$i")
                )
            }
            
            logger.i("Generated ${mockSongs.size} mock random songs")
            Result.success(mockSongs)
        } catch (e: Exception) {
            logger.e("Error fetching random songs", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getNewestAlbums(size: Int): Result<List<Album>> {
        return try {
            // Return mock data for now
            val mockAlbums = (1..size).map { i ->
                Album(
                    id = "album_$i",
                    name = "Album $i",
                    artist = "Artist $i",
                    songCount = 10 + (i % 5),
                    duration = 3600 + i * 200,
                    year = 2020 + (i % 5),
                    genre = "Rock",
                    coverArt = null
                )
            }
            
            logger.i("Generated ${mockAlbums.size} mock newest albums")
            Result.success(mockAlbums)
        } catch (e: Exception) {
            logger.e("Error fetching newest albums", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getPlaylists(): Result<List<Playlist>> {
        return try {
            // Return mock data for now
            val mockPlaylists = (1..5).map { i ->
                Playlist(
                    id = "playlist_$i",
                    name = "Playlist $i",
                    comment = "A great playlist",
                    songCount = 20 + i * 5,
                    duration = 4800 + i * 600,
                    public = i % 2 == 0,
                    created = "2024-01-01T00:00:00Z",
                    changed = "2024-01-01T00:00:00Z",
                    coverArt = null
                )
            }
            
            logger.i("Generated ${mockPlaylists.size} mock playlists")
            Result.success(mockPlaylists)
        } catch (e: Exception) {
            logger.e("Error fetching playlists", e)
            Result.failure(e)
        }
    }
    
    override suspend fun search(query: String): Result<SearchResult> {
        return try {
            // Return mock search results
            val mockResult = SearchResult(
                artists = listOf(
                    Artist(
                        id = "artist_1",
                        name = "Artist matching '$query'",
                        albumCount = 5,
                        starred = null,
                        coverArt = null
                    )
                ),
                albums = listOf(
                    Album(
                        id = "album_1",
                        name = "Album matching '$query'",
                        artist = "Some Artist",
                        songCount = 12,
                        duration = 3600,
                        year = 2023,
                        genre = "Rock",
                        coverArt = null
                    )
                ),
                songs = (1..5).map { i ->
                    Song(
                        id = "search_song_$i",
                        title = "Song $i matching '$query'",
                        artist = "Artist $i",
                        album = "Album $i",
                        duration = 180 + i * 10,
                        track = i,
                        year = 2020 + (i % 5),
                        genre = "Rock",
                        coverArt = null,
                        streamUrl = getStreamUrl("search_song_$i")
                    )
                }
            )
            
            logger.i("Generated mock search results for query: $query")
            Result.success(mockResult)
        } catch (e: Exception) {
            logger.e("Error searching", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAlbum(id: String): Result<Album> {
        return try {
            // Return mock album with songs
            val mockAlbum = Album(
                id = id,
                name = "Album $id",
                artist = "Artist Name",
                songCount = 10,
                duration = 3600,
                year = 2023,
                genre = "Rock",
                coverArt = null,
                songs = (1..10).map { i ->
                    Song(
                        id = "${id}_song_$i",
                        title = "Track $i",
                        artist = "Artist Name",
                        album = "Album $id",
                        duration = 180 + i * 10,
                        track = i,
                        year = 2023,
                        genre = "Rock",
                        coverArt = null,
                        streamUrl = getStreamUrl("${id}_song_$i")
                    )
                }
            )
            
            logger.i("Generated mock album: $id")
            Result.success(mockAlbum)
        } catch (e: Exception) {
            logger.e("Error fetching album", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getPlaylist(id: String): Result<Playlist> {
        return try {
            // Return mock playlist with songs
            val mockPlaylist = Playlist(
                id = id,
                name = "Playlist $id",
                comment = "A great playlist",
                songCount = 15,
                duration = 2700,
                public = true,
                created = "2024-01-01T00:00:00Z",
                changed = "2024-01-01T00:00:00Z",
                coverArt = null,
                songs = (1..15).map { i ->
                    Song(
                        id = "${id}_song_$i",
                        title = "Song $i",
                        artist = "Artist $i",
                        album = "Album $i",
                        duration = 180,
                        track = i,
                        year = 2023,
                        genre = "Rock",
                        coverArt = null,
                        streamUrl = getStreamUrl("${id}_song_$i")
                    )
                }
            )
            
            logger.i("Generated mock playlist: $id")
            Result.success(mockPlaylist)
        } catch (e: Exception) {
            logger.e("Error fetching playlist", e)
            Result.failure(e)
        }
    }
    
    override fun getStreamUrl(songId: String): String {
        val config = currentConfig ?: throw IllegalStateException("Not authenticated")
        // Return a mock stream URL - in real implementation this would be a valid Subsonic stream URL
        return "${config.url}/rest/stream?id=$songId&u=${config.username}&p=${config.password}&v=1.16.1&c=Periphyllidae&f=json"
    }
}
