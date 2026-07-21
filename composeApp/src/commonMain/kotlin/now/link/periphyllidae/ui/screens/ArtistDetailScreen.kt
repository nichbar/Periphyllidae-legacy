package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.periphyllidae.components.Nav
import now.link.periphyllidae.models.Artist
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.ui.components.CoverArtImage
import now.link.periphyllidae.ui.components.RetryErrorCard
import now.link.periphyllidae.ui.layout.ScreenContainer
import now.link.periphyllidae.ui.layout.TopBarConfig
import now.link.periphyllidae.viewmodels.ArtistDetailViewModel
@Composable
fun ArtistDetailScreen(
    artistId: String,
    viewModel: ArtistDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Nav) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val musicState by viewModel.musicState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(artistId) {
        viewModel.loadArtistDetail(artistId)
    }

    // Determine if we should show the artist name in the toolbar
    // Show when scrolled past 60dp
    val showToolbarTitle by remember {
        derivedStateOf {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset

            // Show title if we're not on the first item OR if we've scrolled past threshold
            firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 60
        }
    }

    val topBarConfig = TopBarConfig(
        title = if (showToolbarTitle) state.artist?.name ?: "" else "",
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        onNavigationClick = onNavigateBack
    )

    ScreenContainer(topBarConfig = topBarConfig) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                RetryErrorCard(
                    message = state.error ?: "",
                    onRetry = { viewModel.loadArtistDetail(artistId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(paddingValues)
                        .padding(16.dp)
                )
            }

            state.artist != null -> {
                val currentArtist = state.artist!!
                val currentSong = musicState.currentSong
                val currentSongId = currentSong?.id
                val currentAlbumId = currentSong?.albumId
                val albumIds = currentArtist.albums.map { it.id }.toSet()
                val isCurrentArtistPlaying = musicState.isPlaying &&
                    (currentSong?.artistId == currentArtist.id ||
                        currentSongId != null && state.topSongs.any { it.id == currentSongId } ||
                        currentAlbumId != null && currentAlbumId in albumIds)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Artist Header
                        item {
                            ArtistHeader(
                                artist = currentArtist,
                                onPlayAll = { viewModel.playAllSongsOfArtist() }
                            )
                        }

                        // Albums Section
                        if (currentArtist.albums.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Albums",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            items(currentArtist.albums) { album ->
                                ArtistAlbumCard(
                                    album = album,
                                    isCurrent = album.id == currentAlbumId,
                                    isPlaying = musicState.isPlaying && album.id == currentAlbumId,
                                    onPlayClick = { viewModel.handleAlbumPlaybackClick(album) },
                                    onNavigateTo = onNavigateTo
                                )
                            }
                        }

                        // Top Songs Section (if available)
                        if (state.topSongs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Top Songs",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            itemsIndexed(state.topSongs) { index, song ->
                                ArtistSongItem(
                                    song = song,
                                    trackNumber = index + 1,
                                    isCurrent = song.id == currentSongId,
                                    onPlay = { viewModel.playSong(song) }
                                )
                            }
                        }

                        // Additional Information Section
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Artist Information",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = "${currentArtist.albumCount} albums available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // FAB
                    FloatingActionButton(
                        onClick = { viewModel.handleArtistPlaybackFabClick() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = if (isCurrentArtistPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrentArtistPlaying) "Pause artist" else "Play all artist songs",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    artist: Artist,
    onPlayAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Top Row: Artist Image + Artist Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Artist Image
                CoverArtImage(
                    coverArtUrl = artist.coverArt,
                    coverArtId = artist.coverArtId,
                    contentDescription = "Artist image for ${artist.name}",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Artist Info Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Artist Name
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Album Count
                    Text(
                        text = "${artist.albumCount} albums",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Section: Artist Info Chips (below artist image area)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Album Count
                CompactInfoChip(
                    label = "${artist.albumCount} albums"
                )

                // Play All Chip
                CompactInfoChip(
                    label = "Play all songs"
                )
            }
        }
    }
}

@Composable
private fun ArtistAlbumCard(
    album: Album,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onNavigateTo: (Nav) -> Unit
) {
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album content area (clickable for navigation)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        onClick = { onNavigateTo(Nav.AlbumDetail(album.id)) },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtImage(
                    coverArtUrl = album.coverArt,
                    coverArtId = album.coverArtId,
                    contentDescription = "Album cover for ${album.name}",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = album.year?.toString() ?: "Unknown year",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.6f)
                        )

                        if (album.songCount > 0) {
                            Text(
                                text = "• ${album.songCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    }

                    album.duration?.let { duration ->
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onPlayClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause album" else "Play album",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CompactInfoChip(
    label: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
        )
    }
}

@Composable
private fun ArtistSongItem(
    song: Song,
    trackNumber: Int,
    isCurrent: Boolean,
    onPlay: () -> Unit
) {
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Number
            Text(
                text = trackNumber.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .width(32.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Song Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Duration
            song.duration?.let { duration ->
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Format duration from seconds to MM:SS format
 */
private fun formatDuration(durationInSeconds: Int): String {
    val minutes = durationInSeconds / 60
    val seconds = durationInSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
