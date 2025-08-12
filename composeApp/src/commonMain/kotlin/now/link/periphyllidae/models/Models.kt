package now.link.periphyllidae.models

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val url: String,
    val username: String,
    val password: String,
    val name: String? = null
)

@Serializable
data class SearchResult(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList()
)
