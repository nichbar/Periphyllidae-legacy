package now.link.periphyllidae.repository

import com.russhwolf.settings.Settings
import now.link.periphyllidae.models.ServerConfig
import now.link.periphyllidae.models.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

interface SettingsRepository {
    fun saveServerConfig(config: ServerConfig)
    fun getServerConfig(): ServerConfig?
    fun clearServerConfig()
    fun saveTheme(theme: String)
    fun getTheme(): String
    fun saveIsAuthenticated(isAuthenticated: Boolean)
    fun getIsAuthenticated(): Boolean
    fun saveServerSalt(serverKey: String, salt: String)
    fun getServerSalt(serverKey: String): String?
    fun savePlaybackQueue(queue: List<Song>)
    fun getPlaybackQueue(): List<Song>?
    fun saveCurrentSongIndex(index: Int)
    fun getCurrentSongIndex(): Int
    fun saveCurrentSongPosition(position: Long)
    fun getCurrentSongPosition(): Long
    fun saveLogEnabled(enabled: Boolean)
    fun getLogEnabled(): Boolean
}

class SettingsRepositoryImpl(
    private val settings: Settings,
    private val json: Json
) : SettingsRepository {
    
    companion object {
        private const val KEY_SERVER_CONFIG = "server_config"
        private const val KEY_THEME = "theme"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"
        private const val KEY_SERVER_SALT_PREFIX = "server_salt_"
        private const val KEY_PLAYBACK_QUEUE = "playback_queue"
        private const val KEY_CURRENT_SONG_INDEX = "current_song_index"
        private const val KEY_CURRENT_SONG_POSITION = "current_song_position"
        private const val KEY_LOG_ENABLED = "log_enabled"
    }
    
    override fun saveServerConfig(config: ServerConfig) {
        settings.putString(KEY_SERVER_CONFIG, json.encodeToString(config))
    }
    
    override fun getServerConfig(): ServerConfig? {
        return settings.getStringOrNull(KEY_SERVER_CONFIG)?.let { configString ->
            try {
                json.decodeFromString<ServerConfig>(configString)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override fun clearServerConfig() {
        settings.remove(KEY_SERVER_CONFIG)
    }
    
    override fun saveTheme(theme: String) {
        settings.putString(KEY_THEME, theme)
    }
    
    override fun getTheme(): String {
        return settings.getString(KEY_THEME, "SYSTEM")
    }
    
    override fun saveIsAuthenticated(isAuthenticated: Boolean) {
        settings.putBoolean(KEY_IS_AUTHENTICATED, isAuthenticated)
    }
    
    override fun getIsAuthenticated(): Boolean {
        return settings.getBoolean(KEY_IS_AUTHENTICATED, false)
    }
    
    override fun saveServerSalt(serverKey: String, salt: String) {
        settings.putString(KEY_SERVER_SALT_PREFIX + serverKey, salt)
    }
    
    override fun getServerSalt(serverKey: String): String? {
        return settings.getStringOrNull(KEY_SERVER_SALT_PREFIX + serverKey)
    }
    
    override fun savePlaybackQueue(queue: List<Song>) {
        settings.putString(KEY_PLAYBACK_QUEUE, json.encodeToString(queue))
    }
    
    override fun getPlaybackQueue(): List<Song>? {
        return settings.getStringOrNull(KEY_PLAYBACK_QUEUE)?.let { queueString ->
            try {
                json.decodeFromString<List<Song>>(queueString)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override fun saveCurrentSongIndex(index: Int) {
        settings.putInt(KEY_CURRENT_SONG_INDEX, index)
    }
    
    override fun getCurrentSongIndex(): Int {
        return settings.getInt(KEY_CURRENT_SONG_INDEX, 0)
    }
    
    override fun saveCurrentSongPosition(position: Long) {
        settings.putLong(KEY_CURRENT_SONG_POSITION, position)
    }
    
    override fun getCurrentSongPosition(): Long {
        return settings.getLong(KEY_CURRENT_SONG_POSITION, 0L)
    }
    
    override fun saveLogEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_LOG_ENABLED, enabled)
    }
    
    override fun getLogEnabled(): Boolean {
        return settings.getBoolean(KEY_LOG_ENABLED, false)
    }
}
