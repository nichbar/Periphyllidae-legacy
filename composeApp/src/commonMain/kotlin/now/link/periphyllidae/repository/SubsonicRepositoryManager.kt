package now.link.periphyllidae.repository

import now.link.periphyllidae.models.ServerConfig
import now.link.periphyllidae.utils.LogManager

/**
 * Manager for server-specific SubsonicRepository instances
 * Handles creation, caching, and lifecycle of repository instances
 * Thread-safe and provides the current active repository
 */
class SubsonicRepositoryManager(
    private val repositoryFactory: SubsonicRepositoryFactory
) {
    companion object {
        private const val TAG = "SubsonicRepositoryManager"
    }
    
    // Cache of repository instances by server key
    private val repositoryCache = mutableMapOf<String, SubsonicRepository>()
    
    // Currently active repository
    @Volatile
    private var currentRepository: SubsonicRepository? = null
    
    /**
     * Gets or creates a repository for the specified server config
     * Thread-safe and caches instances for reuse
     */
    fun getRepository(serverConfig: ServerConfig): SubsonicRepository {
        val serverKey = generateServerKey(serverConfig)
        
        return synchronized(repositoryCache) {
            repositoryCache.getOrPut(serverKey) {
                LogManager.i(TAG, "Creating new repository instance for ${serverConfig.url}")
                repositoryFactory.createRepository(serverConfig)
            }
        }
    }
    
    /**
     * Sets the active repository for the given server config
     * This is used by stores to get the current repository instance
     */
    fun setActiveRepository(serverConfig: ServerConfig) {
        currentRepository = getRepository(serverConfig)
        LogManager.i(TAG, "Active repository set for ${serverConfig.url}")
    }
    
    /**
     * Gets the currently active repository
     * Throws IllegalStateException if no repository is active
     */
    fun getCurrentRepository(): SubsonicRepository {
        return currentRepository ?: throw IllegalStateException("No active repository - user not authenticated")
    }
    
    /**
     * Checks if there's an active repository
     */
    fun hasActiveRepository(): Boolean {
        return currentRepository != null
    }
    
    /**
     * Clears all cached repositories (e.g., on logout)
     */
    fun clearCache() {
        synchronized(repositoryCache) {
            repositoryCache.clear()
            currentRepository = null
            LogManager.i(TAG, "Repository cache cleared")
        }
    }
    
    /**
     * Removes a specific repository from cache (e.g., when server is deleted)
     */
    fun removeRepository(serverConfig: ServerConfig) {
        val serverKey = generateServerKey(serverConfig)
        synchronized(repositoryCache) {
            repositoryCache.remove(serverKey)
            // If this was the active repository, clear it
            if (currentRepository == repositoryCache[serverKey]) {
                currentRepository = null
            }
            LogManager.i(TAG, "Removed repository for ${serverConfig.url}")
        }
    }
    
    private fun generateServerKey(serverConfig: ServerConfig): String {
        // Use URL + username as the key since same server can have different users
        return "${serverConfig.url}:${serverConfig.username}"
    }
}
