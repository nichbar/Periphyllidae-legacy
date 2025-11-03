package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import now.link.periphyllidae.components.Nav
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Artist
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.mvi.FindView
import now.link.periphyllidae.mvi.HistoryItemType
import now.link.periphyllidae.ui.layout.ScreenContainer
import now.link.periphyllidae.ui.layout.TopBarConfig
import now.link.periphyllidae.viewmodels.FindViewModel
import org.koin.compose.koinInject

@Composable
fun FindScreen(
    onNavigateTo: (Nav) -> Unit
) {
    val viewModel: FindViewModel = koinInject()
    val state by viewModel.findState.collectAsStateWithLifecycle()
    val isSearchBarFocused by viewModel.isSearchBarFocused.collectAsStateWithLifecycle()

    val topBarConfig = TopBarConfig(
        title = "Find",
        navigationIcon = if (state.currentView != FindView.Search) Icons.Default.ArrowBack else null,
        onNavigationClick = if (state.currentView != FindView.Search) { { viewModel.backToSearch() } } else null
    )

    ScreenContainer(
        topBarConfig = topBarConfig
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state.currentView) {
                FindView.Search -> SearchViewContent(
                    state = state,
                    viewModel = viewModel,
                    isSearchBarFocused = isSearchBarFocused,
                    onNavigateTo = onNavigateTo
                )
                FindView.AllSongs -> BrowseSongsContent(
                    songs = state.browseSongs,
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    error = state.error,
                    onPlaySong = viewModel::playSong,
                    onLoadMore = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.LoadMoreSongs(state.browseSongs.size)) }
                )
                FindView.AllAlbums -> BrowseAlbumsContent(
                    albums = state.browseAlbums,
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    error = state.error,
                    onPlayAlbum = viewModel::playAlbum,
                    onAlbumClick = { album -> onNavigateTo.invoke(Nav.AlbumDetail(album.id)) },
                    onLoadMore = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.LoadMoreAlbums(state.browseAlbums.size)) }
                )
                FindView.AllArtists -> BrowseArtistsContent(
                    artists = state.browseArtists,
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    error = state.error,
                    onArtistClick = { artist -> onNavigateTo.invoke(Nav.ArtistDetail(artist.id)) },
                    onLoadMore = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.LoadMoreArtists(state.browseArtists.size)) }
                )
            }
        }
    }
}

