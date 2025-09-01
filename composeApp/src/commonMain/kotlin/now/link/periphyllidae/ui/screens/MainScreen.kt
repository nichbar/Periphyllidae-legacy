package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.periphyllidae.ui.components.FloatingMusicBar
import now.link.periphyllidae.viewmodels.HomeViewModel
import now.link.periphyllidae.viewmodels.SearchViewModel
import now.link.periphyllidae.viewmodels.SettingsViewModel
import now.link.periphyllidae.store.MusicStore
import org.koin.compose.koinInject

data class TabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onLogout: () -> Unit
) {
    val musicStore: MusicStore = koinInject()
    val musicState by musicStore.state.collectAsStateWithLifecycle()
    
    // Inject ViewModels once and remember them to prevent recomposition
    val homeViewModel: HomeViewModel = koinInject()
    val searchViewModel: SearchViewModel = koinInject()
    val settingsViewModel: SettingsViewModel = koinInject()
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    val tabs = listOf(
        TabItem("Home", Icons.Filled.Home, Icons.Outlined.Home),
        TabItem("Search", Icons.Filled.Search, Icons.Outlined.Search),
        TabItem("Playlists", Icons.Filled.List, Icons.Outlined.List),
        TabItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        bottomBar = {
            Column {
                // Floating Music Bar
                val currentSong = musicState.currentSong
                if (currentSong != null) {
                    FloatingMusicBar(
                        currentSong = currentSong,
                        isPlaying = musicState.isPlaying,
                        onBarClick = onNavigateToNowPlaying,
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
                            onClick = { selectedTabIndex = index }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    HomeScreen(viewModel = homeViewModel)
                }
                1 -> {
                    SearchScreen(
                        viewModel = searchViewModel,
                        onNavigateBack = { /* Not needed in tab */ },
                        onNavigateToNowPlaying = onNavigateToNowPlaying,
                        onNavigateToArtist = onNavigateToArtist
                    )
                }
                2 -> {
                    PlaylistsScreen()
                }
                3 -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = { /* Not needed in tab */ },
                        onLogout = onLogout
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
