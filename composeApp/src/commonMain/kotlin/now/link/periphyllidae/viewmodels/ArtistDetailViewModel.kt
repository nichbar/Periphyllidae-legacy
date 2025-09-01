package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.ArtistDetailIntent
import now.link.periphyllidae.mvi.ArtistDetailState
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Song

/**
 * ViewModel for Artist Detail screen
 * Handles artist-specific data and interactions with the global MusicStore
 */
class ArtistDetailViewModel(
    private val subsonicRepository: SubsonicRepository,
    private val musicStore: MusicStore
) : ViewModel() {
    
    private val _state = MutableStateFlow(ArtistDetailState())
    val state: StateFlow<ArtistDetailState> = _state.asStateFlow()
    
    fun handleIntent(intent: ArtistDetailIntent) {
        when (intent) {
            is ArtistDetailIntent.LoadArtistDetail -> loadArtistDetail(intent.artistId)
            is ArtistDetailIntent.PlayAllSongs -> playAllSongs()
            is ArtistDetailIntent.PlayAlbum -> playAlbum(intent.album)
            is ArtistDetailIntent.PlaySong -> playSong(intent.song)
        }
    }
    
    fun loadArtistDetail(artistId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val artistResult = subsonicRepository.getArtist(artistId)
                val topSongsResult = subsonicRepository.getTopSongs(artistId, limit = 10)
                
                if (artistResult.isSuccess && topSongsResult.isSuccess) {
                    _state.value = _state.value.copy(
                        artist = artistResult.getOrNull(),
                        topSongs = topSongsResult.getOrNull() ?: emptyList(),
                        isLoading = false,
                        error = null
                    )
                } else {
                    val error = artistResult.exceptionOrNull()?.message 
                        ?: topSongsResult.exceptionOrNull()?.message 
                        ?: "Unknown error"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load artist details: $error"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load artist details: ${e.message}"
                )
            }
        }
    }
    
    fun playAllSongs() {
        viewModelScope.launch {
            val artist = _state.value.artist
            if (artist != null) {
                try {
                    // Get all songs from all albums for this artist
                    val allSongs = mutableListOf<Song>()
                    artist.albums.forEach { album ->
                        val albumSongsResult = subsonicRepository.getAlbumSongs(album.id)
                        if (albumSongsResult.isSuccess) {
                            allSongs.addAll(albumSongsResult.getOrNull() ?: emptyList())
                        }
                    }
                    
                    if (allSongs.isNotEmpty()) {
                        musicStore.handleIntent(MusicIntent.SetQueue(allSongs, 0))
                        musicStore.handleIntent(MusicIntent.Play)
                    }
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        error = "Failed to play artist songs: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun playAlbum(album: Album) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlayAlbum(album))
        }
    }
    
    fun playSong(song: Song) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlaySong(song))
        }
    }
}
