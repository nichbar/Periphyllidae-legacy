package now.link.periphyllidae.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.periphyllidae.mvi.*
import now.link.periphyllidae.repository.SubsonicRepository
import now.link.periphyllidae.utils.LogManager

class SearchStore(
    private val subsonicRepository: SubsonicRepository,
    private val musicStore: MusicStore
) {
    // Store-scoped coroutine scope
    private val storeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    
    fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> {
                _state.value = _state.value.copy(query = intent.query)
            }
            is SearchIntent.Search -> {
                search(intent.query)
            }
            is SearchIntent.ClearResults -> {
                _state.value = _state.value.copy(
                    query = "",
                    results = null,
                    error = null
                )
            }
            is SearchIntent.PlaySong -> {
                // Delegate to global music store
                musicStore.handleIntent(MusicIntent.PlaySong(intent.song))
            }
            is SearchIntent.PlayAlbum -> {
                // Delegate to global music store
                musicStore.handleIntent(MusicIntent.PlayAlbum(intent.album))
            }
        }
    }
    
    private fun search(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(results = null)
            return
        }
        
        storeScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val result = subsonicRepository.search(query.trim())
                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        results = result.getOrThrow(),
                        error = null
                    )
                    LogManager.i("SearchStore", "Search completed for query: $query")
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Search failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                LogManager.e("SearchStore", "Search error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Search failed. Please check your connection."
                )
            }
        }
    }
}
