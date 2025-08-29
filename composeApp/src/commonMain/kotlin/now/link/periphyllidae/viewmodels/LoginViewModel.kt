package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.AuthIntent
import now.link.periphyllidae.mvi.AuthState
import now.link.periphyllidae.store.AuthStore

/**
 * Page-scoped ViewModel for the Login screen
 * Handles UI-specific logic and interacts with the global AuthStore
 */
class LoginViewModel(
    private val authStore: AuthStore
) : ViewModel() {
    
    val state: StateFlow<AuthState> = authStore.state
    
    fun handleIntent(intent: AuthIntent) {
        authStore.handleIntent(intent)
    }
    
    fun login(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            authStore.handleIntent(AuthIntent.UpdateServerUrl(serverUrl))
            authStore.handleIntent(AuthIntent.UpdateUsername(username))
            authStore.handleIntent(AuthIntent.UpdatePassword(password))
            authStore.handleIntent(AuthIntent.Login)
        }
    }
    
    fun testConnection(serverUrl: String) {
        viewModelScope.launch {
            authStore.handleIntent(AuthIntent.UpdateServerUrl(serverUrl))
            authStore.handleIntent(AuthIntent.TestConnection)
        }
    }
}
