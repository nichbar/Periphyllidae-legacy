package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.background
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
import now.link.periphyllidae.viewmodels.NowPlayingViewModel
import now.link.periphyllidae.mvi.MusicState

// Enum for different screen layouts
private enum class ScreenLayout {
    PORTRAIT,
    COMPACT,
    LANDSCAPE
}

// Helper function to determine screen layout based on aspect ratio
@Composable
private fun getScreenLayout(maxWidth: Float, maxHeight: Float): ScreenLayout {
    // Calculate the aspect ratio of the available space.
    val aspectRatio = maxWidth / maxHeight

    // Use a 'when' statement to select the appropriate layout
    // based on the calculated aspect ratio.
    return when {
        // If the height is much greater than the width, use a portrait layout.
        aspectRatio < 0.8f -> ScreenLayout.PORTRAIT
        // If the width is much greater than the height, use a landscape layout.
        aspectRatio > 1.2f -> ScreenLayout.LANDSCAPE
        // Otherwise, the shape is close to a square.
        else -> ScreenLayout.COMPACT
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints {
            val boxConstraintsScope = this
            val screenLayout = getScreenLayout(
                boxConstraintsScope.maxWidth.value,
                boxConstraintsScope.maxHeight.value
            )

            if (state.currentSong == null) {
                EmptyStateContent(paddingValues)
            } else {
                when (screenLayout) {
                    ScreenLayout.LANDSCAPE -> WideLayoutContent(
                        state = state,
                        viewModel = viewModel,
                        paddingValues = paddingValues
                    )

                    ScreenLayout.COMPACT -> CompactLayoutContent(
                        state = state,
                        viewModel = viewModel,
                        paddingValues = paddingValues
                    )

                    ScreenLayout.PORTRAIT -> DefaultLayoutContent(
                        state = state,
                        viewModel = viewModel,
                        paddingValues = paddingValues
                    )
                }
            }
        }

        // Overlay Top Bar for back navigation in case of custom layouts
        OverlayTopBar(onNavigateBack = onNavigateBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayTopBar(onNavigateBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp), // Standard TopAppBar height
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
private fun EmptyStateContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
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
    paddingValues: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AlbumArtSection(state)
        Spacer(modifier = Modifier.height(32.dp))
        SongInfoSection(state)
        Spacer(modifier = Modifier.height(32.dp))
        ProgressSection(state, viewModel)
        Spacer(modifier = Modifier.height(32.dp))
        PlaybackControlsSection(state, viewModel, showAllControls = true)
        Spacer(modifier = Modifier.weight(1f))
        QueueInfoSection(state)
    }
}

@Composable
private fun CompactLayoutContent(
    state: MusicState,
    viewModel: NowPlayingViewModel,
    paddingValues: PaddingValues,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp)
    ) {
        val boxConstraintsScope = this

        // Calculate album size based on available space (80% of smaller dimension)
        val availableSize =
            minOf(boxConstraintsScope.maxWidth, boxConstraintsScope.maxHeight) * 0.8f

        // Album art centered in the screen
        AlbumArtSection(
            state = state,
            modifier = Modifier
                .align(Alignment.Center)
                .size(availableSize)
        )

        // Transparent mask with control buttons centered over the album
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(availableSize)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    (availableSize * 0.15f).coerceAtLeast(
                        16.dp
                    )
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous - size based on album size
                val iconButtonSize = (availableSize * 0.12f).coerceIn(32.dp, 48.dp)
                val iconSize = (availableSize * 0.08f).coerceIn(24.dp, 32.dp)

                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    enabled = state.currentIndex > 0,
                    modifier = Modifier.size(iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous song",
                        modifier = Modifier.size(iconSize),
                        tint = Color.White
                    )
                }

                // Play/Pause - larger, based on album size
                val fabSize = (availableSize * 0.2f).coerceIn(64.dp, 96.dp)
                val playIconSize = (availableSize * 0.12f).coerceIn(32.dp, 48.dp)

                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(fabSize),
                    containerColor = Color.White.copy(alpha = 0.9f),
                    contentColor = Color.Black
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(playIconSize)
                    )
                }

                // Next - size based on album size
                IconButton(
                    onClick = { viewModel.skipNext() },
                    enabled = state.currentIndex < state.queue.size - 1,
                    modifier = Modifier.size(iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next song",
                        modifier = Modifier.size(iconSize),
                        tint = Color.White
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
    paddingValues: PaddingValues,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(36.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        // Left side: Album art
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumArtSection(state)
        }

        // Right side: Controls and info
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SongInfoSection(state)
            Spacer(modifier = Modifier.height(32.dp))
            ProgressSection(state, viewModel)
            Spacer(modifier = Modifier.height(32.dp))
            PlaybackControlsSection(state, viewModel, showAllControls = true)
            Spacer(modifier = Modifier.height(24.dp))
            QueueInfoSection(state)
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
private fun SongInfoSection(state: MusicState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = state.currentSong?.title ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.currentSong?.artist ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = state.currentSong?.album ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle (only in full layout)
        if (showAllControls) {
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
        }

        // Previous
        IconButton(
            onClick = { viewModel.skipPrevious() },
            enabled = state.currentIndex > 0
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous song",
                modifier = Modifier.size(32.dp)
            )
        }

        // Play/Pause
        FloatingActionButton(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp)
            )
        }

        // Next
        IconButton(
            onClick = { viewModel.skipNext() },
            enabled = state.currentIndex < state.queue.size - 1
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next song",
                modifier = Modifier.size(32.dp)
            )
        }

        // Repeat (only in full layout)
        if (showAllControls) {
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
