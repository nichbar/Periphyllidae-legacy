package now.link.periphyllidae.platform

import android.content.Context
import kotlinx.coroutines.Dispatchers
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer

class AndroidPlaybackEngineFactory(private val context: Context) : PlaybackEngineFactory {
    override fun createEngine(): PlaybackEngine {
        return MediampPlaybackEngine(
            ExoPlayerMediampPlayer(
                context = context,
                parentCoroutineContext = Dispatchers.Main
            )
        )
    }
}
