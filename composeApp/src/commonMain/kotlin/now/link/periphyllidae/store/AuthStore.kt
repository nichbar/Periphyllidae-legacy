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
        loadSelectedConfiguration()
        loadSavedServers()
    }

    fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.UpdateServerUrl -> updateServerUrl(intent.url)
            is AuthIntent.UpdateUsername -> updateUsername(intent.username)
            is AuthIntent.UpdatePassword -> updatePassword(intent.password)
            is AuthIntent.UpdateAlias -> updateAlias(intent.alias)
            is AuthIntent.Login -> login()
            is AuthIntent.Logout -> logout()
            is AuthIntent.ClearError -> clearError()
            is AuthIntent.ChangeServer -> changeServer(intent.server)
            is AuthIntent.DeleteServer -> deleteServer(intent.server)
            is AuthIntent.RefreshSavedServers -> loadSavedServers()
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

    private fun updateAlias(alias: String) {
        _state.value = _state.value.copy(alias = alias, error = null)
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
                val serverConfig = ServerConfig(
                    url = currentState.serverUrl,
                    username = currentState.username,
                    password = currentState.password,
                    isSelected = true,
                    alias = currentState.alias.ifBlank { "Custom Server" }
                )
                
                val result = subsonicRepository.authenticate(serverConfig)
                
                if (result.isSuccess && result.getOrNull() == true) {
                    // Save configuration
                    settingsRepository.addServerConfig(serverConfig)

                    settingsRepository.saveIsAuthenticated(true)

                    val savedServers = settingsRepository.getServerConfigs()

                    _state.value = currentState.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        currentServer = serverConfig,
                        savedServers = savedServers,
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
            settingsRepository.removeAllServerConfigs()
            settingsRepository.clearHomeContentCache() // Clear cached home content on logout
            _state.value = AuthState() // Reset to default state
        }
    }

    private fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun changeServer(server: ServerConfig) {
        storeScope.launch {
            try {
                // Change to the selected server
                settingsRepository.setSelectedServerConfig(server)

                _state.value = _state.value.copy(
                    currentServer = server,
                    serverUrl = server.url,
                    username = server.username,
                    password = server.password,
                    alias = server.alias,
                    isAuthenticated = true,
                )

                // Notify that server has changed - music store should invalidate player state
                // This will be handled by the UI layer which observes auth state changes

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to switch server: ${e.message}"
                )
            }
        }
    }

    private fun loadSelectedConfiguration() {
        storeScope.launch {
            val config = settingsRepository.getSelectedServerConfig()
            if (config != null) {
                _state.value = _state.value.copy(
                    currentServer = config,
                    serverUrl = config.url,
                    username = config.username,
                    password = config.password,
                    alias = config.alias,
                    isAuthenticated = true
                )
            }
        }
    }

    private fun loadSavedServers() {
        storeScope.launch {
            val savedServers = settingsRepository.getServerConfigs()
            _state.value = _state.value.copy(
                savedServers = savedServers
            )
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
                    alias = currentState.alias.ifBlank { "Custom Server" }
                )
                
                settingsRepository.addServerConfig(serverConfig)
                
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

    private fun deleteServer(server: ServerConfig) {
        storeScope.launch {
            try {
                val currentServers = settingsRepository.getServerConfigs().toMutableList()
                currentServers.removeAll { it.url == server.url && it.username == server.username }
                settingsRepository.saveServerConfigs(currentServers)
                
                // Update state with new server list
                _state.value = _state.value.copy(
                    savedServers = currentServers
                )
                
                // If the deleted server was the current server, reset authentication
                if (_state.value.currentServer?.url == server.url && 
                    _state.value.currentServer?.username == server.username) {
                    
                    settingsRepository.saveIsAuthenticated(false)
                    settingsRepository.removeServerConfig(server)
                    
                    _state.value = _state.value.copy(
                        currentServer = null,
                        serverUrl = "",
                        username = "",
                        password = "",
                        alias = "",
                        isAuthenticated = false
                    )
                }
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to delete server: ${e.message}"
                )
            }
        }
    }
}
