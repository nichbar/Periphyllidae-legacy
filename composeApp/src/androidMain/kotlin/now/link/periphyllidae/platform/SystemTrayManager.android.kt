package now.link.periphyllidae.platform

import now.link.periphyllidae.models.Song

/**
 * Android stub implementation - system tray is not supported on mobile platforms
 */
actual class SystemTrayManager : SystemTray {
    
    override fun initialize(onAction: (TrayAction) -> Unit): Boolean {
        return false // Not supported on Android
    }
    
    override fun updatePlaybackState(song: Song?, isPlaying: Boolean) {
        // No-op on Android
    }
    
    override fun updateProgress(position: Long, duration: Long) {
        // No-op on Android
    }
    
    override fun showNotification(title: String, message: String) {
        // No-op on Android (Android has its own notification system)
    }
    
    override fun dispose() {
        // No-op on Android
    }
    
    override fun isSupported(): Boolean {
        return false // System tray not supported on Android
    }
}