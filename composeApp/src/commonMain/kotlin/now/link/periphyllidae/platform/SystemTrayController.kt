package now.link.periphyllidae.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.utils.LogManager

/**
 * Controller that connects the system tray to the music store
 */
class SystemTrayController(
    private val musicStore: MusicStore,
    private val systemTray: SystemTray,
    private val onShowWindow: () -> Unit,
    private val onExit: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isInitialized = false
    
    companion object {
        private const val TAG = "SystemTrayController"
    }
    
    fun initialize(): Boolean {
        if (!systemTray.isSupported()) {
            LogManager.w(TAG, "System tray not supported, skipping initialization")
            return false
        }
        
        if (isInitialized) {
            LogManager.w(TAG, "System tray already initialized")
            return true
        }
        
        val success = systemTray.initialize { action ->
            handleTrayAction(action)
        }
        
        if (success) {
            isInitialized = true
            observeMusicState()
            LogManager.i(TAG, "System tray controller initialized successfully")
        } else {
            LogManager.e(TAG, "Failed to initialize system tray controller")
        }
        
        return success
    }
    
    fun dispose() {
        if (isInitialized) {
            systemTray.dispose()
            isInitialized = false
            LogManager.d(TAG, "System tray controller disposed")
        }
    }
    
    private fun observeMusicState() {
        scope.launch {
            musicStore.state.collect { musicState ->
                systemTray.updatePlaybackState(
                    song = musicState.currentSong,
                    isPlaying = musicState.isPlaying
                )
                
                // Optional: Update progress for advanced implementations
                systemTray.updateProgress(
                    position = musicState.currentPosition,
                    duration = musicState.duration
                )
            }
        }
    }
    
    private fun handleTrayAction(action: TrayAction) {
        LogManager.d(TAG, "Handling tray action: ${action::class.simpleName}")
        
        when (action) {
            TrayAction.Play -> {
                musicStore.handleIntent(MusicIntent.Play)
            }
            TrayAction.Pause -> {
                musicStore.handleIntent(MusicIntent.Pause)
            }
            TrayAction.Next -> {
                musicStore.handleIntent(MusicIntent.Next)
            }
            TrayAction.Previous -> {
                musicStore.handleIntent(MusicIntent.Previous)
            }
            TrayAction.ShowWindow -> {
                onShowWindow()
            }
            TrayAction.Exit -> {
                dispose()
                onExit()
            }
        }
    }
}