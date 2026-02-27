package now.link.periphyllidae.platform

import kotlinx.coroutines.flow.StateFlow

enum class EnginePlaybackState {
    IDLE,
    PLAYING,
    PAUSED,
    BUFFERING,
    READY,
    FINISHED,
}

interface PlaybackEngine {
    val playbackState: StateFlow<EnginePlaybackState>

    suspend fun playUri(uri: String)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMillis: Long)
    fun getCurrentPositionMillis(): Long
    fun setVolume(volume: Float)
    fun close()
}
