package now.link.periphyllidae

import com.arkivanov.decompose.ComponentContext

// Removed: Android-specific defaultComponentContext is now handled in MainActivity
// The ComponentContext is now passed from Activity's defaultComponentContext()
actual fun defaultComponentContext(): ComponentContext {
    throw IllegalStateException("defaultComponentContext() should not be called on Android. Pass ComponentContext from Activity.")
}
