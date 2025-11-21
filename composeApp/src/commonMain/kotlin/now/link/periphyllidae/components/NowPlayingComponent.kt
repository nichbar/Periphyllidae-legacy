package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface NowPlayingComponent {
    fun onNavigateBack()
    fun onNavigateToArtistDetail(artistId: String)
    fun onNavigateToAlbumDetail(albumId: String)
}

class DefaultNowPlayingComponent(
    componentContext: ComponentContext,
    private val onNavigateBack: () -> Unit,
    private val onNavigateToArtistDetail: (String) -> Unit,
    private val onNavigateToAlbumDetail: (String) -> Unit
) : NowPlayingComponent, ComponentContext by componentContext {

    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }

    override fun onNavigateToArtistDetail(artistId: String) {
        onNavigateToArtistDetail.invoke(artistId)
    }

    override fun onNavigateToAlbumDetail(albumId: String) {
        onNavigateToAlbumDetail.invoke(albumId)
    }
}
