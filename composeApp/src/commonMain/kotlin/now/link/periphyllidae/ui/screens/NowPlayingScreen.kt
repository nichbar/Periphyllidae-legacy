package now.link.periphyllidae.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import now.link.periphyllidae.ui.layout.ScreenContainer
import now.link.periphyllidae.viewmodels.NowPlayingViewModel
import now.link.periphyllidae.mvi.MusicState

// Enum for different screen layouts
private enum class ScreenLayout {
    COMPACT,    // ~1:1 aspect ratio (400x400)
    PORTRAIT,   // Tall aspect ratio
    LANDSCAPE   // Wide aspect ratio
}

private val OverlayTopBarHeight = 64.dp
private val ContentTopPadding = OverlayTopBarHeight + 8.dp

// Helper function to determine screen layout based on size and aspect ratio
private fun getScreenLayout(maxWidth: Float, maxHeight: Float): ScreenLayout {
    val aspectRatio = maxWidth / maxHeight

    return when {
        // Force compact controls when width/height is constrained.
        maxWidth < 420f || maxHeight < 620f -> ScreenLayout.COMPACT
        // Compact/square layout (~1:1 aspect ratio, e.g., 400x400)
        aspectRatio in 0.9f..1.1f -> ScreenLayout.COMPACT
        // Ultra-wide screens (landscape tablets, desktop monitors)
        aspectRatio >= 1.5f -> ScreenLayout.LANDSCAPE
        // Standard phones and medium aspect ratios
        else -> ScreenLayout.PORTRAIT
    }
}

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToAlbumDetail: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenContainer() { paddingValues ->
        BoxWithConstraints {
            // Dynamic layout detection that updates when constraints change
            val currentLayout = getScreenLayout(
                this.maxWidth.value,
                this.maxHeight.value
            )

            if (state.currentSong == null) {
                EmptyStateContent()
            } else {
                // Animated content switcher for smooth layout transitions
                AnimatedContent(
                    targetState = currentLayout,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "layout_transition"
                ) { layout ->
                    when (layout) {
                        ScreenLayout.COMPACT -> CompactLayoutContent(
                            state = state,
                            viewModel = viewModel,
                            onNavigateToArtistDetail = onNavigateToArtistDetail
                        )

                        ScreenLayout.LANDSCAPE -> WideLayoutContent(
                            state = state,
                            viewModel = viewModel,
                            onNavigateToArtistDetail = onNavigateToArtistDetail,
                            onNavigateToAlbumDetail = onNavigateToAlbumDetail
                        )

                        ScreenLayout.PORTRAIT -> DefaultLayoutContent(
                            state = state,
                            viewModel = viewModel,
                            onNavigateToArtistDetail = onNavigateToArtistDetail,
                            onNavigateToAlbumDetail = onNavigateToAlbumDetail
                        )
                    }
                }
            }
        }

        // Floating Top Bar for back navigation
        OverlayTopBar(onNavigateBack = onNavigateBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayTopBar(onNavigateBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(OverlayTopBarHeight),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun EmptyStateContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = ContentTopPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No song is currently playing",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DefaultLayoutContent(
    state: MusicState,
    viewModel: NowPlayingViewModel,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToAlbumDetail: (String) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = ContentTopPadding)
    ) {
        val boxConstraintsScope = this
        val isCompactScreen = boxConstraintsScope.maxWidth < 600.dp
        val isShortScreen = boxConstraintsScope.maxHeight < 760.dp

        // Responsive padding based on screen size
        val responsivePadding = if (isCompactScreen) 24.dp else 36.dp
        val responsiveSpacing = if (isCompactScreen) 20.dp else 28.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = responsivePadding,
                    start = responsivePadding,
                    end = responsivePadding,
                    bottom = responsivePadding
                )
                .then(if (isShortScreen) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .animateContentSize(
                    animationSpec = tween(300)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Responsive album art size
            val albumArtSize = if (isCompactScreen) {
                minOf(
                    boxConstraintsScope.maxWidth - responsivePadding * 2,
                    boxConstraintsScope.maxHeight * if (isShortScreen) 0.3f else 0.4f
                )
            } else {
                boxConstraintsScope.maxWidth * if (isShortScreen) 0.55f else 0.7f
            }

            AlbumArtSection(
                state = state,
                modifier = Modifier
                    .size(albumArtSize)
                    .animateContentSize(
                        animationSpec = tween(300)
                    )
            )

            Spacer(modifier = Modifier.height(responsiveSpacing))
            SongInfoSection(
                state = state,
                viewModel = viewModel,
                onNavigateToArtistDetail = onNavigateToArtistDetail,
                onNavigateToAlbumDetail = onNavigateToAlbumDetail
            )
            Spacer(modifier = Modifier.height(responsiveSpacing))
            ProgressSection(state, viewModel)
            Spacer(modifier = Modifier.height(responsiveSpacing))
            PlaybackControlsSection(state, viewModel, showAllControls = !isShortScreen)
            if (isShortScreen) {
                Spacer(modifier = Modifier.height(responsivePadding))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}


@Composable
private fun CompactLayoutContent(
    state: MusicState,
    viewModel: NowPlayingViewModel,
    onNavigateToArtistDetail: (String) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = ContentTopPadding)
            .padding(horizontal = 16.dp)
    ) {
        val isShortScreen = maxHeight < 560.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isShortScreen) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Compact album art (160dp x 160dp with 16dp corner radius)
            val coverSize = if (isShortScreen) 128.dp else 160.dp
            Card(
                modifier = Modifier
                    .size(coverSize),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                AsyncImage(
                    model = state.currentSong?.coverArt,
                    contentDescription = "Album cover for ${state.currentSong?.title ?: ""}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Song info - only title and artist (no album)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.currentSong?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = state.currentSong?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        state.currentSong?.let { song ->
                            viewModel.getArtistIdForCurrentSong { artistId ->
                                artistId?.let { id ->
                                    onNavigateToArtistDetail(id)
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Minimal playback controls - only prev/play/next (no shuffle/repeat, no progress bar)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous (smaller - 40dp)
                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    enabled = state.currentIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous song",
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Play/Pause (smaller - 56dp)
                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Next (smaller - 40dp)
                IconButton(
                    onClick = { viewModel.skipNext() },
                    enabled = state.currentIndex < state.queue.size - 1
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next song",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WideLayoutContent(
    state: MusicState,
    viewModel: NowPlayingViewModel,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToAlbumDetail: (String) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = ContentTopPadding)
    ) {
        val boxConstraintsScope = this
        val isUltraWide = boxConstraintsScope.maxWidth > 1200.dp

        // Responsive spacing based on screen width
        val responsiveSpacing = when {
            isUltraWide -> 64.dp
            boxConstraintsScope.maxWidth > 800.dp -> 48.dp
            else -> 32.dp
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(
                    animationSpec = tween(300)
                ),
            horizontalArrangement = Arrangement.spacedBy(responsiveSpacing)
        ) {
            // Left side: Album art with responsive sizing
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val albumArtSize = if (isUltraWide) {
                    boxConstraintsScope.maxHeight * 0.8f
                } else {
                    boxConstraintsScope.maxHeight * 0.7f
                }

                AlbumArtSection(
                    state = state,
                    modifier = Modifier
                        .size(albumArtSize)
                        .animateContentSize(
                            animationSpec = tween(300)
                        )
                )
            }

            // Right side: Controls and info with responsive layout
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val controlSpacing = if (isUltraWide) 40.dp else 32.dp

                SongInfoSection(
                    state = state,
                    viewModel = viewModel,
                    onNavigateToArtistDetail = onNavigateToArtistDetail,
                    onNavigateToAlbumDetail = onNavigateToAlbumDetail
                )
                Spacer(modifier = Modifier.height(controlSpacing))
                ProgressSection(state, viewModel)
                Spacer(modifier = Modifier.height(controlSpacing))
                PlaybackControlsSection(state, viewModel, showAllControls = true)
            }
        }
    }
}

@Composable
private fun AlbumArtSection(
    state: MusicState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.then(
            if (modifier == Modifier) {
                Modifier.fillMaxWidth().aspectRatio(1f)
            } else {
                Modifier.aspectRatio(1f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        AsyncImage(
            model = state.currentSong?.coverArt,
            contentDescription = "Album cover for ${state.currentSong?.title ?: ""}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun SongInfoSection(
    state: MusicState,
    viewModel: NowPlayingViewModel,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToAlbumDetail: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = state.currentSong?.title ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.currentSong?.artist ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                state.currentSong?.let { song ->
                    viewModel.getArtistIdForCurrentSong { artistId ->
                        artistId?.let { id ->
                            onNavigateToArtistDetail(id)
                        }
                    }
                }
            }
        )

        Text(
            text = state.currentSong?.album ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                state.currentSong?.let { song ->
                    viewModel.getAlbumIdForCurrentSong { albumId ->
                        albumId?.let { id ->
                            onNavigateToAlbumDetail(id)
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ProgressSection(
    state: MusicState,
    viewModel: NowPlayingViewModel,
) {
    Column {
        Slider(
            value = if (state.duration > 0) {
                (state.currentPosition.toFloat() / state.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f,
            onValueChange = { progress ->
                val newPosition = (progress * state.duration).toLong()
                viewModel.seekTo(newPosition)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(state.currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = formatTime(state.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PlaybackControlsSection(
    state: MusicState,
    viewModel: NowPlayingViewModel,
    showAllControls: Boolean = true,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 380.dp
        val canShowSideControls = showAllControls && !isNarrow
        val spacing = if (isNarrow) 8.dp else 16.dp
        val sideIconSize = if (isNarrow) 28.dp else 32.dp
        val fabSize = if (isNarrow) 56.dp else 64.dp
        val fabIconSize = if (isNarrow) 28.dp else 32.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle (only in full/wide layout)
            if (canShowSideControls) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Toggle shuffle",
                        tint = if (state.isShuffle) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
                Spacer(modifier = Modifier.width(spacing))
            }

            // Previous
            IconButton(
                onClick = { viewModel.skipPrevious() },
                enabled = state.currentIndex > 0
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous song",
                    modifier = Modifier.size(sideIconSize)
                )
            }

            Spacer(modifier = Modifier.width(spacing))

            // Play/Pause
            FloatingActionButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(fabSize)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(fabIconSize)
                )
            }

            Spacer(modifier = Modifier.width(spacing))

            // Next
            IconButton(
                onClick = { viewModel.skipNext() },
                enabled = state.currentIndex < state.queue.size - 1
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next song",
                    modifier = Modifier.size(sideIconSize)
                )
            }

            // Repeat (only in full/wide layout)
            if (canShowSideControls) {
                Spacer(modifier = Modifier.width(spacing))
                IconButton(
                    onClick = { viewModel.toggleRepeat() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Toggle repeat",
                        tint = if (state.isRepeat) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueInfoSection(state: MusicState) {
    if (state.queue.size > 1) {
        Text(
            text = "${state.currentIndex + 1} of ${state.queue.size} in queue",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
