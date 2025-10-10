package now.link.periphyllidae.platform

import now.link.periphyllidae.models.Song

/**
 * State representation for the system tray
 */
data class TrayState(
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val isWindowVisible: Boolean = true
)