package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.cache.CacheStats
import now.link.periphyllidae.platform.MusicPlayer
import now.link.periphyllidae.store.AppStore
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.utils.ApplicationScopeManager

/**
 * Cache management state representing the current UI state
 */
data class CacheManagementUiState(
    val cacheStats: CacheStats? = null,
    val isLoading: Boolean = false,
    val isClearingCache: Boolean = false,
    val isUpdatingCacheSize: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showClearConfirmation: Boolean = false,
    val cacheSizeMB: Int = 1024 // Default 1GB
)

/**
 * Intent class for cache management actions
 */
sealed class CacheManagementIntent {
    object RefreshCacheStats : CacheManagementIntent()
    object ClearCache : CacheManagementIntent()
    object ConfirmClearCache : CacheManagementIntent()
    object DismissClearConfirmation : CacheManagementIntent()
    object ClearMessages : CacheManagementIntent()
    data class UpdateCacheSize(val sizeMB: Int) : CacheManagementIntent()
}

/**
 * ViewModel for cache management functionality
 * Handles cache statistics, clearing, and size configuration
 */
class CacheManagementViewModel(
    private val musicPlayer: MusicPlayer,
    private val appStore: AppStore
) : ViewModel() {

    companion object {
        private const val TAG = "CacheManagementViewModel"
        private const val MIN_CACHE_SIZE_MB = 100
        private const val MAX_CACHE_SIZE_MB = 5120 // 5GB
    }

    // Use shared application scope to prevent memory leaks
    private val scope = ApplicationScopeManager.scope

    private val _uiState = MutableStateFlow(CacheManagementUiState())
    val uiState: StateFlow<CacheManagementUiState> = _uiState.asStateFlow()

    init {
        LogManager.d(TAG, "Initializing CacheManagementViewModel")
        loadInitialCacheSize()
        refreshCacheStats()
    }

    /**
     * Handle cache management intents
     */
    fun handleIntent(intent: CacheManagementIntent) {
        when (intent) {
            is CacheManagementIntent.RefreshCacheStats -> refreshCacheStats()
            is CacheManagementIntent.ClearCache -> showClearConfirmation()
            is CacheManagementIntent.ConfirmClearCache -> clearCache()
            is CacheManagementIntent.DismissClearConfirmation -> dismissClearConfirmation()
            is CacheManagementIntent.ClearMessages -> clearMessages()
            is CacheManagementIntent.UpdateCacheSize -> updateCacheSize(intent.sizeMB)
        }
    }

    /**
     * Refresh cache statistics from the music player
     */
    private fun refreshCacheStats() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )

                LogManager.d(TAG, "Fetching cache statistics")
                val stats = musicPlayer.getCacheStats()

                _uiState.value = _uiState.value.copy(
                    cacheStats = stats,
                    isLoading = false
                )

                LogManager.d(TAG, "Cache stats updated: $stats")
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to fetch cache statistics", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load cache information: ${e.message}"
                )
            }
        }
    }

    /**
     * Show clear cache confirmation dialog
     */
    private fun showClearConfirmation() {
        _uiState.value = _uiState.value.copy(
            showClearConfirmation = true
        )
    }

    /**
     * Dismiss clear cache confirmation dialog
     */
    private fun dismissClearConfirmation() {
        _uiState.value = _uiState.value.copy(
            showClearConfirmation = false
        )
    }

    /**
     * Clear the entire cache
     */
    private fun clearCache() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isClearingCache = true,
                    showClearConfirmation = false,
                    errorMessage = null,
                    successMessage = null
                )

                LogManager.d(TAG, "Clearing cache")
                musicPlayer.clearCache()

                // Refresh stats after clearing
                val stats = musicPlayer.getCacheStats()

                _uiState.value = _uiState.value.copy(
                    cacheStats = stats,
                    isClearingCache = false,
                    successMessage = "Cache cleared successfully"
                )

                LogManager.d(TAG, "Cache cleared successfully")
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to clear cache", e)
                _uiState.value = _uiState.value.copy(
                    isClearingCache = false,
                    errorMessage = "Failed to clear cache: ${e.message}"
                )
            }
        }
    }

    /**
     * Update cache size configuration
     */
    private fun updateCacheSize(sizeMB: Int) {
        val clampedSize = sizeMB.coerceIn(MIN_CACHE_SIZE_MB, MAX_CACHE_SIZE_MB)

        if (clampedSize != _uiState.value.cacheSizeMB) {
            viewModelScope.launch {
                try {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingCacheSize = true,
                        cacheSizeMB = clampedSize,
                        errorMessage = null
                    )

                    LogManager.d(TAG, "Updating cache size to ${clampedSize}MB")

                    // Update the actual cache size in MusicPlayer
                    val newSizeBytes = clampedSize * 1024L * 1024L
                    val success = musicPlayer.updateCacheSize(newSizeBytes)

                    if (success) {
                        // Save the cache size preference
                        saveCacheSizePreference(clampedSize)

                        // Refresh cache stats after update
                        val stats = musicPlayer.getCacheStats()

                        _uiState.value = _uiState.value.copy(
                            isUpdatingCacheSize = false,
                            cacheStats = stats,
                            successMessage = "Cache size updated to ${formatSize(clampedSize * 1024L * 1024L)}"
                        )

                        LogManager.d(TAG, "Cache size updated successfully")
                    } else {
                        throw Exception("Failed to update cache size in MusicPlayer")
                    }
                } catch (e: Exception) {
                    LogManager.e(TAG, "Failed to update cache size", e)
                    _uiState.value = _uiState.value.copy(
                        isUpdatingCacheSize = false,
                        errorMessage = "Failed to update cache size: ${e.message}",
                        cacheSizeMB = _uiState.value.cacheSizeMB // Revert on error
                    )
                }
            }
        }
    }

    /**
     * Load initial cache size from preferences
     */
    private fun loadInitialCacheSize() {
        viewModelScope.launch {
            try {
                val cacheSize = appStore.state.value.cacheSizeMB
                _uiState.value = _uiState.value.copy(
                    cacheSizeMB = cacheSize
                )
                LogManager.d(TAG, "Loaded cache size preference: ${cacheSize}MB")
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to load cache size preference", e)
                _uiState.value = _uiState.value.copy(
                    cacheSizeMB = 1024 // Fallback to default
                )
            }
        }
    }

    /**
     * Save cache size preference to storage
     */
    private suspend fun saveCacheSizePreference(sizeMB: Int) {
        try {
            appStore.handleIntent(now.link.periphyllidae.mvi.AppIntent.ChangeCacheSize(sizeMB))
            LogManager.d(TAG, "Cache size preference saved: ${sizeMB}MB")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to save cache size preference", e)
            throw e // Re-throw to handle in UI
        }
    }

    /**
     * Clear success and error messages
     */
    private fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * Get cache directory path for display
     */
    fun getCacheDirectoryPath(): String? {
        return musicPlayer.getCacheDirectoryPath()
    }

    /**
     * Format bytes to human-readable format
     */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1fKB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1fMB".format(mb)
        val gb = mb / 1024.0
        return "%.1fGB".format(gb)
    }
}