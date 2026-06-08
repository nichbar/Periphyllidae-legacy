package now.link.periphyllidae.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock

data class TopBarConfig(
    val title: String? = null,
    val navigationIcon: ImageVector? = null,
    val onNavigationClick: (() -> Unit)? = null,
    val actions: @Composable RowScope.() -> Unit = {}
)

private const val NavigationClickDebounceMillis = 450L

private object TopBarNavigationClickGate {
    private var lastClickAtMillis = 0L

    fun runIfAllowed(action: () -> Unit) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastClickAtMillis < NavigationClickDebounceMillis) return

        lastClickAtMillis = now
        action()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    topBarConfig: TopBarConfig? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    if (topBarConfig != null) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        topBarConfig.title?.let { title ->
                            Text(title)
                        }
                    },
                    navigationIcon = {
                        topBarConfig.navigationIcon?.let { icon ->
                            IconButton(
                                onClick = {
                                    topBarConfig.onNavigationClick?.let { onNavigationClick ->
                                        TopBarNavigationClickGate.runIfAllowed(onNavigationClick)
                                    }
                                }
                            ) {
                                Icon(icon, contentDescription = "Back")
                            }
                        }
                    },
                    actions = topBarConfig.actions
                )
            },
            floatingActionButton = floatingActionButton
        ) { paddingValues ->
            content(paddingValues)
        }
    } else {
        // Simple container without top bar
        Scaffold(
            modifier = modifier,
            floatingActionButton = floatingActionButton
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}

@Composable
fun SimpleScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        content()
    }
}

@Composable
fun PaddedScreenContainer(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        content()
    }
}
