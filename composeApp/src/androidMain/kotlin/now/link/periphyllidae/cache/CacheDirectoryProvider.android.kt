package now.link.periphyllidae.cache

import android.content.Context
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class CacheDirectoryProvider : KoinComponent {
    private val context: Context by inject()
    
    actual fun getCacheDirectory(): Path {
        val cacheDir = context.cacheDir.resolve("music_cache")
        return cacheDir.absolutePath.toPath()
    }
}
