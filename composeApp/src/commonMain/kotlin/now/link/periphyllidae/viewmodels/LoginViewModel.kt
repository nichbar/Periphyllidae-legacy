package now.link.periphyllidae.viewmodels

import now.link.periphyllidae.mvi.AuthIntent
import now.link.periphyllidae.mvi.AuthState
import now.link.periphyllidae.mvi.LoginState
import now.link.periphyllidae.store.AuthStore

/**
 * Page-scoped ViewModel for the Login screen
 * Manages local form state and delegates authentication to AuthStore
 */
class LoginViewModel(
    authStore: AuthStore
) : BaseServerConfigViewModel<LoginState>(
    authStore = authStore,
    initialState = LoginState()
) {
    
    override fun mergeStates(formState: LoginState, authState: AuthState): LoginState {
        return formState.copy(
            isLoading = authState.isLoading,
            error = authState.error,
            isAuthenticated = authState.isAuthenticated
        )
    }
    
    override fun copyFormStateWithServerUrl(state: LoginState, url: String): LoginState = 
        state.copy(serverUrl = url)
    
    override fun copyFormStateWithUsername(state: LoginState, username: String): LoginState = 
        state.copy(username = username)
    
    override fun copyFormStateWithPassword(state: LoginState, password: String): LoginState = 
        state.copy(password = password)
    
    override fun copyFormStateWithAlias(state: LoginState, alias: String): LoginState = 
        state.copy(alias = alias)
    
    fun login() {
        val currentState = getCurrentFormState()
        authStore.handleIntent(
            AuthIntent.Login(
                serverUrl = currentState.serverUrl,
                username = currentState.username,
                password = currentState.password,
                alias = currentState.alias
            )
        )
    }
}
