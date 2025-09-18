package now.link.periphyllidae.ui.content

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import now.link.periphyllidae.components.MainComponent
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.ui.components.FloatingMusicBar
import now.link.periphyllidae.ui.components.LandscapeFloatingMusicBar
import now.link.periphyllidae.ui.screens.*
import org.koin.compose.koinInject

data class TabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainContent(
    component: MainComponent,
    modifier: Modifier = Modifier
) {
    val musicStore: MusicStore = koinInject()
    val musicState by musicStore.state.collectAsStateWithLifecycle()
    
    val tabs = listOf(
        TabItem("Home", Icons.Filled.Home, Icons.Outlined.Home),
        TabItem("Search", Icons.Filled.Search, Icons.Outlined.Search),
        TabItem("Playlists", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List),
        TabItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )
    
    val stack by component.tabStack.subscribeAsState()
    val selectedTabIndex = when (stack.active.instance) {
        is MainComponent.TabChild.HomeChild -> 0
        is MainComponent.TabChild.SearchChild -> 1
        is MainComponent.TabChild.PlaylistsChild -> 2
        is MainComponent.TabChild.SettingsChild -> 3
    }

    BoxWithConstraints(modifier = modifier) {
        val box = this
        val isLandscape = box.maxWidth > box.maxHeight

        if (isLandscape) {
            LandscapeMainContent(
                component = component,
                selectedTabIndex = selectedTabIndex,
                tabs = tabs,
                musicState = musicState,
                musicStore = musicStore
            )
        } else {
            PortraitMainContent(
                component = component,
                selectedTabIndex = selectedTabIndex,
                tabs = tabs,
                musicState = musicState,
                musicStore = musicStore
            )
        }
    }
}

@Composable
private fun PortraitMainContent(
    component: MainComponent,
    selectedTabIndex: Int,
    tabs: List<TabItem>,
    musicState: now.link.periphyllidae.mvi.MusicState,
    musicStore: MusicStore
) {
    Scaffold(
        bottomBar = {
            Column {
                // Floating Music Bar
                val currentSong = musicState.currentSong
                if (currentSong != null) {
                    FloatingMusicBar(
                        currentSong = currentSong,
                        isPlaying = musicState.isPlaying,
                        onBarClick = component::onNavigateToNowPlaying,
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
                
                // Bottom Navigation
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
        TabContent(
            component = component,
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun LandscapeMainContent(
    component: MainComponent,
    selectedTabIndex: Int,
    tabs: List<TabItem>,
    musicState: now.link.periphyllidae.mvi.MusicState,
    musicStore: MusicStore
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Main content area with navigation rail
        Row(
            modifier = Modifier.weight(1f),
        ) {
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
            
            // Main content
            TabContent(
                component = component,
                paddingValues = PaddingValues(0.dp),
                modifier = Modifier.weight(1f)
            )
        }
        
        // Enhanced Floating Music Bar for landscape
        val currentSong = musicState.currentSong
        if (currentSong != null) {
            LandscapeFloatingMusicBar(
                currentSong = currentSong,
                isPlaying = musicState.isPlaying,
                musicState = musicState,
                onBarClick = component::onNavigateToNowPlaying,
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
                }
            )
        }
    }
}

@Composable
private fun TabContent(
    component: MainComponent,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier
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
                    HomeScreen(viewModel = koinInject())
                }
                is MainComponent.TabChild.SearchChild -> {
                    SearchScreen(
                        viewModel = koinInject(),
                        onNavigateBack = { /* Not needed in tab */ },
                        onNavigateToNowPlaying = child.component::onNavigateToNowPlaying,
                        onNavigateToArtist = child.component::onNavigateToArtist
                    )
                }
                is MainComponent.TabChild.PlaylistsChild -> {
                    PlaylistsScreen()
                }
                is MainComponent.TabChild.SettingsChild -> {
                    SettingsScreen(
                        viewModel = koinInject(),
                        onNavigateToLogs = child.component::onNavigateToLogs,
                        onNavigateToServerManagement = child.component::onNavigateToServerManagement,
                        onLogout = child.component::onLogout
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Playlists - Coming Soon")
    }
}
