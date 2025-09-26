package now.link.periphyllidae.di

import org.koin.dsl.module
import com.russhwolf.settings.Settings
import now.link.periphyllidae.platform.MediampPlayerFactory
import now.link.periphyllidae.platform.JvmMediampPlayerFactory
import java.util.Properties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

actual val platformModule = module {
    single<Settings> {
        object : Settings {
            private val settingsFile = File(System.getProperty("user.home"), ".periphyllidae.properties")
            private val properties = Properties().apply {
                if (settingsFile.exists()) {
                    FileInputStream(settingsFile).use { load(it) }
                }
            }
            
            private fun save() {
                settingsFile.parentFile?.mkdirs()
                FileOutputStream(settingsFile).use { properties.store(it, "Periphyllidae Settings") }
            }

            override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
                properties.getProperty(key)?.toBoolean() ?: defaultValue
            override fun getBooleanOrNull(key: String): Boolean? = 
                properties.getProperty(key)?.toBoolean()
            override fun getDouble(key: String, defaultValue: Double): Double =
                properties.getProperty(key)?.toDouble() ?: defaultValue
            override fun getDoubleOrNull(key: String): Double? = 
                properties.getProperty(key)?.toDoubleOrNull()
            override fun getFloat(key: String, defaultValue: Float): Float =
                properties.getProperty(key)?.toFloat() ?: defaultValue
            override fun getFloatOrNull(key: String): Float? = 
                properties.getProperty(key)?.toFloatOrNull()
            override fun getInt(key: String, defaultValue: Int): Int =
                properties.getProperty(key)?.toInt() ?: defaultValue
            override fun getIntOrNull(key: String): Int? = 
                properties.getProperty(key)?.toIntOrNull()
            override fun getLong(key: String, defaultValue: Long): Long =
                properties.getProperty(key)?.toLong() ?: defaultValue
            override fun getLongOrNull(key: String): Long? = 
                properties.getProperty(key)?.toLongOrNull()
            override fun getString(key: String, defaultValue: String): String =
                properties.getProperty(key) ?: defaultValue
            override fun getStringOrNull(key: String): String? = 
                properties.getProperty(key)

            override fun putBoolean(key: String, value: Boolean) { 
                properties.setProperty(key, value.toString())
                save()
            }
            override fun putDouble(key: String, value: Double) { 
                properties.setProperty(key, value.toString())
                save()
            }
            override fun putFloat(key: String, value: Float) { 
                properties.setProperty(key, value.toString())
                save()
            }
            override fun putInt(key: String, value: Int) { 
                properties.setProperty(key, value.toString())
                save()
            }
            override fun putLong(key: String, value: Long) { 
                properties.setProperty(key, value.toString())
                save()
            }
            override fun putString(key: String, value: String) { 
                properties.setProperty(key, value)
                save()
            }

            override fun remove(key: String) { 
                properties.remove(key)
                save()
            }
            override fun clear() { 
                properties.clear()
                save()
            }
            override fun hasKey(key: String): Boolean = properties.containsKey(key)
            override val keys: Set<String> get() = properties.stringPropertyNames()
            override val size: Int get() = properties.size
        }
    }
    
    // MediaMP player factory for JVM
    single<MediampPlayerFactory> { JvmMediampPlayerFactory() }
}
