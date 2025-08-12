package now.link.periphyllidae.store

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import now.link.periphyllidae.mvi.*
import now.link.periphyllidae.platform.MusicPlayer
import co.touchlab.kermit.Logger

class NowPlayingStore(
    private val musicPlayer: MusicPlayer,
    private val logger: Logger
) : ViewModel() {
    
    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()
    
    init {
        // Observe music player state changes
        viewModelScope.launch {
            combine(
                musicPlayer.currentSong,
                musicPlayer.isPlaying,
                musicPlayer.currentPosition,
                musicPlayer.duration,
                musicPlayer.queue,
                musicPlayer.currentIndex
            ) { currentSong, isPlaying, currentPosition, duration, queue, currentIndex ->
                _state.value = _state.value.copy(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    queue = queue,
                    currentIndex = currentIndex
                )
            }.collect { }
        }
    }
    
    fun handleIntent(intent: NowPlayingIntent) {
        when (intent) {
            is NowPlayingIntent.Play -> {
                musicPlayer.resume()
            }
            is NowPlayingIntent.Pause -> {
                musicPlayer.pause()
            }
            is NowPlayingIntent.Next -> {
                musicPlayer.next()
            }
            is NowPlayingIntent.Previous -> {
                musicPlayer.previous()
            }
            is NowPlayingIntent.SeekTo -> {
                musicPlayer.seekTo(intent.position)
            }
            is NowPlayingIntent.ToggleRepeat -> {
                val newRepeatState = !_state.value.isRepeat
                _state.value = _state.value.copy(isRepeat = newRepeatState)
                musicPlayer.setRepeat(newRepeatState)
            }
            is NowPlayingIntent.ToggleShuffle -> {
                val newShuffleState = !_state.value.isShuffle
                _state.value = _state.value.copy(isShuffle = newShuffleState)
                musicPlayer.setShuffle(newShuffleState)
            }
        }
    }
}
