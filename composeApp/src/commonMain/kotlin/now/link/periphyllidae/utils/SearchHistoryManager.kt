package now.link.periphyllidae.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import now.link.periphyllidae.mvi.HistoryItem
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Artist

/**
 * Manages search history persistence across platforms
 * Handles storage and retrieval of the last 5 clicked items
 */
class SearchHistoryManager(
    private val platformStorage: PlatformStorage
) {
    companion object {
        private const val TAG = "SearchHistoryManager"
        private const val MAX_HISTORY_ITEMS = 5
        private const val HISTORY_KEY = "search_history"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Add an item to search history
     * @param item The history item to add
     */
    suspend fun addToHistory(item: HistoryItem) {
        try {
            val currentHistory = getHistory().toMutableList()

            // Remove existing item with same ID if it exists
            currentHistory.removeAll { it.id == item.id }

            // Add new item to the beginning
            currentHistory.add(0, item)

            // Keep only the last MAX_HISTORY_ITEMS
            val trimmedHistory = currentHistory.take(MAX_HISTORY_ITEMS)

            val historyJson = json.encodeToString(trimmedHistory)
            platformStorage.saveString(HISTORY_KEY, historyJson)

            LogManager.i(TAG, "Added item to search history: ${item.name}")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error adding item to search history", e)
        }
    }

    /**
     * Get the current search history
     * @return List of history items sorted by most recent first
     */
    suspend fun getHistory(): List<HistoryItem> {
        try {
            val historyJson = platformStorage.getString(HISTORY_KEY) ?: return emptyList()
            return json.decodeFromString<List<HistoryItem>>(historyJson)
        } catch (e: Exception) {
            LogManager.e(TAG, "Error retrieving search history", e)
            return emptyList()
        }
    }

    /**
     * Clear all search history
     */
    suspend fun clearHistory() {
        try {
            platformStorage.remove(HISTORY_KEY)
            LogManager.i(TAG, "Search history cleared")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error clearing search history", e)
        }
    }

    /**
     * Create a HistoryItem from a Song
     */
    fun createHistoryItem(song: Song): HistoryItem {
        return HistoryItem(
            id = "song_${song.id}",
            type = now.link.periphyllidae.mvi.HistoryItemType.Song,
            name = song.title,
            subtitle = song.artist,
            imageUrl = song.coverArt,
            imageId = song.coverArtId,
            timestamp = System.currentTimeMillis(),
            data = song
        )
    }

    /**
     * Create a HistoryItem from an Album
     */
    fun createHistoryItem(album: Album): HistoryItem {
        return HistoryItem(
            id = "album_${album.id}",
            type = now.link.periphyllidae.mvi.HistoryItemType.Album,
            name = album.name,
            subtitle = album.artist,
            imageUrl = album.coverArt,
            imageId = album.coverArtId,
            timestamp = System.currentTimeMillis(),
            data = album
        )
    }

    /**
     * Create a HistoryItem from an Artist
     */
    fun createHistoryItem(artist: Artist): HistoryItem {
        return HistoryItem(
            id = "artist_${artist.id}",
            type = now.link.periphyllidae.mvi.HistoryItemType.Artist,
            name = artist.name,
            subtitle = "${artist.albumCount} albums",
            imageUrl = artist.coverArt,
            imageId = artist.coverArtId,
            timestamp = System.currentTimeMillis(),
            data = artist
        )
    }
}

/**
 * Platform-specific storage interface
 * Implemented separately for Android and JVM platforms
 */
expect class PlatformStorage {
    suspend fun saveString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun remove(key: String)
}
