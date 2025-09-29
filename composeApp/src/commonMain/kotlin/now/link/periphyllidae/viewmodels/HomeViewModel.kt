package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.AuthState
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.store.AuthStore
import now.link.periphyllidae.store.MusicStore

/**
 * Page-scoped ViewModel for the Home screen
 * Handles UI-specific logic and interacts with the global MusicStore
 */
class HomeViewModel(
    private val musicStore: MusicStore,
    private val authStore: AuthStore
) : ViewModel() {
    
    val musicState: StateFlow<MusicState> = musicStore.state
    val authState: StateFlow<AuthState> = authStore.state

    fun refreshContent() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.RefreshHomeContent)
        }
    }
    
    fun playAlbum(album: now.link.periphyllidae.models.Album) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlayAlbum(album))
        }
    }
    
    fun playSong(song: now.link.periphyllidae.models.Song) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlaySong(song))
        }
    }
    
    fun playRandomSong() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlayRandomSong)
        }
    }
}
