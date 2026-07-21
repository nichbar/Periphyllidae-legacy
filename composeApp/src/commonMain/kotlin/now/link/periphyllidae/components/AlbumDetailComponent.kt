package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext
import now.link.periphyllidae.viewmodels.AlbumDetailViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface AlbumDetailComponent : NavigationComponent {
    val albumId: String
    val viewModel: AlbumDetailViewModel
    fun onNavigateBack()
}

class DefaultAlbumDetailComponent(
    componentContext: ComponentContext,
    override val albumId: String,
    private val onNavigateBack: () -> Unit,
    private val nav: (Nav) -> Unit,
) : AlbumDetailComponent, ComponentContext by componentContext, KoinComponent {

    // Retain the ViewModel with the Decompose stack entry so back navigation
    // restores previously loaded album content instead of recreating state.
    override val viewModel: AlbumDetailViewModel by inject()

    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }
}