package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.FindIntent
import now.link.periphyllidae.mvi.FindState
import now.link.periphyllidae.mvi.FindView
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.mvi.HistoryItemType
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.repository.SubsonicRepositoryManager
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.utils.ApplicationScopeManager
import now.link.periphyllidae.utils.SearchHistoryManager
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.models.Album
import now.link.periphyllidae.models.Artist
import now.link.periphyllidae.ui.screens.find.utils.customAlphabeticalComparator

/**
 * ViewModel for the Find screen
 * Handles search, browsing, and search history functionality
 */
class FindViewModel(
    private val subsonicRepositoryManager: SubsonicRepositoryManager,
    private val musicStore: MusicStore,
    private val searchHistoryManager: SearchHistoryManager
) : ViewModel() {

    companion object {
        private const val TAG = "FindViewModel"
        private const val DEFAULT_PAGE_SIZE = 100
    }

    // Find state
    private val _findState = MutableStateFlow(FindState())
    val findState: StateFlow<FindState> = _findState.asStateFlow()

    // Local UI state
    private val _isSearchBarFocused = MutableStateFlow(false)
    val isSearchBarFocused: StateFlow<Boolean> = _isSearchBarFocused.asStateFlow()

    init {
        loadSearchHistory()
    }

    fun handleIntent(intent: FindIntent) {
        when (intent) {
            is FindIntent.SearchQuery -> {
                _findState.value = _findState.value.copy(searchQuery = intent.query)
                if (intent.query.isNotBlank()) {
                    search(intent.query)
                } else {
                    _findState.value = _findState.value.copy(searchResults = null)
                }
            }

            is FindIntent.SelectHistoryItem -> {
                handleHistoryItemSelection(intent.item)
            }

            is FindIntent.BrowseAllSongs -> {
                browseAllSongs()
            }

            is FindIntent.BrowseAllAlbums -> {
                browseAllAlbums()
            }

            is FindIntent.BrowseAllArtists -> {
                browseAllArtists()
            }

            is FindIntent.ClearHistory -> {
                clearHistory()
            }

            is FindIntent.AddToHistory -> {
                addToHistory(intent.item)
            }

            is FindIntent.LoadMoreSongs -> {
                loadMoreSongs(intent.offset)
            }

            is FindIntent.LoadMoreAlbums -> {
                loadMoreAlbums(intent.offset)
            }

            is FindIntent.LoadMoreArtists -> {
                loadMoreArtists(intent.offset)
            }

            is FindIntent.ClearError -> {
                _findState.value = _findState.value.copy(error = null)
            }

            is FindIntent.BackToSearch -> {
                _findState.value = _findState.value.copy(currentView = FindView.Search)
            }
        }
    }

    private fun search(query: String) {
        ApplicationScopeManager.scope.launch {
            _findState.value = _findState.value.copy(isLoading = true, error = null)

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.search(query.trim())

                if (result.isSuccess) {
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        searchResults = result.getOrThrow(),
                        error = null
                    )
                    LogManager.i(TAG, "Search completed for query: $query")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        error = "Search failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Search error", e)
                _findState.value = _findState.value.copy(
                    isLoading = false,
                    error = "Search failed. Please check your connection."
                )
            }
        }
    }

    private fun browseAllSongs() {
        ApplicationScopeManager.scope.launch {
            _findState.value = _findState.value.copy(
                currentView = FindView.AllSongs,
                isLoading = true,
                error = null
            )

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllSongs(DEFAULT_PAGE_SIZE)

                if (result.isSuccess) {
                    val sortedSongs = result.getOrThrow().sortedWith { a, b ->
                        customAlphabeticalComparator(a.title, b.title)
                    }
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseSongs = sortedSongs,
                        error = null
                    )
                    LogManager.i(TAG, "Loaded ${_findState.value.browseSongs.size} songs")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        error = "Failed to load songs. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading songs", e)
                _findState.value = _findState.value.copy(
                    isLoading = false,
                    error = "Failed to load songs. Please check your connection."
                )
            }
        }
    }

    private fun browseAllAlbums() {
        ApplicationScopeManager.scope.launch {
            _findState.value = _findState.value.copy(
                currentView = FindView.AllAlbums,
                isLoading = true,
                error = null
            )

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllAlbums(DEFAULT_PAGE_SIZE)

                if (result.isSuccess) {
                    val sortedAlbums = result.getOrThrow().sortedWith { a, b ->
                        customAlphabeticalComparator(a.name, b.name)
                    }
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseAlbums = sortedAlbums,
                        error = null
                    )
                    LogManager.i(TAG, "Loaded ${_findState.value.browseAlbums.size} albums")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        error = "Failed to load albums. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading albums", e)
                _findState.value = _findState.value.copy(
                    isLoading = false,
                    error = "Failed to load albums. Please check your connection."
                )
            }
        }
    }

    private fun browseAllArtists() {
        ApplicationScopeManager.scope.launch {
            _findState.value = _findState.value.copy(
                currentView = FindView.AllArtists,
                isLoading = true,
                error = null
            )

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllArtists(DEFAULT_PAGE_SIZE)

                if (result.isSuccess) {
                    val sortedArtists = result.getOrThrow().sortedWith { a, b ->
                        customAlphabeticalComparator(a.name, b.name)
                    }
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseArtists = sortedArtists,
                        error = null
                    )
                    LogManager.i(TAG, "Loaded ${_findState.value.browseArtists.size} artists")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        error = "Failed to load artists. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading artists", e)
                _findState.value = _findState.value.copy(
                    isLoading = false,
                    error = "Failed to load artists. Please check your connection."
                )
            }
        }
    }

    private fun loadMoreSongs(offset: Int) {
        ApplicationScopeManager.scope.launch {
            _findState.value = _findState.value.copy(isLoadingMore = true)

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllSongs(DEFAULT_PAGE_SIZE)

                if (result.isSuccess) {
                    val newSongs = result.getOrThrow()
                    val allSongsSorted = (_findState.value.browseSongs + newSongs).sortedWith { a, b ->
                        customAlphabeticalComparator(a.title, b.title)
                    }
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        browseSongs = allSongsSorted
                    )
                    LogManager.i(TAG, "Loaded ${newSongs.size} additional songs")
                } else {
                    _findState.value = _findState.value.copy(isLoadingMore = false)
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading more songs", e)
                _findState.value = _findState.value.copy(isLoadingMore = false)
            }
        }
    }

    private fun loadMoreAlbums(offset: Int) {
        ApplicationScopeManager.scope.launch {
            _findState.value = _findState.value.copy(isLoadingMore = true)

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllAlbums(DEFAULT_PAGE_SIZE)

                if (result.isSuccess) {
                    val newAlbums = result.getOrThrow()
                    val allAlbumsSorted = (_findState.value.browseAlbums + newAlbums).sortedWith { a, b ->
                        customAlphabeticalComparator(a.name, b.name)
                    }
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        browseAlbums = allAlbumsSorted
                    )
                    LogManager.i(TAG, "Loaded ${newAlbums.size} additional albums")
                } else {
                    _findState.value = _findState.value.copy(isLoadingMore = false)
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading more albums", e)
                _findState.value = _findState.value.copy(isLoadingMore = false)
            }
        }
    }

    private fun loadMoreArtists(offset: Int) {
        ApplicationScopeManager.scope.launch {
            _findState.value = _findState.value.copy(isLoadingMore = true)

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllArtists(DEFAULT_PAGE_SIZE)

                if (result.isSuccess) {
                    val newArtists = result.getOrThrow()
                    val allArtistsSorted = (_findState.value.browseArtists + newArtists).sortedWith { a, b ->
                        customAlphabeticalComparator(a.name, b.name)
                    }
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        browseArtists = allArtistsSorted
                    )
                    LogManager.i(TAG, "Loaded ${newArtists.size} additional artists")
                } else {
                    _findState.value = _findState.value.copy(isLoadingMore = false)
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading more artists", e)
                _findState.value = _findState.value.copy(isLoadingMore = false)
            }
        }
    }

    private fun handleHistoryItemSelection(item: now.link.periphyllidae.mvi.HistoryItem) {
        when (item.type) {
            is HistoryItemType.Song -> {
                val song = item.data as? Song
                song?.let {
                    playSong(it)
                    addToHistory(item)
                }
            }
            is HistoryItemType.Album -> {
                val album = item.data as? Album
                album?.let {
                    addToHistory(item)
                    // Navigation to album detail will be handled by the UI
                }
            }
            is HistoryItemType.Artist -> {
                val artist = item.data as? Artist
                artist?.let {
                    addToHistory(item)
                    // Navigation to artist detail will be handled by the UI
                }
            }
        }
    }

    private fun loadSearchHistory() {
        ApplicationScopeManager.scope.launch {
            try {
                val history = searchHistoryManager.getHistory()
                _findState.value = _findState.value.copy(searchHistory = history)
                LogManager.i(TAG, "Loaded ${history.size} search history items")
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading search history", e)
            }
        }
    }

    private fun addToHistory(item: now.link.periphyllidae.mvi.HistoryItem) {
        ApplicationScopeManager.scope.launch {
            try {
                searchHistoryManager.addToHistory(item)
                val updatedHistory = searchHistoryManager.getHistory()
                _findState.value = _findState.value.copy(searchHistory = updatedHistory)
            } catch (e: Exception) {
                LogManager.e(TAG, "Error adding item to history", e)
            }
        }
    }

    private fun clearHistory() {
        ApplicationScopeManager.scope.launch {
            try {
                searchHistoryManager.clearHistory()
                _findState.value = _findState.value.copy(searchHistory = emptyList())
                LogManager.i(TAG, "Search history cleared")
            } catch (e: Exception) {
                LogManager.e(TAG, "Error clearing search history", e)
            }
        }
    }

    // Public methods for UI
    fun playSong(song: Song) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlaySong(song))
        }
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlayAlbum(album))
        }
    }

    fun playAllSongsOfArtist(artist: Artist) {
        viewModelScope.launch {
            musicStore.handleIntent(MusicIntent.PlayAllSongsOfArtist(artist))
        }
    }

    fun addToHistoryFromSong(song: Song) {
        val historyItem = searchHistoryManager.createHistoryItem(song)
        handleIntent(FindIntent.AddToHistory(historyItem))
    }

    fun addToHistoryFromAlbum(album: Album) {
        val historyItem = searchHistoryManager.createHistoryItem(album)
        handleIntent(FindIntent.AddToHistory(historyItem))
    }

    fun addToHistoryFromArtist(artist: Artist) {
        val historyItem = searchHistoryManager.createHistoryItem(artist)
        handleIntent(FindIntent.AddToHistory(historyItem))
    }

    fun setSearchBarFocus(focused: Boolean) {
        _isSearchBarFocused.value = focused
    }

    fun updateSearchQuery(query: String) {
        _findState.value = _findState.value.copy(searchQuery = query)
    }

    fun performSearch(query: String) {
        handleIntent(FindIntent.SearchQuery(query))
    }

    fun clearSearch() {
        handleIntent(FindIntent.SearchQuery(""))
    }

    fun backToSearch() {
        handleIntent(FindIntent.BackToSearch)
    }
}