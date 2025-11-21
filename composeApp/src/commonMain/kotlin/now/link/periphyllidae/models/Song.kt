package now.link.periphyllidae.models

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artistId: String? = null,
    val albumId: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val streamUrl: String
)
