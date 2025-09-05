package now.link.periphyllidae.ui.content

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
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
            is RootComponent.Child.ArtistDetailChild -> ArtistDetailContent(child.component)
        }
    }
}

@Composable
private fun LoginContent(component: now.link.periphyllidae.components.LoginComponent) {
    LoginScreen(
        viewModel = koinInject(),
        onLoginSuccess = {
            (component as now.link.periphyllidae.components.DefaultLoginComponent).onLoginSuccess()
        }
    )
}

@Composable
private fun NowPlayingContent(component: now.link.periphyllidae.components.NowPlayingComponent) {
    NowPlayingScreen(
        viewModel = koinInject(),
        onNavigateBack = component::onNavigateBack
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
        onNavigateToNowPlaying = component::onNavigateToNowPlaying
    )
}
