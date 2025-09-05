package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface LoginComponent {
    // Add any specific login component methods here if needed
}

class DefaultLoginComponent(
    componentContext: ComponentContext,
    private val onLoginSuccess: () -> Unit
) : LoginComponent, ComponentContext by componentContext {
    
    fun onLoginSuccess() {
        onLoginSuccess.invoke()
    }
}
