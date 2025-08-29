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
import now.link.periphyllidae.platform.MusicPlayer
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.Album

class MusicStore(
    private val subsonicRepository: SubsonicRepository,
    private val musicPlayer: MusicPlayer
) {
    // App-scoped coroutine scope for the store
    private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init {
        // Listen to music player state changes
        observePlayerState()
    }

    fun handleIntent(intent: MusicIntent) {
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
            is MusicIntent.PlaySong -> playSong(intent.song)
            is MusicIntent.PlayAlbum -> playAlbum(intent.album)
            is MusicIntent.SetQueue -> setQueue(intent.songs, intent.startIndex)
        }
    }

    private fun play() {
        val currentState = _state.value
        currentState.currentSong?.let { song ->
            musicPlayer.play(song)
            _state.value = currentState.copy(isPlaying = true)
        }
    }

    private fun pause() {
        musicPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    private fun next() {
        val currentState = _state.value
        val nextIndex = if (currentState.isShuffle) {
            currentState.queue.indices.random()
        } else {
            (currentState.currentIndex + 1) % currentState.queue.size
        }
        
        if (nextIndex < currentState.queue.size) {
            val nextSong = currentState.queue[nextIndex]
            playSpecificSong(nextSong, nextIndex)
        }
    }

    private fun previous() {
        val currentState = _state.value
        val prevIndex = if (currentState.currentIndex > 0) {
            currentState.currentIndex - 1
        } else {
            currentState.queue.size - 1
        }
        
        if (prevIndex >= 0 && prevIndex < currentState.queue.size) {
            val prevSong = currentState.queue[prevIndex]
            playSpecificSong(prevSong, prevIndex)
        }
    }

    private fun seekTo(position: Long) {
        musicPlayer.seekTo(position)
        _state.value = _state.value.copy(currentPosition = position)
    }

    private fun toggleRepeat() {
        val newRepeat = !_state.value.isRepeat
        musicPlayer.setRepeat(newRepeat)
        _state.value = _state.value.copy(isRepeat = newRepeat)
    }

    private fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffle
        musicPlayer.setShuffle(newShuffle)
        _state.value = _state.value.copy(isShuffle = newShuffle)
    }

    private fun loadHomeContent() {
        if (_state.value.isLoadingHome) return
        
        _state.value = _state.value.copy(isLoadingHome = true, homeError = null)
        
        storeScope.launch {
            try {
                val playlistsResult = subsonicRepository.getPlaylists()
                val randomSongsResult = subsonicRepository.getRandomSongs(20)
                val newestAlbumsResult = subsonicRepository.getNewestAlbums(10)
                
                val playlists = if (playlistsResult.isSuccess) playlistsResult.getOrNull() ?: emptyList() else emptyList()
                val randomSongs = if (randomSongsResult.isSuccess) randomSongsResult.getOrNull() ?: emptyList() else emptyList()
                val newestAlbums = if (newestAlbumsResult.isSuccess) newestAlbumsResult.getOrNull() ?: emptyList() else emptyList()
                
                _state.value = _state.value.copy(
                    isLoadingHome = false,
                    playlists = playlists,
                    randomSongs = randomSongs,
                    newestAlbums = newestAlbums,
                    homeError = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingHome = false,
                    homeError = "Failed to load content: ${e.message}"
                )
            }
        }
    }

    private fun refreshHomeContent() {
        loadHomeContent()
    }

    private fun playSong(song: Song) {
        // Create a queue with just this song for now
        setQueue(listOf(song), 0)
        playSpecificSong(song, 0)
    }

    private fun playAlbum(album: Album) {
        storeScope.launch {
            try {
                val albumResult = subsonicRepository.getAlbum(album.id)
                if (albumResult.isSuccess) {
                    val albumData = albumResult.getOrNull()
                    val songs = albumData?.songs ?: emptyList()
                    if (songs.isNotEmpty()) {
                        setQueue(songs, 0)
                        playSpecificSong(songs[0], 0)
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    homeError = "Failed to load album songs: ${e.message}"
                )
            }
        }
    }

    private fun setQueue(songs: List<Song>, startIndex: Int) {
        _state.value = _state.value.copy(
            queue = songs,
            currentIndex = startIndex
        )
    }

    private fun playSpecificSong(song: Song, index: Int) {
        musicPlayer.play(song)
        _state.value = _state.value.copy(
            currentSong = song,
            currentIndex = index,
            isPlaying = true,
            currentPosition = 0L,
            duration = song.duration?.times(1000L) ?: 0L
        )
    }

    private fun observePlayerState() {
        storeScope.launch {
            // This would ideally observe the actual music player state
            // For now, we'll update state manually in the methods above
        }
    }
}
