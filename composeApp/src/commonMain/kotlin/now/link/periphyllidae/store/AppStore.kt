package now.link.periphyllidae.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.AppIntent
import now.link.periphyllidae.mvi.AppState
import now.link.periphyllidae.mvi.AppTheme
import now.link.periphyllidae.repository.SettingsRepository

class AppStore(
    private val settingsRepository: SettingsRepository
) {
    // App-scoped coroutine scope for the store
    private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        loadSavedTheme()
    }

    fun handleIntent(intent: AppIntent) {
        when (intent) {
            is AppIntent.ChangeTheme -> changeTheme(intent.theme)
        }
    }

    private fun changeTheme(theme: AppTheme) {
        storeScope.launch {
            settingsRepository.saveTheme(theme.name)
            _state.value = _state.value.copy(theme = theme)
        }
    }

    private fun loadSavedTheme() {
        storeScope.launch {
            val savedTheme = settingsRepository.getTheme()
            val theme = try {
                AppTheme.valueOf(savedTheme)
            } catch (e: Exception) {
                AppTheme.SYSTEM
            }
            _state.value = _state.value.copy(theme = theme)
        }
    }
}
