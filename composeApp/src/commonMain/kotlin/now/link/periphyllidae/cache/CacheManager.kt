package now.link.periphyllidae.cache

import com.mayakapps.kache.FileKache
import com.mayakapps.kache.JavaFileKache
import com.mayakapps.kache.KacheStrategy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Metadata stored alongside cached content
 */
@Serializable
data class CacheMetadata(
    val contentType: String,
    val contentLength: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Data structure for content and metadata during streaming/caching
 */
data class CachedContent(
    val bytes: ByteArray,
    val metadata: CacheMetadata
)

/**
 * Cache Manager that provides HTTP-based caching proxy functionality using Kache with dual cache architecture.
 * Uses separate content and metadata caches for improved flexibility and modern Kotlin multiplatform support.
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
        private const val CONTENT_CACHE_SIZE_RATIO = 0.95  // 95% for content, 5% for metadata
        private const val METADATA_CACHE_SIZE_RATIO = 0.05  // 5% for metadata
        private const val WARNING_THRESHOLD_PERCENTAGE = 0.90  // Warn at 90% capacity
        private const val CLEANUP_THRESHOLD_PERCENTAGE = 0.95   // Force cleanup at 95% capacity
        
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
    private val activeDownloads = mutableMapOf<String, Channel<CachedContent>>()
    private val json = Json { ignoreUnknownKeys = true }

    // Circuit breaker for cache operations
    private var cacheFailureCount = 0
    private var lastCacheFailureTime = 0L
    private var circuitBreakerOpen = false
    private val circuitBreakerThreshold = 3
    private val circuitBreakerResetTimeMs = 30000L // 30 seconds

    // Dual cache architecture: separate caches for content and metadata
    private lateinit var contentCache: JavaFileKache
    private lateinit var metadataCache: JavaFileKache
    private lateinit var contentCacheDir: File
    private lateinit var metadataCacheDir: File
    private var serverPort: Int = 0
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        LogManager.i(TAG, "Initializing CacheManager with Kache dual cache architecture")
        LogManager.d(TAG, "Cache directory: $cacheDirectory")
        LogManager.d(TAG, "Max cache size: ${maxCacheSize / (1024 * 1024)}MB")

        // Create directory structure for dual caches
        val baseCacheDir = File(cacheDirectory.toString())
        val dirCreated = baseCacheDir.mkdirs()
        LogManager.v(TAG, "Base cache directory creation result: $dirCreated (exists: ${baseCacheDir.exists()})")

        contentCacheDir = File(baseCacheDir, "content")
        metadataCacheDir = File(baseCacheDir, "metadata")
        contentCacheDir.mkdirs()
        metadataCacheDir.mkdirs()

        // Calculate size distribution between caches
        val contentMaxSize = (maxCacheSize * CONTENT_CACHE_SIZE_RATIO).toLong()
        val metadataMaxSize = (maxCacheSize * METADATA_CACHE_SIZE_RATIO).toLong()

        LogManager.d(TAG, "Content cache: ${contentMaxSize / (1024 * 1024)}MB, Metadata cache: ${metadataMaxSize / (1024 * 1024)}MB")

        // Initialize dual caches with Kache
        contentCache = JavaFileKache(
            directory = contentCacheDir,
            maxSize = contentMaxSize
        ) {
            strategy = KacheStrategy.LRU
        }

        metadataCache = JavaFileKache(
            directory = metadataCacheDir,
            maxSize = metadataMaxSize
        ) {
            strategy = KacheStrategy.LRU
        }

        val initTime = System.currentTimeMillis() - startTime
        LogManager.d(TAG, "Kache dual caches initialized in ${initTime}ms")
        LogManager.d(TAG, "Content cache max size: ${contentMaxSize / (1024 * 1024)}MB")
        LogManager.d(TAG, "Metadata cache max size: ${metadataMaxSize / (1024 * 1024)}MB")

        // REQ-NF-03: Start Ktor server on application launch
        startServer()
        val totalInitTime = System.currentTimeMillis() - startTime
        LogManager.i(TAG, "CacheManager initialized successfully on port $serverPort in ${totalInitTime}ms")
    }

    // Helper methods for dual cache key management
    private fun String.toContentKey(): String = "${this}_content"
    private fun String.toMetadataKey(): String = "${this}_metadata"
    
    private fun startServer() {
        val serverStartTime = System.currentTimeMillis()
        LogManager.d(TAG, "Starting cache server...")
        
        // Find an available port
        serverPort = findAvailablePort()
        LogManager.v(TAG, "Found available port: $serverPort")
        
        server = embeddedServer(CIO, port = serverPort, host = SERVER_HOST) {
            routing {
                // REQ-FSV-02: Dynamic endpoint using content identifier
                get("/cache/{key}") {
                    val requestStartTime = System.currentTimeMillis()
                    val key = call.parameters["key"] ?: run {
                        LogManager.w(TAG, "Cache request missing key parameter")
                        call.respond(HttpStatusCode.BadRequest, "Missing key parameter")
                        return@get
                    }
                    
                    LogManager.v(TAG, "Handling cache request for key: $key")
                    
                    try {
                        handleCacheRequest(key, call)
                        val requestTime = System.currentTimeMillis() - requestStartTime
                        LogManager.v(TAG, "Cache request for key $key completed in ${requestTime}ms")
                    } catch (e: Exception) {
                        val requestTime = System.currentTimeMillis() - requestStartTime
                        LogManager.e(TAG, "Error handling cache request for key: $key (after ${requestTime}ms)", e)
                        call.respond(HttpStatusCode.InternalServerError, "Cache error")
                    }
                }
                
                get("/health") {
                    call.respond(HttpStatusCode.OK, "Cache server healthy")
                }
            }
        }
        
        server?.start(wait = false)
        val serverStartupTime = System.currentTimeMillis() - serverStartTime
        LogManager.i(TAG, "Cache server started on $SERVER_HOST:$serverPort in ${serverStartupTime}ms")
    }
    
    private suspend fun handleCacheRequest(key: String, call: ApplicationCall) {
        val handleStartTime = System.currentTimeMillis()
        LogManager.d(TAG, "Processing cache request for key: $key")

        // REQ-FSV-03: Check if fully cached, serve from local file
        withContext(Dispatchers.IO) {
            val cacheCheckTime = System.currentTimeMillis()

            // LRU Tracking: Call cache.get() to update LRU order before serving content
            val contentFile = contentCache.get(key.toContentKey())
            val metadataFile = metadataCache.get(key.toMetadataKey())
            val cacheCheckDuration = System.currentTimeMillis() - cacheCheckTime

            if (contentFile != null && metadataFile != null) {
                LogManager.d(TAG, "Cache HIT for key: $key (lookup took ${cacheCheckDuration}ms)")

                try {
                    // Read content file
                    val contentReadTime = System.currentTimeMillis()
                    val bytes = contentFile.readBytes()
                    val contentReadDuration = System.currentTimeMillis() - contentReadTime
                    LogManager.v(TAG, "Read ${bytes.size} bytes from cache in ${contentReadDuration}ms for key: $key")

                    // Read metadata file
                    val metadata = try {
                        val metadataReadTime = System.currentTimeMillis()
                        val metadataJson = metadataFile.readText()
                        val metadataReadDuration = System.currentTimeMillis() - metadataReadTime
                        val parsedMetadata = json.decodeFromString<CacheMetadata>(metadataJson)
                        LogManager.v(TAG, "Read metadata in ${metadataReadDuration}ms for key: $key")
                        parsedMetadata
                    } catch (e: Exception) {
                        LogManager.w(TAG, "Could not read metadata for key: $key, using defaults", e)
                        // Fallback to default metadata
                        CacheMetadata(contentType = "audio/mpeg")
                    }

                    val responseTime = System.currentTimeMillis()
                    withContext(Dispatchers.Main) {
                        call.response.headers.append("Content-Length", bytes.size.toString())
                        call.response.headers.append("Cache-Control", "public, max-age=31536000")

                        // Use the stored content type
                        val contentType = try {
                            ContentType.parse(metadata.contentType)
                        } catch (e: Exception) {
                            LogManager.w(TAG, "Invalid content type: ${metadata.contentType}, using audio/mpeg")
                            ContentType.Audio.MPEG
                        }

                        call.respondBytes(bytes, contentType)
                    }
                    val responseSetupDuration = System.currentTimeMillis() - responseTime
                    val totalCacheServeTime = System.currentTimeMillis() - handleStartTime

                    LogManager.v(TAG, "Successfully served ${bytes.size} bytes with content-type ${metadata.contentType} from cache for key: $key (total: ${totalCacheServeTime}ms, response setup: ${responseSetupDuration}ms)")
                    return@withContext
                } catch (e: Exception) {
                    LogManager.e(TAG, "Error reading cached files for key: $key", e)
                    // Fall through to streaming
                }
            } else {
                LogManager.d(TAG, "Cache MISS for key: $key (lookup took ${cacheCheckDuration}ms) - content exists: ${contentFile != null}, metadata exists: ${metadataFile != null}")
            }

            // REQ-FSV-04: Stream from remote while caching
            val originalUrl = getOriginalUrlForKey(key)
            if (originalUrl != null) {
                LogManager.d(TAG, "Starting stream and cache for key: $key -> $originalUrl")
                withContext(Dispatchers.Main) {
                    streamAndCache(originalUrl, key, call)
                }
            } else {
                LogManager.w(TAG, "No original URL found for key: $key in URL mappings (${urlMappings.size} total mappings)")
                withContext(Dispatchers.Main) {
                    call.respond(HttpStatusCode.NotFound, "Content not found")
                }
            }
        }
    }
    
    private suspend fun streamAndCache(originalUrl: String, key: String, call: ApplicationCall) {
        val streamStartTime = System.currentTimeMillis()
        LogManager.d(TAG, "Starting streamAndCache for key: $key")
        
        downloadMutex.withLock {
            // REQ-NF-02: Ensure only one download per URL
            var downloadChannel = activeDownloads[key]
            
            if (downloadChannel == null) {
                LogManager.d(TAG, "Starting new download for key: $key (active downloads: ${activeDownloads.size})")
                // Start new download
                downloadChannel = Channel(Channel.UNLIMITED)
                activeDownloads[key] = downloadChannel
                
                scope.launch {
                    try {
                        performDownloadAndCache(originalUrl, key, downloadChannel)
                    } finally {
                        downloadMutex.withLock {
                            activeDownloads.remove(key)
                            LogManager.v(TAG, "Removed download channel for key: $key (remaining: ${activeDownloads.size})")
                        }
                        downloadChannel.close()
                    }
                }
            } else {
                LogManager.d(TAG, "Joining existing download for key: $key")
            }
            
            // Stream content to client
            val streamSetupTime = System.currentTimeMillis() - streamStartTime
            LogManager.v(TAG, "Stream setup completed in ${streamSetupTime}ms for key: $key")
            streamFromDownload(downloadChannel, call, key)
        }
    }
    
    private suspend fun performDownloadAndCache(originalUrl: String, key: String, downloadChannel: Channel<CachedContent>?) {
        val downloadStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "Starting download and cache for: $originalUrl (key: $key)")

        withContext(Dispatchers.IO) {
            // Check circuit breaker state
            if (isCircuitBreakerOpen()) {
                LogManager.w(TAG, "Cache circuit breaker is open - bypassing cache for key: $key")
                streamDirectlyWithoutCaching(originalUrl, downloadChannel)
                return@withContext
            }

            // Check cache capacity before attempting to cache
            checkCacheCapacityAndCleanup()

            try {
                // REQ-FW-01: Use Ktor HTTP client to download
                val httpStartTime = System.currentTimeMillis()
                LogManager.d(TAG, "Starting HTTP request to: $originalUrl")
                val response = httpClient.get(originalUrl)
                val httpResponseTime = System.currentTimeMillis() - httpStartTime

                if (response.status != HttpStatusCode.OK) {
                    LogManager.w(TAG, "HTTP error ${response.status} for URL: $originalUrl (after ${httpResponseTime}ms)")
                    return@withContext
                }

                LogManager.d(TAG, "HTTP response received in ${httpResponseTime}ms for: $originalUrl (status: ${response.status})")

                // Get response as byte array - simple and reliable approach
                val bodyStartTime = System.currentTimeMillis()
                val responseBytes: ByteArray = response.body()
                val bodyReadTime = System.currentTimeMillis() - bodyStartTime
                LogManager.d(TAG, "Downloaded ${responseBytes.size} bytes in ${bodyReadTime}ms from: $originalUrl")

                // Extract content type from response headers
                val contentTypeHeader = response.headers["Content-Type"] ?: "audio/mpeg"
                val contentLength = response.headers["Content-Length"]?.toLongOrNull()
                LogManager.v(TAG, "Response headers - Content-Type: $contentTypeHeader, Content-Length: $contentLength")

                val metadata = CacheMetadata(
                    contentType = contentTypeHeader,
                    contentLength = contentLength
                )

                // Store content in content cache
                val contentWriteStartTime = System.currentTimeMillis()
                val contentFile = contentCache.put(key.toContentKey()) { tempFile ->
                    try {
                        tempFile.writeBytes(responseBytes)
                        true
                    } catch (e: Exception) {
                        LogManager.e(TAG, "Failed to write content to cache", e)
                        false
                    }
                }

                if (contentFile == null) {
                    recordCacheFailure()
                    LogManager.w(TAG, "Failed to store content in cache for key: $key")
                    streamDirectlyWithoutCaching(originalUrl, downloadChannel)
                    return@withContext
                }

                val contentWriteTime = System.currentTimeMillis() - contentWriteStartTime
                LogManager.v(TAG, "Content written to cache in ${contentWriteTime}ms for key: $key")

                // Store metadata in metadata cache
                val metadataWriteStartTime = System.currentTimeMillis()
                val metadataJson = json.encodeToString(metadata)
                val metadataFile = metadataCache.put(key.toMetadataKey()) { tempFile ->
                    try {
                        tempFile.writeText(metadataJson)
                        true
                    } catch (e: Exception) {
                        LogManager.e(TAG, "Failed to write metadata to cache", e)
                        false
                    }
                }

                if (metadataFile == null) {
                    recordCacheFailure()
                    // Rollback content if metadata storage fails
                    contentCache.remove(key.toContentKey())
                    LogManager.w(TAG, "Failed to store metadata in cache for key: $key, rolled back content")
                    streamDirectlyWithoutCaching(originalUrl, downloadChannel)
                    return@withContext
                }

                val metadataWriteTime = System.currentTimeMillis() - metadataWriteStartTime
                LogManager.v(TAG, "Metadata written to cache in ${metadataWriteTime}ms for key: $key")

                // Send to all waiting clients
                val cachedContent = CachedContent(responseBytes, metadata)
                val channelResult = downloadChannel?.trySend(cachedContent)
                LogManager.v(TAG, "Content sent to download channel: ${channelResult?.isSuccess ?: "no channel"}")

                recordCacheSuccess() // Record successful cache operation
                val totalDownloadTime = System.currentTimeMillis() - downloadStartTime
                LogManager.i(TAG, "Successfully cached ${responseBytes.size} bytes with content-type $contentTypeHeader for key: $key (total: ${totalDownloadTime}ms)")

            } catch (e: Exception) {
                val failureTime = System.currentTimeMillis() - downloadStartTime
                LogManager.e(TAG, "Download failed for URL: $originalUrl after ${failureTime}ms", e)
                recordCacheFailure()
                // Cleanup any partially written cache entries
                contentCache.remove(key.toContentKey())
                metadataCache.remove(key.toMetadataKey())
            }
        }
    }

    private suspend fun checkCacheCapacityAndCleanup() = withContext(Dispatchers.IO) {
        try {
            val contentCurrentSize = contentCache.size
            val contentMaxSize = contentCache.maxSize
            val metadataCurrentSize = metadataCache.size
            val metadataMaxSize = metadataCache.maxSize

            val totalCurrentSize = contentCurrentSize + metadataCurrentSize
            val totalMaxSize = contentMaxSize + metadataMaxSize
            val usagePercentage = totalCurrentSize.toDouble() / totalMaxSize

            val totalCurrentSizeMB = totalCurrentSize / (1024 * 1024)
            val totalMaxSizeMB = totalMaxSize / (1024 * 1024)
            val usagePercentageFormatted = String.format("%.1f", usagePercentage * 100)

            LogManager.v(TAG, "Cache capacity: ${totalCurrentSizeMB}MB / ${totalMaxSizeMB}MB (${usagePercentageFormatted}%)")
            LogManager.v(TAG, "Content: ${contentCurrentSize / (1024 * 1024)}MB / ${contentMaxSize / (1024 * 1024)}MB")
            LogManager.v(TAG, "Metadata: ${metadataCurrentSize / (1024 * 1024)}MB / ${metadataMaxSize / (1024 * 1024)}MB")

            when {
                usagePercentage >= CLEANUP_THRESHOLD_PERCENTAGE -> {
                    LogManager.w(TAG, "Cache at ${usagePercentageFormatted}% capacity - triggering proactive cleanup")
                    performProactiveCleanup()
                }

                usagePercentage >= WARNING_THRESHOLD_PERCENTAGE -> {
                    LogManager.w(TAG, "Cache at ${usagePercentageFormatted}% capacity - approaching limit, consider cleanup")
                }

                else -> {
                    LogManager.v(TAG, "Cache usage at ${usagePercentageFormatted}% - OK")
                }
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "Failed to check cache capacity", e)
        }
    }

    private suspend fun performProactiveCleanup() = withContext(Dispatchers.IO) {
        try {
            val cleanupStartTime = System.currentTimeMillis()
            val contentSizeBefore = contentCache.size
            val metadataSizeBefore = metadataCache.size
            val totalSizeBefore = contentSizeBefore + metadataSizeBefore

            LogManager.i(TAG, "Starting proactive cache cleanup (current: ${totalSizeBefore / (1024 * 1024)}MB)")

            // Trim both caches to 80% of their capacity
            val contentTargetSize = (contentCache.maxSize * 0.8).toLong()
            val metadataTargetSize = (metadataCache.maxSize * 0.8).toLong()

            contentCache.trimToSize(contentTargetSize)
            metadataCache.trimToSize(metadataTargetSize)

            // Check if cleanup helped
            val contentSizeAfter = contentCache.size
            val metadataSizeAfter = metadataCache.size
            val totalSizeAfter = contentSizeAfter + metadataSizeAfter
            val cleanupTime = System.currentTimeMillis() - cleanupStartTime

            val spaceFreed = totalSizeBefore - totalSizeAfter
            val contentSpaceFreed = contentSizeBefore - contentSizeAfter
            val metadataSpaceFreed = metadataSizeBefore - metadataSizeAfter

            LogManager.i(TAG, "Proactive cleanup completed in ${cleanupTime}ms")
            LogManager.i(TAG, "Content cache freed: ${contentSpaceFreed / (1024 * 1024)}MB")
            LogManager.i(TAG, "Metadata cache freed: ${metadataSpaceFreed / (1024 * 1024)}MB")
            LogManager.i(TAG, "Total space freed: ${spaceFreed / (1024 * 1024)}MB")
            LogManager.i(TAG, "Cache size after cleanup: ${totalSizeAfter / (1024 * 1024)}MB")

            if (spaceFreed == 0L) {
                LogManager.w(TAG, "Cleanup didn't free significant space - cache may be already below target size")
            }

        } catch (e: Exception) {
            LogManager.e(TAG, "Proactive cache cleanup failed", e)
        }
    }

    private fun isCircuitBreakerOpen(): Boolean {
        val currentTime = System.currentTimeMillis()

        // Check if circuit breaker should be reset
        if (circuitBreakerOpen && (currentTime - lastCacheFailureTime) > circuitBreakerResetTimeMs) {
            LogManager.i(TAG, "Cache circuit breaker resetting after timeout")
            circuitBreakerOpen = false
            cacheFailureCount = 0
            return false
        }

        return circuitBreakerOpen
    }

    private fun recordCacheFailure() {
        cacheFailureCount++
        lastCacheFailureTime = System.currentTimeMillis()

        if (cacheFailureCount >= circuitBreakerThreshold) {
            circuitBreakerOpen = true
            LogManager.w(TAG, "Cache circuit breaker opened after $cacheFailureCount consecutive failures")
        }

        LogManager.v(TAG, "Cache failure recorded: $cacheFailureCount/$circuitBreakerThreshold (breaker open: $circuitBreakerOpen)")
    }

    private fun recordCacheSuccess() {
        // Reset failure count on success
        if (cacheFailureCount > 0) {
            LogManager.d(TAG, "Cache operation succeeded - resetting failure count from $cacheFailureCount to 0")
            cacheFailureCount = 0
        }

        // Close circuit breaker if it was open
        if (circuitBreakerOpen) {
            LogManager.i(TAG, "Cache operation succeeded - closing circuit breaker")
            circuitBreakerOpen = false
        }
    }

    private suspend fun streamDirectlyWithoutCaching(originalUrl: String, downloadChannel: Channel<CachedContent>?) {
        val streamStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "Starting direct streaming (no caching) for: $originalUrl")

        withContext(Dispatchers.IO) {
            try {
                val httpStartTime = System.currentTimeMillis()
                LogManager.d(TAG, "Starting direct HTTP request to: $originalUrl")
                val response = httpClient.get(originalUrl)
                val httpResponseTime = System.currentTimeMillis() - httpStartTime

                if (response.status != HttpStatusCode.OK) {
                    LogManager.w(TAG, "Direct HTTP error ${response.status} for URL: $originalUrl")
                    downloadChannel?.close()
                    return@withContext
                }

                LogManager.d(TAG, "Direct HTTP response received in ${httpResponseTime}ms for: $originalUrl")

                // Get response as byte array
                val bodyStartTime = System.currentTimeMillis()
                val responseBytes: ByteArray = response.body()
                val bodyReadTime = System.currentTimeMillis() - bodyStartTime
                LogManager.d(TAG, "Direct download ${responseBytes.size} bytes in ${bodyReadTime}ms from: $originalUrl")

                // Extract content type from response headers
                val contentTypeHeader = response.headers["Content-Type"] ?: "audio/mpeg"
                val contentLength = response.headers["Content-Length"]?.toLongOrNull()

                val metadata = CacheMetadata(
                    contentType = contentTypeHeader,
                    contentLength = contentLength
                )

                // Send to waiting clients
                val cachedContent = CachedContent(responseBytes, metadata)
                val channelResult = downloadChannel?.trySend(cachedContent)
                LogManager.v(TAG, "Direct content sent to download channel: ${channelResult?.isSuccess ?: "no channel"}")

                val totalStreamTime = System.currentTimeMillis() - streamStartTime
                LogManager.i(TAG, "Direct streaming completed successfully for ${responseBytes.size} bytes in ${totalStreamTime}ms")

            } catch (e: Exception) {
                val failureTime = System.currentTimeMillis() - streamStartTime
                LogManager.e(TAG, "Direct streaming failed for URL: $originalUrl after ${failureTime}ms", e)
                downloadChannel?.close()
            }
        }
    }

    private suspend fun streamFromDownload(downloadChannel: Channel<CachedContent>, call: ApplicationCall, key: String) {
        val streamStartTime = System.currentTimeMillis()
        LogManager.d(TAG, "Waiting for download content to stream for key: $key")
        
        try {
            // Wait for and serve the complete download
            val receiveStartTime = System.currentTimeMillis()
            val cachedContent = downloadChannel.receive()
            val receiveTime = System.currentTimeMillis() - receiveStartTime
            LogManager.d(TAG, "Received ${cachedContent.bytes.size} bytes from download channel in ${receiveTime}ms for key: $key")
            
            val responseStartTime = System.currentTimeMillis()
            call.response.headers.append("Content-Type", cachedContent.metadata.contentType)
            call.response.headers.append("Cache-Control", "public, max-age=31536000")
            call.response.headers.append("Content-Length", cachedContent.bytes.size.toString())
            
            // Use the actual content type from the download
            val contentType = try {
                ContentType.parse(cachedContent.metadata.contentType)
            } catch (e: Exception) {
                LogManager.w(TAG, "Invalid content type: ${cachedContent.metadata.contentType}, using audio/mpeg")
                ContentType.Audio.MPEG
            }
            
            call.respondBytes(cachedContent.bytes, contentType)
            
            val responseTime = System.currentTimeMillis() - responseStartTime
            val totalStreamTime = System.currentTimeMillis() - streamStartTime
            LogManager.d(TAG, "Finished streaming ${cachedContent.bytes.size} bytes with content-type ${cachedContent.metadata.contentType} for key: $key (total: ${totalStreamTime}ms, response: ${responseTime}ms)")
            
        } catch (e: Exception) {
            val errorTime = System.currentTimeMillis() - streamStartTime
            LogManager.e(TAG, "Error streaming content for key: $key after ${errorTime}ms", e)
        }
    }

    fun preload(url: String) {
        val key = url.toCacheKey()
        LogManager.d(TAG, "Preloading URL: $url (key: $key)")
        scope.launch {
            val preloadStartTime = System.currentTimeMillis()
            try {
                performDownloadAndCache(url, key, null)
                val preloadTime = System.currentTimeMillis() - preloadStartTime
                LogManager.i(TAG, "Preload completed for URL: $url in ${preloadTime}ms")
            } catch (e: Exception) {
                val preloadTime = System.currentTimeMillis() - preloadStartTime
                LogManager.e(TAG, "Preload failed for URL: $url after ${preloadTime}ms", e)
            } finally {
                downloadMutex.withLock {
                    activeDownloads.remove(key)
                }
            }
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
        LogManager.d(TAG, "Generated local URL: $localUrl for original: $originalUrl (key: $key)")
        LogManager.v(TAG, "URL mappings now contains ${urlMappings.size} entries")
        return localUrl
    }
    
    // Simple in-memory mapping for reverse lookup
    private val urlMappings = mutableMapOf<String, String>()
    
    private fun storeUrlMapping(key: String, originalUrl: String) {
        urlMappings[key] = originalUrl
        LogManager.v(TAG, "Stored URL mapping: $key -> $originalUrl (total mappings: ${urlMappings.size})")
    }
    
    private fun getOriginalUrlForKey(key: String): String? {
        val url = urlMappings[key]
        if (url == null) {
            LogManager.v(TAG, "No URL mapping found for key: $key")
        }
        return url
    }
    
    /**
     * Check if a URL is already cached
     */
    suspend fun isCached(url: String): Boolean = withContext(Dispatchers.IO) {
        val checkStartTime = System.currentTimeMillis()
        val key = url.toCacheKey()
        val contentFile = contentCache.get(key.toContentKey())
        val metadataFile = metadataCache.get(key.toMetadataKey())
        val cached = contentFile != null && metadataFile != null
        val checkTime = System.currentTimeMillis() - checkStartTime
        LogManager.v(TAG, "Cache check for URL: $url -> $cached (${checkTime}ms)")
        cached
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val statsStartTime = System.currentTimeMillis()
        val stats = CacheStats(
            contentCurrentSize = contentCache.size,
            contentMaxSize = contentCache.maxSize,
            metadataCurrentSize = metadataCache.size,
            metadataMaxSize = metadataCache.maxSize,
            contentEntryCount = try {
                File(contentCacheDir, "files").listFiles()?.size ?: 0
            } catch (e: Exception) { 0 },
            metadataEntryCount = try {
                File(metadataCacheDir, "files").listFiles()?.size ?: 0
            } catch (e: Exception) { 0 }
        )
        val statsTime = System.currentTimeMillis() - statsStartTime
        LogManager.d(TAG, "Cache stats calculated in ${statsTime}ms")
        LogManager.d(TAG, "Content: ${stats.contentCurrentSize / (1024 * 1024)}MB / ${stats.contentMaxSize / (1024 * 1024)}MB, ${stats.contentEntryCount} entries")
        LogManager.d(TAG, "Metadata: ${stats.metadataCurrentSize / (1024 * 1024)}MB / ${stats.metadataMaxSize / (1024 * 1024)}MB, ${stats.metadataEntryCount} entries")
        LogManager.d(TAG, "Total: ${stats.totalCurrentSize / (1024 * 1024)}MB / ${stats.totalMaxSize / (1024 * 1024)}MB, ${stats.totalEntryCount} entries")
        stats
    }
    
    /**
     * Clear all cached content
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val clearStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "Clearing all cache content")
        val contentSizeBefore = contentCache.size
        val metadataSizeBefore = metadataCache.size
        val totalSizeBefore = contentSizeBefore + metadataSizeBefore
        val entriesBefore = urlMappings.size

        contentCache.clear()
        metadataCache.clear()
        urlMappings.clear()

        val clearTime = System.currentTimeMillis() - clearStartTime
        LogManager.i(TAG, "Cache cleared successfully: removed ${totalSizeBefore / (1024 * 1024)}MB and $entriesBefore URL mappings in ${clearTime}ms")
        LogManager.i(TAG, "Content cache cleared: ${contentSizeBefore / (1024 * 1024)}MB")
        LogManager.i(TAG, "Metadata cache cleared: ${metadataSizeBefore / (1024 * 1024)}MB")
    }
    
    /**
     * Remove specific URL from cache
     */
    suspend fun removeCached(url: String): Boolean = withContext(Dispatchers.IO) {
        val removeStartTime = System.currentTimeMillis()
        val key = url.toCacheKey()
        LogManager.d(TAG, "Removing cached content for URL: $url (key: $key)")

        urlMappings.remove(key)
        contentCache.remove(key.toContentKey())
        metadataCache.remove(key.toMetadataKey())
        val removeTime = System.currentTimeMillis() - removeStartTime
        LogManager.d(TAG, "Cache removal for URL: $url completed (${removeTime}ms)")
        true // Consider removal successful since remove() doesn't return a status
    }

    /**
     * Update cache size limit and reinitialize cache with new size
     */
    suspend fun updateCacheSize(newMaxSize: Long): Boolean = withContext(Dispatchers.IO) {
        val updateStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "Updating cache size from ${maxCacheSize / (1024 * 1024)}MB to ${newMaxSize / (1024 * 1024)}MB")

        return@withContext try {
            // Calculate new size distribution
            val newContentMaxSize = (newMaxSize * CONTENT_CACHE_SIZE_RATIO).toLong()
            val newMetadataMaxSize = (newMaxSize * METADATA_CACHE_SIZE_RATIO).toLong()

            val currentContentSize = contentCache.size
            val currentMetadataSize = metadataCache.size

            // Resize both caches
            contentCache.resize(newContentMaxSize)
            metadataCache.resize(newMetadataMaxSize)

            val updateTime = System.currentTimeMillis() - updateStartTime
            LogManager.i(TAG, "Cache size updated successfully in ${updateTime}ms")
            LogManager.i(TAG, "Content cache: ${currentContentSize / (1024 * 1024)}MB -> ${newContentMaxSize / (1024 * 1024)}MB")
            LogManager.i(TAG, "Metadata cache: ${currentMetadataSize / (1024 * 1024)}MB -> ${newMetadataMaxSize / (1024 * 1024)}MB")

            true
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to update cache size", e)
            false
        }
    }

    /**
     * Get current cache directory path
     */
    fun getCacheDirectoryPath(): String = cacheDirectory.toString()

    /**
     * REQ-NF-03: Stop server gracefully on application exit
     */
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        val shutdownStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "Shutting down CacheManager")
        LogManager.d(TAG, "Active downloads at shutdown: ${activeDownloads.size}")
        LogManager.d(TAG, "URL mappings at shutdown: ${urlMappings.size}")

        try {
            val serverStopStartTime = System.currentTimeMillis()
            server?.stop(1000, 2000)
            val serverStopTime = System.currentTimeMillis() - serverStopStartTime
            LogManager.d(TAG, "Cache server stopped in ${serverStopTime}ms")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error stopping cache server", e)
        }

        try {
            val cacheCloseStartTime = System.currentTimeMillis()
            val finalContentSize = contentCache.size
            val finalMetadataSize = metadataCache.size
            val finalTotalSize = finalContentSize + finalMetadataSize

            contentCache.close()
            metadataCache.close()

            val cacheCloseTime = System.currentTimeMillis() - cacheCloseStartTime
            LogManager.d(TAG, "Kache caches closed in ${cacheCloseTime}ms")
            LogManager.d(TAG, "Final content cache size: ${finalContentSize / (1024 * 1024)}MB")
            LogManager.d(TAG, "Final metadata cache size: ${finalMetadataSize / (1024 * 1024)}MB")
            LogManager.d(TAG, "Final total cache size: ${finalTotalSize / (1024 * 1024)}MB")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error closing Kache caches", e)
        }

        scope.cancel()
        val totalShutdownTime = System.currentTimeMillis() - shutdownStartTime
        LogManager.i(TAG, "CacheManager shutdown complete in ${totalShutdownTime}ms")
    }
    
    private fun findAvailablePort(): Int {
        val portSearchStartTime = System.currentTimeMillis()
        return try {
            ServerSocket(0).use { socket ->
                val port = socket.localPort
                val portSearchTime = System.currentTimeMillis() - portSearchStartTime
                LogManager.v(TAG, "Found available port $port in ${portSearchTime}ms")
                port
            }
        } catch (e: Exception) {
            val fallbackPort = Random.nextInt(8080, 65000)
            val portSearchTime = System.currentTimeMillis() - portSearchStartTime
            LogManager.w(TAG, "Could not find available port in ${portSearchTime}ms, using random port $fallbackPort", e)
            fallbackPort
        }
    }
    
    fun getServerPort(): Int = serverPort
    
    fun getServerUrl(): String = "http://$SERVER_HOST:$serverPort"
}

data class CacheStats(
    val contentCurrentSize: Long,
    val contentMaxSize: Long,
    val metadataCurrentSize: Long,
    val metadataMaxSize: Long,
    val totalCurrentSize: Long = contentCurrentSize + metadataCurrentSize,
    val totalMaxSize: Long = contentMaxSize + metadataMaxSize,
    val contentEntryCount: Int = 0,
    val metadataEntryCount: Int = 0,
    val totalEntryCount: Int = contentEntryCount + metadataEntryCount
)

// Utility function to hash a string to create a valid cache key
fun String.toCacheKey(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(this.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) }
}
