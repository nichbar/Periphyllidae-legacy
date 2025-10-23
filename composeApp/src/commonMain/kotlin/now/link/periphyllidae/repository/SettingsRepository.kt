package now.link.periphyllidae.repository

import com.russhwolf.settings.Settings
import now.link.periphyllidae.models.ServerConfig
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.HomeContentCache
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

interface SettingsRepository {
    fun addServerConfig(config: ServerConfig)
    fun removeServerConfig(config: ServerConfig)
    fun removeAllServerConfigs()
    fun setSelectedServerConfig(config: ServerConfig)
    fun getSelectedServerConfig(): ServerConfig?
    fun saveServerConfigs(configs: List<ServerConfig>)
    fun getServerConfigs(): List<ServerConfig>
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
    fun saveHomeContentCache(cache: HomeContentCache)
    fun getHomeContentCache(): HomeContentCache?
    fun clearHomeContentCache()
    fun saveWindowWidth(width: Int)
    fun getWindowWidth(): Int
    fun saveWindowHeight(height: Int)
    fun getWindowHeight(): Int
    fun saveWindowPosX(x: Int)
    fun getWindowPosX(): Int
    fun saveWindowPosY(y: Int)
    fun getWindowPosY(): Int
    fun saveVolume(volume: Float)
    fun getVolume(): Float
    fun saveShuffle(enabled: Boolean)
    fun getShuffle(): Boolean
    fun saveRepeat(enabled: Boolean)
    fun getRepeat(): Boolean
    fun saveCacheSize(sizeMB: Int)
    fun getCacheSize(): Int
}

