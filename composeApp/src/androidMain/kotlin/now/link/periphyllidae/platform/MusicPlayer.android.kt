package now.link.periphyllidae.platform

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import now.link.periphyllidae.models.Song
import android.content.Context

actual class MusicPlayer(private val context: Context) {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    
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
    
    init {
        setupPlayerListener()
    }
    
    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.value = when (playbackState) {
                    Player.STATE_IDLE -> PlaybackState.IDLE
                    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                    Player.STATE_READY -> if (player.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
                    Player.STATE_ENDED -> PlaybackState.IDLE
                    else -> PlaybackState.ERROR
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index >= 0 && index < currentQueue.size) {
                    _currentIndex.value = index
                    _currentSong.value = currentQueue[index]
                }
            }
        })
    }
    
    actual fun play(song: Song) {
        setQueue(listOf(song), 0)
        player.prepare()
        player.play()
    }
    
    actual fun pause() {
        player.pause()
    }
    
    actual fun resume() {
        player.play()
    }
    
    actual fun stop() {
        player.stop()
        _currentSong.value = null
        _currentPosition.value = 0L
    }
    
    actual fun seekTo(position: Long) {
        player.seekTo(position)
    }
    
    actual fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }
    
    actual fun previous() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        }
    }
    
    actual fun setQueue(songs: List<Song>, startIndex: Int) {
        currentQueue = songs
        _queue.value = songs
        
        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.streamUrl)
                .setMediaId(song.id)
                .build()
        }
        
        player.setMediaItems(mediaItems, startIndex, 0)
        if (startIndex < songs.size) {
            _currentSong.value = songs[startIndex]
            _currentIndex.value = startIndex
        }
    }
    
    actual fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
    }
    
    actual fun setRepeat(enabled: Boolean) {
        player.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }
    
    fun release() {
        player.release()
    }
}
