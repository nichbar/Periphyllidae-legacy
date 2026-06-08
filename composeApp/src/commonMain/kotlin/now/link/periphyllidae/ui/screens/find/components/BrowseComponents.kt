package now.link.periphyllidae.ui.screens.find.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.launch
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Artist
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.ui.components.CoverArtImage
import now.link.periphyllidae.ui.screens.find.navigation.AlphabetSidebar
import now.link.periphyllidae.ui.screens.find.utils.createCharIndexMap
import now.link.periphyllidae.ui.screens.find.utils.getAvailableChars

@Composable
fun BrowseSongsContent(
    songs: List<Song>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onPlaySong: (Song) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Calculate character index mapping for quick locate
    val charIndexMap = remember(songs) {
        createCharIndexMap(songs) { song -> song.title }
    }
    val availableChars = remember(songs) {
        getAvailableChars(charIndexMap)
    }

    // Only show sidebar if we have enough items and multiple characters
    val shouldShowSidebar = songs.size > 10 && availableChars.size > 1

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
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
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
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

            if (!isLoading && !isLoadingMore && songs.isNotEmpty()) {
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

        // Quick locate sidebar
        if (shouldShowSidebar) {
            AlphabetSidebar(
                availableChars = availableChars,
                charIndexMap = charIndexMap,
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
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
    onPlayAlbum: (Album) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Calculate character index mapping for quick locate
    val charIndexMap = remember(albums) {
        createCharIndexMap(albums) { album -> album.name }
    }
    val availableChars = remember(albums) {
        getAvailableChars(charIndexMap)
    }

    // Only show sidebar if we have enough items and multiple characters
    val shouldShowSidebar = albums.size > 10 && availableChars.size > 1

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            if (isLoading && albums.isEmpty()) {
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
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(albums, key = { it.id }) { album ->
                val currentSongId = currentSong?.id
                val isCurrentAlbumPlaying = isPlaying &&
                    (currentSong?.albumId == album.id ||
                        currentSongId != null && album.songs.any { it.id == currentSongId })

                AlbumListItem(
                    album = album,
                    isPlaying = isCurrentAlbumPlaying,
                    onPlay = { onPlayAlbum(album) },
                    onClick = { onAlbumClick(album) }
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

            if (!isLoading && !isLoadingMore && albums.isNotEmpty()) {
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

        // Quick locate sidebar
        if (shouldShowSidebar) {
            AlphabetSidebar(
                availableChars = availableChars,
                charIndexMap = charIndexMap,
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
fun BrowseArtistsContent(
    artists: List<Artist>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onArtistClick: (Artist) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Calculate character index mapping for quick locate
    val charIndexMap = remember(artists) {
        createCharIndexMap(artists) { artist -> artist.name }
    }
    val availableChars = remember(artists) {
        getAvailableChars(charIndexMap)
    }

    // Only show sidebar if we have enough items and multiple characters
    val shouldShowSidebar = artists.size > 10 && availableChars.size > 1

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
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    BoxWithConstraints(
                        modifier = Modifier
                    ) {
                        val box = this

                        val availableWidth = box.maxWidth
                        val minItemWidth = 100.dp
                        val spacing = 12.dp
                        val padding = 32.dp // 16dp on each side
                        val availableWidthForItems = availableWidth - padding
                        val numberOfColumns =
                            ((availableWidthForItems + spacing) / (minItemWidth + spacing)).toInt()
                                .coerceAtLeast(1)

                        LazyColumn(
                            state = listState,
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
                        }
                    }

                    // Quick locate sidebar
                    if (shouldShowSidebar) {
                        AlphabetSidebar(
                            availableChars = availableChars,
                            charIndexMap = charIndexMap,
                            listState = listState,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }

        if (!isLoading && !isLoadingMore && artists.isNotEmpty()) {
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
