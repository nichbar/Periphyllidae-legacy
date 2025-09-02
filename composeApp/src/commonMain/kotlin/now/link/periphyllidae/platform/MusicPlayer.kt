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
import co.touchlab.kermit.Logger
import now.link.periphyllidae.models.Song
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.playUri

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, BUFFERING, ERROR
}

class MusicPlayer(private val playerFactory: MediampPlayerFactory) {
    private val logger = Logger.withTag("MusicPlayer")
    
    private val mediaPlayer: MediampPlayer by lazy { 
        logger.d("Creating MediaMP player instance")
        playerFactory.createPlayer()
    }
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
        logger.i("Initializing MusicPlayer")
        startPositionUpdater()
        observePlayerState()
        logger.d("MusicPlayer initialization completed")
    }
    
    private fun observePlayerState() {
        logger.d("Starting player state observation")
        scope.launch {
            // TODO: Implement proper MediaMP state observation once API is clarified
            // For now, we'll implement basic state management
            logger.v("Player state observer started")
        }
    }
    
    private fun startPositionUpdater() {
        logger.d("Starting position updater")
        scope.launch {
            while (isActive) {
                if (_isPlaying.value) {
                    _currentPosition.value = mediaPlayer.getCurrentPositionMillis()
                    logger.v("Position update tick (playing: ${_isPlaying.value})")
                }
                delay(1000) // Update every second
            }
        }
    }
    
    private fun handlePlaybackEnded() {
        logger.i("Playback ended for current song")
        if (repeatEnabled && _currentSong.value != null) {
            logger.d("Repeat enabled, replaying current song")
            play(_currentSong.value!!)
        } else {
            logger.d("Moving to next song")
            next()
        }
    }
    
    fun play(song: Song) {
        logger.i("Playing song: ${song.title} by ${song.artist} (ID: ${song.id})")
        _currentSong.value = song
        val streamUrl = song.streamUrl ?: run {
            logger.e("No stream URL available for song: ${song.title}")
            return
        }
        
        logger.d("Stream URL: $streamUrl")
        
        scope.launch {
            try {
                logger.d("Starting MediaMP playback")
                mediaPlayer.playUri(streamUrl)

                _playbackState.value = PlaybackState.BUFFERING
                logger.d("Playback state set to BUFFERING")
                
                // For now, let's implement basic state management without calling MediaMP directly
                // until we figure out the correct API
                updateCurrentIndex(song)
                _isPlaying.value = true
                _playbackState.value = PlaybackState.PLAYING
                logger.i("Song started playing: ${song.title}")
            } catch (e: Exception) {
                logger.e("Failed to play song: ${song.title}", e)
                _playbackState.value = PlaybackState.ERROR
                _isPlaying.value = false
            }
        }
    }
    
    fun pause() {
        logger.d("Pausing playback")
        try {
            mediaPlayer.pause()

            _isPlaying.value = false
            _playbackState.value = PlaybackState.PAUSED
            logger.i("Playback paused successfully")
        } catch (e: Exception) {
            logger.e("Failed to pause playback", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun resume() {
        logger.d("Resuming playback")
        try {
            mediaPlayer.resume()

            _isPlaying.value = true
            _playbackState.value = PlaybackState.PLAYING
            logger.i("Playback resumed successfully")
        } catch (e: Exception) {
            logger.e("Failed to resume playback", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun stop() {
        logger.d("Stopping playback")
        try {
            mediaPlayer.stopPlayback()

            _currentSong.value = null
            _currentPosition.value = 0L
            _duration.value = 0L
            _isPlaying.value = false
            _playbackState.value = PlaybackState.IDLE
            logger.i("Playback stopped successfully")
        } catch (e: Exception) {
            logger.e("Failed to stop playback", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun seekTo(position: Long) {
        logger.d("Seeking to position: ${position}ms")
        try {
             mediaPlayer.seekTo(position)
            _currentPosition.value = position
            logger.i("Seeked to position: ${position}ms")
        } catch (e: Exception) {
            logger.e("Failed to seek to position: ${position}ms", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun next() {
        logger.d("Moving to next song")
        if (currentQueue.isEmpty()) {
            logger.w("Next requested but queue is empty")
            return
        }
        
        val nextIndex = if (shuffleEnabled) {
            val randomIndex = currentQueue.indices.random()
            logger.d("Shuffle enabled, selecting random index: $randomIndex")
            randomIndex
        } else {
            val nextIndex = (_currentIndex.value + 1) % currentQueue.size
            logger.d("Sequential play, next index: $nextIndex")
            nextIndex
        }
        
        val nextSong = currentQueue.getOrNull(nextIndex)
        if (nextSong != null) {
            logger.i("Playing next song: ${nextSong.title} (index: $nextIndex)")
            _currentIndex.value = nextIndex
            play(nextSong)
        } else {
            logger.w("Next song at index $nextIndex is null")
        }
    }
    
    fun previous() {
        logger.d("Moving to previous song")
        if (currentQueue.isEmpty()) {
            logger.w("Previous requested but queue is empty")
            return
        }
        
        val previousIndex = if (shuffleEnabled) {
            val randomIndex = currentQueue.indices.random()
            logger.d("Shuffle enabled, selecting random index: $randomIndex")
            randomIndex
        } else {
            val prevIndex = if (_currentIndex.value > 0) _currentIndex.value - 1 else currentQueue.size - 1
            logger.d("Sequential play, previous index: $prevIndex")
            prevIndex
        }
        
        val previousSong = currentQueue.getOrNull(previousIndex)
        if (previousSong != null) {
            logger.i("Playing previous song: ${previousSong.title} (index: $previousIndex)")
            _currentIndex.value = previousIndex
            play(previousSong)
        } else {
            logger.w("Previous song at index $previousIndex is null")
        }
    }
    
    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        logger.i("Setting queue with ${songs.size} songs, starting at index: $startIndex")
        logger.v("Queue songs: ${songs.map { "${it.title} by ${it.artist}" }}")
        currentQueue = songs
        _queue.value = songs
        _currentIndex.value = startIndex.coerceIn(0, songs.size - 1)
        logger.d("Queue set successfully, current index: ${_currentIndex.value}")
        
        songs.getOrNull(_currentIndex.value)?.let { song ->
            logger.i("Auto-playing first song: ${song.title}")
            play(song)
        } ?: logger.w("No song found at index ${_currentIndex.value}")
    }
    
    fun setShuffle(enabled: Boolean) {
        logger.i("Setting shuffle: $enabled")
        shuffleEnabled = enabled
        logger.d("Shuffle mode updated to: $enabled")
    }
    
    fun setRepeat(enabled: Boolean) {
        logger.i("Setting repeat: $enabled")
        repeatEnabled = enabled
        logger.d("Repeat mode updated to: $enabled")
    }
    
    fun release() {
        logger.i("Releasing MusicPlayer resources")
        try {
             mediaPlayer.close()
             logger.d("MediaPlayer resources released successfully")
        } catch (e: Exception) {
            logger.e("Error releasing MediaPlayer resources", e)
        }
        logger.i("MusicPlayer cleanup completed")
    }
    
    private fun updateCurrentIndex(song: Song) {
        logger.v("Updating current index for song: ${song.title}")
        val index = currentQueue.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            _currentIndex.value = index
            logger.d("Current index updated to: $index for song: ${song.title}")
        } else {
            logger.w("Could not find song ${song.title} in current queue")
        }
    }
}
