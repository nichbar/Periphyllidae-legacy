package now.link.periphyllidae.platform

import android.content.Context
import kotlinx.coroutines.Dispatchers
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer

class AndroidMediampPlayerFactory(private val context: Context) : MediampPlayerFactory {
    override fun createPlayer(): MediampPlayer {
        return ExoPlayerMediampPlayer(
            context = context,
            parentCoroutineContext = Dispatchers.Main
        )
    }
}
