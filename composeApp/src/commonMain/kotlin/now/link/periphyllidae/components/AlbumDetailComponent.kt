package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface AlbumDetailComponent : NavigationComponent {
    val albumId: String
    fun onNavigateBack()
}

class DefaultAlbumDetailComponent(
    componentContext: ComponentContext,
    override val albumId: String,
    private val onNavigateBack: () -> Unit,
    private val nav: (Nav) -> Unit,
) : AlbumDetailComponent, ComponentContext by componentContext {

    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }
}