package git.shin.komorei.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Genre
import git.shin.komorei.model.Source
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    val genres: List<Genre> = repository.genres

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow<Genre?>(null)
    val selectedGenre: StateFlow<Genre?> = _selectedGenre.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Debounce 300ms search flow to prevent excessive query calls
        _searchQuery
            .debounce(300.milliseconds)
            .onEach { query ->
                executeSearch(query, _selectedGenre.value)
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _selectedGenre.value = null
        _searchUiState.value = SearchUiState.Idle
    }

    fun selectGenre(genre: Genre) {
        if (_selectedGenre.value?.id == genre.id) {
            _selectedGenre.value = null
            if (_searchQuery.value.isBlank()) {
                _searchUiState.value = SearchUiState.Idle
            } else {
                executeSearch(_searchQuery.value, null)
            }
        } else {
            _selectedGenre.value = genre
            executeSearch(_searchQuery.value, genre)
        }
    }

    fun getSourceName(sourceId: String): String {
        return repository.getSourceName(sourceId)
    }

    private fun executeSearch(query: String, genre: Genre?) {
        if (query.isBlank() && genre == null) {
            _searchUiState.value = SearchUiState.Idle
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchUiState.value = SearchUiState.Loading
            try {
                val resultsBySource = repository.searchMultiSource(
                    query = query,
                    selectedGenreId = genre?.id
                )
                val totalCount = resultsBySource.values.sumOf { it.size }
                _searchUiState.value = SearchUiState.Success(
                    resultsBySource = resultsBySource,
                    totalCount = totalCount
                )
            } catch (e: Exception) {
                _searchUiState.value = SearchUiState.Error(e.localizedMessage ?: "Lỗi tìm kiếm")
            }
        }
    }
}
