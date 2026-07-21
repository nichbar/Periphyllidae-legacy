package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext
import now.link.periphyllidae.viewmodels.ArtistDetailViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface ArtistDetailComponent: NavigationComponent{
    val artistId: String
    val viewModel: ArtistDetailViewModel
    fun onNavigateBack()
}

class DefaultArtistDetailComponent(
    componentContext: ComponentContext,
    override val artistId: String,
    private val onNavigateBack: () -> Unit,
    private val nav: (Nav) -> Unit,
) : ArtistDetailComponent, ComponentContext by componentContext, KoinComponent {

    // Retain the ViewModel with the Decompose stack entry so back navigation
    // restores previously loaded artist content instead of recreating state.
    override val viewModel: ArtistDetailViewModel by inject()

    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }
}
