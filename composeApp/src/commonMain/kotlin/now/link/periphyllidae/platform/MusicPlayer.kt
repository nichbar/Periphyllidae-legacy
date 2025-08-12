package now.link.periphyllidae.platform

import kotlinx.coroutines.flow.StateFlow
import now.link.periphyllidae.models.Song

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, BUFFERING, ERROR
}

expect class MusicPlayer {
    fun play(song: Song)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(position: Long)
    fun next()
    fun previous()
    fun setQueue(songs: List<Song>, startIndex: Int = 0)
    fun setShuffle(enabled: Boolean)
    fun setRepeat(enabled: Boolean)
    
    val isPlaying: StateFlow<Boolean>
    val currentSong: StateFlow<Song?>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val playbackState: StateFlow<PlaybackState>
    val queue: StateFlow<List<Song>>
    val currentIndex: StateFlow<Int>
}
