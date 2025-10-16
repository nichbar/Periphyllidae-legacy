package now.link.periphyllidae.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

/**
 * Centralized application scope manager for handling coroutine lifecycle.
 *
 * This provides a single application-scoped coroutine that is properly cancelled
 * when the application shuts down, preventing memory leaks from Store classes
 * that would otherwise create their own never-cancelled scopes.
 */
object ApplicationScopeManager {
    private const val TAG = "ApplicationScopeManager"

    /**
     * Application-scoped coroutine scope with SupervisorJob to prevent
     * cascading cancellations while ensuring proper cleanup.
     */
    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Provides the application scope for use throughout the application.
     * All Store classes should use this scope instead of creating their own.
     */
    val scope: CoroutineScope
        get() = applicationScope

    /**
     * Cancels the application scope and all its children.
     * This should be called when the application is shutting down
     * to prevent memory leaks.
     */
    fun shutdown() {
        LogManager.i(TAG, "Shutting down application scope")
        applicationScope.cancel()
        LogManager.i(TAG, "Application scope cancelled")
    }

    /**
     * Checks if the application scope is still active.
     */
    val isActive: Boolean
        get() = applicationScope.isActive
}