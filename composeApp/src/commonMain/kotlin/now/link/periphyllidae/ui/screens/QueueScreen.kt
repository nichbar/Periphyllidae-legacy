package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.mvi.MusicState
import now.link.periphyllidae.ui.components.CoverArtImage
import now.link.periphyllidae.ui.layout.ScreenContainer
import now.link.periphyllidae.ui.layout.TopBarConfig
import now.link.periphyllidae.viewmodels.QueueViewModel

@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenContainer(
        topBarConfig = TopBarConfig(
            title = "Queue",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            onNavigationClick = onNavigateBack,
            actions = {
                IconButton(
                    onClick = viewModel::clearQueue,
                    enabled = state.queue.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear queue"
                    )
                }
            }
        )
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.queue.isEmpty()) {
                EmptyQueueContent()
            } else {
                QueueContent(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun QueueContent(
    state: MusicState,
    viewModel: QueueViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QueueList(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun QueueList(
    state: MusicState,
    viewModel: QueueViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var hasCenteredInitialCurrentItem by remember { mutableStateOf(false) }
    val nowPlayingIndex = state.currentIndex
    val nowPlayingSongId = state.currentSong?.id

    LaunchedEffect(state.queue.size, nowPlayingIndex, nowPlayingSongId) {
        if (
            hasCenteredInitialCurrentItem ||
            nowPlayingIndex !in state.queue.indices ||
            state.queue[nowPlayingIndex].id != nowPlayingSongId
        ) {
            return@LaunchedEffect
        }

        listState.scrollToItem(nowPlayingIndex)
        withFrameNanos { }

        val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == nowPlayingIndex }
        val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        val centeredOffset = currentItem?.let {
            -((viewportHeight - it.size) / 2).coerceAtLeast(0)
        } ?: 0

        listState.scrollToItem(nowPlayingIndex, centeredOffset)
        hasCenteredInitialCurrentItem = true
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.queue) { index, song ->
                QueueSongRow(
                    song = song,
                    isCurrent = index == state.currentIndex,
                    onPlay = { viewModel.playQueueItem(index) },
                    onRemove = { viewModel.removeQueueItem(index) }
                )
            }
        }
    }
}

@Composable
private fun QueueSongRow(
    song: Song,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtImage(
            coverArtUrl = song.coverArt,
            coverArtId = song.coverArtId,
            contentDescription = song.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} - ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        QueueRowActions(
            onRemove = onRemove
        )
    }
}

@Composable
private fun QueueRowActions(
    onRemove: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove from queue",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyQueueContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
