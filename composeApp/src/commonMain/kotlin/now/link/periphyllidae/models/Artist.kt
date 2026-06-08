package now.link.periphyllidae.models

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val starred: String? = null,
    val coverArt: String? = null,
    val coverArtId: String? = null,
    val albums: List<Album> = emptyList()
)
