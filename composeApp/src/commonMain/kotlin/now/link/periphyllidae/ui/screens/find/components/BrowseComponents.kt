package now.link.periphyllidae.ui.screens.find.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Artist
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.ui.components.CoverArtImage
import now.link.periphyllidae.ui.components.RetryErrorCard

@Composable
fun BrowseSongsContent(
    songs: List<Song>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    loadMoreError: String?,
    hasMore: Boolean,
    onPlaySong: (Song) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLoading && songs.isEmpty()) {
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

        if (error != null) {
            item {
                RetryErrorCard(
                    message = error,
                    onRetry = onRetry
                )
            }
        }

        items(songs, key = { it.id }) { song ->
            SongSearchItem(
                song = song,
                onPlay = { onPlaySong(song) }
            )
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (loadMoreError != null) {
            item {
                RetryErrorCard(
                    message = loadMoreError,
                    onRetry = onLoadMore
                )
            }
        }

        if (!isLoading && !isLoadingMore && loadMoreError == null && hasMore) {
            item {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load More")
                }
            }
        }
    }
}

@Composable
fun BrowseAlbumsContent(
    albums: List<Album>,
    currentSong: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    loadMoreError: String?,
    hasMore: Boolean,
    onPlayAlbum: (Album) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Album) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Column {
        when {
            isLoading && albums.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                RetryErrorCard(
                    message = error,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            else -> {
                BoxWithConstraints {
                    val availableWidth = maxWidth
                    val minItemWidth = 100.dp
                    val spacing = 12.dp
                    val padding = 32.dp // 16dp on each side
                    val availableWidthForItems = availableWidth - padding
                    val numberOfColumns =
                        ((availableWidthForItems + spacing) / (minItemWidth + spacing)).toInt()
                            .coerceAtLeast(1)

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val chunkedAlbums = albums.chunked(numberOfColumns)

                        items(
                            chunkedAlbums,
                            key = { chunk -> chunk.map { it.id }.hashCode() }) { rowAlbums ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                for (i in 0 until numberOfColumns) {
                                    if (i < rowAlbums.size) {
                                        val album = rowAlbums[i]
                                        val currentSongId = currentSong?.id
                                        val isCurrentAlbumPlaying = isPlaying &&
                                            (currentSong?.albumId == album.id ||
                                                currentSongId != null && album.songs.any { it.id == currentSongId })

                                        Box(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            AlbumGridItem(
                                                album = album,
                                                isPlaying = isCurrentAlbumPlaying,
                                                onPlay = { onPlayAlbum(album) },
                                                onClick = { onAlbumClick(album) },
                                                onArtistClick = album.artistId?.let {
                                                    { onArtistClick(album) }
                                                }
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        if (loadMoreError != null) {
                            item {
                                RetryErrorCard(
                                    message = loadMoreError,
                                    onRetry = onLoadMore
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isLoading && !isLoadingMore && loadMoreError == null && hasMore) {
            TextButton(
                onClick = onLoadMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Load More")
            }
        }
    }
}

@Composable
fun BrowseArtistsContent(
    artists: List<Artist>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    loadMoreError: String?,
    hasMore: Boolean,
    onArtistClick: (Artist) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Column {
        when {
            isLoading && artists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                RetryErrorCard(
                    message = error,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            else -> {
                BoxWithConstraints {
                    val availableWidth = maxWidth
                    val minItemWidth = 100.dp
                    val spacing = 12.dp
                    val padding = 32.dp // 16dp on each side
                    val availableWidthForItems = availableWidth - padding
                    val numberOfColumns =
                        ((availableWidthForItems + spacing) / (minItemWidth + spacing)).toInt()
                            .coerceAtLeast(1)

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val chunkedArtists = artists.chunked(numberOfColumns)

                        items(
                            chunkedArtists,
                            key = { chunk -> chunk.map { it.id }.hashCode() }) { rowArtists ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                for (i in 0 until numberOfColumns) {
                                    if (i < rowArtists.size) {
                                        Box(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            ArtistGridItem(
                                                artist = rowArtists[i],
                                                onArtistClick = { onArtistClick(rowArtists[i]) }
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        if (loadMoreError != null) {
                            item {
                                RetryErrorCard(
                                    message = loadMoreError,
                                    onRetry = onLoadMore
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isLoading && !isLoadingMore && loadMoreError == null && hasMore) {
            TextButton(
                onClick = onLoadMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Load More")
            }
        }
    }
}

@Composable
fun AlbumGridItem(
    album: Album,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onClick: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Card(
            modifier = Modifier
                .size(80.dp)
                .clickable { onPlay() },
            shape = CircleShape
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CoverArtImage(
                    coverArtUrl = album.coverArt,
                    coverArtId = album.coverArtId,
                    contentDescription = "Play album ${album.name}",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause album",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        )

        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onArtistClick != null) {
                        Modifier.clickable { onArtistClick() }
                    } else {
                        Modifier
                    }
                )
        )
    }
}

@Composable
fun ArtistGridItem(
    artist: Artist,
    onArtistClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onArtistClick() }
            .width(100.dp)
    ) {
        Card(
            modifier = Modifier.size(80.dp),
            shape = CircleShape
        ) {
            CoverArtImage(
                coverArtUrl = artist.coverArt,
                coverArtId = artist.coverArtId,
                contentDescription = "Artist ${artist.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${artist.albumCount} albums",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
