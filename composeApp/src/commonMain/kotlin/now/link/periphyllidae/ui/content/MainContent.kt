package now.link.periphyllidae.ui.content

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import now.link.periphyllidae.components.MainComponent
import now.link.periphyllidae.ui.layout.AppLayout
import now.link.periphyllidae.ui.screens.*

@Composable
fun MainContent(
    component: MainComponent,
    modifier: Modifier = Modifier,
) {
    AppLayout(
        component = component,
        modifier = modifier
    ) { paddingValues ->
        TabContent(
            component = component,
            paddingValues = paddingValues
        )
    }
}


@Composable
private fun TabContent(
    component: MainComponent,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Children(
            stack = component.tabStack,
            animation = stackAnimation(fade()),
        ) {
            when (val child = it.instance) {
                is MainComponent.TabChild.HomeChild -> {
                    HomeScreen(
                        onNavigateTo = { nav ->
                            child.component.onNavigateTo(nav)
                        }
                    )
                }

                is MainComponent.TabChild.FindChild -> {
                    FindScreen(
                        viewModel = child.component.viewModel,
                        onNavigateTo = { nav ->
                            child.component.onNavigateTo(nav)
                        }
                    )
                }

                is MainComponent.TabChild.PlaylistsChild -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Playlists - Coming Soon")
                    }
                }

                is MainComponent.TabChild.SettingsChild -> {
                    SettingsScreen(
                        onNavigateTo = { nav ->
                            child.component.onNavigateTo(nav)
                        }
                    )
                }
            }
        }
    }
}
