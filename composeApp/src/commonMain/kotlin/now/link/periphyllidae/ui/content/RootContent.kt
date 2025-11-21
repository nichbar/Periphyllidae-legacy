package now.link.periphyllidae.ui.content

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import now.link.periphyllidae.components.AlbumDetailComponent
import now.link.periphyllidae.components.LoginComponent
import now.link.periphyllidae.components.RootComponent
import now.link.periphyllidae.ui.screens.*
import org.koin.compose.koinInject

@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier
) {
    Children(
        stack = component.stack,
        modifier = modifier.fillMaxSize(),
        animation = stackAnimation(slide()),
    ) {
        when (val child = it.instance) {
            is RootComponent.Child.LoginChild -> LoginContent(child.component)
            is RootComponent.Child.MainChild -> MainContent(child.component)
            is RootComponent.Child.NowPlayingChild -> NowPlayingContent(child.component)
            is RootComponent.Child.LogsChild -> LogsContent(child.component)
            is RootComponent.Child.ServerManagementChild -> ServerManagementContent(child.component)
            is RootComponent.Child.AddServerChild -> AddServerContent(child.component)
            is RootComponent.Child.CacheManagementChild -> CacheManagementContent(child.component)
            is RootComponent.Child.ArtistDetailChild -> ArtistDetailContent(child.component)
            is RootComponent.Child.AlbumDetailChild -> AlbumDetailContent(child.component)
        }
    }
}

@Composable
private fun LoginContent(component: LoginComponent) {
    LoginScreen(
        viewModel = koinInject(),
        onNavigateTo = { component::onNavigateTo },
    )
}

@Composable
private fun NowPlayingContent(component: now.link.periphyllidae.components.NowPlayingComponent) {
    NowPlayingScreen(
        viewModel = koinInject(),
        onNavigateBack = component::onNavigateBack,
        onNavigateToArtistDetail = component::onNavigateToArtistDetail,
        onNavigateToAlbumDetail = component::onNavigateToAlbumDetail
    )
}

@Composable
private fun LogsContent(component: now.link.periphyllidae.components.LogsComponent) {
    LogsScreen(
        onBackClick = component::onNavigateBack
    )
}

@Composable
private fun ArtistDetailContent(component: now.link.periphyllidae.components.ArtistDetailComponent) {
    ArtistDetailScreen(
        artistId = component.artistId,
        viewModel = koinInject(),
        onNavigateBack = component::onNavigateBack,
        onNavigateTo = component::onNavigateTo
    )
}

@Composable
private fun AlbumDetailContent(component: AlbumDetailComponent) {
    AlbumDetailScreen(
        albumId = component.albumId,
        viewModel = koinInject(),
        onNavigateBack = component::onNavigateBack,
        onNavigateTo = component::onNavigateTo
    )
}

@Composable
private fun ServerManagementContent(component: now.link.periphyllidae.components.ServerManagementComponent) {
    ServerManagementScreen(
        viewModel = koinInject(),
        onNavigateBack = component::onNavigateBack,
        onNavigateTo = component::onNavigateTo
    )
}

@Composable
private fun AddServerContent(component: now.link.periphyllidae.components.AddServerComponent) {
    AddServerScreen(
        viewModel = koinInject(),
        onServerAdded = component::onServerAdded,
        onNavigateBack = component::onNavigateBack
    )
}

@Composable
private fun CacheManagementContent(component: now.link.periphyllidae.components.CacheManagementComponent) {
    CacheManagementScreen(
        component = component
    )
}
