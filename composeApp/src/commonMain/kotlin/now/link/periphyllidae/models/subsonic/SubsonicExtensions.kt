package now.link.periphyllidae.models.subsonic

import now.link.periphyllidae.models.*
import now.link.periphyllidae.repository.SubsonicRepository

fun SubsonicArtist.toDomainModel(): Artist {
    return Artist(
        id = this.id,
        name = this.name,
        albumCount = this.albumCount ?: 0,
        starred = this.starred,
        coverArt = this.coverArt
    )
}

fun SubsonicAlbum.toDomainModel(repository: SubsonicRepository): Album {
    return Album(
        id = this.id,
        name = this.name,
        artist = this.artist ?: "Unknown Artist",
        songCount = this.songCount ?: this.song.size,
        duration = this.duration ?: 0,
        year = this.year,
        genre = this.genre,
        coverArt = this.coverArt?.let { repository.getCoverArtUrl(it) },
        songs = this.song.map { it.toDomainModel(repository) }
    )
}

fun SubsonicSong.toDomainModel(repository: SubsonicRepository): Song {
    return Song(
        id = this.id,
        title = this.title,
        artist = this.artist ?: "Unknown Artist",
        album = this.album ?: "Unknown Album",
        duration = this.duration,
        track = this.track,
        year = this.year,
        genre = this.genre,
        coverArt = this.coverArt?.let { repository.getCoverArtUrl(it) },
        streamUrl = repository.getStreamUrl(this.id)
    )
}

fun SubsonicPlaylist.toDomainModel(repository: SubsonicRepository): Playlist {
    val songs = this.entry?.map { song ->
        // Create a SubsonicSong from the entry
        SubsonicSong(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            track = song.track,
            year = song.year,
            genre = song.genre,
            coverArt = song.coverArt
        ).toDomainModel(repository)
    } ?: emptyList()

    return Playlist(
        id = this.id,
        name = this.name,
        songCount = this.songCount ?: songs.size,
        duration = this.duration ?: 0,
        public = this.public ?: false,
        created = this.created ?: "",
        changed = this.changed ?: "",
        coverArt = this.coverArt?.let { repository.getCoverArtUrl(it) },
        songs = songs
    )
}

fun SearchResult3.toDomainModel(repository: SubsonicRepository): SearchResult {
    return SearchResult(
        artists = this.artist.map { it.toDomainModel() },
        albums = this.album.map { it.toDomainModel(repository) },
        songs = this.song.map { it.toDomainModel(repository) }
    )
}
