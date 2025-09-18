package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.AddServerState
import now.link.periphyllidae.models.ServerConfig
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.repository.SettingsRepository

class AddServerViewModel(
    private val subsonicRepository: SubsonicRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AddServerState())
    val state: StateFlow<AddServerState> = _state.asStateFlow()

    fun updateServerUrl(url: String) {
        _state.value = _state.value.copy(
            serverUrl = url,
            error = null
        )
    }

    fun updateUsername(username: String) {
        _state.value = _state.value.copy(
            username = username,
            error = null
        )
    }

    fun updatePassword(password: String) {
        _state.value = _state.value.copy(
            password = password,
            error = null
        )
    }

    fun updateAlias(alias: String) {
        _state.value = _state.value.copy(
            alias = alias,
            error = null
        )
    }

    fun addServer() {
        val currentState = _state.value
        
        if (currentState.serverUrl.isBlank() || 
            currentState.username.isBlank() || 
            currentState.password.isBlank()) {
            _state.value = currentState.copy(
                error = "Please fill in all required fields"
            )
            return
        }

        viewModelScope.launch {
            _state.value = currentState.copy(
                isLoading = true,
                error = null
            )

            try {
                // Create the server configuration for testing
                val testServerConfig = ServerConfig(
                    url = currentState.serverUrl,
                    username = currentState.username,
                    password = currentState.password,
                    alias = currentState.alias.ifBlank { 
                        // Generate a default alias from the URL
                        try {
                            val url = java.net.URL(currentState.serverUrl)
                            url.host ?: "Subsonic Server"
                        } catch (e: Exception) {
                            "Subsonic Server"
                        }
                    }
                )

                // Test the connection using the repository
                val authResult = subsonicRepository.authenticate(testServerConfig)

                if (authResult.isSuccess && authResult.getOrDefault(false)) {
                    // Save the server configuration
                    settingsRepository.addServerConfig(testServerConfig)

                    _state.value = currentState.copy(
                        isLoading = false,
                        isServerAdded = true,
                        error = null
                    )
                } else {
                    val errorMessage = authResult.exceptionOrNull()?.message 
                        ?: "Failed to connect to server. Please check your server URL and credentials."
                    
                    _state.value = currentState.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
            } catch (e: Exception) {
                _state.value = currentState.copy(
                    isLoading = false,
                    error = "Connection failed: ${e.message}"
                )
            }
        }
    }

    fun clearState() {
        _state.value = AddServerState()
    }
}
