package now.link.periphyllidae.models

import kotlinx.serialization.Serializable

@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val songCount: Int,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val coverArtId: String? = null,
    val copyright: String? = null,
    val songs: List<Song> = emptyList()
)
