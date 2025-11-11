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
import kotlin.math.pow
import now.link.periphyllidae.cache.CacheManager
import now.link.periphyllidae.getPlatform
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.utils.LogManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.playUri

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, BUFFERING, ERROR
}

class MusicPlayer(
    private val playerFactory: MediampPlayerFactory
) : KoinComponent {
    private val mediaPlayer: MediampPlayer by lazy {
        LogManager.d(TAG, "Creating MediaMP player instance")
        playerFactory.createPlayer()
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Lazy injection of CacheManager to handle initialization timing
    private val cacheManager: CacheManager? by lazy {
        try {
            inject<CacheManager>().value
        } catch (e: Exception) {
            LogManager.w(TAG, "CacheManager not available, caching disabled", e)
            null
        }
    }
    
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

    private var preloadedSongIds: MutableSet<String> = hashSetOf()
    
    init {
        LogManager.i(TAG, "Initializing MusicPlayer")
        startPositionUpdater()
        observePlayerState()
        LogManager.d(TAG, "MusicPlayer initialization completed")
    }
    
    private fun observePlayerState() {
        LogManager.d(TAG, "Starting player state observation")
        scope.launch {
            // Observe MediaMP's playbackState and map to our internal state
            mediaPlayer.playbackState.collect { mediaState ->
                LogManager.v(TAG, "MediaMP state changed: $mediaState")
                
                // Map MediaMP state to our PlaybackState enum
                val newState = when (mediaState) {
                    org.openani.mediamp.PlaybackState.PAUSED -> PlaybackState.PAUSED
                    org.openani.mediamp.PlaybackState.PAUSED_BUFFERING -> PlaybackState.BUFFERING
                    org.openani.mediamp.PlaybackState.READY -> {
                        // When ready, check if we should be playing or paused
                        if (_isPlaying.value) PlaybackState.PLAYING else PlaybackState.PAUSED
                    }
                    org.openani.mediamp.PlaybackState.FINISHED -> {
                        LogManager.d(TAG, "MediaMP playback ended")
                        // Handle end of playback
                        scope.launch {
                            handlePlaybackEnded()
                        }
                        PlaybackState.IDLE
                    }
                    org.openani.mediamp.PlaybackState.PLAYING -> {
                        LogManager.d(TAG, "MediaMP is playing")
                        PlaybackState.PLAYING
                    }
                    else -> {
                        LogManager.w(TAG, "Unknown MediaMP state: $mediaState")
                        PlaybackState.IDLE
                    }
                }
                
                _playbackState.value = newState
                LogManager.v(TAG, "Internal state updated to: $newState")
            }
        }
        LogManager.d(TAG, "Player state observer started")
    }
    
    private fun startPositionUpdater() {
        LogManager.d(TAG, "Starting position updater")
        scope.launch {
            while (isActive) {
                if (_isPlaying.value && _currentSong.value != null) {
                    try {
                        val currentPos = mediaPlayer.getCurrentPositionMillis()
                        _currentPosition.value = currentPos

                        val duration = _duration.value

                        if (duration == 0L) {
                            return@launch
                        }

                        // Check if we've reached the end of the song (backup detection)
                        // MediaMP should handle this via playbackState, but keep as fallback
                        if (currentPos >= duration - 500) { // 0.5 second buffer
                            LogManager.d(TAG, "Position-based song end detected: currentPos=$currentPos, duration=$duration")
                            // Only trigger if MediaMP hasn't already handled it
                            if (_playbackState.value == PlaybackState.PLAYING) {
                                scope.launch {
                                    handlePlaybackEnded()
                                }
                            }
                        }

                        // Preload next song if nearing end of current song
                        val progress = currentPos.toFloat() / duration.toFloat()
                        if (progress > 0.8f && !shuffleEnabled) {
                            val nextSong = currentQueue.getOrNull(_currentIndex.value + 1)
                            if (nextSong != null && !preloadedSongIds.contains(nextSong.id)) {
                                LogManager.d(TAG, "Preloading next song: ${nextSong.title}")
                                preloadSong(nextSong)
                                preloadedSongIds.add(nextSong.id)
                            }
                        }
                    } catch (e: Exception) {
                        LogManager.e(TAG, "Error updating position", e)
                        // Don't spam logs, just continue
                    }
                }
                delay(1000) // Update every second
            }
        }
    }
    
    private fun handlePlaybackEnded() {
        LogManager.i(TAG, "Handling playback end")
        _isPlaying.value = false

        // Stop current playback before switching songs
        stop()
        
        if (repeatEnabled && _currentSong.value != null) {
            LogManager.d(TAG, "Repeat enabled, replaying current song")
            // Reset position and replay the same song
            _currentPosition.value = 0L
            play(_currentSong.value!!)
        } else if (currentQueue.isNotEmpty()) {
            LogManager.d(TAG, "Moving to next song in queue")
            next()
        } else {
            LogManager.d(TAG, "No more songs in queue, stopping playback")
        }
    }
    
    fun play(song: Song) {
        LogManager.i(TAG, "Playing song: ${song.title} by ${song.artist} (ID: ${song.id})")

        // Cancel cache operations for the previous song if it's different
        val previousSong = _currentSong.value
        if (previousSong != null && previousSong.id != song.id) {
            scope.launch {
                try {
                    LogManager.d(TAG, "Cancelling cache operations for previous song: ${previousSong.title}")
                    cacheManager?.cancelDownloadForUrl(previousSong.streamUrl)
                } catch (e: Exception) {
                    LogManager.w(TAG, "Failed to cancel cache for previous song: ${previousSong.title}", e)
                }
            }
        }

        _currentSong.value = song

        scope.launch {
            var playbackSuccessful = false
            var lastException: Exception? = null

            // Retry logic with exponential backoff
            val maxRetries = 3
            val baseDelayMs = 1000L

            for (attempt in 0..maxRetries) {
                try {
                    LogManager.d(TAG, "Starting MediaMP playback (attempt ${attempt + 1}/${maxRetries + 1})")

                    // Use cached URL if cache is available, otherwise use original URL, use cache only on Desktop
                    val playbackUrl = if (getPlatform().isAndroid) {
                        song.streamUrl
                    } else {
                        getPlaybackUrlWithFallback(song.streamUrl, attempt)
                    }

                    LogManager.d(TAG, "Playback URL (attempt ${attempt + 1}): $playbackUrl")

                    mediaPlayer.playUri(playbackUrl)

                    // Update the duration from the song metadata
                    val songDuration = song.duration?.times(1000L) ?: 0L // Convert to milliseconds
                    _duration.value = songDuration
                    LogManager.d(TAG, "Song duration set to: ${songDuration}ms")

                    // Reset position to start
                    _currentPosition.value = 0L

                    // Update current index and playing state
                    updateCurrentIndex(song)
                    _isPlaying.value = true

                    // State will be automatically updated via MediaMP state observation
                    LogManager.i(TAG, "Song playback initiated: ${song.title} (attempt ${attempt + 1})")
                    playbackSuccessful = true
                    break // Success, exit retry loop

                } catch (e: Exception) {
                    lastException = e
                    LogManager.w(TAG, "Playback failed for song: ${song.title} (attempt ${attempt + 1})", e)

                    if (attempt < maxRetries) {
                        val delayMs = baseDelayMs * (2.0.pow(attempt)).toLong()
                        LogManager.d(TAG, "Retrying playback in ${delayMs}ms...")
                        kotlinx.coroutines.delay(delayMs)
                    }
                }
            }

            if (!playbackSuccessful) {
                LogManager.e(TAG, "All playback attempts failed for song: ${song.title}", lastException ?: Exception("Unknown playback error"))
                _playbackState.value = PlaybackState.ERROR
                _isPlaying.value = false
            }
        }
    }

    /**
     * Get the appropriate playback URL (non-async version for compatibility)
     */
    private fun getPlaybackUrlWithFallback(originalUrl: String, attempt: Int): String {
        // For now, be conservative and use original URL to avoid cache conflicts
        // TODO: Consider async cache readiness check in future improvements
        return if (attempt == 0 && cacheManager != null) {
            try {
                val cacheUrl = cacheManager!!.getLocalPlaybackUrl(originalUrl)
                LogManager.d(TAG, "Using cache URL (attempt 0): $cacheUrl")
                cacheUrl
            } catch (e: Exception) {
                LogManager.w(TAG, "Cache URL generation failed, falling back to original URL", e)
                originalUrl
            }
        } else {
            LogManager.d(TAG, "Using original URL (attempt $attempt, bypassing cache)")
            originalUrl
        }
    }

    fun pause() {
        LogManager.d(TAG, "Pausing playback")
        try {
            mediaPlayer.pause()
            _isPlaying.value = false
            // State will be updated automatically via MediaMP state observation
            LogManager.i(TAG, "Playback pause initiated")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to pause playback", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun resume() {
        LogManager.d(TAG, "Resuming playback")
        try {
            if (_playbackState.value == PlaybackState.PAUSED) {
                mediaPlayer.resume()
                _isPlaying.value = true
                // State will be updated automatically via MediaMP state observation
                LogManager.i(TAG, "Playback resume initiated")
            } else {
                LogManager.w(TAG, "Resume called but player is not in paused state: ${_playbackState.value}")
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to resume playback", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun stop() {
        LogManager.d(TAG, "Stopping playback")
        try {
            mediaPlayer.stopPlayback()

            // Cancel any ongoing cache operations for the current song
            val currentSong = _currentSong.value
            if (currentSong != null) {
                scope.launch {
                    try {
                        LogManager.d(TAG, "Cancelling cache operations for current song: ${currentSong.title}")
                        cacheManager?.cancelDownloadForUrl(currentSong.streamUrl)
                    } catch (e: Exception) {
                        LogManager.w(TAG, "Failed to cancel cache for current song: ${currentSong.title}", e)
                    }
                }
            }

            _currentSong.value = null
            _currentPosition.value = 0L
            _duration.value = 0L
            _isPlaying.value = false
            // State will be updated automatically via MediaMP state observation
            // Don't clear the queue or index, just stop playback
            LogManager.i(TAG, "Playback stop initiated")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to stop playback", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun seekTo(position: Long) {
        LogManager.d(TAG, "Seeking to position: ${position}ms")
        try {
             mediaPlayer.seekTo(position)
            _currentPosition.value = position
            LogManager.i(TAG, "Seeked to position: ${position}ms")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to seek to position: ${position}ms", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }
    
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        LogManager.d(TAG, "Setting volume to: $clampedVolume")
        try {
            mediaPlayer.features[AudioLevelController]?.setVolume(clampedVolume)
            LogManager.i(TAG, "Volume set to: $clampedVolume")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to set volume to: $clampedVolume", e)
        }
    }
    
    fun setVolumeWithoutPersistence(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        LogManager.d(TAG, "Setting volume to: $clampedVolume (without persistence)")
        try {
            mediaPlayer.features[AudioLevelController]?.setVolume(clampedVolume)
            LogManager.i(TAG, "Volume set to: $clampedVolume")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to set volume to: $clampedVolume", e)
        }
    }
    
    fun next() {
        LogManager.d(TAG, "Moving to next song")
        if (currentQueue.isEmpty()) {
            LogManager.w(TAG, "Next requested but queue is empty")
            return
        }
        
        val currentIndex = _currentIndex.value
        val nextIndex = if (shuffleEnabled) {
            // In shuffle mode, pick a random song
            val availableIndices = currentQueue.indices.filter { it != currentIndex }
            if (availableIndices.isNotEmpty()) {
                val randomIndex = availableIndices.random()
                LogManager.d(TAG, "Shuffle enabled, selecting random index: $randomIndex")
                randomIndex
            } else {
                // Only one song in queue, repeat it
                currentIndex
            }
        } else {
            // Sequential play
            val nextIndex = (currentIndex + 1) % currentQueue.size
            LogManager.d(TAG, "Sequential play, next index: $nextIndex")
            nextIndex
        }

        // Stop current playback before switching songs
        stop()
        
        val nextSong = currentQueue.getOrNull(nextIndex)
        if (nextSong != null) {
            LogManager.i(TAG, "Playing next song: ${nextSong.title} (index: $nextIndex)")
            _currentIndex.value = nextIndex
            play(nextSong)
        } else {
            LogManager.w(TAG, "Next song at index $nextIndex is null")
        }
    }
    
    fun previous() {
        LogManager.d(TAG, "Moving to previous song")
        if (currentQueue.isEmpty()) {
            LogManager.w(TAG, "Previous requested but queue is empty")
            return
        }
        
        val currentIndex = _currentIndex.value
        val previousIndex = if (shuffleEnabled) {
            // In shuffle mode, pick a random song
            val availableIndices = currentQueue.indices.filter { it != currentIndex }
            if (availableIndices.isNotEmpty()) {
                val randomIndex = availableIndices.random()
                LogManager.d(TAG, "Shuffle enabled, selecting random index: $randomIndex")
                randomIndex
            } else {
                // Only one song in queue, repeat it
                currentIndex
            }
        } else {
            // Sequential play - go to previous song
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else currentQueue.size - 1
            LogManager.d(TAG, "Sequential play, previous index: $prevIndex")
            prevIndex
        }

        // Stop current playback before switching songs
        stop()
        
        val previousSong = currentQueue.getOrNull(previousIndex)
        if (previousSong != null) {
            LogManager.i(TAG, "Playing previous song: ${previousSong.title} (index: $previousIndex)")
            _currentIndex.value = previousIndex
            play(previousSong)
        } else {
            LogManager.w(TAG, "Previous song at index $previousIndex is null")
        }
    }
    
    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        LogManager.i(TAG, "Setting queue with ${songs.size} songs, starting at index: $startIndex")
        LogManager.v(TAG, "Queue songs: ${songs.map { "${it.title} by ${it.artist}" }}")
        currentQueue = songs
        _queue.value = songs
        _currentIndex.value = startIndex.coerceIn(0, maxOf(0, songs.size - 1))
        LogManager.d(TAG, "Queue set successfully, current index: ${_currentIndex.value}")
        
        // Don't auto-play when setting queue, let the caller decide when to start
        val song = songs.getOrNull(_currentIndex.value)
        if (song != null) {
            _currentSong.value = song
            LogManager.d(TAG, "Current song set to: ${song.title}")
        } else {
            LogManager.w(TAG, "No song found at index ${_currentIndex.value}")
        }
    }
    
    fun setShuffle(enabled: Boolean) {
        LogManager.i(TAG, "Setting shuffle: $enabled")
        shuffleEnabled = enabled
        LogManager.d(TAG, "Shuffle mode updated to: $enabled")
    }
    
    fun setRepeat(enabled: Boolean) {
        LogManager.i(TAG, "Setting repeat: $enabled")
        repeatEnabled = enabled
        LogManager.d(TAG, "Repeat mode updated to: $enabled")
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
        LogManager.d(TAG, "Current position set to: ${position}ms")
    }
    
    /**
     * Play the song at the specified index in the current queue
     */
    fun playAtIndex(index: Int) {
        if (index >= 0 && index < currentQueue.size) {
            val song = currentQueue[index]
            LogManager.i(TAG, "Playing song at index $index: ${song.title}")
            _currentIndex.value = index
            play(song)
        } else {
            LogManager.w(TAG, "Invalid index $index for queue size ${currentQueue.size}")
        }
    }
    
    /**
     * Clear the current queue and stop playback
     */
    fun clearQueue() {
        LogManager.i(TAG, "Clearing queue")
        stop()
        currentQueue = emptyList()
        _queue.value = emptyList()
        _currentIndex.value = 0
        LogManager.d(TAG, "Queue cleared")
    }
    
    fun release() {
        LogManager.i(TAG, "Releasing MusicPlayer resources")
        try {
            mediaPlayer.close()
            LogManager.d(TAG, "MediaPlayer resources released successfully")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error releasing MediaPlayer resources", e)
        }
        
        // Clean up cache if available
        scope.launch {
            try {
                cacheManager?.shutdown()
                LogManager.d(TAG, "Cache manager shutdown completed")
            } catch (e: Exception) {
                LogManager.e(TAG, "Error shutting down cache manager", e)
            }
        }
        
        LogManager.i(TAG, "MusicPlayer cleanup completed")
    }
    
    /**
     * Check if a song is cached
     */
    suspend fun isSongCached(song: Song): Boolean {
        return cacheManager?.isCached(song.streamUrl) ?: false
    }
    
    /**
     * Preload a song into cache
     */
    suspend fun preloadSong(song: Song) {
        cacheManager?.let { cacheManager ->
            if (!cacheManager.isCached(song.streamUrl)) {
                LogManager.d(TAG, "Preloading song: ${song.title}")
                // Trigger caching by requesting the local URL
                cacheManager.preload(song.streamUrl)
            }
        }
    }
    
    /**
     * Preload multiple songs into cache
     */
    suspend fun preloadSongs(songs: List<Song>) {
        songs.forEach { song ->
            preloadSong(song)
        }
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats() = cacheManager?.getCacheStats()
    
    /**
     * Clear all cached content
     */
    suspend fun clearCache() {
        cacheManager?.clearCache()
        LogManager.i(TAG, "Music cache cleared")
    }

    /**
     * Update cache size limit
     */
    suspend fun updateCacheSize(newMaxSize: Long): Boolean {
        return try {
            val result = cacheManager?.updateCacheSize(newMaxSize) ?: false
            if (result) {
                LogManager.i(TAG, "Cache size updated to ${newMaxSize / (1024 * 1024)}MB")
            } else {
                LogManager.w(TAG, "Failed to update cache size")
            }
            result
        } catch (e: Exception) {
            LogManager.e(TAG, "Error updating cache size", e)
            false
        }
    }

    /**
     * Get cache directory path
     */
    fun getCacheDirectoryPath(): String? = cacheManager?.getCacheDirectoryPath()
    
    private fun updateCurrentIndex(song: Song) {
        LogManager.v(TAG, "Updating current index for song: ${song.title}")
        val index = currentQueue.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            _currentIndex.value = index
            LogManager.d(TAG, "Current index updated to: $index for song: ${song.title}")
        } else {
            LogManager.w(TAG, "Could not find song ${song.title} in current queue")
        }
    }

    companion object {
        private const val TAG = "MusicPlayer"
    }
}
