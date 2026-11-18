package now.link.periphyllidae.platform

import now.link.periphyllidae.models.Song
import now.link.periphyllidae.utils.LogManager
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

/**
 * Desktop implementation of SystemTray using Java AWT
 */
class SystemTrayManager : SystemTray {
    private var systemTray: java.awt.SystemTray? = null
    private var trayIcon: TrayIcon? = null
    private var onActionCallback: ((TrayAction) -> Unit)? = null
    private var currentSong: Song? = null
    private var isPlaying: Boolean = false
    
    companion object {
        private const val TAG = "SystemTrayManager"
        private const val ICON_SIZE = 16
    }
    
    override fun isSupported(): Boolean {
        return java.awt.SystemTray.isSupported()
    }
    
    override fun initialize(onAction: (TrayAction) -> Unit): Boolean {
        if (!isSupported()) {
            LogManager.w(TAG, "System tray is not supported on this platform")
            return false
        }
        
        this.onActionCallback = onAction
        
        try {
            systemTray = java.awt.SystemTray.getSystemTray()
            
            // Create tray icon
            val icon = createTrayIcon()
            trayIcon = TrayIcon(icon, "Periphyllidae", createPopupMenu()).apply {
                isImageAutoSize = true
                
                // Handle double-click to show/hide window
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.clickCount == 2) {
                            onAction(TrayAction.ShowWindow)
                        }
                    }
                })
            }
            
            systemTray?.add(trayIcon)
            
            LogManager.i(TAG, "System tray initialized successfully")
            return true
            
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to initialize system tray", e)
            return false
        }
    }
    
    override fun updatePlaybackState(song: Song?, isPlaying: Boolean) {
        this.currentSong = song
        this.isPlaying = isPlaying
        
        SwingUtilities.invokeLater {
            trayIcon?.let { icon ->
                // Update tooltip
                val tooltip = buildString {
                    append("Periphyllidae")
                    if (song != null) {
                        append("\n")
                        append(if (isPlaying) "▶️ " else "⏸️ ")
                        append("${song.title} - ${song.artist}")
                    } else {
                        append("\n⏹️ Not playing")
                    }
                }
                icon.toolTip = tooltip
                
                // Update icon to show playing state
                val newIcon = createTrayIcon()
                icon.image = newIcon
                
                // Update popup menu
                icon.popupMenu = createPopupMenu()
            }
        }
    }
    
    override fun showNotification(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }
    
    override fun dispose() {
        trayIcon?.let { icon ->
            systemTray?.remove(icon)
        }
        trayIcon = null
        systemTray = null
        onActionCallback = null
        LogManager.d(TAG, "System tray disposed")
    }
    
    private fun createTrayIcon(): Image {
        // Create a simple icon programmatically since we don't have access to resources yet
        val image = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        
        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Clear background
        g2d.composite = AlphaComposite.Clear
        g2d.fillRect(0, 0, ICON_SIZE, ICON_SIZE)
        g2d.composite = AlphaComposite.SrcOver
        
        // Draw jellyfish-inspired icon (simplified version of the vector)
        g2d.color = Color(0, 0, 0) // #6366f1 - primary color
        
        // Draw jellyfish bell (ellipse)
        g2d.fillOval(2, 2, 12, 8)
        
        // Draw tentacles (simple lines)
        g2d.stroke = BasicStroke(1f)
        g2d.drawLine(4, 10, 3, 14)
        g2d.drawLine(6, 10, 6, 14)
        g2d.drawLine(8, 10, 8, 14)
        g2d.drawLine(10, 10, 10, 14)
        g2d.drawLine(12, 10, 13, 14)

        g2d.rotate(45.0)

        g2d.dispose()
        return image
    }
    
    private fun createPopupMenu(): PopupMenu {
        val menu = PopupMenu()
        
        // Current song info
        if (currentSong != null) {
            val songInfo = MenuItem("♪ ${currentSong!!.title}").apply {
                isEnabled = false
            }
            menu.add(songInfo)
            
            val artistInfo = MenuItem("  by ${currentSong!!.artist}").apply {
                isEnabled = false
            }
            menu.add(artistInfo)
            menu.addSeparator()
        }
        
        // Play/Pause
        val playPauseItem = MenuItem(if (isPlaying) "⏸️ Pause" else "▶️ Play").apply {
            addActionListener { 
                onActionCallback?.invoke(if (isPlaying) TrayAction.Pause else TrayAction.Play)
            }
        }
        menu.add(playPauseItem)
        
        // Previous Track
        val previousItem = MenuItem("⏮️ Previous").apply {
            addActionListener { onActionCallback?.invoke(TrayAction.Previous) }
        }
        menu.add(previousItem)
        
        // Next Track
        val nextItem = MenuItem("⏭️ Next").apply {
            addActionListener { onActionCallback?.invoke(TrayAction.Next) }
        }
        menu.add(nextItem)
        
        menu.addSeparator()
        
        // Show Window
        val showItem = MenuItem("🪟 Show Periphyllidae").apply {
            addActionListener { onActionCallback?.invoke(TrayAction.ShowWindow) }
        }
        menu.add(showItem)
        
        menu.addSeparator()
        
        // Exit
        val exitItem = MenuItem("❌ Exit").apply {
            addActionListener { onActionCallback?.invoke(TrayAction.Exit) }
        }
        menu.add(exitItem)
        
        return menu
    }
}