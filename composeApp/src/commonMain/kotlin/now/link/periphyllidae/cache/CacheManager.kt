package now.link.periphyllidae.cache

import com.mayakapps.kache.JavaFileKache
import com.mayakapps.kache.KacheStrategy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import now.link.periphyllidae.utils.LogManager
import okio.Path
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import io.ktor.utils.io.*

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
        private const val CHUNK_SIZE = 256 * 1024 // 64KB chunks for streaming
        private const val STREAM_NO_PROGRESS_TIMEOUT_MS = 15_000L
        private const val STREAM_NO_PROGRESS_MAX_ITERATIONS = 180

        /**
         * Clean corrupted or invalid files from cache directories that could cause
         * Kache to throw NullPointerException during initialization.
         */
        private fun cleanCorruptedCacheFiles(cacheDir: File) {
            try {
                val filesDir = File(cacheDir, "files")
                if (!filesDir.exists()) return

                val files = filesDir.listFiles()
                if (files == null || files.isEmpty()) return

                var removedCount = 0
                for (file in files) {
                    // Remove names ( files with unusualnull bytes, unusual chars, hidden files)
                    // or files that are incomplete/corrupted
                    val fileName = file.name
                    if (fileName.isNullOrEmpty() ||
                        fileName.startsWith(".") ||
                        fileName.contains("\u0000") ||
                        !file.canRead() ||
                        (file.isFile && file.length() == 0L) ||
                        (file.isDirectory && file.listFiles()?.isEmpty() == true)
                    ) {
                        try {
                            if (file.isDirectory) {
                                file.deleteRecursively()
                            } else {
                                file.delete()
                            }
                            removedCount++
                        } catch (e: Exception) {
                            LogManager.w(TAG, "Failed to remove corrupted file: ${file.absolutePath}", e)
                        }
                    }
                }

                if (removedCount > 0) {
                    LogManager.w(TAG, "Cleaned $removedCount corrupted files from ${cacheDir.absolutePath}")
                }
            } catch (e: Exception) {
                LogManager.w(TAG, "Failed to clean cache directory: ${cacheDir.absolutePath}", e)
            }
        }

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
    private val activeDownloadStates = mutableMapOf<String, DownloadState>()
    private val activeDownloadJobs = mutableMapOf<String, Job>()

    // Download state for progressive caching
    private class DownloadState(
        val metadata: MutableStateFlow<CacheMetadata?> = MutableStateFlow(null),
        val downloadedBytes: MutableStateFlow<Long> = MutableStateFlow(0L),
        val isComplete: MutableStateFlow<Boolean> = MutableStateFlow(false),
        val error: MutableStateFlow<Throwable?> = MutableStateFlow(null),
        val isFinalizing: MutableStateFlow<Boolean> = MutableStateFlow(false), // CRITICAL FIX #2: Track when finalization is in progress
        var tempFile: File? = null
    )
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

        // Clean any corrupted files from cache directories to prevent Kache NPE
        cleanCorruptedCacheFiles(contentCacheDir)
        cleanCorruptedCacheFiles(metadataCacheDir)

        // Calculate size distribution between caches (must be done before cache initialization)
        val contentMaxSize = (maxCacheSize * CONTENT_CACHE_SIZE_RATIO).toLong()
        val metadataMaxSize = (maxCacheSize * METADATA_CACHE_SIZE_RATIO).toLong()

        LogManager.d(TAG, "Content cache: ${contentMaxSize / (1024 * 1024)}MB, Metadata cache: ${metadataMaxSize / (1024 * 1024)}MB")

        // Try to initialize caches, fall back to clearing and retrying if corrupted
        var cacheInitSuccess = false
        var retryCount = 0
        val maxRetries = 2

        while (!cacheInitSuccess && retryCount < maxRetries) {
            try {
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
                cacheInitSuccess = true
            } catch (e: Exception) {
                retryCount++
                if (retryCount >= maxRetries) {
                    // Last resort: clear entire cache directories
                    LogManager.e(TAG, "Failed to initialize Kache caches after $retryCount attempts, clearing cache directories", e)
                    try {
                        contentCacheDir.deleteRecursively()
                        metadataCacheDir.deleteRecursively()
                        contentCacheDir.mkdirs()
                        metadataCacheDir.mkdirs()
                        LogManager.i(TAG, "Cache directories cleared, retrying initialization...")
                    } catch (clearEx: Exception) {
                        LogManager.e(TAG, "Failed to clear cache directories", clearEx)
                    }

                    // Try one more time after clearing
                    try {
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
                        cacheInitSuccess = true
                    } catch (finalEx: Exception) {
                        LogManager.e(TAG, "Final cache initialization attempt failed", finalEx)
                        throw finalEx
                    }
                } else {
                    LogManager.w(TAG, "Cache initialization attempt $retryCount failed, cleaning and retrying", e)
                    cleanCorruptedCacheFiles(contentCacheDir)
                    cleanCorruptedCacheFiles(metadataCacheDir)
                }
            }
        }

        val initTime = System.currentTimeMillis() - startTime
        LogManager.d(TAG, "Kache dual caches initialized in ${initTime}ms")
        LogManager.d(TAG, "Content cache max size: ${contentMaxSize / (1024 * 1024)}MB")
        LogManager.d(TAG, "Metadata cache max size: ${metadataMaxSize / (1024 * 1024)}MB")

        // REQ-NF-03: Start Ktor server on application launch
        startServer()

        // CRITICAL FIX #1: Start periodic cleanup of stale urlMappings
        // This prevents unbounded memory leak when Kache's LRU evicts entries
        startPeriodicUrlMappingsCleanup()

        val totalInitTime = System.currentTimeMillis() - startTime
        LogManager.i(TAG, "CacheManager initialized successfully on port $serverPort in ${totalInitTime}ms")
    }

    // Helper methods for dual cache key management
    private fun String.toContentKey(): String = "${this}_content"
    private fun String.toMetadataKey(): String = "${this}_metadata"

    /**
     * CRITICAL FIX #1: Periodic cleanup of stale urlMappings entries
     * Runs every 5 minutes to remove entries whose cache has been evicted by Kache's LRU
     */
    private fun startPeriodicUrlMappingsCleanup() {
        scope.launch {
            while (isActive) {
                try {
                    delay(5 * 60 * 1000L) // 5 minutes
                    cleanupStaleUrlMappings()
                } catch (e: Exception) {
                    LogManager.w(TAG, "Error in periodic urlMappings cleanup", e)
                }
            }
        }
    }

    private suspend fun cleanupStaleUrlMappings() = withContext(Dispatchers.IO) {
        val cleanupStartTime = System.currentTimeMillis()
        val sizeBefore = urlMappings.size
        var removedCount = 0

        // Collect stale entries
        val staleKeys = mutableSetOf<String>()
        for ((key, _) in urlMappings) {
            val contentFile = contentCache.get(key.toContentKey())
            val metadataFile = contentCache.get(key.toMetadataKey())
            if (contentFile == null || metadataFile == null) {
                staleKeys.add(key)
            }
        }

        // Remove stale entries
        for (key in staleKeys) {
            urlMappings.remove(key)
            removedCount++
        }

        val cleanupTime = System.currentTimeMillis() - cleanupStartTime
        if (removedCount > 0) {
            LogManager.i(TAG, "Cleaned up $removedCount stale urlMappings entries (from $sizeBefore entries) in ${cleanupTime}ms")
        } else {
            LogManager.v(TAG, "urlMappings cleanup: no stale entries found (${urlMappings.size} entries)")
        }
    }

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
                    LogManager.v(TAG, "Request Headers: ${call.request.headers.entries().joinToString { "${it.key}=${it.value}" }}")

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
        LogManager.i(TAG, "⏱️ REQUEST START - Processing cache request for key: $key at ${handleStartTime}ms")

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
                    // Read metadata
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

                    // Check for Range header
                    val rangeHeader = call.request.headers["Range"]

                    if (rangeHeader != null && metadata.contentLength != null) {
                        // Serve partial content (HTTP 206)
                        serveRangeRequest(call, contentFile, metadata, rangeHeader)
                    } else {
                        // Serve complete file
                        val responseTime = System.currentTimeMillis()
                        withContext(Dispatchers.IO) {
                            call.response.headers.append("Accept-Ranges", "bytes")
                            call.response.headers.append("Cache-Control", "public, max-age=31536000")

                            LogManager.v(TAG, "Response (Cache HIT): 200 OK")
                            LogManager.v(TAG, "Response Headers: ${call.response.headers.allValues().entries().joinToString { "${it.key}=${it.value}" }}")
                            call.respondFile(contentFile)
                        }
                        val responseSetupDuration = System.currentTimeMillis() - responseTime
                        val totalCacheServeTime = System.currentTimeMillis() - handleStartTime

                        LogManager.v(TAG, "Successfully served from cache for key: $key (total: ${totalCacheServeTime}ms, response setup: ${responseSetupDuration}ms)")
                    }
                    return@withContext
                } catch (e: Exception) {
                    LogManager.e(TAG, "Error reading cached files for key: $key", e)
                    // Fall through to streaming
                }
            } else {
                // Check if a download is already in progress
                if (activeDownloadStates.containsKey(key)) {
                    LogManager.d(TAG, "Cache MISS but download in progress for key: $key (lookup took ${cacheCheckDuration}ms) - joining existing download")
                } else {
                    LogManager.d(TAG, "Cache MISS for key: $key (lookup took ${cacheCheckDuration}ms) - content exists: ${contentFile != null}, metadata exists: ${metadataFile != null}")
                }
            }

            // REQ-FSV-04: Stream from remote while caching
            val originalUrl = getOriginalUrlForKey(key)
            if (originalUrl != null) {
                LogManager.d(TAG, "Starting stream and cache for key: $key -> $originalUrl")

                // Parse range header if present
                val rangeHeader = call.request.headers["Range"]
                streamAndCache(originalUrl, key, call, rangeHeader)
            } else {
                LogManager.w(TAG, "No original URL found for key: $key in URL mappings (${urlMappings.size} total mappings)")
                call.respond(HttpStatusCode.NotFound, "Content not found")
            }
        }
    }
    
    private suspend fun streamAndCache(originalUrl: String, key: String, call: ApplicationCall, rangeHeader: String? = null) {
        val streamStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "=== streamAndCache ENTERED for key: $key, url: $originalUrl, range: $rangeHeader ===")

        val downloadState = downloadMutex.withLock {
            // REQ-NF-02: Ensure only one download per URL
            var state = activeDownloadStates[key]

            if (state == null) {
                LogManager.i(TAG, "Starting NEW download for key: $key (active downloads: ${activeDownloadStates.size})")
                // Start new download
                state = DownloadState()
                activeDownloadStates[key] = state
                LogManager.d(TAG, "Created new DownloadState for key: $key")

                val downloadJob = scope.launch {
                    try {
                        LogManager.i(TAG, "Launching performDownloadAndCache in background coroutine for key: $key")
                        performDownloadAndCache(originalUrl, key, state)
                        LogManager.i(TAG, "performDownloadAndCache COMPLETED for key: $key")
                    } finally {
                        downloadMutex.withLock {
                            // Only remove if it's the same state we created
                            if (activeDownloadStates[key] === state) {
                                activeDownloadStates.remove(key)
                                activeDownloadJobs.remove(key)
                            }
                        }
                        LogManager.d(TAG, "Download job finished for key: $key")
                    }
                }
                activeDownloadJobs[key] = downloadJob
            } else {
                LogManager.d(TAG, "Joining existing download for key: $key")
            }

            state
        }

        // Stream content to client
        streamFromDownload(downloadState, call, key, rangeHeader)
        LogManager.i(TAG, "=== streamAndCache EXITING for key: $key ===")
    }

    private suspend fun serveRangeRequest(
        call: ApplicationCall,
        contentFile: File,
        metadata: CacheMetadata,
        rangeHeader: String
    ) = withContext(Dispatchers.IO) {
        LogManager.d(TAG, "Serving range request: $rangeHeader")

        // Parse range header: "bytes=start-end"
        val rangeRegex = Regex("bytes=(\\d+)-(\\d*)")
        val matchResult = rangeRegex.find(rangeHeader)

        if (matchResult == null) {
            LogManager.w(TAG, "Invalid range header: $rangeHeader")
            val bytes = contentFile.readBytes()
            withContext(Dispatchers.Main) {
                call.respondBytes(bytes, ContentType.parse(metadata.contentType))
            }
            return@withContext
        }

        val start = matchResult.groupValues[1].toLongOrNull() ?: 0L
        val totalLength = metadata.contentLength!!
        val end = matchResult.groupValues[2].toLongOrNull() ?: (totalLength - 1)
        val rangeLength = (end - start + 1).toInt()

        LogManager.d(TAG, "Range: bytes $start-$end/$totalLength (${rangeLength} bytes)")

        // Read the requested range
        val rangeBytes = contentFile.inputStream().use { input ->
            input.skip(start)
            val buffer = ByteArray(rangeLength)
            val bytesRead = input.read(buffer, 0, rangeLength)
            buffer.copyOf(bytesRead)
        }

        // Set HTTP 206 Partial Content headers
        withContext(Dispatchers.Main) {
            call.response.status(HttpStatusCode.PartialContent)
            call.response.headers.append("Content-Range", "bytes $start-$end/$totalLength")
            call.response.headers.append("Content-Length", rangeBytes.size.toString())
            call.response.headers.append("Accept-Ranges", "bytes")
            call.response.headers.append("Cache-Control", "public, max-age=31536000")

            LogManager.v(TAG, "Response (Range): 206 Partial Content")
            LogManager.v(TAG, "Response Headers: ${call.response.headers.allValues().entries().joinToString { "${it.key}=${it.value}" }}")

            val contentType = try {
                ContentType.parse(metadata.contentType)
            } catch (e: Exception) {
                LogManager.w(TAG, "Invalid content type: ${metadata.contentType}, using audio/mpeg")
                ContentType.Audio.MPEG
            }

            call.respondBytes(rangeBytes, contentType)
        }
        LogManager.d(TAG, "Served ${rangeBytes.size} bytes for range request")
    }

    private suspend fun performDownloadAndCache(originalUrl: String, key: String, downloadState: DownloadState) {
        val downloadStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "Starting streaming download for: $originalUrl (key: $key)")

        withContext(Dispatchers.IO) {
            // Check circuit breaker
            if (isCircuitBreakerOpen()) {
                LogManager.w(TAG, "Cache circuit breaker is open - bypassing cache for key: $key")
                streamDirectlyWithoutCaching(originalUrl, downloadState)
                return@withContext
            }

            // Check cache capacity before attempting to cache
            checkCacheCapacityAndCleanup()

            var tempFile: File? = null

            try {
                // OPTIMIZATION: Use prepareGet for true streaming (headers immediately available)
                // This allows us to start serving content to the client as soon as headers arrive
                // without waiting for the full body to download.
                val httpStartTime = System.currentTimeMillis()
                LogManager.d(TAG, "Starting streaming request to: $originalUrl")

                httpClient.prepareGet(originalUrl).execute { response ->
                    val httpResponseTime = System.currentTimeMillis() - httpStartTime

                    if (response.status != HttpStatusCode.OK) {
                        LogManager.w(TAG, "HTTP error ${response.status} for URL: $originalUrl (after ${httpResponseTime}ms)")
                        downloadState.error.value = Exception("HTTP ${response.status}")
                        return@execute
                    }

                    LogManager.d(TAG, "HTTP headers received in ${httpResponseTime}ms for: $originalUrl")

                    // Extract metadata immediately from headers
                    val contentTypeHeader = response.headers["Content-Type"] ?: "audio/mpeg"
                    val contentLength = response.headers["Content-Length"]?.toLongOrNull()
                    LogManager.i(TAG, "📋 Received metadata from headers: Content-Type: $contentTypeHeader, Length: $contentLength")

                    val metadata = CacheMetadata(contentType = contentTypeHeader, contentLength = contentLength)

                    // Store metadata IMMEDIATELY! This unblocks readers waiting on downloadState.metadata
                    downloadState.metadata.value = metadata
                    LogManager.i(TAG, "✅ Metadata stored from GET headers")

                    // Create temp file for caching
                    tempFile = File.createTempFile("cache_${key}_", ".tmp", contentCacheDir)
                    downloadState.tempFile = tempFile
                    LogManager.v(TAG, "Created temp file: ${tempFile!!.absolutePath}")

                    // Get streaming channel directly from the response
                    val channel: ByteReadChannel = response.body()

                    var totalBytesRead = 0L
                    val buffer = ByteArray(CHUNK_SIZE)
                    var firstChunkTime = 0L
                    var chunkCount = 0

                    tempFile.outputStream().use { outputStream ->
                        // Stream chunks: read from server, update state, write to cache
                        while (!channel.isClosedForRead) {
                            val chunkStartTime = System.currentTimeMillis()

                            // Read chunk from server
                            val bytesRead = channel.readAvailable(buffer, 0, CHUNK_SIZE)
                            if (bytesRead == -1) break

                            totalBytesRead += bytesRead
                            chunkCount++

                            // Write to cache file
                            try {
                                outputStream.write(buffer, 0, bytesRead)
                                outputStream.flush() // Ensure data is visible to readers

                                // Update downloaded bytes to notify readers
                                downloadState.downloadedBytes.value = totalBytesRead

                                if (firstChunkTime == 0L) {
                                    firstChunkTime = System.currentTimeMillis()
                                    val timeToFirstByte = firstChunkTime - httpStartTime
                                    LogManager.i(TAG, "⚡ FIRST CHUNK received in ${timeToFirstByte}ms (${bytesRead} bytes)")
                                }
                            } catch (e: Exception) {
                                LogManager.e(TAG, "Failed to write chunk to cache", e)
                                recordCacheFailure()
                                // We continue even if caching fails, though this might be problematic for readers
                                // In a real Tailing File Reader, if writing fails, we might need to notify error
                                downloadState.error.value = e
                                break
                            }

                            val chunkReadTime = System.currentTimeMillis() - chunkStartTime
                            if (chunkCount % 50 == 0) {  // Log less frequently
                                LogManager.v(TAG, "Downloaded chunk #$chunkCount: $bytesRead bytes in ${chunkReadTime}ms (total: $totalBytesRead)")
                            }
                        }
                    }

                    // Download complete
                    downloadState.isComplete.value = true

                    val totalDownloadTime = System.currentTimeMillis() - downloadStartTime
                    val downloadSpeedMBps = if (totalDownloadTime > 0) {
                        (totalBytesRead / 1024.0 / 1024.0) / (totalDownloadTime / 1000.0)
                    } else 0.0

                    LogManager.i(TAG, "📥 DOWNLOAD COMPLETE: $totalBytesRead bytes in ${totalDownloadTime}ms (${String.format("%.2f", downloadSpeedMBps)} MB/s, $chunkCount chunks)")

                    // CRITICAL FIX #2: Signal that finalization is starting
                    // This prevents streaming clients from trying to access the temp file while it's being moved
                    downloadState.isFinalizing.value = true
                    LogManager.v(TAG, "Starting cache finalization for key: $key")

                    // Finalize cache
                    finalizeCacheFiles(key, tempFile!!, metadata)
                    recordCacheSuccess()
                    LogManager.i(TAG, "Successfully cached $totalBytesRead bytes for key: $key")

                    // CRITICAL FIX #2: Clear finalizing flag to allow clients to switch to cached file
                    downloadState.isFinalizing.value = false
                    LogManager.v(TAG, "Cache finalization complete for key: $key")
                }

            } catch (e: Exception) {
                val failureTime = System.currentTimeMillis() - downloadStartTime
                LogManager.e(TAG, "Download failed for URL: $originalUrl after ${failureTime}ms", e)
                recordCacheFailure()
                downloadState.error.value = e

                // CRITICAL FIX #2: Clear finalizing flag on error
                downloadState.isFinalizing.value = false

                // Clean up
                tempFile?.delete()
                contentCache.remove(key.toContentKey())
                metadataCache.remove(key.toMetadataKey())
            }
        }
    }

    private suspend fun finalizeCacheFiles(
        key: String,
        tempFile: File,
        metadata: CacheMetadata
    ) = withContext(Dispatchers.IO) {
        try {
            // Move temp file to cache
            val cacheWriteStartTime = System.currentTimeMillis()

            val contentFile = contentCache.put(key.toContentKey()) { targetFile ->
                try {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    true
                } catch (e: Exception) {
                    LogManager.e(TAG, "Failed to move temp file to cache", e)
                    false
                }
            }

            if (contentFile == null) {
                recordCacheFailure()
                LogManager.w(TAG, "Failed to store content in cache for key: $key")
                tempFile.delete()
                return@withContext
            }

            val cacheWriteTime = System.currentTimeMillis() - cacheWriteStartTime
            LogManager.v(TAG, "Content finalized in cache in ${cacheWriteTime}ms for key: $key")

            // Store metadata
            val metadataJson = json.encodeToString(metadata)
            val metadataFile = metadataCache.put(key.toMetadataKey()) { targetFile ->
                try {
                    targetFile.writeText(metadataJson)
                    true
                } catch (e: Exception) {
                    LogManager.e(TAG, "Failed to write metadata to cache", e)
                    false
                }
            }

            if (metadataFile == null) {
                recordCacheFailure()
                contentCache.remove(key.toContentKey())
                LogManager.w(TAG, "Failed to store metadata, rolled back content for key: $key")
                return@withContext
            }

            LogManager.d(TAG, "Cache finalization complete for key: $key")

        } catch (e: Exception) {
            LogManager.e(TAG, "Error finalizing cache files", e)
            tempFile.delete()
            throw e
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
            LogManager.w(TAG, "Cache circuit breaker opened after $cacheFailureCount consecutive failures, cancelling all downloads")

            // Cancel all active downloads when circuit breaker opens
            scope.launch {
                try {
                    cancelAllDownloads()
                } catch (e: Exception) {
                    LogManager.w(TAG, "Error cancelling downloads when circuit breaker opened", e)
                }
            }
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

    private suspend fun streamDirectlyWithoutCaching(originalUrl: String, downloadState: DownloadState) {
        val streamStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "Starting direct streaming (no caching) for: $originalUrl")

        withContext(Dispatchers.IO) {
            try {
                // OPTIMIZATION: Use prepareGet for true streaming (headers immediately available)
                val httpStartTime = System.currentTimeMillis()
                LogManager.d(TAG, "Starting parallel HEAD and GET requests for direct streaming: $originalUrl")

                httpClient.prepareGet(originalUrl).execute { response ->
                    val httpResponseTime = System.currentTimeMillis() - httpStartTime

                    if (response.status != HttpStatusCode.OK) {
                        LogManager.w(TAG, "Direct HTTP error ${response.status} for URL: $originalUrl")
                        downloadState.error.value = Exception("HTTP ${response.status}")
                        return@execute
                    }

                    LogManager.d(TAG, "Direct HTTP headers received in ${httpResponseTime}ms for: $originalUrl")

                    // Extract metadata immediately from headers
                    val contentTypeHeader = response.headers["Content-Type"] ?: "audio/mpeg"
                    val contentLength = response.headers["Content-Length"]?.toLongOrNull()
                    val metadata = CacheMetadata(contentType = contentTypeHeader, contentLength = contentLength)

                    // Store metadata immediately
                    downloadState.metadata.value = metadata
                    LogManager.i(TAG, "✅ Direct metadata stored from HEADers in ${httpResponseTime}ms")

                    // Create temp file anyway for direct streaming to support multiple clients
                    val tempFile = File.createTempFile("direct_${System.currentTimeMillis()}_", ".tmp", contentCacheDir)
                    downloadState.tempFile = tempFile

                    // Stream using ByteReadChannel
                    val channel: ByteReadChannel = response.body()
                    val buffer = ByteArray(CHUNK_SIZE)
                    var totalBytesRead = 0L
                    var chunkCount = 0

                    tempFile.outputStream().use { outputStream ->
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer, 0, CHUNK_SIZE)
                            if (bytesRead == -1) break

                            outputStream.write(buffer, 0, bytesRead)
                            outputStream.flush()

                            totalBytesRead += bytesRead
                            chunkCount++

                            downloadState.downloadedBytes.value = totalBytesRead
                        }
                    }

                    // Send completion
                    downloadState.isComplete.value = true

                    val totalStreamTime = System.currentTimeMillis() - streamStartTime
                    LogManager.i(TAG, "Direct streaming completed successfully for ${totalBytesRead} bytes in ${totalStreamTime}ms")

                    // Schedule deletion of temp file
                    scope.launch {
                        delay(30000) // Keep for 30s for any trailing clients
                        tempFile.delete()
                    }
                }

            } catch (e: Exception) {
                val failureTime = System.currentTimeMillis() - streamStartTime
                LogManager.e(TAG, "Direct streaming failed for URL: $originalUrl after ${failureTime}ms", e)
                downloadState.error.value = e
            }
        }
    }

    private suspend fun streamFromDownload(downloadState: DownloadState, call: ApplicationCall, key: String, rangeHeader: String? = null) {
        val streamStartTime = System.currentTimeMillis()
        LogManager.i(TAG, "🎵 STREAM START - Setting up streaming response for key: $key, range: $rangeHeader")

        try {
            // 1. Wait for metadata to be available
            val metadata = withTimeoutOrNull(30000) {
                downloadState.metadata.filterNotNull().first()
            }

            if (metadata == null) {
                LogManager.e(TAG, "Timeout waiting for metadata for key: $key")
                call.respond(HttpStatusCode.GatewayTimeout, "Timeout waiting for metadata")
                return
            }

            LogManager.i(TAG, "📋 Received metadata: ${metadata.contentType}, length: ${metadata.contentLength}")

            // 2. Parse Range header if present
            var startOffset = 0L
            var endOffset = (metadata.contentLength ?: 0L) - 1

            if (rangeHeader != null) {
                val rangeRegex = Regex("bytes=(\\d+)-(\\d*)")
                val matchResult = rangeRegex.find(rangeHeader)
                if (matchResult != null) {
                    startOffset = matchResult.groupValues[1].toLong()
                    val endStr = matchResult.groupValues[2]
                    if (endStr.isNotEmpty()) {
                        endOffset = endStr.toLong()
                    }
                    LogManager.i(TAG, "Parsed Range: $startOffset-$endOffset")
                }
            }

            // 3. Set response headers
            val contentType = try {
                ContentType.parse(metadata.contentType)
            } catch (e: Exception) {
                ContentType.Audio.MPEG
            }

            val responseStatus = if (rangeHeader != null) HttpStatusCode.PartialContent else HttpStatusCode.OK
            if (rangeHeader != null) {
                val totalLength = metadata.contentLength ?: "*"
                call.response.headers.append("Content-Range", "bytes $startOffset-$endOffset/$totalLength")
            }

            LogManager.v(TAG, "Response (Streaming): $responseStatus")
            LogManager.v(TAG, "Response Headers: ${call.response.headers.allValues().entries().joinToString { "${it.key}=${it.value}" }}")

            // 4. Start streaming with Tailing Reader
            val responseLength = if (metadata.contentLength != null) endOffset - startOffset + 1 else null
            call.respondBytesWriter(status = responseStatus, contentType = contentType, contentLength = responseLength) {
                LogManager.i(TAG, "📡 ByteWriteChannel opened - tailing file for key: $key starting at $startOffset")

                var currentOffset = startOffset
                val buffer = ByteArray(CHUNK_SIZE)
                var firstDataReceived = false
                var chunkCount = 0
                var fileNotFoundRetryCount = 0  // CRITICAL FIX #3: Infinite loop protection
                val MAX_FILE_NOT_FOUND_RETRIES = 50  // 5 seconds max (50 * 100ms)

                var previousOffset = currentOffset
                var lastProgressAt = System.currentTimeMillis()
                var noProgressIterations = 0

                while (currentOffset <= endOffset && currentCoroutineContext().isActive) {
                    // Check for errors
                    val error = downloadState.error.value
                    if (error != null) {
                        LogManager.e(TAG, "Error in download stream for key: $key", error)
                        break
                    }

                    val availableBytes = downloadState.downloadedBytes.value
                    val isComplete = downloadState.isComplete.value
                    val isFinalizing = downloadState.isFinalizing.value

                    if (currentOffset > previousOffset) {
                        previousOffset = currentOffset
                        lastProgressAt = System.currentTimeMillis()
                        noProgressIterations = 0
                    } else {
                        noProgressIterations++
                        val noProgressForMs = System.currentTimeMillis() - lastProgressAt
                        if (
                            !isComplete &&
                            noProgressIterations >= STREAM_NO_PROGRESS_MAX_ITERATIONS &&
                            noProgressForMs >= STREAM_NO_PROGRESS_TIMEOUT_MS
                        ) {
                            LogManager.w(
                                TAG,
                                "Stream no-progress watchdog triggered for key=$key: offset=$currentOffset, available=$availableBytes, noProgressMs=${noProgressForMs}, iterations=$noProgressIterations"
                            )
                            break
                        }
                    }

                    if (currentOffset < availableBytes) {
                        // There is data to read
                        var fileToRead = downloadState.tempFile

                        // CRITICAL FIX #2: Handle finalization phase - wait for it to complete
                        if ((fileToRead == null || !fileToRead.exists()) && isFinalizing) {
                            LogManager.d(TAG, "Temp file is being finalized, waiting for completion for key: $key")
                            delay(50)  // Short wait for finalization to complete
                            continue
                        }

                        // Handle race condition: Temp file might be moved to cache if download completed
                        if ((fileToRead == null || !fileToRead.exists()) && isComplete && !isFinalizing) {
                            val cachedFile = contentCache.get(key.toContentKey())
                            if (cachedFile != null && cachedFile.exists()) {
                                LogManager.d(TAG, "Temp file gone but download complete - switching to cached file for key: $key")
                                fileToRead = cachedFile
                                fileNotFoundRetryCount = 0  // Reset retry count on successful switch
                            }
                        }

                        if (fileToRead == null || !fileToRead.exists()) {
                            if (isComplete) {
                                LogManager.w(TAG, "Source file missing and download complete for key: $key - stream may be incomplete")
                                break
                            }
                            // CRITICAL FIX #3: Infinite loop protection
                            fileNotFoundRetryCount++
                            if (fileNotFoundRetryCount >= MAX_FILE_NOT_FOUND_RETRIES) {
                                LogManager.e(TAG, "File not found after $MAX_FILE_NOT_FOUND_RETRIES retries for key: $key - giving up")
                                break
                            }
                            // Wait a bit for file to be created
                            delay(100)
                            continue
                        }

                        // Reset retry count on successful file access
                        fileNotFoundRetryCount = 0

                        try {
                            fileToRead.inputStream().use { input ->
                                input.skip(currentOffset)
                                while (currentOffset < availableBytes && currentOffset <= endOffset && currentCoroutineContext().isActive) {
                                    val remainingInResponse = endOffset - currentOffset + 1
                                    val toRead = (availableBytes - currentOffset)
                                        .coerceAtMost(CHUNK_SIZE.toLong())
                                        .coerceAtMost(remainingInResponse)
                                        .toInt()

                                    if (toRead <= 0) break

                                    val bytesRead = input.read(buffer, 0, toRead)
                                    if (bytesRead == -1) break

                                    try {
                                        writeFully(buffer, 0, bytesRead)
                                        flush()
                                    } catch (e: IOException) {
                                        if (e.message?.contains("Broken pipe") == true || e.message?.contains("Channel is closed") == true) {
                                            LogManager.d(TAG, "Client disconnected (channel closed) for key: $key")
                                            return@respondBytesWriter
                                        }
                                        throw e
                                    }

                                    currentOffset += bytesRead
                                    chunkCount++

                                    if (!firstDataReceived) {
                                        val timeToFirstData = System.currentTimeMillis() - streamStartTime
                                        LogManager.i(TAG, "⚡ FIRST DATA CHUNK - Streaming to client after ${timeToFirstData}ms! (at offset $startOffset)")
                                        firstDataReceived = true
                                    }

                                    if (chunkCount % 100 == 0) {
                                        LogManager.v(TAG, "Streamed $currentOffset bytes to client for key: $key")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is IOException && (e.message?.contains("Broken pipe") == true || e.message?.contains("Channel is closed") == true)) {
                                LogManager.d(TAG, "Client disconnected (channel closed) during read/open for key: $key")
                                return@respondBytesWriter
                            }
                            // If the file disappeared while we were trying to open it, loop around and try to find the cached file
                            LogManager.w(TAG, "Error reading from file for key: $key - retrying loop", e)
                        }
                    } else if (isComplete) {
                        // Reached end of file and download is finished
                        LogManager.i(TAG, "🎉 STREAMING COMPLETE - Sent up to $currentOffset bytes for key: $key")
                        break
                    } else {
                        // Wait for more data
                        try {
                            withTimeout(5000) {
                                downloadState.downloadedBytes.filter { it > currentOffset }.first()
                            }
                        } catch (e: TimeoutCancellationException) {
                            // Check again, don't just hang forever if notifications failed
                            // If download is complete, we can read from the cached file
                            if (downloadState.isComplete.value) {
                                break
                            }
                        }
                    }
                }
            }

            val totalStreamTime = System.currentTimeMillis() - streamStartTime
            LogManager.i(TAG, "📊 SUMMARY - Total stream duration: ${totalStreamTime}ms for key: $key")

        } catch (e: Exception) {
            if (e is IOException && e.message?.contains("Broken pipe") == true) {
                LogManager.d(TAG, "Stream disconnected (Broken pipe) for key: $key")
            } else {
                val errorTime = System.currentTimeMillis() - streamStartTime
                LogManager.e(TAG, "Error streaming content for key: $key after ${errorTime}ms", e)
            }
        }
    }

    fun preload(url: String) {
        val key = url.toCacheKey()
        LogManager.d(TAG, "Preloading URL: $url (key: $key)")
        scope.launch {
            val preloadStartTime = System.currentTimeMillis()
            try {
                val state = downloadMutex.withLock {
                    val s = DownloadState()
                    activeDownloadStates[key] = s
                    s
                }
                performDownloadAndCache(url, key, state)
                val preloadTime = System.currentTimeMillis() - preloadStartTime
                LogManager.i(TAG, "Preload completed for URL: $url in ${preloadTime}ms")
            } catch (e: Exception) {
                val preloadTime = System.currentTimeMillis() - preloadStartTime
                LogManager.e(TAG, "Preload failed for URL: $url after ${preloadTime}ms", e)
            } finally {
                downloadMutex.withLock {
                    activeDownloadStates.remove(key)
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
     * Cancel download for a specific URL
     */
    suspend fun cancelDownloadForUrl(url: String) {
        val key = url.toCacheKey()
        LogManager.d(TAG, "Cancelling download for URL: $url (key: $key)")

        downloadMutex.withLock {
            try {
                // Cancel the download job
                activeDownloadJobs[key]?.let { job ->
                    LogManager.d(TAG, "Cancelling download job for key: $key")
                    job.cancel()
                    activeDownloadJobs.remove(key)
                }

                // Remove the download state
                activeDownloadStates[key]?.let { state ->
                    LogManager.d(TAG, "Removing download state for key: $key")
                    activeDownloadStates.remove(key)
                }

                LogManager.d(TAG, "Download cancelled successfully for key: $key (remaining downloads: ${activeDownloadStates.size})")
            } catch (e: Exception) {
                LogManager.w(TAG, "Error cancelling download for key: $key", e)
            }
        }
    }

    /**
     * Cancel all active downloads
     */
    suspend fun cancelAllDownloads() {
        LogManager.i(TAG, "Cancelling all active downloads (${activeDownloadStates.size} total)")

        downloadMutex.withLock {
            try {
                // Cancel all download jobs
                activeDownloadJobs.values.forEach { job ->
                    try {
                        job.cancel()
                    } catch (e: Exception) {
                        LogManager.w(TAG, "Error cancelling download job", e)
                    }
                }
                activeDownloadJobs.clear()

                // Clear all download states
                activeDownloadStates.clear()

                LogManager.i(TAG, "All downloads cancelled successfully")
            } catch (e: Exception) {
                LogManager.e(TAG, "Error cancelling all downloads", e)
            }
        }
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
        LogManager.d(TAG, "Active download states at shutdown: ${activeDownloadStates.size}")
        LogManager.d(TAG, "Active download jobs at shutdown: ${activeDownloadJobs.size}")
        LogManager.d(TAG, "URL mappings at shutdown: ${urlMappings.size}")

        // Cancel all active downloads first
        cancelAllDownloads()

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
