package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.Playlist
import now.link.periphyllidae.viewmodels.HomeViewModel
import now.link.periphyllidae.ui.components.FloatingMusicBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadHomeContent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Periphyllidae",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshContent() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoadingHome && state.newestAlbums.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = if (state.currentSong != null) 96.dp else 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Recent Albums Section
                    if (state.newestAlbums.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "Recently Added Albums",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.newestAlbums) { album ->
                                        // TODO: Create AlbumCard component
                                        Card(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .clickable { viewModel.playAlbum(album) }
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = album.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Random Songs Section
                    if (state.randomSongs.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "Random Songs",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.randomSongs.take(10).forEach { song ->
                                        // TODO: Fix SongListItem reference
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.playSong(song) }
                                        ) {
                                            Text(
                                                text = song.title,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Playlists Section
                    if (state.playlists.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "Your Playlists",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.playlists.forEach { playlist ->
                                        // TODO: Fix PlaylistListItem reference
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { /* TODO: Navigate to playlist detail */ }
                                        ) {
                                            Text(
                                                text = playlist.name,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Error Display
                    if (state.homeError != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.homeError ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                // Floating Music Bar
                val currentSong = state.currentSong
                if (currentSong != null) {
                    FloatingMusicBar(
                        currentSong = currentSong,
                        isPlaying = state.isPlaying,
                        onBarClick = onNavigateToNowPlaying,
                        onPlayPauseClick = {
                            if (state.isPlaying) {
                                viewModel.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Pause)
                            } else {
                                viewModel.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Play)
                            }
                        },
                        onNextClick = {
                            viewModel.handleIntent(now.link.periphyllidae.mvi.MusicIntent.Next)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun AlbumCard(
        album: Album,
        onPlay: () -> Unit,
    ) {
        Card(
            modifier = Modifier
                .width(160.dp)
                .clickable { onPlay() },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                AsyncImage(
                    model = album.coverArt,
                    contentDescription = "Album cover for ${album.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    fun SongListItem(
        song: Song,
        onPlay: () -> Unit,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPlay() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.coverArt,
                    contentDescription = "Cover art for ${song.title}",
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play ${song.title}"
                    )
                }
            }
        }
    }

    @Composable
    fun PlaylistListItem(
        playlist: Playlist,
        onClick: () -> Unit,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = playlist.coverArt,
                    contentDescription = "Cover art for ${playlist.name}",
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.songCount} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
