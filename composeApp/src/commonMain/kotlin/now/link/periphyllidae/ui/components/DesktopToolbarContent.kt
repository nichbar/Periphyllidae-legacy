package now.link.periphyllidae.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState

@Composable
expect fun WindowScope.DesktopToolbarContent(windowState: WindowState, onCloseRequest: () -> Unit)