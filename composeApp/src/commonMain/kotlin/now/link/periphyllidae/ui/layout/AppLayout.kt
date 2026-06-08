package now.link.periphyllidae.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import now.link.periphyllidae.components.MainComponent
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.store.MusicStore
import org.koin.compose.koinInject

data class TabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
fun AppLayout(
    component: MainComponent,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val musicStore: MusicStore = koinInject()
    val musicState by musicStore.state.collectAsStateWithLifecycle()

    val tabs = listOf(
        TabItem("Home", Icons.Filled.Home, Icons.Outlined.Home),
        TabItem("Find", Icons.Filled.Search, Icons.Outlined.Search),
        TabItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    val stack by component.tabStack.subscribeAsState()
    val selectedTabIndex = when (stack.active.instance) {
        is MainComponent.TabChild.HomeChild -> 0
        is MainComponent.TabChild.FindChild -> 1
        is MainComponent.TabChild.PlaylistsChild -> 0
        is MainComponent.TabChild.SettingsChild -> 2
    }

    BoxWithConstraints(modifier = modifier) {
        val box = this
        val isLandscape = box.maxWidth > box.maxHeight

        if (isLandscape) {
            LandscapeAppLayout(
                component = component,
                selectedTabIndex = selectedTabIndex,
                tabs = tabs,
                musicState = musicState,
                musicStore = musicStore,
                content = content
            )
        } else {
            PortraitAppLayout(
                component = component,
                selectedTabIndex = selectedTabIndex,
                tabs = tabs,
                musicState = musicState,
                musicStore = musicStore,
                content = content
            )
        }
    }
}

@Composable
private fun PortraitAppLayout(
    component: MainComponent,
    selectedTabIndex: Int,
    tabs: List<TabItem>,
    musicState: MusicState,
    musicStore: MusicStore,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        bottomBar = {
            Column {
                // Floating Music Bar Overlay (appears above bottom nav)
                val currentSong = musicState.currentSong
                if (currentSong != null) {
                    MusicBarOverlay(
                        currentSong = currentSong,
                        isPlaying = musicState.isPlaying,
                        onBarClick = { component.onNavigateTo(now.link.periphyllidae.components.Nav.NowPlaying) },
                        onPlayPauseClick = {
                            if (musicState.isPlaying) {
                                musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Pause)
                            } else {
                                musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Play)
                            }
                        },
                        onNextClick = {
                            musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Next)
                        }
                    )
                }

                // Bottom Navigation Bar
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selectedTabIndex == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            selected = selectedTabIndex == index,
                            onClick = { component.selectTab(index) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

@Composable
private fun LandscapeAppLayout(
    component: MainComponent,
    selectedTabIndex: Int,
    tabs: List<TabItem>,
    musicState: MusicState,
    musicStore: MusicStore,
    content: @Composable (PaddingValues) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation Rail
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            tabs.forEachIndexed { index, tab ->
                NavigationRailItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTabIndex == index) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title
                        )
                    },
                    label = { Text(tab.title) },
                    selected = selectedTabIndex == index,
                    onClick = { component.selectTab(index) }
                )
            }
        }

        // Main content area
        Box(modifier = Modifier.weight(1f)) {
            content(PaddingValues(0.dp))

            // Enhanced Floating Music Bar for landscape
            val currentSong = musicState.currentSong
            if (currentSong != null) {
                LandscapeMusicBarOverlay(
                    currentSong = currentSong,
                    isPlaying = musicState.isPlaying,
                    musicState = musicState,
                    onBarClick = { component.onNavigateTo(now.link.periphyllidae.components.Nav.NowPlaying) },
                    onPlayPauseClick = {
                        if (musicState.isPlaying) {
                            musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Pause)
                        } else {
                            musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Play)
                        }
                    },
                    onNextClick = {
                        musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Next)
                    },
                    onPreviousClick = {
                        musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Previous)
                    },
                    onShuffleClick = {
                        musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.ToggleShuffle)
                    },
                    onRepeatClick = {
                        musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.ToggleRepeat)
                    },
                    onVolumeChange = { volume ->
                        musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.SetVolume(volume))
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
