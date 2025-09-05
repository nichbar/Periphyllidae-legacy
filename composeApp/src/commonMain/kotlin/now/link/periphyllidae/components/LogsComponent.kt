package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface LogsComponent {
    fun onNavigateBack()
}

class DefaultLogsComponent(
    componentContext: ComponentContext,
    private val onNavigateBack: () -> Unit
) : LogsComponent, ComponentContext by componentContext {
    
    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }
}
