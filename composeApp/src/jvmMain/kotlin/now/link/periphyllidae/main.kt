package now.link.periphyllidae

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import now.link.periphyllidae.di.commonModule
import now.link.periphyllidae.di.platformModule
import now.link.periphyllidae.repository.SettingsRepository

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    // Initialize Koin once, outside of the application scope
    val koinApplication = startKoin {
        modules(commonModule, platformModule)
    }
    
    val settingsRepository = koinApplication.koin.get<SettingsRepository>()
    
    application {
        // Load saved window dimensions and position
        val savedWidth = settingsRepository.getWindowWidth()
        val savedHeight = settingsRepository.getWindowHeight()
        val savedPosX = settingsRepository.getWindowPosX()
        val savedPosY = settingsRepository.getWindowPosY()
        
        // Create window state with saved values
        val windowState = remember {
            WindowState(
                size = DpSize(width = savedWidth.dp, height = savedHeight.dp),
                position = if (savedPosX >= 0 && savedPosY >= 0) {
                    WindowPosition(savedPosX.dp, savedPosY.dp)
                } else {
                    WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)
                }
            )
        }
        
        // Save window state whenever it changes (with a small delay to avoid excessive saves)
        LaunchedEffect(windowState.size, windowState.position) {
            GlobalScope.launch {
                // Add a small delay to debounce rapid changes during window resizing
                kotlinx.coroutines.delay(500)

                if (windowState.placement == WindowPlacement.Floating) {
                    // If the window is maximized or minimized, do not save the size/position

                    settingsRepository.saveWindowWidth(windowState.size.width.value.toInt())
                    settingsRepository.saveWindowHeight(windowState.size.height.value.toInt())

                    // Only save position if it's not centered (has actual coordinates)
                    val position = windowState.position
                    if (position is WindowPosition.Absolute) {
                        settingsRepository.saveWindowPosX(position.x.value.toInt())
                        settingsRepository.saveWindowPosY(position.y.value.toInt())
                    }
                    return@launch
                }
            }
        }

        val closeRequest = {
            stopKoin()
            exitApplication()
        }

        Window(
            onCloseRequest = { closeRequest() },
            title = "Periphyllidae",
            state = windowState,
            undecorated = true
        ) {
            // Pass the window state and close request to App()
            App(
                windowScope = this,
                windowState = windowState,
                onCloseRequest = closeRequest,
                showDesktopToolbar = true
            )
        }
    }
}