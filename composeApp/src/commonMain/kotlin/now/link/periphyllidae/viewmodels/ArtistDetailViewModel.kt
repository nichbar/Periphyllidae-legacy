package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.ArtistDetailState
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.repository.SubsonicRepositoryManager

/**
 * ViewModel for Artist Detail screen
 * Handles artist-specific data and interactions with the global MusicStore
 */
class ArtistDetailViewModel(
    private val musicStore: MusicStore,
    private val subsonicRepositoryManager: SubsonicRepositoryManager,
) : ViewModel() {
    
    private val _state = MutableStateFlow(ArtistDetailState())
    val state: StateFlow<ArtistDetailState> = _state.asStateFlow()

    fun loadArtistDetail(artistId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val artistResult = subsonicRepositoryManager.getCurrentRepository().getArtist(artistId)
                val topSongsResult = subsonicRepositoryManager.getCurrentRepository().getTopSongs(artistId, limit = 10)
                
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
    
    fun playAllSongsOfArtist() {
        viewModelScope.launch {
            val artist = _state.value.artist
            if (artist != null) {
                musicStore.handleIntent(MusicIntent.PlayAllSongsOfArtist(artist))
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
