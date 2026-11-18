package now.link.periphyllidae.di

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
    // CacheDirectoryProvider is bound by the platform module (see PlatformModule.jvm.kt).
}