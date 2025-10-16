package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.SearchIntent
import now.link.periphyllidae.mvi.SearchState
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.repository.SubsonicRepositoryManager
import now.link.periphyllidae.utils.LogManager
import now.link.periphyllidae.utils.ApplicationScopeManager

/**
 * Page-scoped ViewModel for the Search screen
 * Handles search-specific UI logic and interacts with SearchStore and MusicStore
 */
class SearchViewModel(
    private val subsonicRepositoryManager: SubsonicRepositoryManager,
    private val musicStore: MusicStore
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
    }

    // Search state - moved from SearchStore
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // Local UI state for search screen
    private val _isSearchBarFocused = MutableStateFlow(false)
    val isSearchBarFocused: StateFlow<Boolean> = _isSearchBarFocused.asStateFlow()

    fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> {
                _searchState.value = _searchState.value.copy(query = intent.query)
            }
            is SearchIntent.Search -> {
                search(intent.query)
            }
            is SearchIntent.ClearResults -> {
                _searchState.value = _searchState.value.copy(
                    query = "",
                    results = null,
                    error = null
                )
            }
        }
    }

    private fun search(query: String) {
        if (query.isBlank()) {
            _searchState.value = _searchState.value.copy(results = null)
            return
        }

        // Use shared application scope to prevent memory leaks
        ApplicationScopeManager.scope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true, error = null)

            try {
                val repository = subsonicRepositoryManager.getCurrentRepository()
                val result = repository.search(query.trim())
                if (result.isSuccess) {
                    _searchState.value = _searchState.value.copy(
                        isLoading = false,
                        results = result.getOrThrow(),
                        error = null
                    )
                    LogManager.i(TAG, "Search completed for query: $query")
                } else {
                    _searchState.value = _searchState.value.copy(
                        isLoading = false,
                        error = "Search failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Search error", e)
                _searchState.value = _searchState.value.copy(
                    isLoading = false,
                    error = "Search failed. Please check your connection."
                )
            }
        }
    }
  
    fun clearSearch() {
        handleIntent(SearchIntent.ClearResults)
        handleIntent(SearchIntent.UpdateQuery(""))
    }

    fun setSearchBarFocus(focused: Boolean) {
        _isSearchBarFocused.value = focused
    }

    fun playSearchResult(song: now.link.periphyllidae.models.Song) {
        viewModelScope.launch {
            musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.PlaySong(song))
        }
    }

    fun updateQuery(query: String) {
        handleIntent(SearchIntent.UpdateQuery(query))
    }

    fun performSearch(query: String) {
        handleIntent(SearchIntent.Search(query))
    }

    fun playAlbum(album: now.link.periphyllidae.models.Album) {
        viewModelScope.launch {
            musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.PlayAlbum(album))
        }
    }
}
