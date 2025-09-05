package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface ArtistDetailComponent {
    val artistId: String
    fun onNavigateBack()
    fun onNavigateToNowPlaying()
}

class DefaultArtistDetailComponent(
    componentContext: ComponentContext,
    override val artistId: String,
    private val onNavigateBack: () -> Unit,
    private val onNavigateToNowPlaying: () -> Unit
) : ArtistDetailComponent, ComponentContext by componentContext {
    
    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }
    
    override fun onNavigateToNowPlaying() {
        onNavigateToNowPlaying.invoke()
    }
}
