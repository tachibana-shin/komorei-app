package git.shin.komorei.ui.screens.search

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterValue
import git.shin.komorei.ui.navigation.SearchArgsCodec
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Paged search results of the per-source search screen. [isIdle] is true only
 * before the debounced pipeline has anything to search (blank query AND no
 * enabled filters) so the screen can show the "nhập từ khóa để bắt đầu" hint
 * instead of fetching the whole catalog.
 */
data class SourceSearchUiState(
    val items: List<Anime> = emptyList(),
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val loadedPage: Int = 0,
) {
    val isIdle: Boolean get() = !isLoading && !isLoadingMore && items.isEmpty() && error == null && loadedPage == 0
}

/**
 * State of the per-source search screen (Aidoku `SourceSearchViewModel`): owns
 * the query, the source's `filters()`, the enabled [FilterValue]s and the
 * debounced, paginated results.
 *
 * The query and every filter change flow through a single
 * `combine(...).debounce(300ms)` pipeline so typing and filter toggling share
 * one throttle. A blank query with no active filters stays idle — nothing is
 * fetched — while filters alone (or a non-blank query alone) drive a real
 * search (Aidoku searches with `query: nil` + the enabled filters).
 *
 * The search rides the route URL (`source_search/{sourceId}?query=&filters=`,
 * see [git.shin.komorei.ui.navigation.SearchArgsCodec]) and every change is
 * mirrored back into the [SavedStateHandle], so leaving the screen (tab
 * switch / process death) and returning re-creates the exact search.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SourceSearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private companion object {
        // Same names as the route's optional query arguments; writing a handle
        // key shadows the immutable nav argument so `get` returns the live value.
        const val KEY_QUERY = "query"
        const val KEY_FILTERS = "filters"
    }

    private val handle = savedStateHandle

    val sourceId: String = handle.get<String>("sourceId").orEmpty()

    private val _query = MutableStateFlow(handle.get<String>(KEY_QUERY).orEmpty())
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow<List<Filter>>(emptyList())
    val filters: StateFlow<List<Filter>> = _filters.asStateFlow()

    private val _filtersLoading = MutableStateFlow(false)
    val filtersLoading: StateFlow<Boolean> = _filtersLoading.asStateFlow()

    private val _enabledFilters = MutableStateFlow(SearchArgsCodec.fromJson(handle.get<String>(KEY_FILTERS)))
    val enabledFilters: StateFlow<List<FilterValue>> = _enabledFilters.asStateFlow()

    private val _uiState = MutableStateFlow(SourceSearchUiState())
    val uiState: StateFlow<SourceSearchUiState> = _uiState.asStateFlow()

    /**
     * Debounce window for the query+filters pipeline. Mutable so the JVM test
     * can drop it to 0 (the test Main scheduler never advances its virtual
     * clock, so a 300ms debounce would never fire there).
     */
    internal var searchDebounceMillis: Long = 300

    private var searchJob: Job? = null

    init {
        loadFilters()
        combine(_query, _enabledFilters) { q, f -> q to f }
            // The first emission (the restored/default params at collection) is
            // dropped: its debounce would read the default 300ms window even
            // when the test set 0 after construction. Restored non-default
            // searches run directly below instead.
            .drop(1)
            .debounce { searchDebounceMillis }
            .distinctUntilChanged()
            .onEach { (q, f) ->
                if (q.isBlank() && f.isEmpty()) {
                    searchJob?.cancel()
                    _uiState.value = SourceSearchUiState()
                } else {
                    runSearch(query = q.ifBlank { null }, filters = f, reset = true)
                }
            }.launchIn(viewModelScope)

        // Restored search (route args / tab switch / process death): run it
        // without waiting for a debounced pipeline emission.
        if (_query.value.isNotBlank() || _enabledFilters.value.isNotEmpty()) {
            runSearch(query = _query.value.ifBlank { null }, filters = _enabledFilters.value, reset = true)
        }
    }

    fun loadFilters() {
        if (_filters.value.isNotEmpty() || _filtersLoading.value || sourceId.isBlank()) return
        _filtersLoading.value = true
        viewModelScope.launch {
            runCatching { repository.getFilters(sourceId) }
                .onSuccess { _filters.value = it }
                .onFailure { /* the header row just stays without pills; retried next open */ }
            _filtersLoading.value = false
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
        handle[KEY_QUERY] = query
    }

    /** Clears the query but keeps the enabled filters. */
    fun clearQuery() {
        _query.value = ""
        handle[KEY_QUERY] = ""
    }

    /**
     * Upserts one filter value (Aidoku `update*Filter`): a `null` [value]
     * removes the filter by id, otherwise the id is replaced or appended.
     */
    fun setFilterValue(
        id: String,
        value: FilterValue?,
    ) {
        _enabledFilters.value = _enabledFilters.value.filter { it.id != id } + listOfNotNull(value)
        mirrorFilters()
    }

    /** Replaces ALL filter values at once (full sheet "Áp dụng"). */
    fun replaceAllFilters(values: List<FilterValue>) {
        _enabledFilters.value = values
        mirrorFilters()
    }

    fun resetAllFilters() {
        _enabledFilters.value = emptyList()
        mirrorFilters()
    }

    /** Persists the enabled filters so the restored VM re-creates the search. */
    private fun mirrorFilters() {
        handle[KEY_FILTERS] = SearchArgsCodec.toJson(_enabledFilters.value)
    }

    /** Appends the next page using the current query + filters. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasNextPage || sourceId.isBlank()) return
        runSearch(
            query = _query.value.ifBlank { null },
            filters = _enabledFilters.value,
            reset = false,
            nextPage = state.loadedPage + 1,
        )
    }

    fun retry() {
        runSearch(query = _query.value.ifBlank { null }, filters = _enabledFilters.value, reset = true)
    }

    /**
     * Pull-to-refresh: re-fetches page 1 with the current query + filters
     * without flipping the loading skeleton — the existing results stay on
     * screen under the refresh indicator until the reload lands.
     */
    fun refresh() {
        val state = _uiState.value
        if (sourceId.isBlank() || state.isRefreshing || state.items.isEmpty()) return
        val query = _query.value.ifBlank { null }
        val filters = _enabledFilters.value
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true) }
                runCatching { repository.search(sourceId, query, 1, filters) }
                    .onSuccess { result ->
                        _uiState.update {
                            it.copy(
                                items = result.entries,
                                hasNextPage = result.hasNextPage,
                                loadedPage = 1,
                                isLoading = false,
                                isLoadingMore = false,
                                error = null,
                                isRefreshing = false,
                            )
                        }
                    }.onFailure { e ->
                        _uiState.update { s ->
                            s.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                error = e.message ?: appContext.getString(R.string.source_search_error),
                                isRefreshing = false,
                            )
                        }
                    }
            }
    }

    private fun runSearch(
        query: String?,
        filters: List<FilterValue>,
        reset: Boolean,
        nextPage: Int = 1,
    ) {
        if (sourceId.isBlank()) return
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                val page = if (reset) 1 else nextPage
                _uiState.update { s ->
                    if (reset) s.copy(isLoading = true, error = null) else s.copy(isLoadingMore = true, error = null)
                }
                runCatching { repository.search(sourceId, query, page, filters) }
                    .onSuccess { result ->
                        _uiState.update { s ->
                            if (reset) {
                                s.copy(
                                    items = result.entries,
                                    hasNextPage = result.hasNextPage,
                                    loadedPage = 1,
                                    isLoading = false,
                                    isLoadingMore = false,
                                    error = null,
                                    isRefreshing = false,
                                )
                            } else {
                                s.copy(
                                    items = (s.items + result.entries).distinctBy { it.id },
                                    hasNextPage = result.hasNextPage,
                                    loadedPage = page,
                                    isLoading = false,
                                    isLoadingMore = false,
                                    error = null,
                                    isRefreshing = s.isRefreshing,
                                )
                            }
                        }
                    }.onFailure { e ->
                        _uiState.update { s ->
                            s.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                error = e.message ?: appContext.getString(R.string.source_search_error),
                                isRefreshing = false,
                            )
                        }
                    }
            }
    }
}
