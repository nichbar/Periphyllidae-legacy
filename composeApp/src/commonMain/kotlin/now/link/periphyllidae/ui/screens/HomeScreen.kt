package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import now.link.periphyllidae.components.Nav
import now.link.periphyllidae.ui.components.CoverArtImage
import now.link.periphyllidae.ui.components.RetryErrorCard
import now.link.periphyllidae.ui.layout.ScreenContainer
import org.koin.compose.koinInject
import now.link.periphyllidae.viewmodels.HomeViewModel
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Playlist

// Constants for consistent design system
private object HomeScreenDefaults {
    val sectionVerticalSpacing = 16.dp
    val itemSpacing = 12.dp
    val itemSpacingLandscape = 16.dp
    val cardContentPadding = 12.dp
    val horizontalPadding = 16.dp
    val loadingStatePadding = 32.dp
    val cardWidth = 138.dp
    val cardCornerRadius = 12.dp
    val playlistCardPadding = 16.dp
    val errorCardPadding = 16.dp
    
    fun getItemSpacing(isLandscape: Boolean) = if (isLandscape) itemSpacingLandscape else itemSpacing
    fun getSectionSpacing(isLandscape: Boolean) = if (isLandscape) 32.dp else 24.dp
}

// Data classes for configuration-driven UI
data class AlbumSectionData(
    val title: String,
    val albums: List<Album>,
    val maxItems: Int? = null
) {
    val displayAlbums: List<Album>
        get() = maxItems?.let { albums.take(it) } ?: albums
}

sealed class HomeSection {
    data class AlbumsSection(val data: AlbumSectionData) : HomeSection()
    data class PlaylistsSection(val playlists: List<Playlist>) : HomeSection()
    data class LoadingSection(val isLoading: Boolean) : HomeSection()
    data class ErrorSection(val errorMessage: String?) : HomeSection()
}

