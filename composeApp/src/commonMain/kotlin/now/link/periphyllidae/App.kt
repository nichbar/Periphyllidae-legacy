package now.link.periphyllidae

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import org.koin.compose.KoinApplication
import now.link.periphyllidae.di.commonModule
import now.link.periphyllidae.di.platformModule
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.ui.theme.PeriphyllidaeTheme
import now.link.periphyllidae.navigation.AppNavigation
import now.link.periphyllidae.navigation.Screen
import now.link.periphyllidae.mvi.AppTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    KoinApplication(application = {
        modules(commonModule, platformModule)
    }) {
        AppContent()
    }
}

@Composable
private fun AppContent() {
    val settingsRepository = koinInject<SettingsRepository>()
    val navController = rememberNavController()
    
    // Determine start destination based on authentication state
    val startDestination = if (settingsRepository.getIsAuthenticated()) {
        Screen.Main.route
    } else {
        Screen.Login.route
    }
    
    // Get theme preference
    val savedTheme = remember { 
        try {
            AppTheme.valueOf(settingsRepository.getTheme())
        } catch (e: Exception) {
            AppTheme.SYSTEM
        }
    }
    
    val isDarkTheme = when (savedTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    
    PeriphyllidaeTheme(darkTheme = isDarkTheme) {
        AppNavigation(
            navController = navController,
            startDestination = startDestination
        )
    }
}