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
            // Observe MediaMP's playbackState and map to our internal state
            mediaPlayer.playbackState.collect { mediaState ->
                logger.v("MediaMP state changed: $mediaState")
                
                // Map MediaMP state to our PlaybackState enum
                val newState = when (mediaState) {
                    org.openani.mediamp.PlaybackState.PAUSED -> PlaybackState.IDLE
                    org.openani.mediamp.PlaybackState.PAUSED_BUFFERING -> PlaybackState.BUFFERING
                    org.openani.mediamp.PlaybackState.READY -> {
                        // When ready, check if we should be playing or paused
                        if (_isPlaying.value) PlaybackState.PLAYING else PlaybackState.PAUSED
                    }
                    org.openani.mediamp.PlaybackState.FINISHED -> {
                        logger.d("MediaMP playback ended")
                        // Handle end of playback
                        scope.launch {
                            handlePlaybackEnded()
                        }
                        PlaybackState.IDLE
                    }
                    else -> {
                        logger.w("Unknown MediaMP state: $mediaState")
                        PlaybackState.IDLE
                    }
                }
                
                _playbackState.value = newState
                logger.v("Internal state updated to: $newState")
            }
        }
        logger.d("Player state observer started")
    }
    
    private fun startPositionUpdater() {
        logger.d("Starting position updater")
        scope.launch {
            while (isActive) {
                if (_isPlaying.value && _currentSong.value != null) {
                    try {
                        val currentPos = mediaPlayer.getCurrentPositionMillis()
                        _currentPosition.value = currentPos
                        
                        // Check if we've reached the end of the song (backup detection)
                        // MediaMP should handle this via playbackState, but keep as fallback
                        val duration = _duration.value
                        if (duration > 0 && currentPos >= duration - 500) { // 0.5 second buffer
                            logger.d("Position-based song end detected: currentPos=$currentPos, duration=$duration")
                            // Only trigger if MediaMP hasn't already handled it
                            if (_playbackState.value == PlaybackState.PLAYING) {
                                scope.launch {
                                    handlePlaybackEnded()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.e("Error updating position", e)
                        // Don't spam logs, just continue
                    }
                }
                delay(1000) // Update every second
            }
        }
    }
    
    private fun handlePlaybackEnded() {
        logger.i("Handling playback end")
        _isPlaying.value = false
        
        if (repeatEnabled && _currentSong.value != null) {
            logger.d("Repeat enabled, replaying current song")
            // Reset position and replay the same song
            _currentPosition.value = 0L
            play(_currentSong.value!!)
        } else if (currentQueue.isNotEmpty()) {
            logger.d("Moving to next song in queue")
            next()
        } else {
            logger.d("No more songs in queue, stopping playback")
            stop()
        }
    }
    
    fun play(song: Song) {
        logger.i("Playing song: ${song.title} by ${song.artist} (ID: ${song.id})")
        _currentSong.value = song
        val streamUrl = song.streamUrl ?: run {
            logger.e("No stream URL available for song: ${song.title}")
            _playbackState.value = PlaybackState.ERROR
            return
        }
        
        logger.d("Stream URL: $streamUrl")
        
        scope.launch {
            try {
                logger.d("Starting MediaMP playback")
                
                mediaPlayer.playUri(streamUrl)
                
                // Update the duration from the song metadata
                val songDuration = song.duration?.times(1000L) ?: 0L // Convert to milliseconds
                _duration.value = songDuration
                logger.d("Song duration set to: ${songDuration}ms")
                
                // Reset position to start
                _currentPosition.value = 0L
                
                // Update current index and playing state
                updateCurrentIndex(song)
                _isPlaying.value = true
                
                // State will be automatically updated via MediaMP state observation
                logger.i("Song playback initiated: ${song.title}")
                
                // Start monitoring for actual duration from player
                monitorDuration()
                
            } catch (e: Exception) {
                logger.e("Failed to play song: ${song.title}", e)
                _playbackState.value = PlaybackState.ERROR
                _isPlaying.value = false
            }
        }
    }
    
    private fun monitorDuration() {
        scope.launch {
            // Try to get actual duration from the player after a short delay
            delay(2000) // Wait for media to load
            try {
                val actualDuration = mediaPlayer.getCurrentPositionMillis()
                if (actualDuration > 0) {
                    _duration.value = actualDuration
                    logger.d("Updated duration from player: ${actualDuration}ms")
                }
            } catch (e: Exception) {
                logger.w("Could not get duration from player, using metadata duration", e)
            }
        }
    }
    
    fun pause() {
        logger.d("Pausing playback")
        try {
            mediaPlayer.pause()
            _isPlaying.value = false
            // State will be updated automatically via MediaMP state observation
            logger.i("Playback pause initiated")
        } catch (e: Exception) {
            logger.e("Failed to pause playback", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun resume() {
        logger.d("Resuming playback")
        try {
            if (_playbackState.value == PlaybackState.PAUSED) {
                mediaPlayer.resume()
                _isPlaying.value = true
                // State will be updated automatically via MediaMP state observation
                logger.i("Playback resume initiated")
            } else {
                logger.w("Resume called but player is not in paused state: ${_playbackState.value}")
            }
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
            // State will be updated automatically via MediaMP state observation
            // Don't clear the queue or index, just stop playback
            logger.i("Playback stop initiated")
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
        
        val currentIndex = _currentIndex.value
        val nextIndex = if (shuffleEnabled) {
            // In shuffle mode, pick a random song
            val availableIndices = currentQueue.indices.filter { it != currentIndex }
            if (availableIndices.isNotEmpty()) {
                val randomIndex = availableIndices.random()
                logger.d("Shuffle enabled, selecting random index: $randomIndex")
                randomIndex
            } else {
                // Only one song in queue, repeat it
                currentIndex
            }
        } else {
            // Sequential play
            val nextIndex = (currentIndex + 1) % currentQueue.size
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
            stop()
        }
    }
    
    fun previous() {
        logger.d("Moving to previous song")
        if (currentQueue.isEmpty()) {
            logger.w("Previous requested but queue is empty")
            return
        }
        
        val currentIndex = _currentIndex.value
        val previousIndex = if (shuffleEnabled) {
            // In shuffle mode, pick a random song
            val availableIndices = currentQueue.indices.filter { it != currentIndex }
            if (availableIndices.isNotEmpty()) {
                val randomIndex = availableIndices.random()
                logger.d("Shuffle enabled, selecting random index: $randomIndex")
                randomIndex
            } else {
                // Only one song in queue, repeat it
                currentIndex
            }
        } else {
            // Sequential play - go to previous song
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else currentQueue.size - 1
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
        _currentIndex.value = startIndex.coerceIn(0, maxOf(0, songs.size - 1))
        logger.d("Queue set successfully, current index: ${_currentIndex.value}")
        
        // Don't auto-play when setting queue, let the caller decide when to start
        val song = songs.getOrNull(_currentIndex.value)
        if (song != null) {
            _currentSong.value = song
            logger.d("Current song set to: ${song.title}")
        } else {
            logger.w("No song found at index ${_currentIndex.value}")
        }
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
    
    fun getCurrentSong(): Song? = _currentSong.value
    
    fun getCurrentPosition(): Long = _currentPosition.value
    
    fun getDuration(): Long = _duration.value
    
    fun isCurrentlyPlaying(): Boolean = _isPlaying.value
    
    fun getCurrentQueue(): List<Song> = currentQueue
    
    fun getCurrentIndex(): Int = _currentIndex.value
    
    fun getShuffleEnabled(): Boolean = shuffleEnabled
    
    fun getRepeatEnabled(): Boolean = repeatEnabled
    
    fun getPlaybackState(): PlaybackState = _playbackState.value
    
    /**
     * Check if there's a current song that can be resumed
     */
    fun canResume(): Boolean = _currentSong.value != null && _playbackState.value == PlaybackState.PAUSED
    
    /**
     * Set the current position without seeking (useful for restoring state)
     */
    fun setCurrentPosition(position: Long) {
        _currentPosition.value = position
        logger.d("Current position set to: ${position}ms")
    }
    
    /**
     * Play the song at the specified index in the current queue
     */
    fun playAtIndex(index: Int) {
        if (index >= 0 && index < currentQueue.size) {
            val song = currentQueue[index]
            logger.i("Playing song at index $index: ${song.title}")
            _currentIndex.value = index
            play(song)
        } else {
            logger.w("Invalid index $index for queue size ${currentQueue.size}")
        }
    }
    
    /**
     * Clear the current queue and stop playback
     */
    fun clearQueue() {
        logger.i("Clearing queue")
        stop()
        currentQueue = emptyList()
        _queue.value = emptyList()
        _currentIndex.value = 0
        logger.d("Queue cleared")
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
