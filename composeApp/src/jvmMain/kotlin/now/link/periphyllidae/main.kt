package now.link.periphyllidae

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin

fun main() {
    // Initialize Koin for Desktop
    startKoin {
        modules(now.link.periphyllidae.di.commonModule, now.link.periphyllidae.di.platformModule)
    }
    
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Periphyllidae - Subsonic Music Player",
        ) {
            App()
        }
    }
}ink.periphyllidae

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Periphyllidae",
    ) {
        App()
    }
}