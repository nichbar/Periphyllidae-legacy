package now.link.periphyllidae.cache

import okio.Path

/**
 * Platform-specific cache directory provider
 */
expect class CacheDirectoryProvider() {
    fun getCacheDirectory(): Path
}
