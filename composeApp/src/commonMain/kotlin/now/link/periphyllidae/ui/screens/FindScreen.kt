package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.periphyllidae.components.Nav
import now.link.periphyllidae.mvi.FindView
import now.link.periphyllidae.mvi.HistoryItemType
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Artist
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.ui.components.RetryErrorCard
import now.link.periphyllidae.ui.layout.ScreenContainer
import now.link.periphyllidae.ui.layout.TopBarConfig
import now.link.periphyllidae.ui.screens.find.components.*
import now.link.periphyllidae.viewmodels.FindViewModel

@Composable
fun FindScreen(
    viewModel: FindViewModel,
    onNavigateTo: (Nav) -> Unit,
) {
    val state by viewModel.findState.collectAsStateWithLifecycle()
    val musicState by viewModel.musicState.collectAsStateWithLifecycle()
    val isSearchBarFocused by viewModel.isSearchBarFocused.collectAsStateWithLifecycle()

    val topBarConfig = TopBarConfig(
        title = when (state.currentView) {
            FindView.Search -> "Find"
            FindView.AllSongs -> "All Songs"
            FindView.AllAlbums -> "All Albums"
            FindView.AllArtists -> "All Artists"
        },
        navigationIcon = if (state.currentView != FindView.Search) Icons.Default.ArrowBack else null,
        onNavigationClick = if (state.currentView != FindView.Search) {
            { viewModel.backToSearch() }
        } else null
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
                    loadMoreError = state.loadMoreError,
                    hasMore = state.hasMoreSongs,
                    onPlaySong = viewModel::playSong,
                    onRetry = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.BrowseAllSongs) },
                    onLoadMore = {
                        viewModel.handleIntent(
                            now.link.periphyllidae.mvi.FindIntent.LoadMoreSongs(
                                state.browseSongs.size
                            )
                        )
                    }
                )

                FindView.AllAlbums -> BrowseAlbumsContent(
                    albums = state.browseAlbums,
                    currentSong = musicState.currentSong,
                    isPlaying = musicState.isPlaying,
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    error = state.error,
                    loadMoreError = state.loadMoreError,
                    hasMore = state.hasMoreAlbums,
                    onPlayAlbum = viewModel::handleAlbumPlaybackClick,
                    onAlbumClick = { album -> onNavigateTo.invoke(Nav.AlbumDetail(album.id)) },
                    onArtistClick = { album ->
                        album.artistId?.let { onNavigateTo.invoke(Nav.ArtistDetail(it)) }
                    },
                    onRetry = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.BrowseAllAlbums) },
                    onLoadMore = {
                        viewModel.handleIntent(
                            now.link.periphyllidae.mvi.FindIntent.LoadMoreAlbums(
                                state.browseAlbums.size
                            )
                        )
                    }
                )

                FindView.AllArtists -> BrowseArtistsContent(
                    artists = state.browseArtists,
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    error = state.error,
                    loadMoreError = state.loadMoreError,
                    hasMore = state.hasMoreArtists,
                    onArtistClick = { artist -> onNavigateTo.invoke(Nav.ArtistDetail(artist.id)) },
                    onRetry = { viewModel.handleIntent(now.link.periphyllidae.mvi.FindIntent.BrowseAllArtists) },
                    onLoadMore = {
                        viewModel.handleIntent(
                            now.link.periphyllidae.mvi.FindIntent.LoadMoreArtists(
                                state.browseArtists.size
                            )
                        )
                    }
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
    onNavigateTo: (Nav) -> Unit,
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
                onSearch = { viewModel.performSearch(state.searchQuery) },
                onClear = { viewModel.clearSearch() },
                onFocusChange = { viewModel.setSearchBarFocus(it) },
                isFocused = isSearchBarFocused,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
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

        // Search History Section
        if (state.searchHistory.isNotEmpty() && state.searchQuery.isEmpty()) {
            item {
                SearchHistorySection(
                    historyItems = state.searchHistory,
                    onHistoryItemClick = { item ->
                        viewModel.handleIntent(
                            now.link.periphyllidae.mvi.FindIntent.SelectHistoryItem(
                                item
                            )
                        )
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
                RetryErrorCard(
                    message = state.error,
                    onRetry = { viewModel.performSearch(state.searchQuery) }
                )
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
        if (!state.isLoading && state.error == null && state.searchResults == null && state.searchQuery.isEmpty() && state.searchHistory.isEmpty()) {
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
