package now.link.periphyllidae

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Periphyllidae - Subsonic Music Player",
        state = WindowState(
            width = 450.dp,  // 9:16 aspect ratio (450x800)
            height = 800.dp
        )
    ) {
        App()
    }
}