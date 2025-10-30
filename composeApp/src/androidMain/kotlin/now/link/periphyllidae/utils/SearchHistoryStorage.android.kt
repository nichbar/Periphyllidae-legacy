package now.link.periphyllidae.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of PlatformStorage using SharedPreferences
 */
actual class PlatformStorage {

    private val context: Context by lazy {
        // This will be set by the dependency injection system
        throw IllegalStateException("PlatformStorage not properly initialized")
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    }

    actual suspend fun saveString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    actual suspend fun getString(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    actual suspend fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    // Factory method for proper initialization
    companion object {
        fun create(context: Context): PlatformStorage {
            return object : PlatformStorage() {
                override val context: Context = context
            }
        }
    }
}