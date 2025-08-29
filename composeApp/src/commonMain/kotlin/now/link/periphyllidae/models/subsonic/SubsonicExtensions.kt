package now.link.periphyllidae.models.subsonic

import now.link.periphyllidae.models.*

fun SubsonicArtist.toDomainModel(): Artist {
    return Artist(
        id = this.id,
        name = this.name,
        albumCount = this.albumCount ?: 0,
        starred = this.starred,
        coverArt = this.coverArt
    )
}

fun SubsonicAlbum.toDomainModel(baseUrl: String): Album {
    return Album(
        id = this.id,
        name = this.name,
        artist = this.artist ?: "Unknown Artist",
        songCount = this.songCount ?: this.song.size,
        duration = this.duration ?: 0,
        year = this.year,
        genre = this.genre,
        coverArt = this.coverArt?.let { getCoverArtUrl(baseUrl, it) },
        songs = this.song.map { it.toDomainModel(baseUrl) }
    )
}

fun SubsonicSong.toDomainModel(baseUrl: String): Song {
    return Song(
        id = this.id,
        title = this.title,
        artist = this.artist ?: "Unknown Artist",
        album = this.album ?: "Unknown Album",
        duration = this.duration,
        track = this.track,
        year = this.year,
        genre = this.genre,
        coverArt = this.coverArt?.let { getCoverArtUrl(baseUrl, it) },
        streamUrl = this.id // We'll set the full URL later when we have auth params
    )
}

fun SubsonicPlaylist.toDomainModel(baseUrl: String): Playlist {
    return Playlist(
        id = this.id,
        name = this.name,
        comment = this.comment,
        songCount = this.songCount ?: this.entry.size,
        duration = this.duration ?: 0,
        public = this.public ?: false,
        created = this.created ?: "",
        changed = this.changed ?: "",
        coverArt = this.coverArt?.let { getCoverArtUrl(baseUrl, it) },
        songs = this.entry.map { song ->
            song.copy(id = song.id).toDomainModel(baseUrl)
        }
    )
}

fun SearchResult3.toDomainModel(baseUrl: String): SearchResult {
    return SearchResult(
        artists = this.artist.map { it.toDomainModel() },
        albums = this.album.map { it.toDomainModel(baseUrl) },
        songs = this.song.map { it.toDomainModel(baseUrl) }
    )
}

private fun getCoverArtUrl(baseUrl: String, coverArtId: String): String {
    // Note: This will need authentication parameters added by the repository
    return "$baseUrl/rest/getCoverArt?id=$coverArtId"
}

// This function is no longer needed since we handle stream URLs in the repository
// private fun getStreamUrl(baseUrl: String, songId: String): String {
//     return "$baseUrl/rest/stream?id=$songId"
// }
