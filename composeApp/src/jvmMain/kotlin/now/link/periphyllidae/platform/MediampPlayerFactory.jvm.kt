package now.link.periphyllidae.platform

import kotlinx.coroutines.Dispatchers
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.vlc.VlcMediampPlayer

class JvmMediampPlayerFactory : MediampPlayerFactory {
    override fun createPlayer(): MediampPlayer {
        return VlcMediampPlayer(parentCoroutineContext = Dispatchers.Main)
    }
}
