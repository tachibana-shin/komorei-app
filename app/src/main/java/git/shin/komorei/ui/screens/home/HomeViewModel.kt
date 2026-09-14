package git.shin.komorei.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.HomeComponent
import git.shin.komorei.model.Listing
import git.shin.komorei.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home tab state for one source: the FULL [getHome] layout — every
 * [HomeComponent] row of the runner's `home()` (BigScroller / ImageScroller /
 * Scroller / AnimeEpisodeList / AnimeList / Filters / Links), in source order.
 */
data class SourceHomeData(
    val home: List<HomeComponent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * One source's listing state (Aidoku-style "get_dynamic_listings" chips above
 * the home content). [selectedIndex] is 0-based over `[HOME] + listings`
 * exactly like Aidoku's listing header — 0 = home page, i = listings[i-1],
 * whose paged entries live in [page].
 */
data class SourceListingState(
    val listings: List<Listing> = emptyList(),
    val listingsLoading: Boolean = false,
    val selectedIndex: Int = 0,
    val page: ListingPageState = ListingPageState(),
)

/** Paged entries of the source's currently selected listing. */
data class ListingPageState(
    val items: List<Anime> = emptyList(),
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val loadedPage: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository
) : ViewModel() {

    val sources: List<Source> = repository.sources

    private val _sourceDataMap = MutableStateFlow<Map<String, SourceHomeData>>(emptyMap())
    val sourceDataMap: StateFlow<Map<String, SourceHomeData>> = _sourceDataMap.asStateFlow()

    private val _listingStateMap = MutableStateFlow<Map<String, SourceListingState>>(emptyMap())
    val listingStateMap: StateFlow<Map<String, SourceListingState>> = _listingStateMap.asStateFlow()

    init {
        // Pre-fetch data for all sources so swiping horizontally between source tabs is fast & instant
        sources.forEach { source ->
            loadSourceData(source.id)
        }
    }

    fun getSourceName(sourceId: String): String {
        return repository.getSourceName(sourceId)
    }

    fun loadSourceData(sourceId: String) {
        viewModelScope.launch {
            _sourceDataMap.update { map ->
                val existing = map[sourceId] ?: SourceHomeData()
                map + (sourceId to existing.copy(isLoading = true, error = null))
            }
            runCatching {
                val home = repository.getHome(sourceId)
                _sourceDataMap.update { map ->
                    map + (sourceId to SourceHomeData(
                        home = home,
                        isLoading = false,
                        error = null,
                    ))
                }
            }.onFailure { e ->
                _sourceDataMap.update { map ->
                    val existing = map[sourceId] ?: SourceHomeData()
                    map + (sourceId to existing.copy(
                        isLoading = false,
                        error = e.message ?: appContext.getString(R.string.error_load_data)
                    ))
                }
            }
        }
    }

    // ── listings (Aidoku "get_dynamic_listings" chips) ──────────────────────

    /**
     * Loads the source's dynamic listings (runner `listings()`). Called when
     * the source's home page becomes visible; cached afterwards. The "all"
     * aggregator unions every source's listings (see [AnimeRepository.getListings]).
     */
    fun loadListings(sourceId: String) {
        val current = _listingStateMap.value[sourceId] ?: SourceListingState()
        if (current.listings.isNotEmpty() || current.listingsLoading) return
        _listingStateMap.update { map ->
            map + (sourceId to current.copy(listingsLoading = true))
        }
        viewModelScope.launch {
            runCatching { repository.getListings(sourceId) }
                .onSuccess { lists ->
                    _listingStateMap.update { map ->
                        val existing = map[sourceId] ?: SourceListingState()
                        map + (sourceId to existing.copy(
                            listings = lists,
                            listingsLoading = false,
                            // Drop an index that no longer points at a listing.
                            selectedIndex = existing.selectedIndex.coerceAtMost(lists.size),
                        ))
                    }
                }
                .onFailure {
                    // Chips simply stay hidden on failure (next load retries).
                    _listingStateMap.update { map ->
                        val existing = map[sourceId] ?: SourceListingState()
                        map + (sourceId to existing.copy(listingsLoading = false))
                    }
                }
        }
    }

    /**
     * Picks the chip at [selectedIndex] (0 = HOME, i = listings[i-1]) and swaps
     * the page content below to that listing (Aidoku listing header behavior).
     * Selecting a listing always (re)loads it — superseding any in-flight load.
     */
    fun selectListing(sourceId: String, selectedIndex: Int) {
        val state = _listingStateMap.value[sourceId] ?: return
        if (selectedIndex < 0 || selectedIndex > state.listings.size) return
        if (state.selectedIndex == selectedIndex) return
        _listingStateMap.update { map ->
            map + (sourceId to state.copy(selectedIndex = selectedIndex))
        }
        if (selectedIndex > 0) loadListingPage(sourceId, reset = true, supersede = true)
    }

    /**
     * Loads (or appends) a page of the source's selected listing.
     *
     * A reset ([reset] = true) ALWAYS starts from page 1 — `loadedPage` is
     * shared per source and must not leak across listings (reusing it made a
     * chip switch fetch page N+1 of the NEW listing, returning empty for every
     * small catalog). Stale in-flight results are dropped when the user
     * switches chips before they land, and a new chip supersedes an in-flight
     * load instead of being swallowed by its `isLoading` flag.
     */
    fun loadListingPage(sourceId: String, reset: Boolean, supersede: Boolean = false) {
        val state = _listingStateMap.value[sourceId] ?: return
        val listing = state.listings.getOrNull(state.selectedIndex - 1) ?: return
        val page = state.page
        if (page.isLoading || (page.isLoadingMore && !supersede)) return
        if (!reset && !page.hasNextPage) return

        val targetIndex = state.selectedIndex
        viewModelScope.launch {
            _listingStateMap.update { map ->
                val existing = map[sourceId] ?: return@update map
                map + (sourceId to existing.copy(
                    page = if (reset) {
                        // New listing: clear the previous listing's items + progress.
                        ListingPageState(isLoading = true)
                    } else {
                        existing.page.copy(isLoadingMore = true, error = null)
                    },
                ))
            }
            val nextPage = if (reset) 1 else page.loadedPage + 1
            runCatching { repository.getListing(sourceId, listing, nextPage) }
                .onSuccess { result ->
                    _listingStateMap.update { map ->
                        val existing = map[sourceId] ?: return@update map
                        // The user switched chips while this load was in flight.
                        if (existing.selectedIndex != targetIndex) return@update map
                        map + (sourceId to existing.copy(
                            page = ListingPageState(
                                items = (if (reset) result.entries else existing.page.items + result.entries)
                                    .distinctBy { it.id },
                                hasNextPage = result.hasNextPage,
                                loadedPage = nextPage,
                            ),
                        ))
                    }
                }
                .onFailure {
                    _listingStateMap.update { map ->
                        val existing = map[sourceId] ?: return@update map
                        if (existing.selectedIndex != targetIndex) return@update map
                        map + (sourceId to existing.copy(
                            page = existing.page.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                error = appContext.getString(R.string.error_load_data),
                            ),
                        ))
                    }
                }
        }
    }

    /** Infinite-scroll entry: append the next listing page. */
    fun loadListingMore(sourceId: String) = loadListingPage(sourceId, reset = false)
}