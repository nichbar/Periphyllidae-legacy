package now.link.periphyllidae.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import now.link.periphyllidae.models.Song
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

actual class MusicPlayer {
    private val mediaPlayer: MediaPlayer
    
    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    actual val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    actual val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    actual val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    actual val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(0)
    actual val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private var currentQueue: List<Song> = emptyList()
    private var shuffleEnabled = false
    private var repeatEnabled = false
    
    init {
        val audioPlayerComponent = AudioPlayerComponent()
        mediaPlayer = audioPlayerComponent.mediaPlayer()
        setupPlayerListener()
    }
    
    private fun setupPlayerListener() {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _isPlaying.value = true
                _playbackState.value = PlaybackState.PLAYING
            }
            
            override fun paused(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                _playbackState.value = PlaybackState.PAUSED
            }
            
            override fun stopped(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                _playbackState.value = PlaybackState.IDLE
            }
            
            override fun finished(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                _playbackState.value = PlaybackState.IDLE
                
                // Auto-play next song if available
                if (_currentIndex.value < currentQueue.size - 1) {
                    next()
                } else if (repeatEnabled) {
                    setQueue(currentQueue, 0)
                    resume()
                }
            }
            
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _currentPosition.value = newTime
            }
            
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _duration.value = newLength
            }
            
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                if (newCache < 100f) {
                    _playbackState.value = PlaybackState.BUFFERING
                }
            }
            
            override fun error(mediaPlayer: MediaPlayer) {
                _playbackState.value = PlaybackState.ERROR
            }
        })
    }
    
    actual fun play(song: Song) {
        setQueue(listOf(song), 0)
        mediaPlayer.media().play(song.streamUrl)
    }
    
    actual fun pause() {
        mediaPlayer.controls().pause()
    }
    
    actual fun resume() {
        mediaPlayer.controls().play()
    }
    
    actual fun stop() {
        mediaPlayer.controls().stop()
        _currentSong.value = null
        _currentPosition.value = 0L
    }
    
    actual fun seekTo(position: Long) {
        mediaPlayer.controls().setTime(position)
    }
    
    actual fun next() {
        val nextIndex = if (shuffleEnabled) {
            currentQueue.indices.random()
        } else {
            (_currentIndex.value + 1).coerceAtMost(currentQueue.size - 1)
        }
        
        if (nextIndex < currentQueue.size) {
            _currentIndex.value = nextIndex
            val nextSong = currentQueue[nextIndex]
            _currentSong.value = nextSong
            mediaPlayer.media().play(nextSong.streamUrl)
        }
    }
    
    actual fun previous() {
        val prevIndex = (_currentIndex.value - 1).coerceAtLeast(0)
        if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            val prevSong = currentQueue[prevIndex]
            _currentSong.value = prevSong
            mediaPlayer.media().play(prevSong.streamUrl)
        }
    }
    
    actual fun setQueue(songs: List<Song>, startIndex: Int) {
        currentQueue = songs
        _queue.value = songs
        
        if (startIndex < songs.size) {
            _currentIndex.value = startIndex
            _currentSong.value = songs[startIndex]
        }
    }
    
    actual fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
    }
    
    actual fun setRepeat(enabled: Boolean) {
        repeatEnabled = enabled
    }
    
    fun release() {
        mediaPlayer.release()
    }
}
