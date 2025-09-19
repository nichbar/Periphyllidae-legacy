package now.link.periphyllidae.viewmodels

import now.link.periphyllidae.mvi.AuthIntent
import now.link.periphyllidae.mvi.AuthState
import now.link.periphyllidae.mvi.AddServerState
import now.link.periphyllidae.store.AuthStore

/**
 * Page-scoped ViewModel for the Add Server screen
 * Manages local form state and delegates server addition to AuthStore
 */
class AddServerViewModel(
    authStore: AuthStore
) : BaseServerConfigViewModel<AddServerState>(
    authStore = authStore,
    initialState = AddServerState()
) {

    override fun mergeStates(formState: AddServerState, authState: AuthState): AddServerState {
        return formState.copy(
            isLoading = authState.isLoading,
            error = authState.error,
            isServerAdded = authState.isServerAdded
        )
    }
    
    override fun copyFormStateWithServerUrl(state: AddServerState, url: String): AddServerState = 
        state.copy(serverUrl = url)
    
    override fun copyFormStateWithUsername(state: AddServerState, username: String): AddServerState = 
        state.copy(username = username)
    
    override fun copyFormStateWithPassword(state: AddServerState, password: String): AddServerState = 
        state.copy(password = password)
    
    override fun copyFormStateWithAlias(state: AddServerState, alias: String): AddServerState = 
        state.copy(alias = alias)

    fun addServer() {
        val currentState = getCurrentFormState()
        authStore.handleIntent(
            AuthIntent.AddServer(
                serverUrl = currentState.serverUrl,
                username = currentState.username,
                password = currentState.password,
                alias = currentState.alias
            )
        )
    }

    fun clearState() {
        authStore.handleIntent(AuthIntent.ResetServerAdded)
        resetFormState()
    }
}
