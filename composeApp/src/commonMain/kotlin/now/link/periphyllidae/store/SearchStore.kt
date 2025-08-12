package now.link.periphyllidae.store

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import now.link.periphyllidae.mvi.*
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.platform.MusicPlayer
import co.touchlab.kermit.Logger

class SearchStore(
    private val subsonicRepository: SubsonicRepository,
    private val musicPlayer: MusicPlayer,
    private val logger: Logger
) : ViewModel() {
    
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    
    fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> {
                _state.value = _state.value.copy(query = intent.query)
            }
            is SearchIntent.Search -> {
                search(intent.query)
            }
            is SearchIntent.ClearResults -> {
                _state.value = _state.value.copy(
                    query = "",
                    results = null,
                    error = null
                )
            }
            is SearchIntent.PlaySong -> {
                musicPlayer.play(intent.song)
            }
            is SearchIntent.PlayAlbum -> {
                viewModelScope.launch {
                    try {
                        val albumResult = subsonicRepository.getAlbum(intent.album.id)
                        if (albumResult.isSuccess) {
                            val albumWithSongs = albumResult.getOrThrow()
                            if (albumWithSongs.songs.isNotEmpty()) {
                                musicPlayer.setQueue(albumWithSongs.songs, 0)
                                musicPlayer.resume()
                            }
                        }
                    } catch (e: Exception) {
                        logger.e("Error playing album", e)
                    }
                }
            }
        }
    }
    
    private fun search(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(results = null)
            return
        }
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val result = subsonicRepository.search(query.trim())
                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        results = result.getOrThrow(),
                        error = null
                    )
                    logger.i("Search completed for query: $query")
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Search failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                logger.e("Search error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Search failed. Please check your connection."
                )
            }
        }
    }
}
