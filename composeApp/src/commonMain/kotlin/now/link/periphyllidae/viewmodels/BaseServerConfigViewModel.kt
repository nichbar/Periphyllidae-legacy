package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import now.link.periphyllidae.mvi.AuthIntent
import now.link.periphyllidae.store.AuthStore

/**
 * Base class for ViewModels that manage server configuration forms
 * Provides common functionality for form state management and AuthStore interaction
 * 
 * @param TState The specific state type for the form (LoginState, AddServerState, etc.)
 */
abstract class BaseServerConfigViewModel<TState : ServerConfigFormState>(
    protected val authStore: AuthStore,
    private val initialState: TState
) : ViewModel() {

    // Local form state
    private val _formState = MutableStateFlow(initialState)
    
    // Combined state for the UI - subclasses define how to merge form and auth state
    val state: StateFlow<TState> = combine(
        _formState,
        authStore.state
    ) { formState, authState ->
        mergeStates(formState, authState)
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = initialState
    )
    
    /**
     * Subclasses implement this to define how form state is merged with auth state
     */
    protected abstract fun mergeStates(formState: TState, authState: now.link.periphyllidae.mvi.AuthState): TState
    
    /**
     * Subclasses implement these to provide type-safe copy methods
     */
    protected abstract fun copyFormStateWithServerUrl(state: TState, url: String): TState
    protected abstract fun copyFormStateWithUsername(state: TState, username: String): TState
    protected abstract fun copyFormStateWithPassword(state: TState, password: String): TState
    protected abstract fun copyFormStateWithAlias(state: TState, alias: String): TState
    
    /**
     * Common form field update methods
     */
    fun updateServerUrl(url: String) {
        _formState.value = copyFormStateWithServerUrl(_formState.value, url)
    }
    
    fun updateUsername(username: String) {
        _formState.value = copyFormStateWithUsername(_formState.value, username)
    }
    
    fun updatePassword(password: String) {
        _formState.value = copyFormStateWithPassword(_formState.value, password)
    }
    
    fun updateAlias(alias: String) {
        _formState.value = copyFormStateWithAlias(_formState.value, alias)
    }
    
    fun clearError() {
        authStore.handleIntent(AuthIntent.ClearError)
    }
    
    /**
     * Gets the current form state for creating auth intents
     */
    protected fun getCurrentFormState(): TState = _formState.value
    
    /**
     * Resets the form state to initial values
     */
    protected fun resetFormState() {
        _formState.value = initialState
    }
}

/**
 * Interface that server configuration form states must implement
 * Provides standardized way to update form fields
 */
interface ServerConfigFormState {
    val serverUrl: String
    val username: String
    val password: String
    val alias: String
}
