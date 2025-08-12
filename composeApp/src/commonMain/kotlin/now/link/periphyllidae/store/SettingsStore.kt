package now.link.periphyllidae.store

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import now.link.periphyllidae.mvi.*
import now.link.periphyllidae.repository.SettingsRepository
import co.touchlab.kermit.Logger

class SettingsStore(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) : ViewModel() {
    
    private val _state = MutableStateFlow(
        SettingsState(
            currentServer = settingsRepository.getServerConfig(),
            theme = AppTheme.valueOf(settingsRepository.getTheme())
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ChangeTheme -> {
                _state.value = _state.value.copy(theme = intent.theme)
                settingsRepository.saveTheme(intent.theme.name)
                logger.i("Theme changed to ${intent.theme}")
            }
            is SettingsIntent.Logout -> {
                settingsRepository.clearServerConfig()
                settingsRepository.saveIsAuthenticated(false)
                logger.i("User logged out")
                // Navigation will be handled in the composable
            }
            is SettingsIntent.ChangeServer -> {
                settingsRepository.saveServerConfig(intent.server)
                _state.value = _state.value.copy(currentServer = intent.server)
                logger.i("Server configuration updated")
            }
        }
    }
}
