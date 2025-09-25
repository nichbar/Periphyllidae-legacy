# Periphyllidae Cache System

## Overview

The Periphyllidae cache system implements a sophisticated disk-based caching proxy for music streaming, as specified in `cache-requirements.md`. It provides transparent caching of music streams with an embedded HTTP server, allowing the music player to seamlessly cache and serve audio content for improved performance and reduced network usage.

## Architecture

The cache system consists of several key components:

### Core Components

1. **DiskLruCache**: A thread-safe LRU (Least Recently Used) disk cache implementation
2. **CacheManager**: The main cache coordinator with embedded Ktor HTTP server
3. **CacheDirectoryProvider**: Platform-specific cache directory resolution
4. **CacheInitializer**: Asynchronous cache system initialization

### System Requirements Compliance

The implementation fulfills all requirements from `cache-requirements.md`:

- **REQ-G-01**: ✅ Provides local HTTP playback URLs via `getLocalPlaybackUrl()`
- **REQ-FS-01**: ✅ Stores complete music streams as single local files
- **REQ-FS-02**: ✅ Uses SHA-256 hash of URLs as unique cache keys
- **REQ-FS-03**: ✅ Uses dedicated application cache directory with DiskLruCache
- **REQ-FSV-01**: ✅ Runs embedded Ktor HTTP server on local port
- **REQ-FSV-02**: ✅ Dynamic endpoint `/cache/{key}` for content serving
- **REQ-FSV-03**: ✅ Serves cached content directly from local files
- **REQ-FSV-04**: ✅ Streams from remote while caching for non-cached content
- **REQ-FW-01**: ✅ Uses Ktor HTTP client for downloads
- **REQ-FW-02**: ✅ Uses DiskLruCache.Editor for cache writing
- **REQ-FW-03**: ✅ Commits editor on successful downloads
- **REQ-FW-04**: ✅ Aborts editor on download failures
- **REQ-FM-01**: ✅ Configurable maximum cache size (default 1GB)
- **REQ-FM-02**: ✅ Automatic LRU eviction when size limit exceeded
- **REQ-NF-01**: ✅ Thread-safe implementation with mutex synchronization
- **REQ-NF-02**: ✅ Single download per URL with multiple client support
- **REQ-NF-03**: ✅ Server lifecycle management (start/stop)

## Usage

### Automatic Integration

The cache system is automatically integrated into the `MusicPlayer` class. When playing songs, the player will:

1. Check if caching is available
2. Generate a local cache URL for the original stream URL
3. Use the local URL for playback, which triggers caching if needed

### Manual Cache Operations

#### Check if a song is cached
```kotlin
val isCached = musicPlayer.isSongCached(song)
```

#### Preload songs into cache
```kotlin
// Single song
musicPlayer.preloadSong(song)

// Multiple songs
musicPlayer.preloadSongs(listOf(song1, song2, song3))
```

#### Get cache statistics
```kotlin
val stats = musicPlayer.getCacheStats()
println("Cache size: ${stats?.currentSize}/${stats?.maxSize} bytes")
```

#### Clear cache
```kotlin
musicPlayer.clearCache()
```

## Implementation Details

### Cache Directory Locations

#### Android
- Location: `{app_cache_dir}/music_cache`
- Example: `/data/data/now.link.periphyllidae/cache/music_cache`

#### JVM (Desktop)
- **Windows**: `%APPDATA%/Periphyllidae/music_cache`
- **macOS**: `~/Library/Caches/Periphyllidae/music_cache`
- **Linux**: `$XDG_CACHE_HOME/Periphyllidae/music_cache` or `~/.cache/Periphyllidae/music_cache`

### Cache Server

The embedded Ktor server runs on a dynamically allocated port and provides:

- **Health endpoint**: `GET /health` - Server health check
- **Cache endpoint**: `GET /cache/{key}` - Content serving by cache key

### URL Transformation

Original streaming URLs are transformed using SHA-256 hashing:

```
Original: https://music.example.com/stream/song123.mp3
Cache Key: a1b2c3d4e5f6...  (SHA-256 hash)
Local URL: http://127.0.0.1:8080/cache/a1b2c3d4e5f6...
```

### Concurrency and Thread Safety

- **Mutex synchronization**: Ensures thread-safe cache operations
- **Single download per URL**: Multiple concurrent requests for the same URL share a single download
- **Asynchronous operations**: Cache operations are non-blocking
- **Coroutine-based**: Uses Kotlin coroutines for all async operations

### Error Handling

The cache system gracefully handles various error conditions:

- **Network failures**: Falls back to direct streaming
- **Cache unavailable**: Disables caching, continues with direct URLs
- **Disk space issues**: Automatic LRU eviction
- **Corrupted cache**: Automatic cache recreation

### Performance Considerations

- **Streaming while caching**: Content is served to the player while being cached
- **LRU eviction**: Automatically manages disk space usage
- **Configurable cache size**: Default 1GB, adjustable during initialization
- **Platform-optimized**: Uses platform-specific optimal cache locations

## Initialization

The cache system is automatically initialized during app startup:

1. `CacheInitializer.initializeAsync()` is called in `App.kt`
2. Platform-specific cache directory is resolved
3. DiskLruCache is opened with the specified directory and size
4. Embedded Ktor server is started on an available port
5. CacheManager is registered with Koin DI container

## Dependencies

The cache system requires these additional dependencies:

- **Ktor Server**: `io.ktor:ktor-server-core`, `io.ktor:ktor-server-cio`
- **Okio**: `com.squareup.okio:okio` (for file I/O operations)

## Testing

To test the cache functionality:

1. **Enable verbose logging** to see cache operations
2. **Play a song** - first play will download and cache
3. **Play the same song again** - second play will serve from cache
4. **Check cache directory** - verify files are created
5. **Monitor logs** for cache hit/miss information

## Troubleshooting

### Cache Not Working
- Check logs for initialization errors
- Verify disk space availability
- Ensure network connectivity for initial downloads

### High Disk Usage
- Check cache statistics with `getCacheStats()`
- Reduce cache size limit if needed
- Clear cache manually if required

### Port Conflicts
- The system automatically finds available ports
- Check logs for server startup messages
- Restart the app if port allocation fails

## Future Enhancements

Potential improvements for the cache system:

1. **Smart preloading**: Automatic playlist preloading
2. **Cache warming**: Background cache population
3. **Bandwidth adaptation**: Quality-based caching
4. **Cache sharing**: Multi-instance cache coordination
5. **Analytics**: Cache hit ratio and performance metrics

## API Reference

### CacheManager

```kotlin
class CacheManager {
    suspend fun isCached(url: String): Boolean
    fun getLocalPlaybackUrl(originalUrl: String): String
    fun getCacheStats(): CacheStats
    suspend fun clearCache()
    suspend fun removeCached(url: String): Boolean
    suspend fun shutdown()
}
```

### MusicPlayer Cache Methods

```kotlin
class MusicPlayer {
    suspend fun isSongCached(song: Song): Boolean
    suspend fun preloadSong(song: Song)
    suspend fun preloadSongs(songs: List<Song>)
    fun getCacheStats(): CacheStats?
    suspend fun clearCache()
}
```

### CacheStats

```kotlin
data class CacheStats(
    val currentSize: Long,     // Current cache size in bytes
    val maxSize: Long,         // Maximum cache size in bytes
    val entryCount: Long       // Number of cached entries
)
```

This comprehensive cache system provides transparent, efficient music streaming with intelligent caching capabilities, enhancing the user experience while reducing network bandwidth usage.
