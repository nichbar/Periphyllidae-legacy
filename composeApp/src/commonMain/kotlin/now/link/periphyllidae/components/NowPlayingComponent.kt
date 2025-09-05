package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface NowPlayingComponent {
    fun onNavigateBack()
}

class DefaultNowPlayingComponent(
    componentContext: ComponentContext,
    private val onNavigateBack: () -> Unit
) : NowPlayingComponent, ComponentContext by componentContext {
    
    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }
}
