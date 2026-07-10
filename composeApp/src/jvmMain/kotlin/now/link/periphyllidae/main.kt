package now.link.periphyllidae

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
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
import now.link.periphyllidae.di.networkModule
import now.link.periphyllidae.di.dataModule
import now.link.periphyllidae.di.cacheModule
import now.link.periphyllidae.di.storeModule
import now.link.periphyllidae.di.viewModelModule
import now.link.periphyllidae.di.platformModule
import now.link.periphyllidae.di.desktopModule
import now.link.periphyllidae.platform.SystemTray
import now.link.periphyllidae.platform.SystemTrayController
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.utils.ApplicationScopeManager
import now.link.periphyllidae.platform.MacWindowUtils

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val isReleaseBuild = System.getProperty("compose.application.resources.dir") != null
    if (isReleaseBuild) {
        LogManager.setConsoleLoggingEnabled(false)
    }

    // Initialize Koin with modular dependency injection
    val koinApplication = startKoin {
        modules(
            networkModule,
            dataModule,
            cacheModule,
            storeModule,
            viewModelModule,
            platformModule,
            desktopModule
        )
    }

    val settingsRepository = koinApplication.koin.get<SettingsRepository>()
    val musicStore = koinApplication.koin.get<MusicStore>()
    val systemTray = koinApplication.koin.get<SystemTray>()

    // Setup cleanup hook for proper shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        LogManager.i("Main", "Application shutdown initiated")
        ApplicationScopeManager.shutdown()
        koinApplication.close()
        LogManager.i("Main", "Application cleanup completed")
    })

    application {
        // Load saved window dimensions and position
        val savedWidth = settingsRepository.getWindowWidth()
        val savedHeight = settingsRepository.getWindowHeight()
        val savedPosX = settingsRepository.getWindowPosX()
        val savedPosY = settingsRepository.getWindowPosY()

        // Window visibility state
        var windowVisible by remember { mutableStateOf(true) }

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

        // System tray controller
        val systemTrayController = remember {
            SystemTrayController(
                musicStore = musicStore,
                systemTray = systemTray,
                onShowWindow = { windowVisible = true },
                onExit = {
                    ApplicationScopeManager.shutdown()
                    stopKoin()
                    exitApplication()
                }
            )
        }

        // Initialize system tray
        LaunchedEffect(Unit) {
            val success = systemTrayController.initialize()
            if (success) {
                LogManager.i("Main", "System tray initialized successfully")
            } else {
                LogManager.w("Main", "Failed to initialize system tray")
            }
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
            // If system tray is supported, minimize to tray instead of exiting
            if (systemTray.isSupported()) {
                windowVisible = false
            } else {
                systemTrayController.dispose()
                ApplicationScopeManager.shutdown()
                stopKoin()
                exitApplication()
            }
        }

        if (windowVisible) {
            Window(
                onCloseRequest = { closeRequest() },
                title = "Periphyllidae",
                state = windowState,
                undecorated = true,
                transparent = true
            ) {
                // Ensure the native macOS window shadow is enabled — it follows
                // the transparent window's alpha channel, which is rounded.
                LaunchedEffect(Unit) {
                    MacWindowUtils.ensureWindowShadow(window)
                }

                window.minimumSize = with(LocalDensity.current) {
                    java.awt.Dimension(100.dp.roundToPx(), 100.dp.roundToPx())
                }

                // Pass the window state and close request to App()
                App(
                    windowScope = this,
                    windowState = windowState,
                    onCloseRequest = closeRequest,
                    showDesktopToolbar = true
                )
            }
        }

        // Cleanup on application exit
        DisposableEffect(Unit) {
            onDispose {
                systemTrayController.dispose()
            }
        }
    }
}