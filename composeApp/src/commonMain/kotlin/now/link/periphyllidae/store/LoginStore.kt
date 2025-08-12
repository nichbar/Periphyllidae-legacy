package now.link.periphyllidae.store

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import now.link.periphyllidae.mvi.*
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.repository.SettingsRepository
import co.touchlab.kermit.Logger

class LoginStore(
    private val subsonicRepository: SubsonicRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) : ViewModel() {
    
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()
    
    fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.UpdateServerUrl -> {
                _state.value = _state.value.copy(serverUrl = intent.url)
            }
            is LoginIntent.UpdateUsername -> {
                _state.value = _state.value.copy(username = intent.username)
            }
            is LoginIntent.UpdatePassword -> {
                _state.value = _state.value.copy(password = intent.password)
            }
            is LoginIntent.Login -> {
                login()
            }
            is LoginIntent.ClearError -> {
                _state.value = _state.value.copy(error = null)
            }
        }
    }
    
    private fun login() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            val serverConfig = now.link.periphyllidae.models.ServerConfig(
                url = _state.value.serverUrl.trim(),
                username = _state.value.username.trim(),
                password = _state.value.password
            )
            
            try {
                val result = subsonicRepository.authenticate(serverConfig)
                if (result.isSuccess) {
                    settingsRepository.saveServerConfig(serverConfig)
                    settingsRepository.saveIsAuthenticated(true)
                    logger.i("Login successful")
                    _state.value = _state.value.copy(isLoading = false)
                    // Navigation will be handled in the composable
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Authentication failed. Please check your credentials."
                    )
                }
            } catch (e: Exception) {
                logger.e("Login error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Connection failed. Please check your server URL and network connection."
                )
            }
        }
    }
}