@Composable
private fun SearchViewContent(
    state: now.link.periphyllidae.mvi.FindState,
    viewModel: FindViewModel,
    isSearchBarFocused: Boolean,
    onNavigateTo: (Nav) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search Bar
        item {
            SearchTextField(
                query = state.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClear = { viewModel.clearSearch() },
                onFocusChange = { viewModel.setSearchBarFocus(it) },
                isFocused = isSearchBarFocused,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Search History Section
        if (state.searchHistory.isNotEmpty() && state.searchQuery.isEmpty()) {
            item {
                SearchHistorySection(
                    historyItems = state.searchHistory,
                    onHistoryItemClick = { item ->
                        viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.SelectHistoryItem(item))
                        when (item.type) {
                            is HistoryItemType.Song -> {
                                // Song is played automatically by ViewModel
                            }
                            is HistoryItemType.Album -> {
                                val album = item.data as? Album
                                album?.let { onNavigateTo.invoke(Nav.AlbumDetail(it.id)) }
                            }
                            is HistoryItemType.Artist -> {
                                val artist = item.data as? Artist
                                artist?.let { onNavigateTo.invoke(Nav.ArtistDetail(it.id)) }
                            }
                        }
                    },
                    onClearHistory = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.ClearHistory) }
                )
            }
        }

        // Quick Actions Section
        if (state.searchQuery.isEmpty()) {
            item {
                QuickActionsSection(
                    onBrowseAllSongs = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.BrowseAllSongs) },
                    onBrowseAllAlbums = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.BrowseAllAlbums) },
                    onBrowseAllArtists = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.BrowseAllArtists) }
                )
            }
        }

        // Loading Indicator
        if (state.isLoading) {
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

        // Error Message
        if (state.error != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // Search Results
        if (state.searchResults != null && !state.isLoading) {
            item {
                SearchResults(
                    results = state.searchResults,
                    onPlaySong = { song ->
                        viewModel.playSong(song)
                        viewModel.addToHistoryFromSong(song)
                    },
                    onPlayAlbum = { album ->
                        viewModel.playAlbum(album)
                        viewModel.addToHistoryFromAlbum(album)
                    },
                    onArtistClick = { artist ->
                        onNavigateTo.invoke(Nav.ArtistDetail(artist.id))
                        viewModel.addToHistoryFromArtist(artist)
                    },
                    onAlbumClick = { album ->
                        onNavigateTo.invoke(Nav.AlbumDetail(album.id))
                        viewModel.addToHistoryFromAlbum(album)
                    }
                )
            }
        }

        // Empty State
        if (!state.isLoading && state.error == null && state.searchResults == null && state.searchQuery.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Search for artists, albums, and songs",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    isFocused: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChange(focusState.isFocused)
            },
        placeholder = { Text("Search music...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        enabled = !isLoading,
        singleLine = true
    )
}

@Composable
private fun SearchHistorySection(
    historyItems: List<now.link.periphyllidae.mvi.HistoryItem>,
    onHistoryItemClick: (now.link.periphyllidae.mvi.HistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onClearHistory) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            historyItems.forEach { item ->
                HistoryItemRow(
                    item = item,
                    onItemClick = { onHistoryItemClick(item) }
                )
                if (item != historyItems.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItemRow(
    item: now.link.periphyllidae.mvi.HistoryItem,
    onItemClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Card(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            item.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Type indicator and play icon
        when (item.type) {
            is HistoryItemType.Song -> {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play song",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            is HistoryItemType.Album -> {
                Icon(
                    Icons.Default.Album,
                    contentDescription = "View album",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            is HistoryItemType.Artist -> {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "View artist",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onBrowseAllSongs: () -> Unit,
    onBrowseAllAlbums: () -> Unit,
    onBrowseAllArtists: () -> Unit
) {
    Column {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "All Songs",
                icon = Icons.Default.MusicNote,
                onClick = onBrowseAllSongs,
                modifier = Modifier.weight(1f)
            )

            QuickActionCard(
                title = "All Albums",
                icon = Icons.Default.Album,
                onClick = onBrowseAllAlbums,
                modifier = Modifier.weight(1f)
            )

            QuickActionCard(
                title = "All Artists",
                icon = Icons.Default.Person,
                onClick = onBrowseAllArtists,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchResults(
    results: now.link.periphyllidae.models.SearchResult,
    onPlaySong: (Song) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Artists
            if (results.artists.isNotEmpty()) {
                Text(
                    text = "Artists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(results.artists, key = { it.id }) { artist ->
                        ArtistIconItem(
                            artist = artist,
                            onArtistClick = { onArtistClick(artist) }
                        )
                    }
                }
            }

            // Albums
            if (results.albums.isNotEmpty()) {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (results.artists.isNotEmpty()) 0.dp else 0.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    results.albums.forEach { album ->
                        AlbumListItem(
                            album = album,
                            onPlay = { onPlayAlbum(album) },
                            onClick = { onAlbumClick(album) }
                        )
                    }
                }
            }

            // Songs
            if (results.songs.isNotEmpty()) {
                Text(
                    text = "Songs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (results.artists.isNotEmpty() || results.albums.isNotEmpty()) 0.dp else 0.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    results.songs.forEach { song ->
                        SongSearchItem(
                            song = song,
                            onPlay = { onPlaySong(song) }
                        )
                    }
                }
            }

            // Empty results
            if (results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// Reuse existing components from SearchScreen
@Composable
private fun ArtistIconItem(
    artist: Artist,
    onArtistClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onArtistClick() }
            .width(80.dp)
    ) {
        Card(
            modifier = Modifier.size(64.dp),
            shape = CircleShape
        ) {
            AsyncImage(
                model = artist.coverArt,
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AlbumListItem(
    album: Album,
    onPlay: () -> Unit,
    onClick: () -> Unit
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
                model = album.coverArt,
                contentDescription = "Album cover for ${album.name}",
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
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
                if (album.year != null) {
                    Text(
                        text = album.year.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play album"
                )
            }
        }
    }
}

@Composable
private fun SongSearchItem(
    song: Song,
    onPlay: () -> Unit
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
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
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
private fun BrowseSongsContent(
    songs: List<Song>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onPlaySong: (Song) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "All Songs",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

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
}

@Composable
private fun BrowseAlbumsContent(
    albums: List<Album>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onPlayAlbum: (Album) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "All Albums",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

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
            AlbumListItem(
                album = album,
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
}

@Composable
private fun BrowseArtistsContent(
    artists: List<Artist>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onArtistClick: (Artist) -> Unit,
    onLoadMore: () -> Unit
) {
    Column {
        Text(
            text = "All Artists",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, bottom = 8.dp)
        )

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
                BoxWithConstraints {
                    val box = this

                    val availableWidth = box.maxWidth
                    val minItemWidth = 100.dp
                    val spacing = 12.dp
                    val padding = 32.dp // 16dp on each side
                    val availableWidthForItems = availableWidth - padding
                    val numberOfColumns = ((availableWidthForItems + spacing) / (minItemWidth + spacing)).toInt().coerceAtLeast(1)

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val chunkedArtists = artists.chunked(numberOfColumns)

                        items(chunkedArtists, key = { chunk -> chunk.map { it.id }.hashCode() }) { rowArtists ->
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
private fun ArtistGridItem(
    artist: Artist,
    onArtistClick: () -> Unit
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
            AsyncImage(
                model = artist.coverArt,
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${artist.albumCount} albums",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}