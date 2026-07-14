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
import now.link.periphyllidae.mvi.MusicState
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
    val musicState: StateFlow<MusicState> = musicStore.state

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
                _findState.value = _findState.value.copy(error = null, loadMoreError = null)
            }

            is FindIntent.BackToSearch -> {
                _findState.value = _findState.value.copy(currentView = FindView.Search)
            }
        }
    }

    private fun search(query: String) {
        ApplicationScopeManager.scope.launch {
            _findState.value = _findState.value.copy(isLoading = true, error = null, loadMoreError = null)

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
                isLoadingMore = false,
                browseSongs = emptyList(),
                hasMoreSongs = false,
                error = null,
                loadMoreError = null
            )

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllSongs(DEFAULT_PAGE_SIZE, offset = 0)

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val sortedSongs = page.sortedWith { a, b ->
                        customAlphabeticalComparator(a.title, b.title)
                    }
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseSongs = sortedSongs,
                        hasMoreSongs = page.size >= DEFAULT_PAGE_SIZE,
                        error = null,
                        loadMoreError = null
                    )
                    LogManager.i(TAG, "Loaded ${_findState.value.browseSongs.size} songs")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseSongs = emptyList(),
                        hasMoreSongs = false,
                        error = "Failed to load songs. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading songs", e)
                _findState.value = _findState.value.copy(
                    isLoading = false,
                    browseSongs = emptyList(),
                    hasMoreSongs = false,
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
                isLoadingMore = false,
                browseAlbums = emptyList(),
                hasMoreAlbums = false,
                error = null,
                loadMoreError = null
            )

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllAlbums(DEFAULT_PAGE_SIZE, offset = 0)

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val sortedAlbums = page.sortedWith { a, b ->
                        customAlphabeticalComparator(a.name, b.name)
                    }
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseAlbums = sortedAlbums,
                        hasMoreAlbums = page.size >= DEFAULT_PAGE_SIZE,
                        error = null,
                        loadMoreError = null
                    )
                    LogManager.i(TAG, "Loaded ${_findState.value.browseAlbums.size} albums")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseAlbums = emptyList(),
                        hasMoreAlbums = false,
                        error = "Failed to load albums. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading albums", e)
                _findState.value = _findState.value.copy(
                    isLoading = false,
                    browseAlbums = emptyList(),
                    hasMoreAlbums = false,
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
                isLoadingMore = false,
                browseArtists = emptyList(),
                hasMoreArtists = false,
                error = null,
                loadMoreError = null
            )

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllArtists(DEFAULT_PAGE_SIZE, offset = 0)

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val sortedArtists = page.sortedWith { a, b ->
                        customAlphabeticalComparator(a.name, b.name)
                    }
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseArtists = sortedArtists,
                        hasMoreArtists = page.size >= DEFAULT_PAGE_SIZE,
                        error = null,
                        loadMoreError = null
                    )
                    LogManager.i(TAG, "Loaded ${_findState.value.browseArtists.size} artists")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoading = false,
                        browseArtists = emptyList(),
                        hasMoreArtists = false,
                        error = "Failed to load artists. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading artists", e)
                _findState.value = _findState.value.copy(
                    isLoading = false,
                    browseArtists = emptyList(),
                    hasMoreArtists = false,
                    error = "Failed to load artists. Please check your connection."
                )
            }
        }
    }

    private fun loadMoreSongs(offset: Int) {
        val currentState = _findState.value
        if (currentState.isLoadingMore || !currentState.hasMoreSongs) return

        _findState.value = currentState.copy(isLoadingMore = true, loadMoreError = null)
        ApplicationScopeManager.scope.launch {
            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllSongs(DEFAULT_PAGE_SIZE, offset = offset)

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val existingIds = _findState.value.browseSongs.map { it.id }.toHashSet()
                    val uniqueNewSongs = page.filter { it.id !in existingIds }
                    val allSongsSorted = (_findState.value.browseSongs + uniqueNewSongs).sortedWith { a, b ->
                        customAlphabeticalComparator(a.title, b.title)
                    }
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        browseSongs = allSongsSorted,
                        hasMoreSongs = page.size >= DEFAULT_PAGE_SIZE,
                        loadMoreError = null
                    )
                    LogManager.i(TAG, "Loaded ${uniqueNewSongs.size} additional songs (page=${page.size})")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        loadMoreError = "Failed to load more songs. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading more songs", e)
                _findState.value = _findState.value.copy(
                    isLoadingMore = false,
                    loadMoreError = "Failed to load more songs. Please check your connection."
                )
            }
        }
    }

    private fun loadMoreAlbums(offset: Int) {
        val currentState = _findState.value
        if (currentState.isLoadingMore || !currentState.hasMoreAlbums) return

        _findState.value = currentState.copy(isLoadingMore = true, loadMoreError = null)
        ApplicationScopeManager.scope.launch {
            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllAlbums(DEFAULT_PAGE_SIZE, offset = offset)

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val existingIds = _findState.value.browseAlbums.map { it.id }.toHashSet()
                    val uniqueNewAlbums = page.filter { it.id !in existingIds }
                    val allAlbumsSorted = (_findState.value.browseAlbums + uniqueNewAlbums).sortedWith { a, b ->
                        customAlphabeticalComparator(a.name, b.name)
                    }
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        browseAlbums = allAlbumsSorted,
                        hasMoreAlbums = page.size >= DEFAULT_PAGE_SIZE,
                        loadMoreError = null
                    )
                    LogManager.i(TAG, "Loaded ${uniqueNewAlbums.size} additional albums (page=${page.size})")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        loadMoreError = "Failed to load more albums. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading more albums", e)
                _findState.value = _findState.value.copy(
                    isLoadingMore = false,
                    loadMoreError = "Failed to load more albums. Please check your connection."
                )
            }
        }
    }

    private fun loadMoreArtists(offset: Int) {
        val currentState = _findState.value
        if (currentState.isLoadingMore || !currentState.hasMoreArtists) return

        _findState.value = currentState.copy(isLoadingMore = true, loadMoreError = null)
        ApplicationScopeManager.scope.launch {
            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.getAllArtists(DEFAULT_PAGE_SIZE, offset = offset)

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val existingIds = _findState.value.browseArtists.map { it.id }.toHashSet()
                    val uniqueNewArtists = page.filter { it.id !in existingIds }
                    val allArtistsSorted = (_findState.value.browseArtists + uniqueNewArtists).sortedWith { a, b ->
                        customAlphabeticalComparator(a.name, b.name)
                    }
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        browseArtists = allArtistsSorted,
                        hasMoreArtists = page.size >= DEFAULT_PAGE_SIZE,
                        loadMoreError = null
                    )
                    LogManager.i(TAG, "Loaded ${uniqueNewArtists.size} additional artists (page=${page.size})")
                } else {
                    _findState.value = _findState.value.copy(
                        isLoadingMore = false,
                        loadMoreError = "Failed to load more artists. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Error loading more artists", e)
                _findState.value = _findState.value.copy(
                    isLoadingMore = false,
                    loadMoreError = "Failed to load more artists. Please check your connection."
                )
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

    fun handleAlbumPlaybackClick(album: Album) {
        viewModelScope.launch {
            val currentMusicState = musicStore.state.value
            val currentSong = currentMusicState.currentSong
            val currentSongId = currentSong?.id
            val isCurrentAlbumSong = currentSong?.albumId == album.id ||
                currentSongId != null && album.songs.any { it.id == currentSongId }

            when {
                isCurrentAlbumSong && currentMusicState.isPlaying -> {
                    musicStore.handleIntent(MusicIntent.Pause)
                }
                isCurrentAlbumSong -> {
                    musicStore.handleIntent(MusicIntent.Play)
                }
                else -> {
                    musicStore.handleIntent(MusicIntent.PlayAlbum(album))
                }
            }
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
