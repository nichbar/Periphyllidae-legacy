package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import now.link.periphyllidae.models.Log
import now.link.periphyllidae.utils.LogManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBackClick: () -> Unit = {}
) {
    val logEntries by LogManager.logEntriesFlow.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            coroutineScope.launch {
                listState.scrollToItem(logEntries.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Scroll to Top") },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    listState.scrollToItem(0)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Scroll to Bottom") },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    if (logEntries.isNotEmpty()) {
                                        listState.scrollToItem(logEntries.size - 1)
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Logs") },
                            onClick = {
                                showMenu = false
                                copyLogsToClipboard(logEntries)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Logs") },
                            onClick = {
                                showMenu = false
                                LogManager.clearLogs()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (logEntries.isEmpty()) {
                // Empty state
                EmptyLogsState()
            } else {
                // Logs list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                    reverseLayout = false
                ) {
                    items(logEntries.size) { index ->
                        LogEntryItem(log = logEntries[index])
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (LogManager.isLogEnabled()) {
                "No logs yet"
            } else {
                "Logging is disabled"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LogEntryItem(log: Log) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with timestamp, level, and tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.getFormattedTime(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Log level badge
                LogLevelBadge(level = log.level)

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Message
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Stack trace if available
            log.throwable?.let { throwable ->
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = throwable.stackTraceToString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun LogLevelBadge(level: Log.LogLevel) {
    val (color, fontWeight) = when (level) {
        Log.LogLevel.VERBOSE -> Color.Gray to FontWeight.Normal
        Log.LogLevel.DEBUG -> Color.Blue to FontWeight.Normal
        Log.LogLevel.INFO -> Color.Green to FontWeight.Bold
        Log.LogLevel.WARN -> Color(0xFFFF9800) to FontWeight.Bold
        Log.LogLevel.ERROR -> Color.Red to FontWeight.Bold
        Log.LogLevel.ASSERT -> Color.Magenta to FontWeight.Bold
    }

    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = level.shortName,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = fontWeight
            ),
            color = color
        )
    }
}

private fun copyLogsToClipboard(logEntries: List<Log>) {
    if (logEntries.isEmpty()) {
        return
    }

    val logText = logEntries.joinToString("\n") { it.getFormattedMessage() }
    
    // TODO: Implement proper clipboard functionality for KMP
    // For now, just log that the user wanted to copy
    LogManager.d("copyLogsToClipboard", "User requested to copy ${logEntries.size} log entries")
}
