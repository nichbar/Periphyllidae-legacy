package now.link.periphyllidae.di

import org.koin.dsl.module

/**
 * @deprecated This module has been refactored into smaller, focused modules for better maintainability.
 *
 * This module is kept for backward compatibility but should not be used in new code.
 * Please use the individual modules instead:
 * - networkModule - HTTP client and JSON configuration
 * - dataModule - Repositories and data access layer
 * - cacheModule - Caching system components
 * - storeModule - State management stores
 * - viewModelModule - Screen-specific ViewModels
 * - platformModule - Platform-specific implementations
 *
 * Migration example:
 * OLD: modules(commonModule, platformModule)
 * NEW: modules(networkModule, dataModule, cacheModule, storeModule, viewModelModule, platformModule)
 */
@Deprecated(
    message = "CommonModule has been refactored. Use individual modules instead.",
    replaceWith = ReplaceWith(
        "listOf(networkModule, dataModule, cacheModule, storeModule, viewModelModule)",
        "now.link.periphyllidae.di.networkModule",
        "now.link.periphyllidae.di.dataModule",
        "now.link.periphyllidae.di.cacheModule",
        "now.link.periphyllidae.di.storeModule",
        "now.link.periphyllidae.di.viewModelModule"
    )
)
val commonModule = module {
    // This module combines all the refactored modules for backward compatibility
    includes(networkModule, dataModule, cacheModule, storeModule, viewModelModule)
}

// Platform-specific modules will be defined in platform-specific files
expect val platformModule: org.koin.core.module.Module