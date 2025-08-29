package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.store.MusicStore

/**
 * Page-scoped ViewModel for the Home screen
 * Handles UI-specific logic and interacts with the global MusicStore
 */
class HomeViewModel(
    private val musicStore: MusicStore
) : ViewModel() {
    
    val state: StateFlow<MusicState> = musicStore.state
    
    init {
        // Load initial home content when the ViewModel is created
        loadHomeContent()
    }
    
    fun handleIntent(intent: MusicIntent) {
        musicStore.handleIntent(intent)
    }
    
    private fun loadHomeContent() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.LoadHomeContent)
        }
    }
    
    fun refreshContent() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.RefreshHomeContent)
        }
    }
}
