package now.link.periphyllidae.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import now.link.periphyllidae.models.Song
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.playUri

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, BUFFERING, ERROR
}

class MusicPlayer(private val playerFactory: MediampPlayerFactory) {
    private val mediaPlayer: MediampPlayer by lazy { playerFactory.createPlayer() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private var currentQueue: List<Song> = emptyList()
    private var shuffleEnabled: Boolean = false
    private var repeatEnabled: Boolean = false
    
    init {
        startPositionUpdater()
        observePlayerState()
    }
    
    private fun observePlayerState() {
        scope.launch {
            // TODO: Implement proper MediaMP state observation once API is clarified
            // For now, we'll implement basic state management
        }
    }
    
    private fun startPositionUpdater() {
        scope.launch {
            while (isActive) {
                if (_isPlaying.value && mediaPlayer != null) {
                    // TODO: Get actual position from MediaMP player
                    // _currentPosition.value = mediaPlayer.currentPosition
                }
                delay(1000) // Update every second
            }
        }
    }
    
    private fun handlePlaybackEnded() {
        if (repeatEnabled && _currentSong.value != null) {
            play(_currentSong.value!!)
        } else {
            next()
        }
    }
    
    fun play(song: Song) {
        _currentSong.value = song
        val streamUrl = song.streamUrl ?: return
        
        scope.launch {
            try {
                mediaPlayer.playUri(streamUrl)

                _playbackState.value = PlaybackState.BUFFERING
                
                // For now, let's implement basic state management without calling MediaMP directly
                // until we figure out the correct API
                updateCurrentIndex(song)
                _isPlaying.value = true
                _playbackState.value = PlaybackState.PLAYING
            } catch (e: Exception) {
                _playbackState.value = PlaybackState.ERROR
                _isPlaying.value = false
            }
        }
    }
    
    fun pause() {
        try {
            mediaPlayer.pause()

            _isPlaying.value = false
            _playbackState.value = PlaybackState.PAUSED
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun resume() {
        try {
            mediaPlayer.resume()

            _isPlaying.value = true
            _playbackState.value = PlaybackState.PLAYING
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun stop() {
        try {
            mediaPlayer.stopPlayback()

            _currentSong.value = null
            _currentPosition.value = 0L
            _duration.value = 0L
            _isPlaying.value = false
            _playbackState.value = PlaybackState.IDLE
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun seekTo(position: Long) {
        try {
             mediaPlayer.seekTo(position)
            _currentPosition.value = position
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun next() {
        if (currentQueue.isEmpty()) return
        
        val nextIndex = if (shuffleEnabled) {
            currentQueue.indices.random()
        } else {
            (_currentIndex.value + 1) % currentQueue.size
        }
        
        val nextSong = currentQueue.getOrNull(nextIndex)
        if (nextSong != null) {
            _currentIndex.value = nextIndex
            play(nextSong)
        }
    }
    
    fun previous() {
        if (currentQueue.isEmpty()) return
        
        val previousIndex = if (shuffleEnabled) {
            currentQueue.indices.random()
        } else {
            if (_currentIndex.value > 0) _currentIndex.value - 1 else currentQueue.size - 1
        }
        
        val previousSong = currentQueue.getOrNull(previousIndex)
        if (previousSong != null) {
            _currentIndex.value = previousIndex
            play(previousSong)
        }
    }
    
    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        currentQueue = songs
        _queue.value = songs
        _currentIndex.value = startIndex.coerceIn(0, songs.size - 1)
        
        songs.getOrNull(_currentIndex.value)?.let { song ->
            play(song)
        }
    }
    
    fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
    }
    
    fun setRepeat(enabled: Boolean) {
        repeatEnabled = enabled
    }
    
    fun release() {
        try {
             mediaPlayer.close()
        } catch (e: Exception) {
            // Ignore errors during cleanup
        }
    }
    
    private fun updateCurrentIndex(song: Song) {
        val index = currentQueue.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            _currentIndex.value = index
        }
    }
}
