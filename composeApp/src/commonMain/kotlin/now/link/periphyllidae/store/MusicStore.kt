package now.link.periphyllidae.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.repository.SubsonicRepositoryManager
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.platform.MusicPlayer
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.HomeContentCache
import now.link.periphyllidae.utils.LogManager

class MusicStore(
    private val subsonicRepositoryManager: SubsonicRepositoryManager,
    private val musicPlayer: MusicPlayer,
    private val settingsRepository: SettingsRepository
) {
    // App-scoped coroutine scope for the store
    private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Track which song IDs have been scrobbled in this session
    private val scrobbledSongIds = mutableSetOf<String>()

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init {
        LogManager.i("MusicStore", "Initializing MusicStore")
        // Listen to music player state changes
        observePlayerState()
        // Load persisted queue
        loadPersistedQueue()
        // Load cached home content immediately for fast startup
        loadCachedHomeContent()
        // Load persisted volume
        loadPersistedVolume()
        // Load persisted shuffle and repeat states
        loadPersistedPlaybackModes()
        LogManager.d("MusicStore", "MusicStore initialization completed")
    }
    
    /**
     * Helper function to get the current repository instance
     * Returns null if no repository is active (user not authenticated)
     */
    private fun getCurrentRepository() = try {
        subsonicRepositoryManager.getCurrentRepository()
    } catch (e: IllegalStateException) {
        LogManager.w("MusicStore", "No active repository: ${e.message}")
        null
    }

    fun handleIntent(intent: MusicIntent) {
        LogManager.d("MusicStore", "Handling intent: ${intent::class.simpleName}")
        when (intent) {
            is MusicIntent.Play -> play()
            is MusicIntent.Pause -> pause()
            is MusicIntent.Next -> next()
            is MusicIntent.Previous -> previous()
            is MusicIntent.SeekTo -> seekTo(intent.position)
            is MusicIntent.ToggleRepeat -> toggleRepeat()
            is MusicIntent.ToggleShuffle -> toggleShuffle()
            is MusicIntent.SetVolume -> setVolume(intent.volume)
            is MusicIntent.LoadHomeContent -> loadHomeContent()
            is MusicIntent.RefreshHomeContent -> refreshHomeContent()
            is MusicIntent.PlayRandomSong -> playRandomSong()
            is MusicIntent.PlaySong -> playSong(intent.song)
            is MusicIntent.PlayAlbum -> playAlbum(intent.album)
            is MusicIntent.SetQueue -> setQueue(intent.songs, intent.startIndex)
            is MusicIntent.InvalidatePlayerState -> invalidatePlayerState()
        }
    }

    private fun play() {
        LogManager.d("MusicStore", "Play requested")
        val currentState = _state.value
        currentState.currentSong?.let { song ->
            LogManager.i("MusicStore", "Playing song: ${song.title} by ${song.artist}")
            if (musicPlayer.canResume()) {
                // Resume paused playback
                LogManager.d("MusicStore", "Resuming paused playback")
                musicPlayer.resume()
            } else if (currentState.currentPosition > 0 && !musicPlayer.isCurrentlyPlaying()) {
                // We have a saved position (likely from app restart), start playback and seek to saved position
                LogManager.d("MusicStore", "Starting playback with saved position: ${currentState.currentPosition}ms")
                musicPlayer.play(song)
                // Seek to saved position after a short delay to allow media to load
                storeScope.launch {
                    delay(1000) // Wait for media to load
                    musicPlayer.seekTo(currentState.currentPosition)
                    LogManager.i("MusicStore", "Resumed from saved position: ${currentState.currentPosition}ms")
                }
            } else {
                // Start playing the current song from the beginning
                LogManager.d("MusicStore", "Starting fresh playback")
                musicPlayer.play(song)
            }
            // State will be updated automatically through observers
        } ?: LogManager.w("MusicStore", "Play requested but no current song available")
    }

    private fun pause() {
        LogManager.d("MusicStore", "Pause requested")
        musicPlayer.pause()
        // State will be updated automatically through observers
        LogManager.i("MusicStore", "Playback paused")
    }

    private fun next() {
        LogManager.d("MusicStore", "Next song requested")
        musicPlayer.next()
        // State will be updated automatically through observers
        LogManager.d("MusicStore", "Next song initiated")
    }

    private fun previous() {
        LogManager.d("MusicStore", "Previous song requested")
        musicPlayer.previous()
        // State will be updated automatically through observers
        LogManager.d("MusicStore", "Previous song initiated")
    }

    private fun seekTo(position: Long) {
        LogManager.d("MusicStore", "Seek requested to position: ${position}ms")
        musicPlayer.seekTo(position)
        // State will be updated automatically through observers
        LogManager.i("MusicStore", "Seeked to position: ${position}ms")
    }

    private fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        LogManager.d("MusicStore", "Setting volume to: $clampedVolume")
        musicPlayer.setVolume(clampedVolume)
        _state.value = _state.value.copy(volume = clampedVolume)
        // Save volume to settings for persistence
        settingsRepository.saveVolume(clampedVolume)
        LogManager.i("MusicStore", "Volume changed to: $clampedVolume and saved to settings")
    }

    private fun toggleRepeat() {
        val newRepeat = !_state.value.isRepeat
        LogManager.d("MusicStore", "Toggling repeat mode: $newRepeat")
        musicPlayer.setRepeat(newRepeat)
        _state.value = _state.value.copy(isRepeat = newRepeat)
        // Save repeat state for persistence
        settingsRepository.saveRepeat(newRepeat)
        LogManager.i("MusicStore", "Repeat mode changed to: $newRepeat and saved to settings")
    }

    private fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffle
        LogManager.d("MusicStore", "Toggling shuffle mode: $newShuffle")
        musicPlayer.setShuffle(newShuffle)
        _state.value = _state.value.copy(isShuffle = newShuffle)
        // Save shuffle state for persistence
        settingsRepository.saveShuffle(newShuffle)
        LogManager.i("MusicStore", "Shuffle mode changed to: $newShuffle and saved to settings")
    }

    private fun loadCachedHomeContent() {
        LogManager.d("MusicStore", "Loading cached home content for immediate display")
        val cachedContent = settingsRepository.getHomeContentCache()
        if (cachedContent != null) {
            LogManager.d("MusicStore", "Found cached home content from ${System.currentTimeMillis() - cachedContent.timestamp}ms ago")
            _state.value = _state.value.copy(
                randomSongs = cachedContent.randomSongs,
                playlists = cachedContent.playlists,
                mostPlayedAlbums = cachedContent.mostPlayedAlbums,
                recentlyPlayedAlbums = cachedContent.recentlyPlayedAlbums,
                newestAlbums = cachedContent.newestAlbums
            )
            LogManager.i("MusicStore", "Cached home content loaded successfully")
        } else {
            LogManager.d("MusicStore", "No cached home content found")
        }
    }

    private fun loadHomeContent() {
        if (_state.value.isLoadingHome) {
            LogManager.w("MusicStore", "Home content loading already in progress, skipping")
            return
        }
        
        LogManager.i("MusicStore", "Loading home content")
        
        // Try to load cached content first
        val cachedContent = settingsRepository.getHomeContentCache()
        if (cachedContent != null) {
            LogManager.d("MusicStore", "Found cached home content from ${System.currentTimeMillis() - cachedContent.timestamp}ms ago")
            _state.value = _state.value.copy(
                isLoadingHome = false,
                randomSongs = cachedContent.randomSongs,
                playlists = cachedContent.playlists,
                mostPlayedAlbums = cachedContent.mostPlayedAlbums,
                recentlyPlayedAlbums = cachedContent.recentlyPlayedAlbums,
                newestAlbums = cachedContent.newestAlbums,
                homeError = null
            )
            LogManager.i("MusicStore", "Loaded cached home content successfully")
        }
        
        // Load fresh content from network
        _state.value = _state.value.copy(isLoadingHome = true, homeError = null)
        
        storeScope.launch {
            try {
                val repository = getCurrentRepository()
                if (repository == null) {
                    _state.value = _state.value.copy(
                        isLoadingHome = false,
                        homeError = "No server configured. Please login to a Subsonic server."
                    )
                    return@launch
                }
                
                LogManager.d("MusicStore", "Fetching playlists, albums, and songs from repository")
                val playlistsResult = repository.getPlaylists()
                val recentlyPlayedResult = repository.getRecentlyPlayedAlbums(10)
                val mostPlayedResult = repository.getMostPlayedAlbums(10)
                val newestAlbumsResult = repository.getNewestAlbums(10)
                val randomSongsResult = repository.getRandomSongs(100)
                
                val playlists = if (playlistsResult.isSuccess) {
                    val result = playlistsResult.getOrNull() ?: emptyList()
                    LogManager.d("MusicStore", "Loaded ${result.size} playlists")
                    result
                } else {
                    LogManager.w("MusicStore", "Failed to load playlists: ${playlistsResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val mostPlayedSongs = if (mostPlayedResult.isSuccess) {
                    val result = mostPlayedResult.getOrNull() ?: emptyList()
                    LogManager.d("MusicStore", "Loaded ${result.size} most played albums")
                    result
                } else {
                    LogManager.w("MusicStore", "Failed to load most played albums: ${mostPlayedResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val recentlyPlayedSongs = if (recentlyPlayedResult.isSuccess) {
                    val result = recentlyPlayedResult.getOrNull() ?: emptyList()
                    LogManager.d("MusicStore", "Loaded ${result.size} recently played albums")
                    result
                } else {
                    LogManager.w("MusicStore", "Failed to load recently played albums: ${recentlyPlayedResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val newestAlbums = if (newestAlbumsResult.isSuccess) {
                    val result = newestAlbumsResult.getOrNull() ?: emptyList()
                    LogManager.d("MusicStore", "Loaded ${result.size} newest albums")
                    result
                } else {
                    LogManager.w("MusicStore", "Failed to load newest albums: ${newestAlbumsResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val randomSongs = if (randomSongsResult.isSuccess) {
                    val result = randomSongsResult.getOrNull() ?: emptyList()
                    LogManager.d("MusicStore", "Loaded ${result.size} random songs")
                    result
                } else {
                    LogManager.w("MusicStore", "Failed to load random songs: ${randomSongsResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                // Update state with fresh content
                _state.value = _state.value.copy(
                    isLoadingHome = false,
                    randomSongs = randomSongs,
                    playlists = playlists,
                    mostPlayedAlbums = mostPlayedSongs,
                    recentlyPlayedAlbums = recentlyPlayedSongs,
                    newestAlbums = newestAlbums,
                    homeError = null
                )
                
                // Cache the fresh content
                val newCache = HomeContentCache(
                    randomSongs = randomSongs,
                    playlists = playlists,
                    mostPlayedAlbums = mostPlayedSongs,
                    recentlyPlayedAlbums = recentlyPlayedSongs,
                    newestAlbums = newestAlbums,
                    timestamp = System.currentTimeMillis()
                )
                settingsRepository.saveHomeContentCache(newCache)
                
                LogManager.i("MusicStore", "Home content loaded and cached successfully")
            } catch (e: Exception) {
                LogManager.e("MusicStore", "Failed to load home content", e)
                _state.value = _state.value.copy(
                    isLoadingHome = false,
                    homeError = "Failed to load content: ${e.message}"
                )
            }
        }
    }

    private fun refreshHomeContent() {
        LogManager.i("MusicStore", "Refreshing home content")
        loadHomeContent()
    }

    private fun playRandomSong() {
        LogManager.d("MusicStore", "Random song play requested")
        val randomSongs = _state.value.randomSongs
        if (randomSongs.isNotEmpty()) {
            val randomSong = randomSongs.random()
            val randomIndex = randomSongs.indexOf(randomSong)
            LogManager.i("MusicStore", "Playing random song from cache: ${randomSong.title} by ${randomSong.artist}")
            setQueue(randomSongs, randomIndex)
            playSpecificSong(randomSong, randomIndex)
        } else {
            LogManager.d("MusicStore", "No random songs in cache, fetching from server")
            // If no random songs loaded, try to get some first
            storeScope.launch {
                try {
                    val repository = getCurrentRepository()
                    if (repository == null) {
                        LogManager.w("MusicStore", "No repository available for random songs")
                        return@launch
                    }
                    
                    val result = repository.getRandomSongs(100)
                    if (result.isSuccess) {
                        val songs = result.getOrNull() ?: emptyList()
                        LogManager.d("MusicStore", "Fetched ${songs.size} random songs from server")
                        if (songs.isNotEmpty()) {
                            _state.value = _state.value.copy(randomSongs = songs)
                            val firstSong = songs.first()
                            LogManager.i("MusicStore", "Playing random song from server: ${firstSong.title} by ${firstSong.artist}")
                            setQueue(songs, 0)
                            playSpecificSong(firstSong, 0)
                        } else {
                            LogManager.w("MusicStore", "No random songs available from server")
                        }
                    } else {
                        LogManager.e("MusicStore", "Failed to fetch random songs: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    LogManager.e("MusicStore", "Failed to load random songs", e)
                    _state.value = _state.value.copy(
                        homeError = "Failed to load random songs: ${e.message}"
                    )
                }
            }
        }
    }

    private fun playSong(song: Song) {
        LogManager.i("MusicStore", "Playing song: ${song.title} by ${song.artist} (ID: ${song.id})")
        // Create a queue with just this song for now
        setQueue(listOf(song), 0)
        playSpecificSong(song, 0)
    }

    private fun playAlbum(album: Album) {
        LogManager.i("MusicStore", "Playing album: ${album.name} by ${album.artist} (ID: ${album.id})")
        storeScope.launch {
            try {
                if (album.songs.isNotEmpty()) {
                    LogManager.d("MusicStore", "Album contains ${album.songs.size} songs")
                    LogManager.i("MusicStore", "Starting album playback with ${album.songs.size} songs")
                    setQueue(album.songs, 0)
                    playSpecificSong(album.songs[0], 0)
                } else {
                    LogManager.d("MusicStore", "Fetching album details for: ${album.id}")
                    
                    val repository = getCurrentRepository()
                    if (repository == null) {
                        LogManager.w("MusicStore", "No repository available for album details")
                        return@launch
                    }

                    val albumResult = repository.getAlbum(album.id)
                    if (albumResult.isSuccess) {
                        val albumData = albumResult.getOrNull()
                        val songs = albumData?.songs ?: emptyList()
                        LogManager.d("MusicStore", "Album contains ${songs.size} songs")
                        if (songs.isNotEmpty()) {
                            LogManager.i("MusicStore", "Starting album playback with ${songs.size} songs")
                            setQueue(songs, 0)
                            playSpecificSong(songs[0], 0)
                        } else {
                            LogManager.w("MusicStore", "Album ${album.name} contains no songs")
                        }
                    } else {
                        LogManager.e("MusicStore", "Failed to load album: ${albumResult.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                LogManager.e("MusicStore", "Failed to load album songs for ${album.name}", e)
                _state.value = _state.value.copy(
                    homeError = "Failed to load album songs: ${e.message}"
                )
            }
        }
    }

    private fun setQueue(songs: List<Song>, startIndex: Int) {
        LogManager.i("MusicStore", "Setting queue with ${songs.size} songs, starting at index $startIndex")
        musicPlayer.setQueue(songs, startIndex)
        // Persist the queue with the actual values we just set
        persistQueue(songs, startIndex)
        LogManager.d("MusicStore", "Queue set in music player and persisted")
    }

    private fun playSpecificSong(song: Song, index: Int) {
        LogManager.i("MusicStore", "Playing specific song: ${song.title} at index $index")
        musicPlayer.playAtIndex(index)
        // State will be updated automatically through observers
        LogManager.d("MusicStore", "Song playback started at index $index")
        // Persist current state
        persistPlaybackState()
    }
    
    private fun loadPersistedQueue() {
        LogManager.d("MusicStore", "Loading persisted playback queue")
        val savedQueue = settingsRepository.getPlaybackQueue()
        val savedIndex = settingsRepository.getCurrentSongIndex()
        val savedPosition = settingsRepository.getCurrentSongPosition()
        
        if (savedQueue != null && savedQueue.isNotEmpty()) {
            LogManager.i("MusicStore", "Restoring queue with ${savedQueue.size} songs, index: $savedIndex, position: ${savedPosition}ms")
            val validIndex = savedIndex.coerceIn(0, savedQueue.size - 1)
            
            // Set queue in music player without auto-playing
            musicPlayer.setQueue(savedQueue, validIndex)
            
            // Restore position if valid
            if (savedPosition > 0) {
                musicPlayer.setCurrentPosition(savedPosition)
            }
            
            // Update store state with restored data
            val currentSong = savedQueue.getOrNull(validIndex)
            _state.value = _state.value.copy(
                queue = savedQueue,
                currentIndex = validIndex,
                currentSong = currentSong,
                currentPosition = savedPosition
            )
            
            // Apply shuffle and repeat states to music player 
            // (they should already be loaded by loadPersistedPlaybackModes)
            musicPlayer.setShuffle(_state.value.isShuffle)
            musicPlayer.setRepeat(_state.value.isRepeat)
            
            LogManager.d("MusicStore", "Persisted state restored successfully")
        } else {
            LogManager.d("MusicStore", "No persisted queue found")
        }
    }
    
    private fun persistQueue(songs: List<Song>? = null, startIndex: Int? = null) {
        LogManager.d("MusicStore", "Persisting playback queue")
        val currentState = _state.value
        
        // Use provided values if available (immediate persistence), otherwise use current state
        val queueToSave = songs ?: currentState.queue
        val indexToSave = startIndex ?: currentState.currentIndex
        
        settingsRepository.savePlaybackQueue(queueToSave)
        settingsRepository.saveCurrentSongIndex(indexToSave)
        LogManager.v("MusicStore", "Queue persisted: ${queueToSave.size} songs, index: $indexToSave")
    }
    
    private fun persistPlaybackState() {
//        LogManager.d("MusicStore", "Persisting playback state")
        val currentState = _state.value
        settingsRepository.saveCurrentSongIndex(currentState.currentIndex)
        settingsRepository.saveCurrentSongPosition(currentState.currentPosition)
//        LogManager.v("MusicStore", "Playback state persisted: index=${currentState.currentIndex}, position=${currentState.currentPosition}ms")
    }
    
    private fun loadPersistedVolume() {
        LogManager.d("MusicStore", "Loading persisted volume")
        try {
            val savedVolume = settingsRepository.getVolume()
            LogManager.i("MusicStore", "Restoring volume: $savedVolume")
            
            // Update state with persisted volume
            _state.value = _state.value.copy(volume = savedVolume)
            
            // Apply volume to music player directly (without triggering persistence)
            musicPlayer.setVolumeWithoutPersistence(savedVolume)
            
            LogManager.d("MusicStore", "Volume restored successfully")
        } catch (e: Exception) {
            LogManager.e("MusicStore", "Failed to load persisted volume", e)
        }
    }
    
    private fun loadPersistedPlaybackModes() {
        LogManager.d("MusicStore", "Loading persisted playback modes")
        try {
            val savedShuffle = settingsRepository.getShuffle()
            val savedRepeat = settingsRepository.getRepeat()
            LogManager.i("MusicStore", "Restoring playback modes: shuffle=$savedShuffle, repeat=$savedRepeat")
            
            // Update state with persisted playback modes
            _state.value = _state.value.copy(
                isShuffle = savedShuffle,
                isRepeat = savedRepeat
            )
            
            // Apply modes to music player
            musicPlayer.setShuffle(savedShuffle)
            musicPlayer.setRepeat(savedRepeat)
            
            LogManager.d("MusicStore", "Playback modes restored successfully")
        } catch (e: Exception) {
            LogManager.e("MusicStore", "Failed to load persisted playback modes", e)
        }
    }

    private fun observePlayerState() {
        LogManager.d("MusicStore", "Starting player state observation")
        storeScope.launch {
            // Observe music player state changes and sync with store state
            musicPlayer.isPlaying.collect { isPlaying ->
                LogManager.v("MusicStore", "Player state changed: isPlaying=$isPlaying")
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        }
        
        storeScope.launch {
            // Observe current position changes
            musicPlayer.currentPosition.collect { position ->
                _state.value = _state.value.copy(currentPosition = position)
                // Persist position periodically (every 10 seconds to avoid too frequent writes)
                if (position % 10000 < 1000) { // roughly every 10 seconds
                    persistPlaybackState()
                }

                // Scrobble logic: report to Subsonic if >60% played and not yet reported
                val state = _state.value
                val song = state.currentSong
                val duration = state.duration
                if (song != null && duration > 0) {
                    val progress = position.toDouble() / duration
                    if (progress > 0.6 && !scrobbledSongIds.contains(song.id)) {
                        scrobbledSongIds.add(song.id)
                        storeScope.launch {
                            try {
                                val repository = getCurrentRepository()
                                if (repository != null) {
                                    repository.scrobbleSong(song.id)
                                    LogManager.i("MusicStore", "Scrobbled song: ${song.title} (${song.id}) at >60% played")
                                } else {
                                    LogManager.w("MusicStore", "No repository available for scrobbling")
                                }
                            } catch (e: Exception) {
                                LogManager.w("MusicStore", "Failed to scrobble song: ${song.title} (${song.id}): $e")
                            }
                        }
                    }
                }
            }
        }
        
        storeScope.launch {
            // Observe duration changes
            musicPlayer.duration.collect { duration ->
                _state.value = _state.value.copy(duration = duration)
            }
        }
        
        storeScope.launch {
            // Observe current song changes
            musicPlayer.currentSong.collect { song ->
                LogManager.v("MusicStore", "Current song changed: ${song?.title}")
                _state.value = _state.value.copy(currentSong = song)

                // Reset scrobble state for new song
                song?.let { if (!scrobbledSongIds.contains(it.id)) scrobbledSongIds.removeAll { id -> id != it.id } }
            }
        }
        
        storeScope.launch {
            // Observe queue changes
            musicPlayer.queue.collect { queue ->
                LogManager.v("MusicStore", "Queue changed: ${queue.size} songs")
                _state.value = _state.value.copy(queue = queue)
                // Persist queue when it changes
                if (queue.isNotEmpty()) {
                    persistQueue()
                }
            }
        }
        
        storeScope.launch {
            // Observe current index changes
            musicPlayer.currentIndex.collect { index ->
                LogManager.v("MusicStore", "Current index changed: $index")
                _state.value = _state.value.copy(currentIndex = index)
                // Persist index when it changes
                persistQueue()
            }
        }
        
        LogManager.d("MusicStore", "Player state observers started")
    }

    private fun invalidatePlayerState() {
        LogManager.i("MusicStore", "Invalidating player state due to server change")
        
        storeScope.launch {
            try {
                // Stop current playback
                musicPlayer.stop()
                
                // Clear all music state
                _state.value = MusicState()
                
                // Clear cached content
                settingsRepository.clearHomeContentCache()
                
                // Clear saved playback state 
                settingsRepository.savePlaybackQueue(emptyList())
                settingsRepository.saveCurrentSongIndex(0)
                settingsRepository.saveCurrentSongPosition(0L)

                LogManager.i("MusicStore", "Player state invalidated and caches cleared")

                // Repository instances are automatically managed by the manager
                // No need to refresh config as new instances are created per server

                // Refresh home content from the new server
                loadHomeContent()
            } catch (e: Exception) {
                LogManager.e("MusicStore", "Error invalidating player state", e)
            }
        }
    }
}
