package now.link.periphyllidae.platform

import kotlinx.coroutines.Dispatchers
import org.openani.mediamp.vlc.VlcMediampPlayer

class JvmPlaybackEngineFactory : PlaybackEngineFactory {
    override fun createEngine(): PlaybackEngine {
        return MediampPlaybackEngine(
            VlcMediampPlayer(parentCoroutineContext = Dispatchers.Main)
        )
    }
}
