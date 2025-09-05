package now.link.periphyllidae

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.WindowScope
import org.koin.compose.KoinApplication
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.di.commonModule
import now.link.periphyllidae.di.platformModule
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.ui.theme.PeriphyllidaeTheme
import now.link.periphyllidae.components.DefaultRootComponent
import now.link.periphyllidae.ui.content.RootContent
import now.link.periphyllidae.mvi.AppTheme
import now.link.periphyllidae.ui.components.DesktopToolbarContent
import org.koin.compose.koinInject

@Composable
fun App(
    windowScope: WindowScope? = null,
    windowState: androidx.compose.ui.window.WindowState? = null,
    onCloseRequest: (() -> Unit)? = null,
    showDesktopToolbar: Boolean = false
) {
    KoinApplication(application = {
        modules(commonModule, platformModule)
    }) {
        AppContent(
            windowScope = windowScope,
            windowState = windowState,
            onCloseRequest = onCloseRequest,
            showDesktopToolbar = showDesktopToolbar
        )
    }
}

@Composable
private fun AppContent(
    windowScope: WindowScope? = null,
    windowState: androidx.compose.ui.window.WindowState? = null,
    onCloseRequest: (() -> Unit)? = null,
    showDesktopToolbar: Boolean = false
) {
    val settingsRepository = koinInject<SettingsRepository>()
    val appStore = koinInject<now.link.periphyllidae.store.AppStore>()
    
    // Initialize LogManager with settings repository
    LaunchedEffect(Unit) {
        LogManager.initialize(settingsRepository)
        LogManager.i("App", "Application started")
    }
    
    // Create root component
    val rootComponent = remember {
        DefaultRootComponent(
            componentContext = defaultComponentContext(),
            settingsRepository = settingsRepository
        )
    }
    
    // Get reactive theme preference from AppStore
    val appState by appStore.state.collectAsState()
    
    val isDarkTheme = when (appState.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    
    PeriphyllidaeTheme(darkTheme = isDarkTheme) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Show desktop toolbar only on desktop platforms when requested
            if (showDesktopToolbar && windowState != null && onCloseRequest != null) {
                // Import the desktop toolbar here with an expect/actual pattern
                windowScope?.DesktopToolbarContent(
                    windowState = windowState,
                    onCloseRequest = onCloseRequest
                )
            }

            // Main app content with Decompose
            RootContent(component = rootComponent)
        }
    }
}