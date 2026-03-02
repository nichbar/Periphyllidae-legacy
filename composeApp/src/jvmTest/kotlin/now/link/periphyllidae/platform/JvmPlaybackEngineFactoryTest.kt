package now.link.periphyllidae.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class JvmPlaybackEngineFactoryTest {
    @Test
    fun createsMpvPlaybackEngine() {
        val engine = JvmPlaybackEngineFactory().createEngine()
        assertTrue(engine is MpvPlaybackEngine)
    }
}
