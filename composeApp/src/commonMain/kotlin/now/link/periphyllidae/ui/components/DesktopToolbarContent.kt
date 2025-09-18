package now.link.periphyllidae.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun DesktopToolbarContent(
    windowScope: Any?,
    windowState: Any?,
    onCloseRequest: () -> Unit
)