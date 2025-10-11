package now.link.periphyllidae

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.koin.compose.KoinApplication
import com.arkivanov.decompose.ComponentContext
import now.link.periphyllidae.cache.CacheInitializer
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.di.commonModule
import now.link.periphyllidae.di.platformModule
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.ui.theme.PeriphyllidaeTheme
import now.link.periphyllidae.components.DefaultRootComponent
import now.link.periphyllidae.ui.content.RootContent
import now.link.periphyllidae.mvi.AppTheme
import now.link.periphyllidae.store.AppStore
import now.link.periphyllidae.ui.components.DesktopToolbarContent
import org.koin.compose.koinInject

private const val TAG = "App"

@Composable
fun App(
    rootComponentContext: ComponentContext? = null,
    windowScope: Any? = null,
    windowState: Any? = null,
    onCloseRequest: (() -> Unit)? = null,
    showDesktopToolbar: Boolean = false
) {
    KoinApplication(application = {
        modules(commonModule, platformModule)
    }) {
        AppContent(
            rootComponentContext = rootComponentContext,
            windowScope = windowScope,
            windowState = windowState,
            onCloseRequest = onCloseRequest,
            showDesktopToolbar = showDesktopToolbar
        )
    }
}

@Composable
private fun AppContent(
    rootComponentContext: ComponentContext? = null,
    windowScope: Any? = null,
    windowState: Any? = null,
    onCloseRequest: (() -> Unit)? = null,
    showDesktopToolbar: Boolean = false
) {
    val settingsRepository = koinInject<SettingsRepository>()
    val appStore = koinInject<AppStore>()
    
    // Initialize LogManager with settings repository and cache system
    LaunchedEffect(Unit) {
        LogManager.initialize(settingsRepository)
        LogManager.i(TAG, "Application started")
        
        // Initialize cache system asynchronously
        CacheInitializer.initializeAsync()
        LogManager.d(TAG, "Cache initialization started")
    }
    
    // Create root component
    val rootComponent = remember {
        DefaultRootComponent(
            componentContext = rootComponentContext ?: defaultComponentContext(),
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
        val platform = remember { getPlatform() }
        val isDesktop = !platform.isAndroid
        
        if (isDesktop) {
            // Desktop: Use rounded corners with Card
            Card(
                modifier = Modifier
                    .fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Show desktop toolbar only on desktop platforms when requested
                    if (showDesktopToolbar && windowState != null && onCloseRequest != null) {
                        // Use expect/actual pattern to handle platform-specific toolbar
                        DesktopToolbarContent(
                            windowScope = windowScope,
                            windowState = windowState,
                            onCloseRequest = onCloseRequest
                        )
                    }

                    // Main app content with Decompose
                    RootContent(component = rootComponent)
                }
            }
        } else {
            // Android: Use regular Column without padding/rounded corners
            Column(modifier = Modifier.fillMaxSize()) {
                // Show desktop toolbar only on desktop platforms when requested
                if (showDesktopToolbar && windowState != null && onCloseRequest != null) {
                    // Use expect/actual pattern to handle platform-specific toolbar
                    DesktopToolbarContent(
                        windowScope = windowScope,
                        windowState = windowState,
                        onCloseRequest = onCloseRequest
                    )
                }

                // Main app content with Decompose
                RootContent(component = rootComponent)
            }
        }
    }
}