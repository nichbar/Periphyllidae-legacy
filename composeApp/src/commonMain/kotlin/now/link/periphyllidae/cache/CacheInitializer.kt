package now.link.periphyllidae.cache

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import now.link.periphyllidae.utils.LogManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext

/**
 * Helper class to initialize the CacheManager asynchronously during app startup
 */
object CacheInitializer : KoinComponent {
    private const val TAG = "CacheInitializer"
    private var isInitialized = false
    
    /**
     * Initialize the cache manager asynchronously and register it with Koin
     */
    fun initializeAsync() {
        if (isInitialized) {
            LogManager.w(TAG, "Cache already initialized")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LogManager.i(TAG, "Starting cache initialization")
                
                val cacheDirectoryProvider: CacheDirectoryProvider by inject()
                val httpClient: HttpClient by inject()
                
                val cacheDirectory = cacheDirectoryProvider.getCacheDirectory()
                val cacheManager = CacheManager.create(
                    cacheDirectory = cacheDirectory,
                    httpClient = httpClient
                )
                
                // Register the initialized cache manager with Koin
                GlobalContext.get().declare(cacheManager)
                
                isInitialized = true
                LogManager.i(TAG, "Cache initialization completed successfully")
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to initialize cache", e)
                // Create a dummy cache manager that doesn't cache anything
                val dummyCacheManager = createDummyCacheManager()
                GlobalContext.get().declare(dummyCacheManager)
            }
        }
    }
    
    private fun createDummyCacheManager(): CacheManager {
        LogManager.w(TAG, "Creating dummy cache manager (caching disabled)")
        // This would need to be implemented as a pass-through cache
        // For now, we'll let the error propagate so the app knows caching is unavailable
        throw IllegalStateException("Cache initialization failed")
    }
    
    fun isInitialized(): Boolean = isInitialized
}
