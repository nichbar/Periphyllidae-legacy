package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.store.MusicStore

/**
 * Page-scoped ViewModel for the Now Playing screen
 * Handles now playing UI logic and controls
 */
class NowPlayingViewModel(
    private val musicStore: MusicStore
) : ViewModel() {
    
    val state: StateFlow<MusicState> = musicStore.state
    
    fun handleIntent(intent: MusicIntent) {
        musicStore.handleIntent(intent)
    }
    
    fun togglePlayPause() {
        viewModelScope.launch {
            val currentState = musicStore.state.value
            if (currentState.isPlaying) {
                musicStore.handleIntent(MusicIntent.Pause)
            } else {
                musicStore.handleIntent(MusicIntent.Play)
            }
        }
    }
    
    fun seekTo(position: Long) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.SeekTo(position))
        }
    }
    
    fun skipNext() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.Next)
        }
    }
    
    fun skipPrevious() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.Previous)
        }
    }
    
    fun toggleRepeat() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.ToggleRepeat)
        }
    }
    
    fun toggleShuffle() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.ToggleShuffle)
        }
    }

    fun getArtistIdForCurrentSong(callback: (String?) -> Unit) {
        viewModelScope.launch {
            val currentSong = musicStore.state.value.currentSong
            if (currentSong != null) {
                // Check if we already have artistId in current song
                if (currentSong.artistId != null) {
                    callback(currentSong.artistId)
                    return@launch
                }

                // If no artistId, fetch song details
                musicStore.handleIntent(MusicIntent.GetSongDetails(currentSong.id))

                // Wait a bit for the details to be fetched and try again
                kotlinx.coroutines.delay(500)
                val updatedSong = musicStore.state.value.currentSong
                callback(updatedSong?.artistId)
            } else {
                callback(null)
            }
        }
    }

    fun getAlbumIdForCurrentSong(callback: (String?) -> Unit) {
        viewModelScope.launch {
            val currentSong = musicStore.state.value.currentSong
            if (currentSong != null) {
                // Check if we already have albumId in current song
                if (currentSong.albumId != null) {
                    callback(currentSong.albumId)
                    return@launch
                }

                // If no albumId, fetch song details
                musicStore.handleIntent(MusicIntent.GetSongDetails(currentSong.id))

                // Wait a bit for the details to be fetched and try again
                kotlinx.coroutines.delay(500)
                val updatedSong = musicStore.state.value.currentSong
                callback(updatedSong?.albumId)
            } else {
                callback(null)
            }
        }
    }
}