@Composable
fun HomeScreen(
    onNavigateTo: (Nav) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = koinInject()
    val musicState by viewModel.musicState.collectAsState()
    val authState by viewModel.authState.collectAsState()

    val currentServer = authState.currentServer
    var lastServerIdentifier by remember { mutableStateOf(currentServer?.let { "${it.url}:${it.username}" }) }

    LaunchedEffect(currentServer) {
        val currentServerIdentifier = currentServer?.let { "${it.url}:${it.username}" }
        if (lastServerIdentifier != currentServerIdentifier) {
            viewModel.refreshContent()
            lastServerIdentifier = currentServerIdentifier
        }
    }

    BoxWithConstraints {
        val box = this
        val isLandscape = box.maxWidth > box.maxHeight

        ScreenContainer(
            modifier = modifier,
            floatingActionButton = {
                ShuffleFloatingActionButton(
                    onClick = { viewModel.playRandomSong() },
                    onLongClick = { viewModel.refreshContent() }
                )
            }
        ) { paddingValues ->
            HomeContent(
                state = musicState,
                isLandscape = isLandscape,
                onAlbumPlay = { album -> viewModel.playAlbum(album) },
                onAlbumClick = {
                    onNavigateTo(Nav.AlbumDetail(it.id))
                },
                onPlaylistClick = { /* TODO: Navigate to playlist detail */ },
                onRetryLoad = { viewModel.refreshContent() },
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShuffleFloatingActionButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(56.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = FloatingActionButtonDefaults.shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Play Random Song"
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: now.link.periphyllidae.mvi.MusicState,
    isLandscape: Boolean,
    onAlbumPlay: (Album) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val sections = buildList {
        add(HomeSection.AlbumsSection(AlbumSectionData("Recently Added", state.newestAlbums)))
        add(HomeSection.AlbumsSection(AlbumSectionData("Most Played", state.mostPlayedAlbums, maxItems = 10)))
        add(HomeSection.AlbumsSection(AlbumSectionData("Recently Played", state.recentlyPlayedAlbums, maxItems = 10)))
        add(HomeSection.PlaylistsSection(state.playlists))
        add(HomeSection.LoadingSection(state.isLoadingHome))
        add(HomeSection.ErrorSection(state.homeError))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.mouseDragScrollable(listState, Orientation.Vertical),
        verticalArrangement = Arrangement.spacedBy(HomeScreenDefaults.getSectionSpacing(isLandscape)),
        contentPadding = PaddingValues(top = HomeScreenDefaults.sectionVerticalSpacing)
    ) {
        sections.forEach { section ->
            renderSection(
                section = section,
                isLandscape = isLandscape,
                onAlbumPlay = onAlbumPlay,
                onAlbumClick = onAlbumClick,
                onPlaylistClick = onPlaylistClick,
                onRetryLoad = onRetryLoad
            )
        }
    }
}

private fun LazyListScope.renderSection(
    section: HomeSection,
    isLandscape: Boolean,
    onAlbumPlay: (Album) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onRetryLoad: () -> Unit
) {
    when (section) {
        is HomeSection.AlbumsSection -> {
            albumSection(
                data = section.data,
                isLandscape = isLandscape,
                onAlbumClick = onAlbumClick,
                onAlbumPlay = onAlbumPlay,
            )
        }
        is HomeSection.PlaylistsSection -> {
            playlistSection(
                playlists = section.playlists,
                onPlaylistClick = onPlaylistClick
            )
        }
        is HomeSection.LoadingSection -> {
            loadingSection(isLoading = section.isLoading)
        }
        is HomeSection.ErrorSection -> {
            errorSection(
                errorMessage = section.errorMessage,
                onRetry = onRetryLoad
            )
        }
    }
}

// LazyListScope extensions for each section type
private fun LazyListScope.albumSection(
    data: AlbumSectionData,
    isLandscape: Boolean,
    onAlbumClick: (Album) -> Unit,
    onAlbumPlay: (Album) -> Unit
) {
    if (data.displayAlbums.isNotEmpty()) {
        item {
            AlbumSectionContent(
                title = data.title,
                albums = data.displayAlbums,
                isLandscape = isLandscape,
                onAlbumClick = onAlbumClick,
                onAlbumPlay = onAlbumPlay
            )
        }
    }
}

private fun LazyListScope.playlistSection(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit
) {
    if (playlists.isNotEmpty()) {
        item {
            PlaylistSectionContent(
                playlists = playlists,
                onPlaylistClick = onPlaylistClick
            )
        }
    }
}

private fun LazyListScope.loadingSection(isLoading: Boolean) {
    if (isLoading) {
        item {
            LoadingStateContent()
        }
    }
}

private fun LazyListScope.errorSection(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    if (errorMessage != null) {
        item {
            ErrorStateContent(
                errorMessage = errorMessage,
                onRetry = onRetry
            )
        }
    }
}

// Reusable content components
@Composable
private fun AlbumSectionContent(
    title: String,
    albums: List<Album>,
    isLandscape: Boolean,
    onAlbumClick: (Album) -> Unit,
    onAlbumPlay: (Album) -> Unit
) {
    val listState = rememberLazyListState()

    Column {
        SectionHeader(title = title)

        LazyRow(
            state = listState,
            modifier = Modifier.mouseDragScrollable(listState, Orientation.Horizontal),
            horizontalArrangement = Arrangement.spacedBy(HomeScreenDefaults.getItemSpacing(isLandscape)),
            contentPadding = PaddingValues(horizontal = HomeScreenDefaults.horizontalPadding)
        ) {
            items(albums, key = { it.id }) { album ->
                MediaCard(
                    title = album.name,
                    subtitle = album.artist,
                    imageUrl = album.coverArt,
                    imageId = album.coverArtId,
                    onClick = { onAlbumClick(album) },
                    onPlay = { onAlbumPlay(album) }
                )
            }
        }
    }
}

internal expect fun Modifier.mouseDragScrollable(
    listState: LazyListState,
    orientation: Orientation
): Modifier

@Composable
private fun PlaylistSectionContent(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit
) {
    Column {
        Spacer(Modifier.height(HomeScreenDefaults.sectionVerticalSpacing))
        SectionHeader(title = "Your Playlists")

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = HomeScreenDefaults.horizontalPadding)
        ) {
            playlists.forEach { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

@Composable
private fun LoadingStateContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HomeScreenDefaults.loadingStatePadding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorStateContent(
    errorMessage: String,
    onRetry: () -> Unit
) {
    RetryErrorCard(
        message = errorMessage,
        onRetry = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeScreenDefaults.horizontalPadding)
    )
}

// Reusable UI components
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(
            start = HomeScreenDefaults.horizontalPadding, 
            bottom = HomeScreenDefaults.cardContentPadding
        )
    )
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    imageId: String?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(HomeScreenDefaults.cardWidth)
    ) {
        Column {
            CoverArtImage(
                coverArtUrl = imageUrl,
                coverArtId = imageId,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(
                        RoundedCornerShape(
                            topStart = HomeScreenDefaults.cardCornerRadius, 
                            topEnd = HomeScreenDefaults.cardCornerRadius
                        )
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onPlay() },
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.padding(HomeScreenDefaults.cardContentPadding)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() }
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
    ) {
        Text(
            text = playlist.name,
            modifier = Modifier.padding(HomeScreenDefaults.playlistCardPadding)
        )
    }
}
