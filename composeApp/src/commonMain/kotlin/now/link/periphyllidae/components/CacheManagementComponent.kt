package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface CacheManagementComponent {
    fun onNavigateBack()
}

class DefaultCacheManagementComponent(
    componentContext: ComponentContext,
    private val onNavigateBack: () -> Unit
) : CacheManagementComponent, ComponentContext by componentContext {

    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }
}