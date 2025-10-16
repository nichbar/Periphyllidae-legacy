package now.link.periphyllidae.di

import now.link.periphyllidae.cache.CacheDirectoryProvider
import org.koin.dsl.module

/**
 * Cache module providing caching system dependencies.
 *
 * This module handles all cache-related dependencies including:
 * - Cache directory provider for platform-specific storage locations
 * - Cache manager for LRU disk caching with configurable size
 * - Embedded HTTP server components for transparent caching
 *
 * Note: CacheManager is initialized asynchronously through CacheInitializer
 * and declared with Koin at runtime, not defined here.
 */
val cacheModule = module {

    /**
     * Provider for platform-specific cache directories.
     * Returns appropriate cache storage locations based on the target platform.
     */
    factory<CacheDirectoryProvider> {
        CacheDirectoryProvider()
    }
}