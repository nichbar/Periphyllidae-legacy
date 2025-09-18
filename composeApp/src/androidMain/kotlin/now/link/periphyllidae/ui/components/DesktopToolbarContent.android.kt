package now.link.periphyllidae.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun DesktopToolbarContent(
    windowScope: Any?,
    windowState: Any?,
    onCloseRequest: () -> Unit,
) {
    // Desktop toolbar is not needed on Android - this is a no-op
}