class SettingsRepositoryImpl(
    private val settings: Settings,
    private val json: Json
) : SettingsRepository {
    
    companion object {
        private const val KEY_SERVER_CONFIGS = "server_configs"
        private const val KEY_THEME = "theme"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"
        private const val KEY_SERVER_SALT_PREFIX = "server_salt_"
        private const val KEY_PLAYBACK_QUEUE = "playback_queue"
        private const val KEY_CURRENT_SONG_INDEX = "current_song_index"
        private const val KEY_CURRENT_SONG_POSITION = "current_song_position"
        private const val KEY_LOG_ENABLED = "log_enabled"
        private const val KEY_HOME_CONTENT_CACHE = "home_content_cache"
        private const val KEY_WINDOW_WIDTH = "window_width"
        private const val KEY_WINDOW_HEIGHT = "window_height"
        private const val KEY_WINDOW_POS_X = "window_pos_x"
        private const val KEY_WINDOW_POS_Y = "window_pos_y"
        private const val KEY_VOLUME = "volume"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_CACHE_SIZE = "cache_size"
    }

    override fun addServerConfig(config: ServerConfig) {
        // Also add to the list of saved servers
        val existingConfigs = getServerConfigs().toMutableList()
        val existingIndex = existingConfigs.indexOfFirst { it.url == config.url && it.username == config.username }
        if (existingIndex >= 0) {
            existingConfigs[existingIndex] = config
        } else {
            existingConfigs.add(config)
        }
        saveServerConfigs(existingConfigs)
    }

    override fun removeServerConfig(config: ServerConfig) {
        val existingConfigs = getServerConfigs().toMutableList()
        val updatedConfigs = existingConfigs.filterNot { it.url == config.url && it.username == config.username }.toMutableList()

        val hasSelectedConfig = updatedConfigs.any { it.isSelected }

        // Ensure one config is always selected
        if (!hasSelectedConfig && updatedConfigs.isNotEmpty()) {
            // If the removed config was selected, select the first one in the updated list
            updatedConfigs[0] = updatedConfigs[0].copy(isSelected = true)
        }

        saveServerConfigs(updatedConfigs)
    }

    override fun removeAllServerConfigs() {
        settings.remove(KEY_SERVER_CONFIGS)
    }

    override fun getSelectedServerConfig(): ServerConfig? {
        return settings.getStringOrNull(KEY_SERVER_CONFIGS)?.let { configsString ->
            try {
                json.decodeFromString<List<ServerConfig>>(configsString)
                    .firstOrNull { it.isSelected }
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun setSelectedServerConfig(config: ServerConfig) {
        val existingConfigs = getServerConfigs().toMutableList()
        val updatedConfigs = existingConfigs.map {
            if (it.url == config.url && it.username == config.username) {
                it.copy(isSelected = true)
            } else {
                it.copy(isSelected = false)
            }
        }
        saveServerConfigs(updatedConfigs)
    }

    override fun saveServerConfigs(configs: List<ServerConfig>) {
        settings.putString(KEY_SERVER_CONFIGS, json.encodeToString(configs))
    }
    
    override fun getServerConfigs(): List<ServerConfig> {
        return settings.getStringOrNull(KEY_SERVER_CONFIGS)?.let { configsString ->
            try {
                json.decodeFromString<List<ServerConfig>>(configsString)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
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
    
    override fun saveHomeContentCache(cache: HomeContentCache) {
        try {
            settings.putString(KEY_HOME_CONTENT_CACHE, json.encodeToString(cache))
        } catch (e: Exception) {
            // Handle serialization error silently
        }
    }
    
    override fun getHomeContentCache(): HomeContentCache? {
        return try {
            val cacheString = settings.getStringOrNull(KEY_HOME_CONTENT_CACHE)
            cacheString?.let { json.decodeFromString<HomeContentCache>(it) }
        } catch (e: Exception) {
            // Handle deserialization error, return null for invalid cache
            null
        }
    }
    
    override fun clearHomeContentCache() {
        settings.remove(KEY_HOME_CONTENT_CACHE)
    }
    
    override fun saveWindowWidth(width: Int) {
        settings.putInt(KEY_WINDOW_WIDTH, width)
    }
    
    override fun getWindowWidth(): Int {
        return settings.getInt(KEY_WINDOW_WIDTH, 450) // Default width
    }
    
    override fun saveWindowHeight(height: Int) {
        settings.putInt(KEY_WINDOW_HEIGHT, height)
    }
    
    override fun getWindowHeight(): Int {
        return settings.getInt(KEY_WINDOW_HEIGHT, 800) // Default height
    }
    
    override fun saveWindowPosX(x: Int) {
        settings.putInt(KEY_WINDOW_POS_X, x)
    }
    
    override fun getWindowPosX(): Int {
        return settings.getInt(KEY_WINDOW_POS_X, -1) // -1 means center
    }
    
    override fun saveWindowPosY(y: Int) {
        settings.putInt(KEY_WINDOW_POS_Y, y)
    }
    
    override fun getWindowPosY(): Int {
        return settings.getInt(KEY_WINDOW_POS_Y, -1) // -1 means center
    }
    
    override fun saveVolume(volume: Float) {
        settings.putFloat(KEY_VOLUME, volume)
    }
    
    override fun getVolume(): Float {
        return settings.getFloat(KEY_VOLUME, 0.7f) // Default volume 70%
    }
    
    override fun saveShuffle(enabled: Boolean) {
        settings.putBoolean(KEY_SHUFFLE, enabled)
    }
    
    override fun getShuffle(): Boolean {
        return settings.getBoolean(KEY_SHUFFLE, false) // Default shuffle off
    }
    
    override fun saveRepeat(enabled: Boolean) {
        settings.putBoolean(KEY_REPEAT, enabled)
    }
    
    override fun getRepeat(): Boolean {
        return settings.getBoolean(KEY_REPEAT, false) // Default repeat off
    }

    override fun saveCacheSize(sizeMB: Int) {
        settings.putInt(KEY_CACHE_SIZE, sizeMB)
    }

    override fun getCacheSize(): Int {
        return settings.getInt(KEY_CACHE_SIZE, 1024) // Default 1GB
    }
}
