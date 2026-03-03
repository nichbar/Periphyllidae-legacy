package now.link.periphyllidae.platform

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MpvPlaybackEngineTest {
    @Test
    fun forwardsCommandsToBridge() = runBlocking {
        val fake = FakeMpvBridge()
        val engine = MpvPlaybackEngine(fake)

        engine.playUri("https://example/stream")
        engine.pause()
        engine.resume()
        engine.seekTo(42_000)
        engine.setVolume(0.5f)
        engine.stop()

        assertEquals(
            listOf(
                "init",
                "load:https://example/stream",
                "pause:true",
                "pause:false",
                "seek:42000",
                "volume:0.5",
                "stop",
            ),
            fake.calls,
        )
    }
}

private class FakeMpvBridge : MpvBridge {
    val calls = mutableListOf<String>()

    override fun initialize() {
        calls += "init"
    }

    override fun loadUrl(url: String) {
        calls += "load:$url"
    }

    override fun pause(paused: Boolean) {
        calls += "pause:$paused"
    }

    override fun stop() {
        calls += "stop"
    }

    override fun seekTo(positionMillis: Long) {
        calls += "seek:$positionMillis"
    }

    override fun currentPositionMillis(): Long = 0L

    override fun setVolume(volume: Float) {
        calls += "volume:$volume"
    }

    override fun close() {}

    override fun observePlaybackStatus(onStatus: (MpvPlaybackStatus) -> Unit) {}
}
