package now.link.periphyllidae.cache

import okio.Path

/**
 * Platform-specific cache directory provider
 */
interface CacheDirectoryProvider {
    fun getCacheDirectory(): Path
}
