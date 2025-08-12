package now.link.periphyllidae.store

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import androidx.lifecycle.viewModelScope
import now.link.periphyllidae.mvi.*
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.platform.MusicPlayer
import co.touchlab.kermit.Logger

class HomeStore(
    private val subsonicRepository: SubsonicRepository,
    private val musicPlayer: MusicPlayer,
    private val logger: Logger
) : ViewModel() {
    
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    
    init {
        handleIntent(HomeIntent.LoadContent)
    }
    
    fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadContent -> {
                loadContent()
            }
            is HomeIntent.Refresh -> {
                loadContent()
            }
            is HomeIntent.PlaySong -> {
                musicPlayer.play(intent.song)
            }
            is HomeIntent.PlayAlbum -> {
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
    
    private fun loadContent() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                // Load content in parallel
                val randomSongsDeferred = async { subsonicRepository.getRandomSongs(20) }
                val newestAlbumsDeferred = async { subsonicRepository.getNewestAlbums(20) }
                val playlistsDeferred = async { subsonicRepository.getPlaylists() }
                
                val randomSongsResult = randomSongsDeferred.await()
                val newestAlbumsResult = newestAlbumsDeferred.await()
                val playlistsResult = playlistsDeferred.await()
                
                val randomSongs = randomSongsResult.getOrNull() ?: emptyList()
                val newestAlbums = newestAlbumsResult.getOrNull() ?: emptyList()
                val playlists = playlistsResult.getOrNull() ?: emptyList()
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    recentAlbums = newestAlbums,
                    randomSongs = randomSongs,
                    playlists = playlists,
                    error = null
                )
                
                logger.i("Home content loaded successfully")
            } catch (e: Exception) {
                logger.e("Error loading home content", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load content. Please try again."
                )
            }
        }
    }
}
