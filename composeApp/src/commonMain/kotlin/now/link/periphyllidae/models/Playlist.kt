package now.link.periphyllidae.models

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val songCount: Int,
    val duration: Int,
    val public: Boolean = false,
    val created: String,
    val changed: String,
    val coverArt: String? = null,
    val songs: List<Song> = emptyList()
)
