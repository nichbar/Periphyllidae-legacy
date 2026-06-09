package now.link.periphyllidae.store

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.repository.SubsonicRepositoryManager
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.platform.MusicPlayer
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Artist
import now.link.periphyllidae.models.HomeContentCache
import now.link.periphyllidae.platform.PlaybackState
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.utils.ApplicationScopeManager

class MusicStore(
    private val subsonicRepositoryManager: SubsonicRepositoryManager,
    private val musicPlayer: MusicPlayer,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "MusicStore"
        private const val POSITION_PERSIST_THROTTLE_MS = 10_000L
        private const val PERSISTENCE_DIAGNOSTIC_WINDOW_MS = 60_000L
    }

    // Use shared application scope to prevent memory leaks
    private val storeScope = ApplicationScopeManager.scope

    // Track which song IDs have been scrobbled in this session
    private val scrobbledSongIds = mutableSetOf<String>()

    private var lastPositionPersistAtMs = 0L
    private var lastPersistedSongId: String? = null
    private var persistenceWriteCountInWindow = 0
    private var persistenceWindowStartMs = 0L

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init {
        LogManager.i(TAG, "Initializing MusicStore")
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
        LogManager.d(TAG, "MusicStore initialization completed")
    }
    
    /**
     * Helper function to get the current repository instance
     * Returns null if no repository is active (user not authenticated)
     */
    private fun getCurrentRepository() = try {
        subsonicRepositoryManager.getCurrentRepository()
    } catch (e: IllegalStateException) {
        LogManager.w(TAG, "No active repository: ${e.message}")
        null
    }

    fun handleIntent(intent: MusicIntent) {
        LogManager.d(TAG, "Handling intent: ${intent::class.simpleName}")
        when (intent) {
            is MusicIntent.Play -> play()
            is MusicIntent.Pause -> pause()
            is MusicIntent.Stop -> stop()
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
            is MusicIntent.PlayAlbumFromPosition -> playAlbumFromPosition(intent.album, intent.startIndex)
            is MusicIntent.PlayAllSongsOfArtist -> playAllSongsOfArtist(intent.artist)
            is MusicIntent.SetQueue -> setQueue(intent.songs, intent.startIndex)
            is MusicIntent.PlayQueueItem -> playQueueItem(intent.index)
            is MusicIntent.MoveQueueItem -> moveQueueItem(intent.fromIndex, intent.toIndex)
            is MusicIntent.RemoveQueueItem -> removeQueueItem(intent.index)
            is MusicIntent.ClearQueue -> clearQueue()
            is MusicIntent.GetSongDetails -> getSongDetails(intent.songId)
            is MusicIntent.InvalidatePlayerState -> invalidatePlayerState()
        }
    }

    private fun play() {
        LogManager.d(TAG, "Play requested")
        val currentState = _state.value
        currentState.currentSong?.let { song ->
            LogManager.i(TAG, "Playing song: ${song.title} by ${song.artist}")
            if (musicPlayer.canResume()) {
                // Resume paused playback
                LogManager.d(TAG, "Resuming paused playback")
                musicPlayer.resume()
            } else if (currentState.currentPosition > 0 && !musicPlayer.isCurrentlyPlaying()) {
                // We have a saved position (likely from app restart), start playback and seek to saved position
                LogManager.d(TAG, "Starting playback with saved position: ${currentState.currentPosition}ms")
                musicPlayer.play(song)
                // Seek to saved position after a short delay to allow media to load
                storeScope.launch {
                    delay(500) // Wait for media to load
                    musicPlayer.seekTo(currentState.currentPosition)
                    LogManager.i(TAG, "Resumed from saved position: ${currentState.currentPosition}ms")
                }
            } else {
                // Stop current playback before switching songs
                musicPlayer.stop()

                // Start playing the current song from the beginning
                LogManager.d(TAG, "Starting fresh playback")
                musicPlayer.play(song)
            }
            // State will be updated automatically through observers
        } ?: LogManager.w(TAG, "Play requested but no current song available")
    }

    private fun pause() {
        LogManager.d(TAG, "Pause requested")
        musicPlayer.pause()
        // State will be updated automatically through observers
        LogManager.i(TAG, "Playback paused")
    }

    private fun stop() {
        LogManager.d(TAG, "Stop requested")
        // Keep queue/current song so playback can resume after notification stop.
        musicPlayer.pause()
        // State will be updated automatically through observers
        LogManager.i(TAG, "Playback stopped (queue preserved)")
    }

    private fun next() {
        LogManager.d(TAG, "Next song requested")
        musicPlayer.next()
        // State will be updated automatically through observers
        LogManager.d(TAG, "Next song initiated")
    }

    private fun previous() {
        LogManager.d(TAG, "Previous song requested")
        musicPlayer.previous()
        // State will be updated automatically through observers
        LogManager.d(TAG, "Previous song initiated")
    }

    private fun seekTo(position: Long) {
        LogManager.d(TAG, "Seek requested to position: ${position}ms")
        musicPlayer.seekTo(position)
        // State will be updated automatically through observers
        LogManager.i(TAG, "Seeked to position: ${position}ms")
    }

    private fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        LogManager.d(TAG, "Setting volume to: $clampedVolume")
        musicPlayer.setVolume(clampedVolume)
        _state.value = _state.value.copy(volume = clampedVolume)
        // Save volume to settings for persistence
        settingsRepository.saveVolume(clampedVolume)
        LogManager.i(TAG, "Volume changed to: $clampedVolume and saved to settings")
    }

    private fun toggleRepeat() {
        val newRepeat = !_state.value.isRepeat
        LogManager.d(TAG, "Toggling repeat mode: $newRepeat")
        musicPlayer.setRepeat(newRepeat)
        _state.value = _state.value.copy(isRepeat = newRepeat)
        // Save repeat state for persistence
        settingsRepository.saveRepeat(newRepeat)
        LogManager.i(TAG, "Repeat mode changed to: $newRepeat and saved to settings")
    }

    private fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffle
        LogManager.d(TAG, "Toggling shuffle mode: $newShuffle")
        musicPlayer.setShuffle(newShuffle)
        _state.value = _state.value.copy(isShuffle = newShuffle)
        // Save shuffle state for persistence
        settingsRepository.saveShuffle(newShuffle)
        LogManager.i(TAG, "Shuffle mode changed to: $newShuffle and saved to settings")
    }

    private fun loadCachedHomeContent() {
        LogManager.d(TAG, "Loading cached home content for immediate display")
        val cachedContent = settingsRepository.getHomeContentCache()
        if (cachedContent != null) {
            LogManager.d(TAG, "Found cached home content from ${System.currentTimeMillis() - cachedContent.timestamp}ms ago")
            _state.value = _state.value.copy(
                randomSongs = cachedContent.randomSongs,
                playlists = cachedContent.playlists,
                mostPlayedAlbums = cachedContent.mostPlayedAlbums,
                recentlyPlayedAlbums = cachedContent.recentlyPlayedAlbums,
                newestAlbums = cachedContent.newestAlbums
            )
            LogManager.i(TAG, "Cached home content loaded successfully")
        } else {
            LogManager.d(TAG, "No cached home content found")
        }
    }

    private fun loadHomeContent() {
        if (_state.value.isLoadingHome) {
            LogManager.w(TAG, "Home content loading already in progress, skipping")
            return
        }
        
        LogManager.i(TAG, "Loading home content")
        
        // Try to load cached content first
        val cachedContent = settingsRepository.getHomeContentCache()
        if (cachedContent != null) {
            LogManager.d(TAG, "Found cached home content from ${(System.currentTimeMillis() - cachedContent.timestamp) / 1000}S ago")
            _state.value = _state.value.copy(
                isLoadingHome = false,
                randomSongs = cachedContent.randomSongs,
                playlists = cachedContent.playlists,
                mostPlayedAlbums = cachedContent.mostPlayedAlbums,
                recentlyPlayedAlbums = cachedContent.recentlyPlayedAlbums,
                newestAlbums = cachedContent.newestAlbums,
                homeError = null
            )
            LogManager.i(TAG, "Loaded cached home content successfully")
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
                
                LogManager.d(TAG, "Fetching playlists, albums, and songs from repository")
                
                // Run all API calls concurrently for better performance
                val playlistsDeferred = async { repository.getPlaylists() }
                val recentlyPlayedDeferred = async { repository.getRecentlyPlayedAlbums(10) }
                val mostPlayedDeferred = async { repository.getMostPlayedAlbums(10) }
                val newestAlbumsDeferred = async { repository.getNewestAlbums(10) }
                val randomSongsDeferred = async { repository.getRandomSongs(100) }
                
                // Wait for all results
                val playlistsResult = playlistsDeferred.await()
                val recentlyPlayedResult = recentlyPlayedDeferred.await()
                val mostPlayedResult = mostPlayedDeferred.await()
                val newestAlbumsResult = newestAlbumsDeferred.await()
                val randomSongsResult = randomSongsDeferred.await()
                val currentState = _state.value
                val failedSections = mutableListOf<String>()

                val playlists = if (playlistsResult.isSuccess) {
                    val result = playlistsResult.getOrNull() ?: emptyList()
                    LogManager.d(TAG, "Loaded ${result.size} playlists")
                    result
                } else {
                    LogManager.w(TAG, "Failed to load playlists: ${playlistsResult.exceptionOrNull()?.message}")
                    failedSections.add("playlists")
                    currentState.playlists
                }
                
                val mostPlayedSongs = if (mostPlayedResult.isSuccess) {
                    val result = mostPlayedResult.getOrNull() ?: emptyList()
                    LogManager.d(TAG, "Loaded ${result.size} most played albums")
                    result
                } else {
                    LogManager.w(TAG, "Failed to load most played albums: ${mostPlayedResult.exceptionOrNull()?.message}")
                    failedSections.add("most played")
                    currentState.mostPlayedAlbums
                }
                
                val recentlyPlayedSongs = if (recentlyPlayedResult.isSuccess) {
                    val result = recentlyPlayedResult.getOrNull() ?: emptyList()
                    LogManager.d(TAG, "Loaded ${result.size} recently played albums")
                    result
                } else {
                    LogManager.w(TAG, "Failed to load recently played albums: ${recentlyPlayedResult.exceptionOrNull()?.message}")
                    failedSections.add("recently played")
                    currentState.recentlyPlayedAlbums
                }
                
                val newestAlbums = if (newestAlbumsResult.isSuccess) {
                    val result = newestAlbumsResult.getOrNull() ?: emptyList()
                    LogManager.d(TAG, "Loaded ${result.size} newest albums")
                    result
                } else {
                    LogManager.w(TAG, "Failed to load newest albums: ${newestAlbumsResult.exceptionOrNull()?.message}")
                    failedSections.add("recently added")
                    currentState.newestAlbums
                }
                
                val randomSongs = if (randomSongsResult.isSuccess) {
                    val result = randomSongsResult.getOrNull() ?: emptyList()
                    LogManager.d(TAG, "Loaded ${result.size} random songs")
                    result
                } else {
                    LogManager.w(TAG, "Failed to load random songs: ${randomSongsResult.exceptionOrNull()?.message}")
                    failedSections.add("random songs")
                    currentState.randomSongs
                }

                val homeError = when {
                    failedSections.isEmpty() -> null
                    failedSections.size == 5 -> "Failed to load home content. Please try again."
                    else -> "Some home sections failed to load: ${failedSections.joinToString()}."
                }
                
                // Update state with fresh content
                _state.value = _state.value.copy(
                    isLoadingHome = false,
                    randomSongs = randomSongs,
                    playlists = playlists,
                    mostPlayedAlbums = mostPlayedSongs,
                    recentlyPlayedAlbums = recentlyPlayedSongs,
                    newestAlbums = newestAlbums,
                    homeError = homeError
                )
                
                if (failedSections.isEmpty()) {
                    val newCache = HomeContentCache(
                        randomSongs = randomSongs,
                        playlists = playlists,
                        mostPlayedAlbums = mostPlayedSongs,
                        recentlyPlayedAlbums = recentlyPlayedSongs,
                        newestAlbums = newestAlbums,
                        timestamp = System.currentTimeMillis()
                    )
                    settingsRepository.saveHomeContentCache(newCache)
                    LogManager.i(TAG, "Home content loaded and cached successfully")
                } else {
                    LogManager.w(TAG, "Home content loaded with failed sections: ${failedSections.joinToString()}")
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to load home content", e)
                _state.value = _state.value.copy(
                    isLoadingHome = false,
                    homeError = "Failed to load content: ${e.message}"
                )
            }
        }
    }

    private fun refreshHomeContent() {
        LogManager.i(TAG, "Refreshing home content")
        loadHomeContent()
    }

    private fun playRandomSong() {
        LogManager.d(TAG, "Random song play requested")
        val randomSongs = _state.value.randomSongs
        if (randomSongs.isNotEmpty()) {
            val randomSong = randomSongs.random()
            val randomIndex = randomSongs.indexOf(randomSong)
            LogManager.i(TAG, "Playing random song from cache: ${randomSong.title} by ${randomSong.artist}")
            setQueue(randomSongs, randomIndex)
            playSpecificSong(randomSong, randomIndex)
        } else {
            LogManager.d(TAG, "No random songs in cache, fetching from server")
            // If no random songs loaded, try to get some first
            storeScope.launch {
                try {
                    val repository = getCurrentRepository()
                    if (repository == null) {
                        LogManager.w(TAG, "No repository available for random songs")
                        _state.value = _state.value.copy(
                            homeError = "No server configured. Please login to a Subsonic server."
                        )
                        return@launch
                    }
                    
                    val result = repository.getRandomSongs(100)
                    if (result.isSuccess) {
                        val songs = result.getOrNull() ?: emptyList()
                        LogManager.d(TAG, "Fetched ${songs.size} random songs from server")
                        if (songs.isNotEmpty()) {
                            _state.value = _state.value.copy(randomSongs = songs)
                            val firstSong = songs.first()
                            LogManager.i(TAG, "Playing random song from server: ${firstSong.title} by ${firstSong.artist}")
                            setQueue(songs, 0)
                            playSpecificSong(firstSong, 0)
                        } else {
                            LogManager.w(TAG, "No random songs available from server")
                            _state.value = _state.value.copy(
                                homeError = "No random songs are available from this server."
                            )
                        }
                    } else {
                        LogManager.e(TAG, "Failed to fetch random songs: ${result.exceptionOrNull()?.message}")
                        _state.value = _state.value.copy(
                            homeError = "Failed to load random songs. Please try again."
                        )
                    }
                } catch (e: Exception) {
                    LogManager.e(TAG, "Failed to load random songs", e)
                    _state.value = _state.value.copy(
                        homeError = "Failed to load random songs: ${e.message}"
                    )
                }
            }
        }
    }

    private fun playSong(song: Song) {
        LogManager.i(TAG, "Playing song: ${song.title} by ${song.artist} (ID: ${song.id})")
        // Create a queue with just this song for now
        setQueue(listOf(song), 0)
        playSpecificSong(song, 0)
    }

    private fun playAlbum(album: Album) {
        LogManager.i(TAG, "Playing album: ${album.name} by ${album.artist} (ID: ${album.id})")
        playAlbumFromPosition(album, 0)
    }

    private fun playAlbumFromPosition(album: Album, startIndex: Int) {
        LogManager.i(TAG, "Playing album: ${album.name} by ${album.artist} (ID: ${album.id}) starting at index $startIndex")
        storeScope.launch {
            try {
                val songs = if (album.songs.isNotEmpty()) {
                    LogManager.d(TAG, "Album contains ${album.songs.size} songs")
                    album.songs
                } else {
                    LogManager.d(TAG, "Fetching album details for: ${album.id}")

                    val repository = getCurrentRepository()
                    if (repository == null) {
                        LogManager.w(TAG, "No repository available for album details")
                        _state.value = _state.value.copy(
                            homeError = "No server configured. Please login to a Subsonic server."
                        )
                        return@launch
                    }

                    val albumResult = repository.getAlbum(album.id)
                    if (albumResult.isSuccess) {
                        val albumData = albumResult.getOrNull()
                        val songs = albumData?.songs ?: emptyList()
                        LogManager.d(TAG, "Album contains ${songs.size} songs")
                        songs
                    } else {
                        LogManager.e(TAG, "Failed to load album: ${albumResult.exceptionOrNull()?.message}")
                        _state.value = _state.value.copy(
                            homeError = "Failed to load album songs. Please try again."
                        )
                        emptyList()
                    }
                }

                if (songs.isNotEmpty()) {
                    val validStartIndex = startIndex.coerceIn(0, songs.size - 1)
                    LogManager.i(TAG, "Starting album playback with ${songs.size} songs from index $validStartIndex")
                    setQueue(songs, validStartIndex)
                    playSpecificSong(songs[validStartIndex], validStartIndex)
                } else {
                    LogManager.w(TAG, "Album ${album.name} contains no playable songs")
                    _state.value = _state.value.copy(
                        homeError = "No playable songs found for ${album.name}."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to load album songs for ${album.name}", e)
                _state.value = _state.value.copy(
                    homeError = "Failed to load album songs: ${e.message}"
                )
            }
        }
    }

    private fun playAllSongsOfArtist(artist: Artist) {
        LogManager.i(TAG, "Playing all songs for artist: ${artist.name} (ID: ${artist.id})")
        storeScope.launch {
            try {
                val repository = getCurrentRepository()
                if (repository == null) {
                    LogManager.w(TAG, "No repository available for artist songs")
                    _state.value = _state.value.copy(
                        homeError = "No server configured. Please login to a Subsonic server."
                    )
                    return@launch
                }

                // Collect all songs from all albums using coroutines for better performance
                val allSongs = mutableListOf<Song>()
                
                if (artist.albums.isNotEmpty()) {
                    LogManager.d(TAG, "Fetching songs from ${artist.albums.size} albums for artist ${artist.name}")
                    
                    // Use async to fetch all albums concurrently for better performance
                    val albumSongResults = artist.albums.map { album ->
                        async {
                            try {
                                repository.getAlbumSongs(album.id)
                            } catch (e: Exception) {
                                LogManager.w(TAG, "Failed to get songs for album ${album.name}: ${e.message}")
                                Result.failure<List<Song>>(e)
                            }
                        }
                    }
                    
                    // Collect all successful results
                    albumSongResults.forEach { deferred ->
                        val result = deferred.await()
                        if (result.isSuccess) {
                            val songs = result.getOrNull() ?: emptyList()
                            allSongs.addAll(songs)
                            LogManager.d(TAG, "Added ${songs.size} songs from album")
                        }
                    }
                } else {
                    LogManager.d(TAG, "Artist has no albums, trying to get songs directly")
                    // Fallback: if artist has no albums but we still want to try playing
                    // This might happen with some server configurations
                }

                if (allSongs.isNotEmpty()) {
                    LogManager.i(TAG, "Starting artist playback with ${allSongs.size} total songs from ${artist.name}")
                    setQueue(allSongs, 0)
                    playSpecificSong(allSongs[0], 0)
                } else {
                    LogManager.w(TAG, "Artist ${artist.name} contains no playable songs")
                    _state.value = _state.value.copy(
                        homeError = "No playable songs found for ${artist.name}."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to load songs for artist ${artist.name}", e)
                _state.value = _state.value.copy(
                    homeError = "Failed to load artist songs: ${e.message}"
                )
            }
        }
    }

    private fun setQueue(songs: List<Song>, startIndex: Int) {
        LogManager.i(TAG, "Setting queue with ${songs.size} songs, starting at index $startIndex")
        val validStartIndex = startIndex.coerceIn(0, maxOf(0, songs.size - 1))
        musicPlayer.setQueue(songs, validStartIndex)
        _state.value = _state.value.copy(
            queue = songs,
            currentIndex = validStartIndex,
            currentSong = songs.getOrNull(validStartIndex),
            currentPosition = 0L,
            duration = 0L
        )
        // Persist the queue with the actual values we just set
        persistQueue(songs, validStartIndex)
        LogManager.d(TAG, "Queue set in music player and persisted")
    }

    private fun playQueueItem(index: Int) {
        val currentState = _state.value
        val song = currentState.queue.getOrNull(index)
        if (song == null) {
            LogManager.w(TAG, "Queue item play requested for invalid index $index")
            return
        }

        val isCurrentSong = currentState.currentIndex == index && currentState.currentSong?.id == song.id
        if (isCurrentSong) {
            LogManager.d(TAG, "Current queue item selected: ${song.title}")
            if (currentState.isPlaying) {
                pause()
            } else {
                play()
            }
            return
        }

        LogManager.i(TAG, "Playing queue item $index: ${song.title}")
        musicPlayer.playAtIndex(index)
        _state.value = currentState.copy(
            currentIndex = index,
            currentSong = song,
            currentPosition = 0L,
            duration = 0L
        )
        persistQueue(currentState.queue, index)
        persistPlaybackState()
    }

    private fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentState = _state.value
        val queue = currentState.queue
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) {
            LogManager.w(TAG, "Queue move ignored: from=$fromIndex, to=$toIndex, size=${queue.size}")
            return
        }

        val updatedQueue = queue.toMutableList()
        val movedSong = updatedQueue.removeAt(fromIndex)
        updatedQueue.add(toIndex, movedSong)

        val currentSongId = currentState.currentSong?.id
        val updatedCurrentIndex = currentSongId
            ?.let { id -> updatedQueue.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: currentState.currentIndex.coerceIn(0, updatedQueue.lastIndex)
        val updatedCurrentSong = updatedQueue.getOrNull(updatedCurrentIndex)

        LogManager.i(TAG, "Moved queue item from $fromIndex to $toIndex")
        musicPlayer.setQueue(updatedQueue, updatedCurrentIndex)
        _state.value = currentState.copy(
            queue = updatedQueue,
            currentIndex = updatedCurrentIndex,
            currentSong = updatedCurrentSong
        )
        persistQueue(updatedQueue, updatedCurrentIndex)
    }

    private fun removeQueueItem(index: Int) {
        val currentState = _state.value
        val queue = currentState.queue
        val removedSong = queue.getOrNull(index)
        if (removedSong == null) {
            LogManager.w(TAG, "Queue item remove requested for invalid index $index")
            return
        }

        val updatedQueue = queue.toMutableList().also { it.removeAt(index) }
        val removingCurrentSong = currentState.currentIndex == index || currentState.currentSong?.id == removedSong.id
        LogManager.i(TAG, "Removing queue item $index: ${removedSong.title}")

        if (updatedQueue.isEmpty()) {
            musicPlayer.clearQueue()
            _state.value = currentState.copy(
                queue = emptyList(),
                currentIndex = 0,
                currentSong = null,
                currentPosition = 0L,
                duration = 0L,
                isPlaying = false,
                playbackState = PlaybackState.IDLE
            )
            settingsRepository.savePlaybackQueue(emptyList())
            settingsRepository.saveCurrentSongIndex(0)
            settingsRepository.saveCurrentSongPosition(0L)
            return
        }

        val updatedCurrentIndex = when {
            removingCurrentSong -> index.coerceAtMost(updatedQueue.lastIndex)
            index < currentState.currentIndex -> currentState.currentIndex - 1
            else -> currentState.currentIndex.coerceIn(0, updatedQueue.lastIndex)
        }
        val updatedCurrentSong = updatedQueue[updatedCurrentIndex]

        if (removingCurrentSong) {
            musicPlayer.stop()
        }
        musicPlayer.setQueue(updatedQueue, updatedCurrentIndex)
        _state.value = currentState.copy(
            queue = updatedQueue,
            currentIndex = updatedCurrentIndex,
            currentSong = updatedCurrentSong,
            isPlaying = if (removingCurrentSong) false else currentState.isPlaying,
            playbackState = if (removingCurrentSong) PlaybackState.IDLE else currentState.playbackState,
            currentPosition = if (removingCurrentSong) 0L else currentState.currentPosition,
            duration = if (removingCurrentSong) 0L else currentState.duration
        )
        persistQueue(updatedQueue, updatedCurrentIndex)

        if (removingCurrentSong && currentState.isPlaying) {
            playSpecificSong(updatedCurrentSong, updatedCurrentIndex)
        } else if (removingCurrentSong) {
            settingsRepository.saveCurrentSongPosition(0L)
        }
    }

    private fun clearQueue() {
        LogManager.i(TAG, "Clearing playback queue")
        musicPlayer.clearQueue()
        _state.value = _state.value.copy(
            queue = emptyList(),
            currentIndex = 0,
            currentSong = null,
            currentPosition = 0L,
            duration = 0L,
            isPlaying = false,
            playbackState = PlaybackState.IDLE
        )
        settingsRepository.savePlaybackQueue(emptyList())
        settingsRepository.saveCurrentSongIndex(0)
        settingsRepository.saveCurrentSongPosition(0L)
    }

    private fun playSpecificSong(song: Song, index: Int) {
        LogManager.i(TAG, "Playing specific song: ${song.title} at index $index")
        musicPlayer.play(song)
        // State will be updated automatically through observers
        LogManager.d(TAG, "Song playback started at index $index")
        // Persist current state
        persistPlaybackState()
    }
    
    private fun loadPersistedQueue() {
        LogManager.d(TAG, "Loading persisted playback queue")
        val savedQueue = settingsRepository.getPlaybackQueue()
        val savedIndex = settingsRepository.getCurrentSongIndex()
        val savedPosition = settingsRepository.getCurrentSongPosition()
        
        if (savedQueue != null && savedQueue.isNotEmpty()) {
            LogManager.i(TAG, "Restoring queue with ${savedQueue.size} songs, index: $savedIndex, position: ${savedPosition}ms")
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
            
            LogManager.d(TAG, "Persisted state restored successfully")
        } else {
            LogManager.d(TAG, "No persisted queue found")
        }
    }
    
    private fun persistQueue(songs: List<Song>? = null, startIndex: Int? = null) {
        LogManager.d(TAG, "Persisting playback queue")
        val currentState = _state.value
        
        // Use provided values if available (immediate persistence), otherwise use current state
        val queueToSave = songs ?: currentState.queue
        val indexToSave = startIndex ?: currentState.currentIndex
        
        settingsRepository.savePlaybackQueue(queueToSave)
        settingsRepository.saveCurrentSongIndex(indexToSave)
        LogManager.v(TAG, "Queue persisted: ${queueToSave.size} songs, index: $indexToSave")
    }
    
    private fun persistPlaybackState() {
//        LogManager.d(TAG, "Persisting playback state")
        val currentState = _state.value
        settingsRepository.saveCurrentSongIndex(currentState.currentIndex)
        settingsRepository.saveCurrentSongPosition(currentState.currentPosition)
//        LogManager.v(TAG, "Playback state persisted: index=${currentState.currentIndex}, position=${currentState.currentPosition}ms")
    }

    private fun maybePersistPlaybackPosition(songId: String?) {
        val nowMs = Clock.System.now().toEpochMilliseconds()

        if (songId != null && songId != lastPersistedSongId) {
            lastPersistedSongId = songId
            lastPositionPersistAtMs = 0L
        }

        if (lastPositionPersistAtMs == 0L || nowMs - lastPositionPersistAtMs >= POSITION_PERSIST_THROTTLE_MS) {
            persistPlaybackState()
            lastPositionPersistAtMs = nowMs

            if (persistenceWindowStartMs == 0L) {
                persistenceWindowStartMs = nowMs
            }
            persistenceWriteCountInWindow++

            if (nowMs - persistenceWindowStartMs >= PERSISTENCE_DIAGNOSTIC_WINDOW_MS) {
                LogManager.d(
                    TAG,
                    "Playback position persistence cadence: writes=$persistenceWriteCountInWindow in ${PERSISTENCE_DIAGNOSTIC_WINDOW_MS / 1000}s"
                )
                persistenceWindowStartMs = nowMs
                persistenceWriteCountInWindow = 0
            }
        }
    }
    
    private fun loadPersistedVolume() {
        LogManager.d(TAG, "Loading persisted volume")
        try {
            val savedVolume = settingsRepository.getVolume()
            LogManager.i(TAG, "Restoring volume: $savedVolume")
            
            // Update state with persisted volume
            _state.value = _state.value.copy(volume = savedVolume)
            
            // Apply volume to music player directly (without triggering persistence)
            musicPlayer.setVolumeWithoutPersistence(savedVolume)
            
            LogManager.d(TAG, "Volume restored successfully")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to load persisted volume", e)
        }
    }
    
    private fun loadPersistedPlaybackModes() {
        LogManager.d(TAG, "Loading persisted playback modes")
        try {
            val savedShuffle = settingsRepository.getShuffle()
            val savedRepeat = settingsRepository.getRepeat()
            LogManager.i(TAG, "Restoring playback modes: shuffle=$savedShuffle, repeat=$savedRepeat")
            
            // Update state with persisted playback modes
            _state.value = _state.value.copy(
                isShuffle = savedShuffle,
                isRepeat = savedRepeat
            )
            
            // Apply modes to music player
            musicPlayer.setShuffle(savedShuffle)
            musicPlayer.setRepeat(savedRepeat)
            
            LogManager.d(TAG, "Playback modes restored successfully")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to load persisted playback modes", e)
        }
    }

    private fun observePlayerState() {
        LogManager.d(TAG, "Starting player state observation")
        storeScope.launch {
            // Observe music player state changes and sync with store state
            musicPlayer.isPlaying.collect { isPlaying ->
                LogManager.v(TAG, "Player state changed: isPlaying=$isPlaying")
                val currentState = _state.value
                if (currentState.isPlaying != isPlaying) {
                    LogManager.d(TAG, "Updating store playing state: ${currentState.isPlaying} -> $isPlaying")
                    _state.value = currentState.copy(isPlaying = isPlaying)
                }
            }
        }
        
        storeScope.launch {
            // Observe current position changes
            musicPlayer.currentPosition.collect { position ->
                val currentState = _state.value
                // Only update if position actually changed to avoid unnecessary state updates
                if (currentState.currentPosition != position) {
                    _state.value = currentState.copy(currentPosition = position)
                    
                    // Persist position with monotonic time-based throttling
                    maybePersistPlaybackPosition(currentState.currentSong?.id)

                    // Scrobble logic: report to Subsonic if >60% played and not yet reported
                    val song = currentState.currentSong
                    val duration = currentState.duration
                    if (song != null && duration > 0) {
                        val progress = position.toDouble() / duration
                        if (progress > 0.6 && !scrobbledSongIds.contains(song.id)) {
                            scrobbledSongIds.add(song.id)
                            storeScope.launch {
                                try {
                                    val repository = getCurrentRepository()
                                    if (repository != null) {
                                        repository.scrobbleSong(song.id)
                                        LogManager.i(TAG, "Scrobbled song: ${song.title} (${song.id}) at >60% played")
                                    } else {
                                        LogManager.w(TAG, "No repository available for scrobbling")
                                    }
                                } catch (e: Exception) {
                                    LogManager.w(TAG, "Failed to scrobble song: ${song.title} (${song.id}): $e")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        storeScope.launch {
            // Observe duration changes
            musicPlayer.duration.collect { duration ->
                val currentState = _state.value
                if (currentState.duration != duration) {
                    LogManager.v(TAG, "Duration changed: ${currentState.duration}ms -> ${duration}ms")
                    _state.value = currentState.copy(duration = duration)
                }
            }
        }
        
        storeScope.launch {
            // Observe current song changes
            musicPlayer.currentSong.collect { song ->
                val currentState = _state.value
                if (currentState.currentSong?.id != song?.id) {
                    LogManager.v(TAG, "Current song changed: ${currentState.currentSong?.title} -> ${song?.title}")
                    _state.value = currentState.copy(currentSong = song)

                    // Reset scrobble state for new song
                    song?.let { 
                        if (!scrobbledSongIds.contains(it.id)) {
                            // Remove scrobbled IDs for previous songs to prevent memory leak
                            scrobbledSongIds.removeAll { id -> id != it.id }
                        }
                    }
                }
            }
        }
        
        storeScope.launch {
            // Observe queue changes
            musicPlayer.queue.collect { queue ->
                val currentState = _state.value
                if (currentState.queue.size != queue.size || 
                    currentState.queue.zip(queue).any { (storeItem, playerItem) -> storeItem.id != playerItem.id }) {
                    LogManager.v(TAG, "Queue changed: ${currentState.queue.size} -> ${queue.size} songs")
                    _state.value = currentState.copy(queue = queue)
                    
                    // Persist queue when it changes
                    if (queue.isNotEmpty()) {
                        persistQueue()
                    }
                }
            }
        }
        
        storeScope.launch {
            // Observe current index changes
            musicPlayer.currentIndex.collect { index ->
                val currentState = _state.value
                if (currentState.currentIndex != index) {
                    LogManager.v(TAG, "Current index changed: ${currentState.currentIndex} -> $index")
                    _state.value = currentState.copy(currentIndex = index)
                    // Persist index when it changes
                    persistQueue()
                }
            }
        }
        
        storeScope.launch {
            // Observe playback state changes from MediaMP for additional validation
            musicPlayer.playbackState.collect { playbackState ->
                LogManager.v(TAG, "MediaMP playback state changed: $playbackState")
                
                val currentState = _state.value
                if (currentState.playbackState != playbackState) {
                    LogManager.d(TAG, "Updating store playback state: ${currentState.playbackState} -> $playbackState")
                    _state.value = currentState.copy(playbackState = playbackState)
                }
                
                // Validate critical state consistency when playback state changes
                val playerIsPlaying = playbackState == PlaybackState.PLAYING
                
                // Check for major inconsistencies that need immediate correction
                if (currentState.isPlaying != playerIsPlaying) {
                    LogManager.w(TAG, "Playback state inconsistency detected: store=${currentState.isPlaying}, player=$playerIsPlaying, mediaState=$playbackState")
                    // Force sync the playing state
                    _state.value = currentState.copy(isPlaying = playerIsPlaying)
                }
            }
        }
        
        LogManager.d(TAG, "Player state observers started")
    }

    private fun invalidatePlayerState() {
        LogManager.i(TAG, "Invalidating player state due to server change")
        
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

                LogManager.i(TAG, "Player state invalidated and caches cleared")

                // Repository instances are automatically managed by the manager
                // No need to refresh config as new instances are created per server

                // Refresh home content from the new server
                loadHomeContent()
            } catch (e: Exception) {
                LogManager.e(TAG, "Error invalidating player state", e)
            }
        }
    }

    private fun getSongDetails(songId: String) {
        LogManager.d(TAG, "Getting song details for: $songId")

        // Check if already cached
        val currentCache = _state.value.songDetailsCache
        if (currentCache.containsKey(songId)) {
            LogManager.d(TAG, "Song details already cached for: $songId")
            return
        }

        storeScope.launch {
            try {
                val repository = getCurrentRepository()
                if (repository == null) {
                    LogManager.w(TAG, "No repository available for getting song details")
                    return@launch
                }

                val result = repository.getSong(songId)
                if (result.isSuccess) {
                    val song = result.getOrNull()
                    if (song != null) {
                        LogManager.d(TAG, "Successfully fetched song details for: ${song.title}")
                        // Update cache with new song details
                        val updatedCache = currentCache.toMutableMap()
                        updatedCache[songId] = song
                        _state.value = _state.value.copy(songDetailsCache = updatedCache)

                        // Update current song if it matches
                        val currentSong = _state.value.currentSong
                        if (currentSong?.id == songId) {
                            LogManager.d(TAG, "Updating current song with fetched details")
                            _state.value = _state.value.copy(currentSong = song)
                        }
                    } else {
                        LogManager.w(TAG, "Song details result was null for: $songId")
                    }
                } else {
                    LogManager.w(TAG, "Failed to fetch song details for $songId: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Exception getting song details for $songId", e)
            }
        }
    }
}
