package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface QueueComponent {
    fun onNavigateBack()
}

class DefaultQueueComponent(
    componentContext: ComponentContext,
    private val onNavigateBack: () -> Unit
) : QueueComponent, ComponentContext by componentContext {

    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }
}
