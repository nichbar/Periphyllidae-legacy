package now.link.periphyllidae.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.SearchIntent
import now.link.periphyllidae.mvi.SearchState
import now.link.periphyllidae.store.SearchStore
import now.link.periphyllidae.store.MusicStore

/**
 * Page-scoped ViewModel for the Search screen
 * Handles search-specific UI logic and interacts with SearchStore and MusicStore
 */
class SearchViewModel(
    private val searchStore: SearchStore,
    private val musicStore: MusicStore
) : ViewModel() {
    
    val state: StateFlow<SearchState> = searchStore.state
    
    // Local UI state for search screen
    private val _isSearchBarFocused = MutableStateFlow(false)
    val isSearchBarFocused: StateFlow<Boolean> = _isSearchBarFocused.asStateFlow()
    
    fun handleSearchIntent(intent: SearchIntent) {
        searchStore.handleIntent(intent)
    }
    
    fun search(query: String) {
        viewModelScope.launch {
            searchStore.handleIntent(SearchIntent.UpdateQuery(query))
            if (query.isNotBlank()) {
                searchStore.handleIntent(SearchIntent.Search(query))
            }
        }
    }
    
    fun clearSearch() {
        viewModelScope.launch {
            searchStore.handleIntent(SearchIntent.ClearResults)
            searchStore.handleIntent(SearchIntent.UpdateQuery(""))
        }
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
        viewModelScope.launch {
            searchStore.handleIntent(SearchIntent.UpdateQuery(query))
        }
    }
    
    fun performSearch(query: String) {
        viewModelScope.launch {
            searchStore.handleIntent(SearchIntent.Search(query))
        }
    }
    
    fun playAlbum(album: now.link.periphyllidae.models.Album) {
        viewModelScope.launch {
            musicStore.handleIntent(now.link.periphyllidae.mvi.MusicIntent.PlayAlbum(album))
        }
    }
}
