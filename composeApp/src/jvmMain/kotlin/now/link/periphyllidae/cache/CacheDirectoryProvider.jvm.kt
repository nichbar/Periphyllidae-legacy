package now.link.periphyllidae.cache

import okio.Path
import okio.Path.Companion.toPath
import java.io.File

actual class CacheDirectoryProvider {
    actual fun getCacheDirectory(): Path {
        val userHome = System.getProperty("user.home")
        val appDataDir = when {
            System.getProperty("os.name").lowercase().contains("windows") -> {
                System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
            }
            System.getProperty("os.name").lowercase().contains("mac") -> {
                "$userHome/Library/Caches"
            }
            else -> {
                System.getenv("XDG_CACHE_HOME") ?: "$userHome/.cache"
            }
        }
        
        val cacheDir = File(appDataDir, "Periphyllidae/music_cache")
        return cacheDir.absolutePath.toPath()
    }
}
