package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import now.link.periphyllidae.viewmodels.HomeViewModel
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinInject(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                actions = {
                    IconButton(onClick = { viewModel.refreshContent() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Main Content
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Recently Added Section
                if (state.newestAlbums.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Recently Added",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.newestAlbums) { album ->
                                    AlbumCard(
                                        album = album,
                                        onPlay = { viewModel.playAlbum(album) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Most Played Section
                if (state.mostPlayedAlbums.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Most Played",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.mostPlayedAlbums.take(10)) { album ->
                                    AlbumCard(
                                        album = album,
                                        onPlay = { viewModel.playAlbum(album) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Recently Played Section
                if (state.recentlyPlayedAlbums.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Recently Played",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.recentlyPlayedAlbums.take(10)) { album ->
                                    AlbumCard(
                                        album = album,
                                        onPlay = { viewModel.playAlbum(album) }
                                    )
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

                // Loading State
                if (state.isLoadingHome) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
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
        }
    }
}

// Component for Album cards in Recently Added section
@Composable
fun AlbumCard(
    album: Album,
    onPlay: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onPlay() }
    ) {
        Column {
            AsyncImage(
                model = album.coverArt,
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Component for Most Played cards
@Composable
fun MostPlayedCard(
    song: Song,
    onPlay: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onPlay() }
    ) {
        Column {
            AsyncImage(
                model = song.coverArt,
                contentDescription = song.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
