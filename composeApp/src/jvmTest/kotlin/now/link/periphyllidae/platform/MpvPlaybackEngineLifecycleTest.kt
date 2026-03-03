package now.link.periphyllidae.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class MpvPlaybackEngineLifecycleTest {
    @Test
    fun closeAlwaysClosesBridge() {
        val fake = FakeLifecycleMpvBridge()
        val engine = MpvPlaybackEngine(fake)
        engine.close()
        assertTrue(fake.closed)
    }
}

private class FakeLifecycleMpvBridge : MpvBridge {
    var closed = false

    override fun initialize() = Unit
    override fun loadUrl(url: String) = Unit
    override fun pause(paused: Boolean) = Unit
    override fun stop() = Unit
    override fun seekTo(positionMillis: Long) = Unit
    override fun currentPositionMillis(): Long = 0L
    override fun setVolume(volume: Float) = Unit
    override fun close() {
        closed = true
    }

    override fun observePlaybackStatus(onStatus: (MpvPlaybackStatus) -> Unit) = Unit
}
