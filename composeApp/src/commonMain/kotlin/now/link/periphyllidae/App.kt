package now.link.periphyllidae

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import org.koin.compose.KoinApplication
import now.link.periphyllidae.di.commonModule
import now.link.periphyllidae.di.platformModule
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.ui.theme.PeriphyllidaeTheme
import now.link.periphyllidae.ui.screens.LoginScreen
import now.link.periphyllidae.ui.screens.HomeScreen
import now.link.periphyllidae.mvi.AppTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
    
    // Simple navigation state
    var isAuthenticated by remember { mutableStateOf(settingsRepository.getIsAuthenticated()) }
    
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
        if (isAuthenticated) {
            HomeScreen(
                viewModel = koinViewModel(),
                onNavigateToSearch = { /* TODO */ },
                onNavigateToSettings = { /* TODO */ },
                onNavigateToNowPlaying = { /* TODO */ }
            )
        } else {
            LoginScreen(
                viewModel = koinViewModel(),
                onLoginSuccess = { 
                    isAuthenticated = true 
                }
            )
        }
    }
}