package git.shin.komorei.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.ContentRatingFilter
import git.shin.komorei.model.Genre
import git.shin.komorei.model.Source
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Multi-source search & discovery VM (Khám Phá tab, Aidoku-style global
 * search). One debounced pipeline combines the query, the genre shortcut and
 * the three global filters — content rating / language / sources — into a
 * single `AnimeRepository.searchMultiSource` call (results flattened into a
 * grid by [SearchDiscoveryScreen]).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository,
) : ViewModel() {

    val genres: List<Genre> = repository.genres

    /** Non-aggregator sources (the "Sources" filter options). */
    val sources: List<Source> = repository.sources.filter { !it.isAggregator }

    /**
     * Test seam — JVM tests set 0 because the test Main scheduler's virtual
     * clock never advances, so a real 300ms debounce would never fire.
     */
    internal var searchDebounceMillis = 300L

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow<Genre?>(null)
    val selectedGenre: StateFlow<Genre?> = _selectedGenre.asStateFlow()

    private val _contentRating = MutableStateFlow(ContentRatingFilter.ALL)
    val contentRating: StateFlow<ContentRatingFilter> = _contentRating.asStateFlow()

    /** Selected language code (e.g. "vi"); null = all languages. */
    private val _language = MutableStateFlow<String?>(null)
    val language: StateFlow<String?> = _language.asStateFlow()

    /** Selected source ids; empty = all non-aggregator sources. */
    private val _sourceFilter = MutableStateFlow<Set<String>>(emptySet())
    val sourceFilter: StateFlow<Set<String>> = _sourceFilter.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private var searchJob: Job? = null

    private data class Params(
        val query: String,
        val genreId: String?,
        val rating: ContentRatingFilter,
        val language: String?,
        val sourceIds: Set<String>,
    ) {
        /** Nothing typed and every filter at its default → no search call. */
        val isDefault: Boolean
            get() = query.isBlank() && genreId == null &&
                rating == ContentRatingFilter.ALL && language == null && sourceIds.isEmpty()
    }

    init {
        // One debounced pipeline: query, genre shortcut and the three global
        // filters all funnel into the same search call.
        combine(
            _searchQuery, _selectedGenre, _contentRating, _language, _sourceFilter,
        ) { query, genre, rating, language, sourceIds ->
            Params(query, genre?.id, rating, language, sourceIds)
        }
            .debounce { searchDebounceMillis }
            .distinctUntilChanged()
            .onEach { executeSearch(it) }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectGenre(genre: Genre) {
        _selectedGenre.value = if (_selectedGenre.value?.id == genre.id) null else genre
    }

    fun setContentRating(rating: ContentRatingFilter) {
        _contentRating.value = rating
    }

    fun setLanguage(language: String?) {
        _language.value = language
    }

    fun setSourceFilter(sourceIds: Set<String>) {
        _sourceFilter.value = sourceIds
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _selectedGenre.value = null
        _contentRating.value = ContentRatingFilter.ALL
        _language.value = null
        _sourceFilter.value = emptySet()
        _searchUiState.value = SearchUiState.Idle
        searchJob?.cancel()
    }

    fun retrySearch() {
        executeSearch(currentParams())
    }

    fun getSourceName(sourceId: String): String = repository.getSourceName(sourceId)

    private fun currentParams() = Params(
        query = _searchQuery.value,
        genreId = _selectedGenre.value?.id,
        rating = _contentRating.value,
        language = _language.value,
        sourceIds = _sourceFilter.value,
    )

    private fun executeSearch(params: Params) {
        if (params.isDefault) {
            searchJob?.cancel()
            _searchUiState.value = SearchUiState.Idle
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchUiState.value = SearchUiState.Loading
            try {
                val resultsBySource = repository.searchMultiSource(
                    query = params.query,
                    selectedGenreId = params.genreId,
                    contentRating = params.rating.repositoryValue,
                    languages = params.language?.let { setOf(it) } ?: emptySet(),
                    sourceIds = params.sourceIds,
                )
                val totalCount = resultsBySource.values.sumOf { it.size }
                _searchUiState.value = SearchUiState.Success(
                    resultsBySource = resultsBySource,
                    totalCount = totalCount,
                )
            } catch (e: Exception) {
                _searchUiState.value = SearchUiState.Error(
                    e.localizedMessage ?: appContext.getString(R.string.error_search),
                )
            }
        }
    }
}