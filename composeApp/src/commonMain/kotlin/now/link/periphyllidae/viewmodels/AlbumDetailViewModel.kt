package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.AlbumDetailState
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.repository.SubsonicRepositoryManager

/**
 * ViewModel for Album Detail screen
 * Handles album-specific data and interactions with the global MusicStore
 */
class AlbumDetailViewModel(
    private val musicStore: MusicStore,
    private val subsonicRepositoryManager: SubsonicRepositoryManager,
) : ViewModel() {
    
    private val _state = MutableStateFlow(AlbumDetailState())
    val state: StateFlow<AlbumDetailState> = _state.asStateFlow()

    fun loadAlbumDetail(albumId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val albumResult = subsonicRepositoryManager.getCurrentRepository().getAlbum(albumId)
                
                if (albumResult.isSuccess) {
                    _state.value = _state.value.copy(
                        album = albumResult.getOrNull(),
                        isLoading = false,
                        error = null
                    )
                } else {
                    val error = albumResult.exceptionOrNull()?.message ?: "Unknown error"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load album details: $error"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load album details: ${e.message}"
                )
            }
        }
    }
    
    fun playAllSongsOfAlbum() {
        viewModelScope.launch {
            val album = _state.value.album
            if (album != null) {
                musicStore.handleIntent(MusicIntent.PlayAlbum(album))
            }
        }
    }
    
    fun playSong(song: Song) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlaySong(song))
        }
    }
}
