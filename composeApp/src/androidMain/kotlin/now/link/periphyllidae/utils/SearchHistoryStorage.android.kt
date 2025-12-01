package now.link.periphyllidae.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of PlatformStorage using SharedPreferences
 */
actual class PlatformStorage {

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun initialize(context: Context) {
            if (appContext == null) {
                synchronized(this) {
                    if (appContext == null) {
                        appContext = context.applicationContext
                    }
                }
            }
        }
    }

    private val context: Context by lazy {
        appContext ?: throw IllegalStateException("PlatformStorage must be initialized before use. Call PlatformStorage.initialize(context) first.")
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("periphyllidae_search_history", Context.MODE_PRIVATE)
    }

    actual suspend fun saveString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putString(key, value).apply()
        }
    }

    actual suspend fun getString(key: String): String? {
        return withContext(Dispatchers.IO) {
            sharedPreferences.getString(key, null)
        }
    }

    actual suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().remove(key).apply()
        }
    }
}