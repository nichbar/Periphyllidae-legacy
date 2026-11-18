package now.link.periphyllidae

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.KoinApplication
import com.arkivanov.decompose.ComponentContext
import now.link.periphyllidae.cache.CacheInitializer
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.di.networkModule
import now.link.periphyllidae.di.dataModule
import now.link.periphyllidae.di.cacheModule
import now.link.periphyllidae.di.storeModule
import now.link.periphyllidae.di.viewModelModule
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.ui.theme.PeriphyllidaeTheme
import now.link.periphyllidae.components.DefaultRootComponent
import now.link.periphyllidae.ui.content.RootContent
import now.link.periphyllidae.mvi.AppTheme
import now.link.periphyllidae.store.AppStore
import org.koin.compose.koinInject
import org.koin.core.module.Module

private const val TAG = "App"

@Composable
fun App(
    rootComponentContext: ComponentContext,
    platformModules: List<Module> = emptyList(),
    desktopToolbar: (@Composable () -> Unit)? = null
) {
    KoinApplication(application = {
        modules(
            networkModule,
            dataModule,
            cacheModule,
            storeModule,
            viewModelModule
        )
        modules(platformModules)
    }) {
        AppContent(
            rootComponentContext = rootComponentContext,
            desktopToolbar = desktopToolbar
        )
    }
}

@Composable
private fun AppContent(
    rootComponentContext: ComponentContext,
    desktopToolbar: (@Composable () -> Unit)? = null
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
            componentContext = rootComponentContext,
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
        // Use rounded corners with Card
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Desktop-only chrome provided by the platform entry point (window toolbar)
                if (desktopToolbar != null) {
                    desktopToolbar()
                }

                // Main app content with Decompose
                RootContent(component = rootComponent)
            }
        }
    }
}