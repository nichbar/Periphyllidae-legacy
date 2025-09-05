package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.utils.NetworkUtils
import now.link.periphyllidae.mvi.AppIntent
import now.link.periphyllidae.mvi.AppState
import now.link.periphyllidae.mvi.AppTheme
import now.link.periphyllidae.mvi.AuthIntent
import now.link.periphyllidae.mvi.AuthState
import now.link.periphyllidae.store.AppStore
import now.link.periphyllidae.store.AuthStore

/**
 * Page-scoped ViewModel for the Settings screen
 * Handles settings UI logic and user preferences
 */
class SettingsViewModel(
    private val appStore: AppStore,
    private val authStore: AuthStore
) : ViewModel() {
    
    val appState: StateFlow<AppState> = appStore.state
    val authState: StateFlow<AuthState> = authStore.state
    
    fun changeTheme(theme: AppTheme) {
        viewModelScope.launch {
            appStore.handleIntent(AppIntent.ChangeTheme(theme))
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            authStore.handleIntent(AuthIntent.Logout)
        }
    }
    
    fun updateServerConfiguration(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            authStore.handleIntent(AuthIntent.UpdateServerUrl(serverUrl))
            authStore.handleIntent(AuthIntent.UpdateUsername(username))
            authStore.handleIntent(AuthIntent.UpdatePassword(password))
            authStore.handleIntent(AuthIntent.SaveConfiguration)
        }
    }
    
    fun testConnection() {
        viewModelScope.launch {
            authStore.handleIntent(AuthIntent.TestConnection)
        }
    }
    
    /**
     * Measures latency to the current server using a simple ping-like request
     * @param serverUrl The server URL to test
     * @return Latency in milliseconds, or -1 if connection failed
     */
    suspend fun measureServerLatency(serverUrl: String): Long {
        return NetworkUtils.measureLatency(serverUrl)
    }
    
    fun toggleLogEnabled() {
        viewModelScope.launch {
            val newValue = !LogManager.isLogEnabled()
            LogManager.setLogEnabled(newValue)
            LogManager.i("SettingsViewModel", "Logging ${if (newValue) "enabled" else "disabled"}")
        }
    }
    
    fun isLogEnabled(): Boolean = LogManager.isLogEnabled()
}
