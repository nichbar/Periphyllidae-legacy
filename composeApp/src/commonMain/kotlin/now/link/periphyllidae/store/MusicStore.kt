package now.link.periphyllidae.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.platform.MusicPlayer
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.utils.LogManager

class MusicStore(
    private val subsonicRepository: SubsonicRepository,
    private val musicPlayer: MusicPlayer,
    private val settingsRepository: SettingsRepository
) {
    // App-scoped coroutine scope for the store
    private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init {
        LogManager.i("MusicStore", "Initializing MusicStore")
        // Listen to music player state changes
        observePlayerState()
        // Load persisted queue
        loadPersistedQueue()
        LogManager.d("MusicStore", "MusicStore initialization completed")
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
            is MusicIntent.LoadHomeContent -> loadHomeContent()
            is MusicIntent.RefreshHomeContent -> refreshHomeContent()
            is MusicIntent.PlayRandomSong -> playRandomSong()
            is MusicIntent.PlaySong -> playSong(intent.song)
            is MusicIntent.PlayAlbum -> playAlbum(intent.album)
            is MusicIntent.SetQueue -> setQueue(intent.songs, intent.startIndex)
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
            } else {
                // Start playing the current song
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

    private fun toggleRepeat() {
        val newRepeat = !_state.value.isRepeat
        LogManager.d("MusicStore", "Toggling repeat mode: $newRepeat")
        musicPlayer.setRepeat(newRepeat)
        _state.value = _state.value.copy(isRepeat = newRepeat)
        LogManager.i("MusicStore", "Repeat mode changed to: $newRepeat")
    }

    private fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffle
        LogManager.d("MusicStore", "Toggling shuffle mode: $newShuffle")
        musicPlayer.setShuffle(newShuffle)
        _state.value = _state.value.copy(isShuffle = newShuffle)
        LogManager.i("MusicStore", "Shuffle mode changed to: $newShuffle")
    }

    private fun loadHomeContent() {
        if (_state.value.isLoadingHome) {
            LogManager.w("MusicStore", "Home content loading already in progress, skipping")
            return
        }
        
        LogManager.i("MusicStore", "Loading home content")
        _state.value = _state.value.copy(isLoadingHome = true, homeError = null)
        
        storeScope.launch {
            try {
                LogManager.d("MusicStore", "Fetching playlists, albums, and songs from repository")
                val playlistsResult = subsonicRepository.getPlaylists()
                val recentlyPlayedResult = subsonicRepository.getRecentlyPlayedAlbums(10)
                val mostPlayedResult = subsonicRepository.getMostPlayedAlbums(10)
                val newestAlbumsResult = subsonicRepository.getNewestAlbums(10)
                val randomSongsResult = subsonicRepository.getRandomSongs(20)
                
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
                
                _state.value = _state.value.copy(
                    isLoadingHome = false,
                    randomSongs = randomSongs,
                    playlists = playlists,
                    mostPlayedAlbums = mostPlayedSongs,
                    recentlyPlayedAlbums = recentlyPlayedSongs,
                    newestAlbums = newestAlbums,
                    homeError = null
                )
                
                LogManager.i("MusicStore", "Home content loaded successfully")
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
            LogManager.i("MusicStore", "Playing random song from cache: ${randomSong.title} by ${randomSong.artist}")
            playSong(randomSong)
        } else {
            LogManager.d("MusicStore", "No random songs in cache, fetching from server")
            // If no random songs loaded, try to get some first
            storeScope.launch {
                try {
                    val result = subsonicRepository.getRandomSongs(50)
                    if (result.isSuccess) {
                        val songs = result.getOrNull() ?: emptyList()
                        LogManager.d("MusicStore", "Fetched ${songs.size} random songs from server")
                        if (songs.isNotEmpty()) {
                            _state.value = _state.value.copy(randomSongs = songs)
                            val randomSong = songs.random()
                            LogManager.i("MusicStore", "Playing random song from server: ${randomSong.title} by ${randomSong.artist}")
                            playSong(randomSong)
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

                    val albumResult = subsonicRepository.getAlbum(album.id)
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
        // Persist the queue
        persistQueue()
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
            
            // Sync shuffle and repeat states with the current store state
            musicPlayer.setShuffle(_state.value.isShuffle)
            musicPlayer.setRepeat(_state.value.isRepeat)
            
            LogManager.d("MusicStore", "Persisted state restored successfully")
        } else {
            LogManager.d("MusicStore", "No persisted queue found")
        }
    }
    
    private fun persistQueue() {
        LogManager.d("MusicStore", "Persisting playback queue")
        val currentState = _state.value
        settingsRepository.savePlaybackQueue(currentState.queue)
        settingsRepository.saveCurrentSongIndex(currentState.currentIndex)
        LogManager.v("MusicStore", "Queue persisted: ${currentState.queue.size} songs, index: ${currentState.currentIndex}")
    }
    
    private fun persistPlaybackState() {
        LogManager.d("MusicStore", "Persisting playback state")
        val currentState = _state.value
        settingsRepository.saveCurrentSongIndex(currentState.currentIndex)
        settingsRepository.saveCurrentSongPosition(currentState.currentPosition)
        LogManager.v("MusicStore", "Playback state persisted: index=${currentState.currentIndex}, position=${currentState.currentPosition}ms")
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
            }
        }
        
        storeScope.launch {
            // Observe queue changes
            musicPlayer.queue.collect { queue ->
                LogManager.v("MusicStore", "Queue changed: ${queue.size} songs")
                _state.value = _state.value.copy(queue = queue)
            }
        }
        
        storeScope.launch {
            // Observe current index changes
            musicPlayer.currentIndex.collect { index ->
                LogManager.v("MusicStore", "Current index changed: $index")
                _state.value = _state.value.copy(currentIndex = index)
            }
        }
        
        LogManager.d("MusicStore", "Player state observers started")
    }
}
