package git.shin.komorei.ui.screens.listing

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Listing
import git.shin.komorei.model.ListingKind
import git.shin.komorei.ui.navigation.ListingArgCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state of the [ListingScreen] — a paginated grid fed by `animeList`. */
data class ListingUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<Anime> = emptyList(),
    val hasNextPage: Boolean = true,
    val error: String? = null,
)

/**
 * One source listing, paginated. The runner (`animeList(listing, page)`) is
 * reached only through the repository → `KrxSourceRegistry.call`, so every
 * wasm call rides the source's dedicated IO thread — never the main thread.
 *
 * The [Listing] itself arrives via the nav route (`sourceId` + URL-safe JSON
 * in `SavedStateHandle`, decoded through [ListingArgCodec]).
 */
@HiltViewModel
class ListingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository,
) : ViewModel() {

    private companion object {
        const val ARG_SOURCE_ID = "sourceId"
        const val ARG_LISTING = "listingArg"
    }

    /** The source whose catalog this listing belongs to. */
    val sourceId: String = savedStateHandle.get<String>(ARG_SOURCE_ID).orEmpty()

    /** The listing to page through (falls back to a generic one on a bad route). */
    val listing: Listing = ListingArgCodec.decode(savedStateHandle.get<String>(ARG_LISTING))
        ?: Listing(
            id = "latest",
            name = appContext.getString(R.string.listing_default_title),
            kind = ListingKind.LIST,
        )

    val listingName: String get() = listing.name
    val sourceName: String get() = repository.getSourceName(sourceId)

    private val _uiState = MutableStateFlow(ListingUiState())
    val uiState: StateFlow<ListingUiState> = _uiState.asStateFlow()

    /** The last page we successfully appended (1-based). */
    private var loadedPage = 0

    init {
        loadMore(reset = true)
    }

    /**
     * Loads the next page. [reset] reloads the first page (initial load and
     * retry); otherwise appends page `loadedPage + 1` while more exist.
     */
    fun loadMore(reset: Boolean = false) {
        if (sourceId.isEmpty()) return
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return
        if (!reset && !state.hasNextPage) return

        viewModelScope.launch {
            _uiState.update { current ->
                if (reset) current.copy(isLoading = true, error = null)
                else current.copy(isLoadingMore = true)
            }
            try {
                val page = repository.getListing(sourceId, listing, loadedPage + 1)
                _uiState.update { current ->
                    ListingUiState(
                        items = (if (reset) page.entries else current.items + page.entries)
                            .distinctBy { it.id },
                        hasNextPage = page.hasNextPage,
                    )
                }
                loadedPage++
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = appContext.getString(R.string.error_load_data),
                    )
                }
            }
        }
    }
}