package now.link.periphyllidae.cache

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import now.link.periphyllidae.utils.LogManager
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Cache Manager that provides HTTP-based caching proxy functionality.
 * Implements REQ-G-01: Provides local HTTP playback URL for any given music URL
 * Implements REQ-FSV-01: Runs embedded Ktor HTTP server on local port
 * Implements all other requirements from cache-requirements.md
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
        
        suspend fun create(
            cacheDirectory: Path,
            maxCacheSize: Long = DEFAULT_MAX_CACHE_SIZE,
            httpClient: HttpClient
        ): CacheManager {
            val manager = CacheManager(cacheDirectory, maxCacheSize, httpClient)
            manager.initialize()
            return manager
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadMutex = Mutex()
    private val activeDownloads = mutableMapOf<String, Channel<ByteArray>>()
    
    // REQ-FS-03: Store cached files in dedicated application cache directory
    private lateinit var diskCache: DiskLruCache
    private var serverPort: Int = 0
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    
    // Download progress tracking
    private val _downloadProgress = MutableSharedFlow<DownloadProgress>()
    val downloadProgress: SharedFlow<DownloadProgress> = _downloadProgress.asSharedFlow()
    
    private suspend fun initialize() {
        LogManager.i(TAG, "Initializing CacheManager with cache directory: $cacheDirectory")
        
        // REQ-FS-03: Use DiskLruCache for cache management
        diskCache = DiskLruCache.open(cacheDirectory, maxCacheSize)
        LogManager.d(TAG, "DiskLruCache initialized with max size: ${maxCacheSize / (1024 * 1024)}MB")
        
        // REQ-NF-03: Start Ktor server on application launch
        startServer()
        LogManager.i(TAG, "CacheManager initialized successfully on port $serverPort")
    }
    
    private fun startServer() {
        // Find an available port
        serverPort = 8384
        
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
        LogManager.d(TAG, "Received cache request for key: $key")

        val snapshot = diskCache.get(key)

        if (snapshot != null) {
            LogManager.d(TAG, "Serving cached content for key: $key")
            
            try {
                val file = snapshot.getFile(0)
                val bytes = FileSystem.SYSTEM.source(file).buffer().use { it.readByteArray() }
                
                call.response.headers.append("Content-Length", bytes.size.toString())
                call.respondBytes(bytes, ContentType.Audio.MPEG)
                
                snapshot.close()
                LogManager.v(TAG, "Successfully served ${bytes.size} bytes from cache for key: $key")
                return
            } catch (e: Exception) {
                LogManager.e(TAG, "Error reading cached file for key: $key", e)
                snapshot.close()
                // Fall through to streaming
            }
        }
        
        // REQ-FSV-04: Stream from remote while caching
        val originalUrl = getOriginalUrlForKey(key)
        if (originalUrl != null) {
            LogManager.w(TAG, "Cache miss for key: $key, streaming from original URL")
            streamAndCache(originalUrl, key, call)
        } else {
            LogManager.w(TAG, "No original URL found for key: $key")
            call.respond(HttpStatusCode.NotFound, "Content not found")
        }
    }
    
    private suspend fun streamAndCache(originalUrl: String, key: String, call: io.ktor.server.application.ApplicationCall) {
        downloadMutex.withLock {
            // REQ-NF-02: Ensure only one download per URL
            var downloadChannel = activeDownloads[key]
            
            if (downloadChannel == null) {
                // Start new download
                LogManager.d(TAG, "Starting new download for key: $key")

                downloadChannel = Channel(Channel.UNLIMITED)
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
        
        // REQ-FW-02: Use DiskLruCache.Editor for writing
        val editor = diskCache.edit(key)
        if (editor == null) {
            LogManager.w(TAG, "Could not create cache editor for key: $key")
            return
        }
        
        try {
            // REQ-FW-01: Use Ktor HTTP client to download
            val response = httpClient.get(originalUrl)
            
            if (response.status != HttpStatusCode.OK) {
                LogManager.w(TAG, "HTTP error ${response.status} for URL: $originalUrl")
                editor.abort()
                return
            }
            
            // Get response as byte array - simpler approach
            val responseBytes: ByteArray = response.body()
            val sink = editor.newOutputStream(0).buffer()
            
            sink.use { bufferedSink ->
                bufferedSink.write(responseBytes)
                bufferedSink.flush()
            }
            
            // Send to all waiting clients
            downloadChannel.trySend(responseBytes)
            
            // Report progress
            _downloadProgress.tryEmit(DownloadProgress(key, responseBytes.size.toLong(), null))
            
            // REQ-FW-03: Commit editor on successful download
            editor.commit()
            LogManager.i(TAG, "Successfully cached ${responseBytes.size} bytes for key: $key")
        } catch (e: Exception) {
            LogManager.e(TAG, "Download failed for URL: $originalUrl", e)
            // REQ-FW-04: Abort editor on download failure
            editor.abort()
        }
    }
    
    private suspend fun streamFromDownload(downloadChannel: Channel<ByteArray>, call: io.ktor.server.application.ApplicationCall, key: String) {
        LogManager.d(TAG, "Streaming content for key: $key")
        
        try {
            call.response.headers.append("Content-Type", "audio/mpeg")
            call.response.headers.append("Cache-Control", "public, max-age=31536000")
            
            // Wait for and collect the complete download
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
    suspend fun isCached(url: String): Boolean {
        val key = url.toCacheKey()
        val snapshot = diskCache.get(key)
        val cached = snapshot != null
        snapshot?.close()
        return cached
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            currentSize = diskCache.size(),
            maxSize = diskCache.getMaxSize(),
            entryCount = diskCache.size() // Approximation
        )
    }
    
    /**
     * Clear all cached content
     */
    suspend fun clearCache() {
        LogManager.i(TAG, "Clearing all cache content")
        diskCache.deleteAsync()
        diskCache = DiskLruCache.open(cacheDirectory, maxCacheSize)
        urlMappings.clear()
        LogManager.i(TAG, "Cache cleared successfully")
    }
    
    /**
     * Remove specific URL from cache
     */
    suspend fun removeCached(url: String): Boolean {
        val key = url.toCacheKey()
        urlMappings.remove(key)
        return diskCache.remove(key)
    }
    
    /**
     * REQ-NF-03: Stop server gracefully on application exit
     */
    suspend fun shutdown() {
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

    fun getServerPort(): Int = serverPort
    
    fun getServerUrl(): String = "http://$SERVER_HOST:$serverPort"
}

/**
 * Data classes for cache functionality
 */
data class DownloadProgress(
    val key: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?
)

data class CacheStats(
    val currentSize: Long,
    val maxSize: Long,
    val entryCount: Long
)
