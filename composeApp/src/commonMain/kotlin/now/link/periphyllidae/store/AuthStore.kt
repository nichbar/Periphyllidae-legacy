package now.link.periphyllidae.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.models.ServerConfig
import now.link.periphyllidae.mvi.AuthIntent
import now.link.periphyllidae.mvi.AuthState
import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.utils.CryptoUtils

class AuthStore(
    private val subsonicRepository: SubsonicRepository,
    private val settingsRepository: SettingsRepository,
    private val cryptoUtils: CryptoUtils
) {
    // App-scoped coroutine scope for the store
    private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        loadSavedConfiguration()
    }

    fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.UpdateServerUrl -> updateServerUrl(intent.url)
            is AuthIntent.UpdateUsername -> updateUsername(intent.username)
            is AuthIntent.UpdatePassword -> updatePassword(intent.password)
            is AuthIntent.Login -> login()
            is AuthIntent.Logout -> logout()
            is AuthIntent.ClearError -> clearError()
            is AuthIntent.ChangeServer -> changeServer(intent.server)
            is AuthIntent.TestConnection -> testConnection()
            is AuthIntent.SaveConfiguration -> saveConfiguration()
        }
    }

    private fun updateServerUrl(url: String) {
        _state.value = _state.value.copy(serverUrl = url, error = null)
    }

    private fun updateUsername(username: String) {
        _state.value = _state.value.copy(username = username, error = null)
    }

    private fun updatePassword(password: String) {
        _state.value = _state.value.copy(password = password, error = null)
    }

    private fun login() {
        val currentState = _state.value
        if (currentState.serverUrl.isBlank() || currentState.username.isBlank() || currentState.password.isBlank()) {
            _state.value = currentState.copy(error = "Please fill in all fields")
            return
        }

        _state.value = currentState.copy(isLoading = true, error = null)

        storeScope.launch {
            try {
                val serverConfig = now.link.periphyllidae.models.ServerConfig(
                    url = currentState.serverUrl,
                    username = currentState.username,
                    password = currentState.password
                )
                
                val result = subsonicRepository.authenticate(serverConfig)
                
                if (result.isSuccess && result.getOrNull() == true) {
                    // Save configuration
                    settingsRepository.saveServerConfig(serverConfig)

                    settingsRepository.saveIsAuthenticated(true)

                    _state.value = currentState.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        currentServer = serverConfig,
                        error = null
                    )
                } else {
                    _state.value = currentState.copy(
                        isLoading = false,
                        error = "Authentication failed. Please check your credentials."
                    )
                }
            } catch (e: Exception) {
                _state.value = currentState.copy(
                    isLoading = false,
                    error = "Login failed: ${e.message}"
                )
            }
        }
    }

    private fun logout() {
        storeScope.launch {
            settingsRepository.saveIsAuthenticated(false)
            settingsRepository.clearServerConfig()
            settingsRepository.clearHomeContentCache() // Clear cached home content on logout
            _state.value = AuthState() // Reset to default state
        }
    }

    private fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun changeServer(server: now.link.periphyllidae.models.ServerConfig) {
        storeScope.launch {
            settingsRepository.saveServerConfig(server)
            
            _state.value = _state.value.copy(
                currentServer = server,
                serverUrl = server.url,
                username = server.username,
                password = server.password,
                isAuthenticated = true
            )
        }
    }

    private fun loadSavedConfiguration() {
        storeScope.launch {
            val config = settingsRepository.getServerConfig()
            if (config != null) {
                _state.value = _state.value.copy(
                    currentServer = config,
                    serverUrl = config.url,
                    username = config.username,
                    password = config.password,
                    isAuthenticated = true
                )
            }
        }
    }

    private fun testConnection() {
        storeScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val currentState = _state.value
                val testConfig = ServerConfig(
                    url = currentState.serverUrl,
                    username = currentState.username,
                    password = currentState.password,
                    name = "Test Server"
                )
                
                // Test the connection by trying to authenticate
                val result = subsonicRepository.authenticate(testConfig)
                
                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Connection test failed"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Connection test failed"
                )
            }
        }
    }

    private fun saveConfiguration() {
        storeScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val currentState = _state.value
                val serverConfig = ServerConfig(
                    url = currentState.serverUrl,
                    username = currentState.username,
                    password = currentState.password,
                    name = "Custom Server"
                )
                
                settingsRepository.saveServerConfig(serverConfig)
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    currentServer = serverConfig,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to save configuration"
                )
            }
        }
    }
}
