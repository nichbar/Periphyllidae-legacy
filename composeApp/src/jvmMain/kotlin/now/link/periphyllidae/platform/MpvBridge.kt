package now.link.periphyllidae.platform

interface MpvBridge {
    fun initialize()
    fun loadUrl(url: String)
    fun pause(paused: Boolean)
    fun stop()
    fun seekTo(positionMillis: Long)
    fun currentPositionMillis(): Long
    fun setVolume(volume: Float)
    fun close()
    fun observePlaybackStatus(onStatus: (MpvPlaybackStatus) -> Unit)
}
