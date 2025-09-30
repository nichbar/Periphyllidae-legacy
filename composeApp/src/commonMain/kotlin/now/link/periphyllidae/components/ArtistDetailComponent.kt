package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface ArtistDetailComponent: NavigationComponent{
    val artistId: String
    fun onNavigateBack()
}

class DefaultArtistDetailComponent(
    componentContext: ComponentContext,
    override val artistId: String,
    private val onNavigateBack: () -> Unit,
    private val nav: (Nav) -> Unit,
) : ArtistDetailComponent, ComponentContext by componentContext {
    
    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }
}
