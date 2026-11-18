package now.link.periphyllidae.platform

import now.link.periphyllidae.JVMPlatform
import now.link.periphyllidae.getPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

class JvmPlaybackEngineFactoryTest {
    @Test
    fun createsMpvPlaybackEngine() {
        val engine = JvmPlaybackEngineFactory().createEngine()
        assertTrue(engine is MpvPlaybackEngine)
    }

    @Test
    fun createsMpvPlaybackEngineAndResolvesJvmPlatform() {
        assertTrue(JvmPlaybackEngineFactory().createEngine() is MpvPlaybackEngine)

        val platform = getPlatform()
        assertTrue(platform is JVMPlatform, "Expected getPlatform() to return JVMPlatform, got ${platform::class}")
        assertTrue(platform.name.isNotBlank(), "Expected host OS platform name to be non-blank")
    }
}
