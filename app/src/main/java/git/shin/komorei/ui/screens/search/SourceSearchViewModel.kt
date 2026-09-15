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
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SourceSearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val sourceId: String = savedStateHandle.get<String>("sourceId").orEmpty()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow<List<Filter>>(emptyList())
    val filters: StateFlow<List<Filter>> = _filters.asStateFlow()

    private val _filtersLoading = MutableStateFlow(false)
    val filtersLoading: StateFlow<Boolean> = _filtersLoading.asStateFlow()

    private val _enabledFilters = MutableStateFlow<List<FilterValue>>(emptyList())
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
            .debounce { searchDebounceMillis }
            .distinctUntilChanged()
            .onEach { (q, f) ->
                if (q.isBlank() && f.isEmpty()) {
                    searchJob?.cancel()
                    _uiState.value = SourceSearchUiState()
                } else {
                    runSearch(query = q.ifBlank { null }, filters = f, reset = true)
                }
            }
            .launchIn(viewModelScope)
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
    }

    /** Clears the query but keeps the enabled filters. */
    fun clearQuery() {
        _query.value = ""
    }

    /**
     * Upserts one filter value (Aidoku `update*Filter`): a `null` [value]
     * removes the filter by id, otherwise the id is replaced or appended.
     */
    fun setFilterValue(id: String, value: FilterValue?) {
        _enabledFilters.value = _enabledFilters.value.filter { it.id != id } + listOfNotNull(value)
    }

    /** Replaces ALL filter values at once (full sheet "Áp dụng"). */
    fun replaceAllFilters(values: List<FilterValue>) {
        _enabledFilters.value = values
    }

    fun resetAllFilters() {
        _enabledFilters.value = emptyList()
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

    private fun runSearch(query: String?, filters: List<FilterValue>, reset: Boolean, nextPage: Int = 1) {
        if (sourceId.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
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
                            )
                        } else {
                            s.copy(
                                items = (s.items + result.entries).distinctBy { it.id },
                                hasNextPage = result.hasNextPage,
                                loadedPage = page,
                                isLoading = false,
                                isLoadingMore = false,
                                error = null,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = e.message ?: appContext.getString(R.string.source_search_error),
                        )
                    }
                }
        }
    }
}