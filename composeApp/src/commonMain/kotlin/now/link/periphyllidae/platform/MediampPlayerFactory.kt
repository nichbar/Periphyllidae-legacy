package now.link.periphyllidae.platform

import org.openani.mediamp.MediampPlayer

interface MediampPlayerFactory {
    fun createPlayer(): MediampPlayer
}
