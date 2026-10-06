package git.shin.komorei.ui.screens.search

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SearchHistoryStore
import git.shin.komorei.data.SourceSearchEvent
import git.shin.komorei.model.Anime
import git.shin.komorei.model.ContentRatingFilter
import git.shin.komorei.model.Genre
import git.shin.komorei.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Multi-source search & discovery VM (Tìm Kiếm tab, Aidoku-style global
 * search). One debounced pipeline combines the query and the three global
 * filters — content rating / language / sources — into a single
 * `AnimeRepository.searchMultiSource` call (results flattened into a grid
 * by [SearchDiscoveryScreen]).
 *
 * Search history is persisted via [SearchHistoryStore] and displayed on
 * the idle screen when there is no active query.
 *
 * Every search parameter is mirrored into the [SavedStateHandle], so the
 * search survives tab switches and process death: the tab navigation pops
 * the entry with `saveState = true` and the restored VM re-reads these
 * keys in `init`.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository,
    private val searchHistoryStore: SearchHistoryStore,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    internal companion object {
        const val KEY_QUERY = "search_query"
        const val KEY_GENRE = "search_genre"
        const val KEY_RATING = "search_rating"
        const val KEY_LANGUAGE = "search_language"
        const val KEY_SOURCES = "search_sources"
    }

    val genres: List<Genre> = repository.genres

    /** Non-aggregator sources (the "Sources" filter options). Collected from the
     * reactive source list so the Discovery tab reflects installs/removals. */
    val sources: StateFlow<List<Source>> =
        repository.sourcesFlow
            .map { all -> all.filter { !it.isAggregator } }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                repository.sources.filter { !it.isAggregator },
            )

    /** Recent search queries (most recent first), persisted across sessions. */
    val searchHistory: StateFlow<List<String>> = searchHistoryStore.history

    /** True while a pull-to-refresh reload of the results is in flight. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Test seam — JVM tests set 0 because the test Main scheduler's virtual
     * clock never advances, so a real 300ms debounce would never fire.
     */
    internal var searchDebounceMillis = 300L

    private val handle = savedStateHandle

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
        /** A non-blank query or selected genre means there is something to search for. */
        val hasQueryOrGenre: Boolean
            get() = query.isNotBlank() || genreId != null
    }

    init {
        // Restore the last search from the SavedStateHandle (tab pop/recreate
        // or process death) BEFORE the pipeline collects, so the first emission
        // already carries the restored params and re-runs that search.
        _searchQuery.value = handle.get<String>(KEY_QUERY).orEmpty()
        handle.get<String>(KEY_GENRE)?.let { id -> _selectedGenre.value = genres.find { it.id == id } }
        handle.get<String>(KEY_RATING)?.let { name ->
            _contentRating.value = runCatching { ContentRatingFilter.valueOf(name) }.getOrNull()
                ?: ContentRatingFilter.ALL
        }
        _language.value = handle.get<String>(KEY_LANGUAGE)
        handle.get<String>(KEY_SOURCES)?.let { raw ->
            _sourceFilter.value = raw.split(',').filter { it.isNotBlank() }.toSet()
        }

        // One debounced pipeline: query, genre shortcut and the three global
        // filters all funnel into the same search call.
        combine(
            _searchQuery,
            _selectedGenre,
            _contentRating,
            _language,
            _sourceFilter,
        ) { query, genre, rating, language, sourceIds ->
            Params(query, genre?.id, rating, language, sourceIds)
        }
            // The first emission (the restored/default params at collection) is
            // dropped: its debounce would read the default 300ms window even
            // when the test set 0 after construction. Restored non-default
            // searches run directly below instead.
            .drop(1)
            .debounce { searchDebounceMillis }
            .distinctUntilChanged()
            .onEach { executeSearch(it) }
            .launchIn(viewModelScope)

        // Restored search (tab switch / process death): run it without waiting
        // for a debounced pipeline emission — only if there's something
        // meaningful to search for (query or genre; filters alone don't).
        if (!currentParams().hasQueryOrGenre) {
            _searchUiState.value = SearchUiState.Idle
        } else {
            executeSearch(currentParams())
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        handle[KEY_QUERY] = query
    }

    fun selectGenre(genre: Genre) {
        _selectedGenre.value = if (_selectedGenre.value?.id == genre.id) null else genre
        handle[KEY_GENRE] = _selectedGenre.value?.id
    }

    fun setContentRating(rating: ContentRatingFilter) {
        _contentRating.value = rating
        handle[KEY_RATING] = rating.name
    }

    fun setLanguage(language: String?) {
        _language.value = language
        handle[KEY_LANGUAGE] = language
    }

    fun setSourceFilter(sourceIds: Set<String>) {
        _sourceFilter.value = sourceIds
        handle[KEY_SOURCES] = sourceIds.joinToString(",")
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _selectedGenre.value = null
        _contentRating.value = ContentRatingFilter.ALL
        _language.value = null
        _sourceFilter.value = emptySet()
        _searchUiState.value = SearchUiState.Idle
        searchJob?.cancel()
        handle[KEY_QUERY] = ""
        handle[KEY_GENRE] = null
        handle[KEY_RATING] = null
        handle[KEY_LANGUAGE] = null
        handle[KEY_SOURCES] = null
    }

    fun retrySearch() {
        executeSearch(currentParams())
    }

    /** Pull-to-refresh on the results list: re-runs the current search. */
    fun refreshSearch() {
        val state = _searchUiState.value
        if (state !is SearchUiState.Success && state !is SearchUiState.Searching) return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                executeSearch(currentParams())
                // `executeSearch` only hands back the job it just started, so
                // without the join the indicator cleared the moment the gesture
                // ended — the old results sat on screen for another second or
                // two and the pull looked like it did nothing.
                searchJob?.join()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Adds the current query to search history (if non-blank). */
    fun addToSearchHistory() {
        val query = _searchQuery.value.trim()
        if (query.isNotBlank()) {
            searchHistoryStore.addQuery(query)
        }
    }

    fun clearSearchHistory() {
        searchHistoryStore.clearHistory()
    }

    fun removeHistoryItem(query: String) {
        searchHistoryStore.removeQuery(query)
    }

    fun getSourceName(sourceId: String): String = repository.getSourceName(sourceId)

    private fun currentParams() =
        Params(
            query = _searchQuery.value,
            genreId = _selectedGenre.value?.id,
            rating = _contentRating.value,
            language = _language.value,
            sourceIds = _sourceFilter.value,
        )

    private fun executeSearch(params: Params) {
        if (!params.hasQueryOrGenre) {
            searchJob?.cancel()
            _searchUiState.value = SearchUiState.Idle
            return
        }

        // Add query to history when executing a search
        addToSearchHistory()

        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                val candidates =
                    repository.candidateSourcesForSearch(
                        contentRating = params.rating.repositoryValue,
                        languages = params.language?.let { setOf(it) } ?: emptySet(),
                        sourceIds = params.sourceIds,
                    )
                val resultsBySource = mutableMapOf<Source, List<Anime>>()
                val sourceErrors = mutableMapOf<Source, String>()
                val emptySources = mutableSetOf<Source>()

                // Search started — every candidate source renders its own section
                // immediately; pending ones show their own loading shimmer.
                _searchUiState.value =
                    SearchUiState.Searching(
                        candidateSources = candidates,
                        resultsBySource = resultsBySource,
                        sourceErrors = sourceErrors,
                        emptySources = emptySources,
                    )
                try {
                    repository
                        .searchMultiSourceStream(
                            query = params.query,
                            selectedGenreId = params.genreId,
                            contentRating = params.rating.repositoryValue,
                            languages = params.language?.let { setOf(it) } ?: emptySet(),
                            sourceIds = params.sourceIds,
                        ).collect { event ->
                            when (event) {
                                is SourceSearchEvent.Completed -> {
                                    if (event.results.isNotEmpty()) {
                                        resultsBySource[event.source] =
                                            event.results.distinctBy { it.sourceId to it.id }
                                    } else {
                                        // Finished with zero matches — record it so the
                                        // section shows "không có kết quả" instead of
                                        // keeping its shimmer / disappearing entirely.
                                        emptySources.add(event.source)
                                    }
                                }
                                is SourceSearchEvent.Failed -> {
                                    sourceErrors[event.source] = event.message
                                }
                            }
                            // Update each source's own section as its result lands —
                            // the other sections are untouched and stay as they are.
                            _searchUiState.value =
                                SearchUiState.Searching(
                                    candidateSources = candidates,
                                    resultsBySource = resultsBySource,
                                    sourceErrors = sourceErrors,
                                    emptySources = emptySources,
                                )
                        }
                    // All sources finished — terminal state with the totals.
                    _searchUiState.value =
                        SearchUiState.Success(
                            resultsBySource = resultsBySource,
                            totalCount = resultsBySource.values.sumOf { it.size },
                            sourceErrors = sourceErrors,
                            emptySources = emptySources,
                        )
                } catch (e: CancellationException) {
                    // A newer search superseded this job (typing, filter change,
                    // Hủy / clearSearch) or the VM scope died — cancellation is
                    // NOT an error and must never surface as the Error state
                    // (it previously leaked "StandaloneCoroutine was cancelled"
                    // into the UI with a bogus Thử lại button).
                    throw e
                } catch (e: Exception) {
                    _searchUiState.value =
                        SearchUiState.Error(
                            e.localizedMessage ?: appContext.getString(R.string.error_search),
                        )
                }
            }
    }
}
