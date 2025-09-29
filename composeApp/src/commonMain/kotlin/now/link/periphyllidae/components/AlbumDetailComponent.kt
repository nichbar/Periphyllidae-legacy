package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface AlbumDetailComponent {
    val albumId: String
    fun onNavigateBack()
    fun onNavigateToNowPlaying()
}

class DefaultAlbumDetailComponent(
    componentContext: ComponentContext,
    override val albumId: String,
    private val onNavigateBack: () -> Unit,
    private val onNavigateToNowPlaying: () -> Unit,
) : AlbumDetailComponent, ComponentContext by componentContext {

    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }

    override fun onNavigateToNowPlaying() {
        onNavigateToNowPlaying.invoke()
    }
}