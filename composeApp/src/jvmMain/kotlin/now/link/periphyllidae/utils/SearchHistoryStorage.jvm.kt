package now.link.periphyllidae.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * JVM implementation of PlatformStorage using local file storage
 */
actual class PlatformStorage {

    private val historyDir: File by lazy {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".periphyllidae")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        appDir
    }

    private val historyFile: File by lazy {
        File(historyDir, "search_history.json")
    }

    actual suspend fun saveString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            try {
                FileOutputStream(historyFile).use { output ->
                    output.write(value.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to save search history", e)
            }
        }
    }

    actual suspend fun getString(key: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!historyFile.exists()) {
                    return@withContext null
                }
                FileInputStream(historyFile).use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to read search history", e)
            }
        }
    }

    actual suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            try {
                if (historyFile.exists()) {
                    historyFile.delete()
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to remove search history", e)
            }
        }
    }
}