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
}
