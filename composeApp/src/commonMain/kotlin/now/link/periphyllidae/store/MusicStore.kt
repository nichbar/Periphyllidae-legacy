package now.link.periphyllidae.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.platform.MusicPlayer
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.Album

class MusicStore(
    private val subsonicRepository: SubsonicRepository,
    private val musicPlayer: MusicPlayer,
    private val settingsRepository: SettingsRepository
) {
    private val logger = Logger.withTag("MusicStore")
    
    // App-scoped coroutine scope for the store
    private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init {
        logger.i("Initializing MusicStore")
        // Listen to music player state changes
        observePlayerState()
        // Load persisted queue
        loadPersistedQueue()
        logger.d("MusicStore initialization completed")
    }

    fun handleIntent(intent: MusicIntent) {
        logger.d("Handling intent: ${intent::class.simpleName}")
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
        logger.d("Play requested")
        val currentState = _state.value
        currentState.currentSong?.let { song ->
            logger.i("Playing song: ${song.title} by ${song.artist}")
            musicPlayer.play(song)
            _state.value = currentState.copy(isPlaying = true)
        } ?: logger.w("Play requested but no current song available")
    }

    private fun pause() {
        logger.d("Pause requested")
        musicPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
        logger.i("Playback paused")
    }

    private fun next() {
        logger.d("Next song requested")
        val currentState = _state.value
        
        if (currentState.queue.isEmpty()) {
            logger.w("Next requested but queue is empty")
            return
        }
        
        val nextIndex = if (currentState.isShuffle) {
            val randomIndex = currentState.queue.indices.random()
            logger.d("Shuffle enabled, selecting random index: $randomIndex")
            randomIndex
        } else {
            val nextIndex = (currentState.currentIndex + 1) % currentState.queue.size
            logger.d("Sequential play, next index: $nextIndex")
            nextIndex
        }
        
        if (nextIndex < currentState.queue.size) {
            val nextSong = currentState.queue[nextIndex]
            logger.i("Playing next song: ${nextSong.title} (index: $nextIndex)")
            playSpecificSong(nextSong, nextIndex)
        } else {
            logger.w("Next index $nextIndex is out of bounds for queue size ${currentState.queue.size}")
        }
    }

    private fun previous() {
        logger.d("Previous song requested")
        val currentState = _state.value
        
        if (currentState.queue.isEmpty()) {
            logger.w("Previous requested but queue is empty")
            return
        }
        
        val prevIndex = if (currentState.currentIndex > 0) {
            currentState.currentIndex - 1
        } else {
            currentState.queue.size - 1
        }
        
        logger.d("Previous index calculated: $prevIndex")
        
        if (prevIndex >= 0 && prevIndex < currentState.queue.size) {
            val prevSong = currentState.queue[prevIndex]
            logger.i("Playing previous song: ${prevSong.title} (index: $prevIndex)")
            playSpecificSong(prevSong, prevIndex)
        } else {
            logger.w("Previous index $prevIndex is out of bounds for queue size ${currentState.queue.size}")
        }
    }

    private fun seekTo(position: Long) {
        logger.d("Seek requested to position: ${position}ms")
        musicPlayer.seekTo(position)
        _state.value = _state.value.copy(currentPosition = position)
        logger.i("Seeked to position: ${position}ms")
    }

    private fun toggleRepeat() {
        val newRepeat = !_state.value.isRepeat
        logger.d("Toggling repeat mode: $newRepeat")
        musicPlayer.setRepeat(newRepeat)
        _state.value = _state.value.copy(isRepeat = newRepeat)
        logger.i("Repeat mode changed to: $newRepeat")
    }

    private fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffle
        logger.d("Toggling shuffle mode: $newShuffle")
        musicPlayer.setShuffle(newShuffle)
        _state.value = _state.value.copy(isShuffle = newShuffle)
        logger.i("Shuffle mode changed to: $newShuffle")
    }

    private fun loadHomeContent() {
        if (_state.value.isLoadingHome) {
            logger.w("Home content loading already in progress, skipping")
            return
        }
        
        logger.i("Loading home content")
        _state.value = _state.value.copy(isLoadingHome = true, homeError = null)
        
        storeScope.launch {
            try {
                logger.d("Fetching playlists, albums, and songs from repository")
                val playlistsResult = subsonicRepository.getPlaylists()
                val recentlyPlayedResult = subsonicRepository.getRecentlyPlayedAlbums(10)
                val mostPlayedResult = subsonicRepository.getMostPlayedAlbums(10)
                val newestAlbumsResult = subsonicRepository.getNewestAlbums(10)
                val randomSongsResult = subsonicRepository.getRandomSongs(20)
                
                val playlists = if (playlistsResult.isSuccess) {
                    val result = playlistsResult.getOrNull() ?: emptyList()
                    logger.d("Loaded ${result.size} playlists")
                    result
                } else {
                    logger.w("Failed to load playlists: ${playlistsResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val mostPlayedSongs = if (mostPlayedResult.isSuccess) {
                    val result = mostPlayedResult.getOrNull() ?: emptyList()
                    logger.d("Loaded ${result.size} most played albums")
                    result
                } else {
                    logger.w("Failed to load most played albums: ${mostPlayedResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val recentlyPlayedSongs = if (recentlyPlayedResult.isSuccess) {
                    val result = recentlyPlayedResult.getOrNull() ?: emptyList()
                    logger.d("Loaded ${result.size} recently played albums")
                    result
                } else {
                    logger.w("Failed to load recently played albums: ${recentlyPlayedResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val newestAlbums = if (newestAlbumsResult.isSuccess) {
                    val result = newestAlbumsResult.getOrNull() ?: emptyList()
                    logger.d("Loaded ${result.size} newest albums")
                    result
                } else {
                    logger.w("Failed to load newest albums: ${newestAlbumsResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val randomSongs = if (randomSongsResult.isSuccess) {
                    val result = randomSongsResult.getOrNull() ?: emptyList()
                    logger.d("Loaded ${result.size} random songs")
                    result
                } else {
                    logger.w("Failed to load random songs: ${randomSongsResult.exceptionOrNull()?.message}")
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
                
                logger.i("Home content loaded successfully")
            } catch (e: Exception) {
                logger.e("Failed to load home content", e)
                _state.value = _state.value.copy(
                    isLoadingHome = false,
                    homeError = "Failed to load content: ${e.message}"
                )
            }
        }
    }

    private fun refreshHomeContent() {
        logger.i("Refreshing home content")
        loadHomeContent()
    }

    private fun playRandomSong() {
        logger.d("Random song play requested")
        val randomSongs = _state.value.randomSongs
        if (randomSongs.isNotEmpty()) {
            val randomSong = randomSongs.random()
            logger.i("Playing random song from cache: ${randomSong.title} by ${randomSong.artist}")
            playSong(randomSong)
        } else {
            logger.d("No random songs in cache, fetching from server")
            // If no random songs loaded, try to get some first
            storeScope.launch {
                try {
                    val result = subsonicRepository.getRandomSongs(50)
                    if (result.isSuccess) {
                        val songs = result.getOrNull() ?: emptyList()
                        logger.d("Fetched ${songs.size} random songs from server")
                        if (songs.isNotEmpty()) {
                            _state.value = _state.value.copy(randomSongs = songs)
                            val randomSong = songs.random()
                            logger.i("Playing random song from server: ${randomSong.title} by ${randomSong.artist}")
                            playSong(randomSong)
                        } else {
                            logger.w("No random songs available from server")
                        }
                    } else {
                        logger.e("Failed to fetch random songs: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    logger.e("Failed to load random songs", e)
                    _state.value = _state.value.copy(
                        homeError = "Failed to load random songs: ${e.message}"
                    )
                }
            }
        }
    }

    private fun playSong(song: Song) {
        logger.i("Playing song: ${song.title} by ${song.artist} (ID: ${song.id})")
        // Create a queue with just this song for now
        setQueue(listOf(song), 0)
        playSpecificSong(song, 0)
    }

    private fun playAlbum(album: Album) {
        logger.i("Playing album: ${album.name} by ${album.artist} (ID: ${album.id})")
        storeScope.launch {
            try {
                logger.d("Fetching album details for: ${album.id}")
                val albumResult = subsonicRepository.getAlbum(album.id)
                if (albumResult.isSuccess) {
                    val albumData = albumResult.getOrNull()
                    val songs = albumData?.songs ?: emptyList()
                    logger.d("Album contains ${songs.size} songs")
                    if (songs.isNotEmpty()) {
                        logger.i("Starting album playback with ${songs.size} songs")
                        setQueue(songs, 0)
                        playSpecificSong(songs[0], 0)
                    } else {
                        logger.w("Album ${album.name} contains no songs")
                    }
                } else {
                    logger.e("Failed to load album: ${albumResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                logger.e("Failed to load album songs for ${album.name}", e)
                _state.value = _state.value.copy(
                    homeError = "Failed to load album songs: ${e.message}"
                )
            }
        }
    }

    private fun setQueue(songs: List<Song>, startIndex: Int) {
        logger.i("Setting queue with ${songs.size} songs, starting at index $startIndex")
        _state.value = _state.value.copy(
            queue = songs,
            currentIndex = startIndex
        )
        // Persist the queue
        persistQueue()
        logger.d("Queue persisted with ${songs.size} songs")
    }

    private fun playSpecificSong(song: Song, index: Int) {
        logger.i("Playing specific song: ${song.title} at index $index")
        musicPlayer.play(song)
        _state.value = _state.value.copy(
            currentSong = song,
            currentIndex = index,
            isPlaying = true,
            currentPosition = 0L,
            duration = song.duration?.times(1000L) ?: 0L
        )
        logger.d("Song duration set to: ${song.duration?.times(1000L) ?: 0L}ms")
        // Persist current state
        persistPlaybackState()
    }
    
    private fun loadPersistedQueue() {
        logger.d("Loading persisted playback queue")
        val savedQueue = settingsRepository.getPlaybackQueue()
        val savedIndex = settingsRepository.getCurrentSongIndex()
        val savedPosition = settingsRepository.getCurrentSongPosition()
        
        if (savedQueue != null && savedQueue.isNotEmpty()) {
            logger.i("Restored queue with ${savedQueue.size} songs, index: $savedIndex, position: ${savedPosition}ms")
            _state.value = _state.value.copy(
                queue = savedQueue,
                currentIndex = savedIndex.coerceIn(0, savedQueue.size - 1),
                currentSong = savedQueue.getOrNull(savedIndex),
                currentPosition = savedPosition
            )
        } else {
            logger.d("No persisted queue found")
        }
    }
    
    private fun persistQueue() {
        logger.d("Persisting playback queue")
        val currentState = _state.value
        settingsRepository.savePlaybackQueue(currentState.queue)
        settingsRepository.saveCurrentSongIndex(currentState.currentIndex)
        logger.v("Queue persisted: ${currentState.queue.size} songs, index: ${currentState.currentIndex}")
    }
    
    private fun persistPlaybackState() {
        logger.d("Persisting playback state")
        val currentState = _state.value
        settingsRepository.saveCurrentSongIndex(currentState.currentIndex)
        settingsRepository.saveCurrentSongPosition(currentState.currentPosition)
        logger.v("Playback state persisted: index=${currentState.currentIndex}, position=${currentState.currentPosition}ms")
    }

    private fun observePlayerState() {
        logger.d("Starting player state observation")
        storeScope.launch {
            // This would ideally observe the actual music player state
            // For now, we'll update state manually in the methods above
            logger.v("Player state observer started")
        }
    }
}
