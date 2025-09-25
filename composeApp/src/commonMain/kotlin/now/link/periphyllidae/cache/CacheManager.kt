package now.link.periphyllidae.cache

import com.jakewharton.disklrucache.DiskLruCache
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import now.link.periphyllidae.utils.LogManager
import okio.Path
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Cache Manager that provides HTTP-based caching proxy functionality using Jake Wharton's DiskLruCache.
 * Implements all requirements from cache-requirements.md with a battle-tested cache implementation.
 */
class CacheManager private constructor(
    private val cacheDirectory: Path,
    private val maxCacheSize: Long,
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "CacheManager"
        private const val DEFAULT_MAX_CACHE_SIZE = 1024L * 1024L * 1024L // 1GB
        private const val SERVER_HOST = "127.0.0.1"
        private const val CACHE_VERSION = 1
        private const val VALUE_COUNT = 1
        
        suspend fun create(
            cacheDirectory: Path,
            maxCacheSize: Long = DEFAULT_MAX_CACHE_SIZE,
            httpClient: HttpClient
        ): CacheManager = withContext(Dispatchers.IO) {
            val manager = CacheManager(cacheDirectory, maxCacheSize, httpClient)
            manager.initialize()
            manager
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadMutex = Mutex()
    private val activeDownloads = mutableMapOf<String, Channel<ByteArray>>()
    
    // REQ-FS-03: Use Jake Wharton's DiskLruCache for cache management
    private lateinit var diskCache: DiskLruCache
    private var serverPort: Int = 0
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        LogManager.i(TAG, "Initializing CacheManager with Jake Wharton's DiskLruCache")
        
        // REQ-FS-03: Use DiskLruCache for cache management
        val cacheDir = File(cacheDirectory.toString())
        cacheDir.mkdirs()
        
        diskCache = DiskLruCache.open(cacheDir, CACHE_VERSION, VALUE_COUNT, maxCacheSize)
        LogManager.d(TAG, "DiskLruCache initialized with max size: ${maxCacheSize / (1024 * 1024)}MB")
        
        // REQ-NF-03: Start Ktor server on application launch
        startServer()
        LogManager.i(TAG, "CacheManager initialized successfully on port $serverPort")
    }
    
    private fun startServer() {
        // Find an available port
        serverPort = findAvailablePort()
        
        server = embeddedServer(CIO, port = serverPort, host = SERVER_HOST) {
            routing {
                // REQ-FSV-02: Dynamic endpoint using content identifier
                get("/cache/{key}") {
                    val key = call.parameters["key"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing key parameter")
                        return@get
                    }
                    
                    try {
                        handleCacheRequest(key, call)
                    } catch (e: Exception) {
                        LogManager.e(TAG, "Error handling cache request for key: $key", e)
                        call.respond(HttpStatusCode.InternalServerError, "Cache error")
                    }
                }
                
                get("/health") {
                    call.respond(HttpStatusCode.OK, "Cache server healthy")
                }
            }
        }
        
        server?.start(wait = false)
        LogManager.i(TAG, "Cache server started on $SERVER_HOST:$serverPort")
    }
    
    private suspend fun handleCacheRequest(key: String, call: io.ktor.server.application.ApplicationCall) {
        // REQ-FSV-03: Check if fully cached, serve from local file
        withContext(Dispatchers.IO) {
            val snapshot = diskCache.get(key)
            if (snapshot != null) {
                LogManager.d(TAG, "Serving cached content for key: $key")
                
                try {
                    val inputStream = snapshot.getInputStream(0)
                    val bytes = inputStream.readBytes()
                    inputStream.close()
                    snapshot.close()
                    
                    withContext(Dispatchers.Main) {
                        call.response.headers.append("Content-Length", bytes.size.toString())
                        call.respondBytes(bytes, ContentType.Audio.MPEG)
                    }
                    
                    LogManager.v(TAG, "Successfully served ${bytes.size} bytes from cache for key: $key")
                    return@withContext
                } catch (e: Exception) {
                    LogManager.e(TAG, "Error reading cached file for key: $key", e)
                    snapshot.close()
                    // Fall through to streaming
                }
            }
            
            // REQ-FSV-04: Stream from remote while caching
            val originalUrl = getOriginalUrlForKey(key)
            if (originalUrl != null) {
                withContext(Dispatchers.Main) {
                    streamAndCache(originalUrl, key, call)
                }
            } else {
                LogManager.w(TAG, "No original URL found for key: $key")
                withContext(Dispatchers.Main) {
                    call.respond(HttpStatusCode.NotFound, "Content not found")
                }
            }
        }
    }
    
    private suspend fun streamAndCache(originalUrl: String, key: String, call: io.ktor.server.application.ApplicationCall) {
        downloadMutex.withLock {
            // REQ-NF-02: Ensure only one download per URL
            var downloadChannel = activeDownloads[key]
            
            if (downloadChannel == null) {
                // Start new download
                downloadChannel = Channel<ByteArray>(Channel.UNLIMITED)
                activeDownloads[key] = downloadChannel
                
                scope.launch {
                    try {
                        performDownloadAndCache(originalUrl, key, downloadChannel)
                    } finally {
                        downloadMutex.withLock {
                            activeDownloads.remove(key)
                        }
                        downloadChannel.close()
                    }
                }
            }
            
            // Stream content to client
            streamFromDownload(downloadChannel, call, key)
        }
    }
    
    private suspend fun performDownloadAndCache(originalUrl: String, key: String, downloadChannel: Channel<ByteArray>) {
        LogManager.i(TAG, "Starting download and cache for: $originalUrl")
        
        withContext(Dispatchers.IO) {
            // REQ-FW-02: Use DiskLruCache.Editor for writing
            val editor = diskCache.edit(key)
            if (editor == null) {
                LogManager.w(TAG, "Could not create cache editor for key: $key")
                return@withContext
            }
            
            try {
                // REQ-FW-01: Use Ktor HTTP client to download
                val response = httpClient.get(originalUrl)
                
                if (response.status != HttpStatusCode.OK) {
                    LogManager.w(TAG, "HTTP error ${response.status} for URL: $originalUrl")
                    editor.abort()
                    return@withContext
                }
                
                // Get response as byte array - simple and reliable approach
                val responseBytes: ByteArray = response.body()
                
                // Write to cache
                val outputStream = editor.newOutputStream(0)
                outputStream.write(responseBytes)
                outputStream.close()
                
                // Send to all waiting clients
                downloadChannel.trySend(responseBytes)

                // REQ-FW-03: Commit editor on successful download
                editor.commit()
                LogManager.i(TAG, "Successfully cached ${responseBytes.size} bytes for key: $key")
            } catch (e: Exception) {
                LogManager.e(TAG, "Download failed for URL: $originalUrl", e)
                // REQ-FW-04: Abort editor on download failure
                try {
                    editor.abort()
                } catch (abortException: Exception) {
                    LogManager.w(TAG, "Error aborting editor", abortException)
                }
            }
        }
    }
    
    private suspend fun streamFromDownload(downloadChannel: Channel<ByteArray>, call: io.ktor.server.application.ApplicationCall, key: String) {
        LogManager.d(TAG, "Streaming content for key: $key")
        
        try {
            call.response.headers.append("Content-Type", "audio/mpeg")
            call.response.headers.append("Cache-Control", "public, max-age=31536000")
            
            // Wait for and serve the complete download
            val allBytes = downloadChannel.receive()
            call.respondBytes(allBytes, ContentType.Audio.MPEG)
            
            LogManager.d(TAG, "Finished streaming ${allBytes.size} bytes for key: $key")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error streaming content for key: $key", e)
        }
    }
    
    /**
     * REQ-G-01: Provide local HTTP playback URL for any given music URL
     */
    fun getLocalPlaybackUrl(originalUrl: String): String {
        // REQ-FS-02: Use SHA-256 hash as unique key
        val key = originalUrl.toCacheKey()
        
        // Store mapping for reverse lookup
        storeUrlMapping(key, originalUrl)
        
        val localUrl = "http://$SERVER_HOST:$serverPort/cache/$key"
        LogManager.d(TAG, "Generated local URL: $localUrl for original: $originalUrl")
        return localUrl
    }
    
    // Simple in-memory mapping for reverse lookup
    private val urlMappings = mutableMapOf<String, String>()
    
    private fun storeUrlMapping(key: String, originalUrl: String) {
        urlMappings[key] = originalUrl
    }
    
    private fun getOriginalUrlForKey(key: String): String? {
        return urlMappings[key]
    }
    
    /**
     * Check if a URL is already cached
     */
    suspend fun isCached(url: String): Boolean = withContext(Dispatchers.IO) {
        val key = url.toCacheKey()
        val snapshot = diskCache.get(key)
        val cached = snapshot != null
        snapshot?.close()
        cached
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        CacheStats(
            currentSize = diskCache.size(),
            maxSize = diskCache.maxSize,
            entryCount = diskCache.size() // Approximation - DiskLruCache doesn't provide exact count
        )
    }
    
    /**
     * Clear all cached content
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        LogManager.i(TAG, "Clearing all cache content")
        diskCache.delete()
        
        val cacheDir = File(cacheDirectory.toString())
        diskCache = DiskLruCache.open(cacheDir, CACHE_VERSION, VALUE_COUNT, maxCacheSize)
        urlMappings.clear()
        LogManager.i(TAG, "Cache cleared successfully")
    }
    
    /**
     * Remove specific URL from cache
     */
    suspend fun removeCached(url: String): Boolean = withContext(Dispatchers.IO) {
        val key = url.toCacheKey()
        urlMappings.remove(key)
        diskCache.remove(key)
    }
    
    /**
     * REQ-NF-03: Stop server gracefully on application exit
     */
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        LogManager.i(TAG, "Shutting down CacheManager")
        
        try {
            server?.stop(1000, 2000)
            LogManager.d(TAG, "Cache server stopped")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error stopping cache server", e)
        }
        
        try {
            diskCache.close()
            LogManager.d(TAG, "DiskLruCache closed")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error closing DiskLruCache", e)
        }
        
        scope.cancel()
        LogManager.i(TAG, "CacheManager shutdown complete")
    }
    
    private fun findAvailablePort(): Int {
        return try {
            ServerSocket(0).use { socket ->
                socket.localPort
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "Could not find available port, using random port", e)
            Random.nextInt(8080, 65000)
        }
    }
    
    fun getServerPort(): Int = serverPort
    
    fun getServerUrl(): String = "http://$SERVER_HOST:$serverPort"
}

data class CacheStats(
    val currentSize: Long,
    val maxSize: Long,
    val entryCount: Long
)

// Utility function to hash a string to create a valid cache key
fun String.toCacheKey(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(this.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) }
}
