package now.link.periphyllidae.platform

import now.link.periphyllidae.models.Song

/**
 * Cross-platform system tray interface
 */
interface SystemTray {
    /**
     * Initialize the system tray. Returns true if successful.
     */
    fun initialize(onAction: (TrayAction) -> Unit): Boolean
    
    /**
     * Update the tray with current playback state
     */
    fun updatePlaybackState(song: Song?, isPlaying: Boolean)
    
    /**
     * Update progress information (optional for basic implementation)
     */
    fun updateProgress(position: Long, duration: Long) {}
    
    /**
     * Show a notification (optional for basic implementation)
     */
    fun showNotification(title: String, message: String) {}
    
    /**
     * Clean up resources
     */
    fun dispose()
    
    /**
     * Check if system tray is supported on this platform
     */
    fun isSupported(): Boolean
}