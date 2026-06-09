package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.store.MusicStore

class QueueViewModel(
    private val musicStore: MusicStore
) : ViewModel() {

    val state: StateFlow<MusicState> = musicStore.state

    fun playQueueItem(index: Int) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlayQueueItem(index))
        }
    }

    fun removeQueueItem(index: Int) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.RemoveQueueItem(index))
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.ClearQueue)
        }
    }

}
