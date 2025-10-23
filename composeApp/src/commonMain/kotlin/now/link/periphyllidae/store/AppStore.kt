package now.link.periphyllidae.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.AppIntent
import now.link.periphyllidae.mvi.AppState
import now.link.periphyllidae.mvi.AppTheme
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.utils.ApplicationScopeManager

class AppStore(
    private val settingsRepository: SettingsRepository
) {
    // Use shared application scope to prevent memory leaks
    private val storeScope = ApplicationScopeManager.scope

    private val _state = MutableStateFlow(
        AppState(
            theme = try {
                AppTheme.valueOf(settingsRepository.getTheme())
            } catch (e: Exception) {
                AppTheme.SYSTEM
            },
            cacheSizeMB = try {
                settingsRepository.getCacheSize()
            } catch (e: Exception) {
                1024 // Default 1GB
            }
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun handleIntent(intent: AppIntent) {
        when (intent) {
            is AppIntent.ChangeTheme -> changeTheme(intent.theme)
            is AppIntent.ChangeCacheSize -> changeCacheSize(intent.sizeMB)
        }
    }

    private fun changeTheme(theme: AppTheme) {
        storeScope.launch {
            settingsRepository.saveTheme(theme.name)
            _state.value = _state.value.copy(theme = theme)
        }
    }

    private fun changeCacheSize(sizeMB: Int) {
        storeScope.launch {
            settingsRepository.saveCacheSize(sizeMB)
            _state.value = _state.value.copy(cacheSizeMB = sizeMB)
        }
    }
}
