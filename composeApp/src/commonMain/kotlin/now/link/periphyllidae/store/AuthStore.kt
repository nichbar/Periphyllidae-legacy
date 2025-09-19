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
import now.link.periphyllidae.repository.SubsonicRepositoryManager
import java.net.URI

class AuthStore(
    private val subsonicRepositoryManager: SubsonicRepositoryManager,
    private val settingsRepository: SettingsRepository,
) {
    // App-scoped coroutine scope for the store
    private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        loadSavedServers()
        loadSelectedConfiguration()
    }

    fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.Login -> login(intent.serverUrl, intent.username, intent.password, intent.alias)
            is AuthIntent.AddServer -> addServer(intent.serverUrl, intent.username, intent.password, intent.alias)
            is AuthIntent.ResetServerAdded -> resetServerAdded()
            is AuthIntent.Logout -> logout()
            is AuthIntent.ClearError -> clearError()
            is AuthIntent.ChangeServer -> changeServer(intent.server)
            is AuthIntent.DeleteServer -> deleteServer(intent.server)
            is AuthIntent.RefreshSavedServers -> loadSavedServers()
        }
    }

    private fun login(serverUrl: String, username: String, password: String, alias: String) {
        authenticateAndSaveServer(
            serverUrl = serverUrl,
            username = username,
            password = password,
            alias = alias,
            isLogin = true,
            onSuccess = { serverConfig, savedServers ->
                settingsRepository.saveIsAuthenticated(true)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    currentServer = serverConfig,
                    savedServers = savedServers,
                    error = null
                )
            },
            onFailure = { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error
                )
            }
        )
    }

    private fun addServer(serverUrl: String, username: String, password: String, alias: String) {
        authenticateAndSaveServer(
            serverUrl = serverUrl,
            username = username,
            password = password,
            alias = alias,
            isLogin = false,
            onSuccess = { _, savedServers ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    savedServers = savedServers,
                    error = null,
                    isServerAdded = true
                )
            },
            onFailure = { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error
                )
            }
        )
    }

    private fun authenticateAndSaveServer(
        serverUrl: String,
        username: String,
        password: String,
        alias: String,
        isLogin: Boolean,
        onSuccess: (ServerConfig, List<ServerConfig>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            onFailure("Please fill in all fields")
            return
        }

        _state.value = _state.value.copy(isLoading = true, error = null)

        storeScope.launch {
            try {
                val serverConfig = ServerConfig(
                    url = serverUrl,
                    username = username,
                    password = password,
                    isSelected = isLogin, // Auto-select only for login
                    alias = alias.ifBlank {
                        // Generate a default alias from the URL for add server
                        try {
                            val url = URI(serverUrl)
                            url.host ?: "Subsonic Server"
                        } catch (e: Exception) {
                            "Subsonic Server"
                        }
                    }
                )
                
                // Get server-specific repository instance
                val repository = subsonicRepositoryManager.getRepository(serverConfig)
                val result = repository.authenticate(serverConfig)
                
                if (result.isSuccess && result.getOrNull() == true) {
                    // Set this repository as active for successful authentication
                    if (isLogin) {
                        subsonicRepositoryManager.setActiveRepository(serverConfig)
                    }
                    
                    // Save the server configuration
                    settingsRepository.addServerConfig(serverConfig)

                    // Reload saved servers to include the new one
                    val savedServers = settingsRepository.getServerConfigs()

                    onSuccess(serverConfig, savedServers)
                } else {
                    val errorMessage = if (isLogin) {
                        "Authentication failed. Please check your credentials."
                    } else {
                        "Failed to connect to server. Please check your server URL and credentials."
                    }
                    onFailure(errorMessage)
                }
            } catch (e: Exception) {
                val errorMessage = if (isLogin) {
                    "Login failed: ${e.message}"
                } else {
                    "Connection failed: ${e.message}"
                }
                onFailure(errorMessage)
            }
        }
    }

    private fun logout() {
        storeScope.launch {
            settingsRepository.saveIsAuthenticated(false)
            settingsRepository.removeAllServerConfigs()
            settingsRepository.clearHomeContentCache() // Clear cached home content on logout

            subsonicRepositoryManager.clearCache() // Clear repository cache

            _state.value = AuthState() // Reset to default state
        }
    }

    private fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun resetServerAdded() {
        _state.value = _state.value.copy(isServerAdded = false)
    }

    private fun changeServer(server: ServerConfig) {
        storeScope.launch {
            try {
                // Set the new active repository
                subsonicRepositoryManager.setActiveRepository(server)
                
                // Change to the selected server
                settingsRepository.setSelectedServerConfig(server)

                _state.value = _state.value.copy(
                    currentServer = server,
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
                // Set the active repository for the saved server
                subsonicRepositoryManager.setActiveRepository(config)
                
                _state.value = _state.value.copy(
                    currentServer = config,
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

    private fun deleteServer(server: ServerConfig) {
        storeScope.launch {
            try {
                val currentServers = settingsRepository.getServerConfigs().toMutableList()
                currentServers.removeAll { it.url == server.url && it.username == server.username }
                settingsRepository.saveServerConfigs(currentServers)
                
                // Remove the repository instance from cache
                subsonicRepositoryManager.removeRepository(server)
                
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
